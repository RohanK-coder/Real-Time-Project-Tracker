# Real-Time Collaborative Project Tracker

Kanban boards that several people edit at once. Changes appear live in every open browser,
conflicting edits are caught rather than silently overwritten, and clients that drop off catch up
on what they missed.

**Stack:** Spring Boot 3.5 (Java 21) · PostgreSQL 16 · Redis 7 · STOMP over WebSocket · React 18 +
Redux Toolkit · nginx · Docker · Testcontainers · k6

| Area | What's in it |
|---|---|
| **Real-time systems** | Per-board event sequence numbers, Redis pub/sub fan-out across replicas, out-of-order buffering, gap detection and `/changes?sinceSeq=` catch-up after reconnects, heartbeats, exponential backoff with jitter |
| **API design** | Resource-oriented REST, RFC 9457 problem-details errors, 409 with the current state on conflicts, consistent pagination envelope, server-side search, idempotent soft deletes with tombstones |
| **Collaboration** | Board membership with owner / editor / viewer roles, activity history, notifications (persisted and pushed live), a conflict-resolution dialog, edit drafts that survive concurrent moves |
| **Caching** | Redis-backed membership cache shared by all replicas, evicted on change (and again after commit to close a race), with database fallback when Redis is down |
| **Testing** | 28 integration tests against real Postgres and Redis, including true parallel multi-user editing; k6 load tests with explicit "no lost update" checks |
| **Production operations** | Flyway migrations, prod profile, non-root layered images, readiness and liveness probes, Prometheus metrics, graceful shutdown, login rate limiting, HMAC-verified webhooks, failure-injection results |

## Architecture

```
 Browser ──HTTPS──► nginx ──/api──►  backend replica 1 ─┐
 (React)            (static +  └───►  backend replica 2 ─┤──► PostgreSQL (source of truth: tasks,
    ▲               proxy)     /ws    (Spring Boot)     │     activity, notifications, board seq)
    │                                       ▲           │
    └──── STOMP over WebSocket ─────────────┘           └──► Redis (pub/sub fan-out, membership
          /topic/boards/{id}, /user/queue/notifications        cache, rate-limit counters)
```

**The write path** (`TaskService`):

1. Lock the board row (`SELECT … FOR UPDATE`).
2. Compare the client's `version` with the stored one. If it is stale, return **409** with the current task.
3. Apply the change, take the board's next `seq`, and write the activity entry and notifications in the same transaction.
4. Commit.
5. Publish `{seq, type, task}` to Redis. Every replica pushes it to its own WebSocket subscribers.

**The read path on the client** (`tasksSlice.js`):

* Apply an event if it is newer than the copy the client already has.
* Hold out-of-order arrivals until the gap fills.
* If a gap lasts more than 750 ms, or after any reconnect, fetch `/changes?sinceSeq=<last>`. It returns
  the latest state of everything changed since then, including deletions.

### Authorization, the same on REST and WebSocket

* Every board-scoped operation goes through `BoardAccessService`:
  * non-members get **404**, so the board's existence isn't leaked
  * members with too low a role get **403**
* **STOMP CONNECT** needs a valid JWT.
* **SUBSCRIBE** to `/topic/boards/{id}` runs the same membership check as REST. Any other destination,
  and any client `SEND`, is rejected. All writes go through REST, where validation, versioning and
  auditing live.
* **Removing a member:**
  * evicts the cached role, so the next REST call is denied immediately
  * closes that user's open sockets, so their reconnect fails the SUBSCRIBE check
* **Registration** always creates a plain member account. Clients can't choose their own role.

## Measured results

From [docs/PERFORMANCE.md](docs/PERFORMANCE.md). Everything ran on one laptop: two backend replicas,
Postgres, Redis, nginx, and the k6 load generator itself.

| Scenario | REST p95 | Write → seen by others, p95 | Events delivered | **Lost** |
|---|---|---|---|---|
| 200 viewers, 10 writers, 10 readers, 2 min | 10–14 ms | 15 ms | 664,000 | **0** |
| 1,000 viewers, 20 writers, 20 readers, 1 min | 55–106 ms | 472 ms | 2,934,000 | **0** |
| 1,000 viewers, 20 writers, 20 readers, 2 min | 87–130 ms | 1,193 ms (failed the 1 s target) | 5,476,000 | **0** |

At 1,000 viewers the load generator itself used about 3.3 CPU cores against about 0.8 per backend
replica, so those latencies include client-side queueing. The performance doc explains the method,
the limits, and five bugs these tests found and fixed.

## Run it locally

Prerequisites: Java 21+, Node 20+, Docker Desktop running.

There are two ways to run it. Use **A** to try the app and **B** while changing code.

### A. Full stack in Docker (2 backend replicas behind nginx)

```bash
cp deploy/.env.example deploy/.env    # first time only: set the secrets, and SEED_DEMO=true for demo accounts
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

Open **http://localhost:8088**. To stop it:

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env down
```

### B. Dev mode (live reload)

Run these from the repo root, each in its own terminal (the backend and frontend keep running):

```bash
# Terminal 1: Postgres + Redis
cd backend && docker compose up -d

# Terminal 2: backend on :8080 (Flyway creates the schema and seeds demo data)
cd backend && ./mvnw spring-boot:run

# Terminal 3: frontend on :5173 (Vite proxies /api and /ws to :8080)
cd frontend && npm install && npm run dev
```

Open **http://localhost:5173**. Stop with Ctrl+C in terminals 2 and 3, then `cd backend && docker compose down`.

Demo accounts (password `demo1234`): **admin** (owner), **alice** and **bob** (editors), **carol** (viewer),
all on the "Launch plan" board. To watch live sync and conflict handling, sign in as alice in one
browser and as bob in another (or a private window). Tabs on the same origin share a login.

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for managed-platform deployment, the configuration
reference, metrics and failure modes.

## Tests

```bash
cd backend && ./mvnw verify      # needs Docker running (Testcontainers)
```

| Suite | Covers |
|---|---|
| `RestAuthorizationIT` | 401 for missing or forged tokens; 404 for outsiders on every endpoint; viewer vs editor vs owner; immediate effect of removal and demotion; last-owner protection; self-registration can't become admin; notification isolation |
| `WebSocketAuthorizationIT` | CONNECT without or with a bad token is refused; non-members can't subscribe; other users' queues and client SENDs are denied; removed members' sockets are closed; notifications reach only their recipient |
| `ConcurrentEditingIT` | Two users saving the same version at once (one 200, one 409 with the winner's state); 8 users × 5 parallel read-modify-write edits with zero lost; 40 parallel creates seen exactly once with contiguous seqs; a move racing a delete |
| `ReconnectCatchUpIT` | A client misses a create, edit, move and delete while offline, then recovers exactly via `/changes`, and live seqs resume without a gap |
| `HistoryNotificationsSearchIT` | Paged activity history (board and task); notification rules and read state; search, filters, LIKE-escaping, stable pagination, page-size cap |
| `GithubWebhookIT` | HMAC signature required; a signed "issue opened" event creates a task |

Load test: start the full stack (option A), then run `./loadtest/run.sh` from the repo root (see
[docs/PERFORMANCE.md](docs/PERFORMANCE.md#reproducing)).

## API overview

All endpoints except auth and the webhook need `Authorization: Bearer <jwt>`. Errors are problem-details JSON.

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | Returns `{token, username, role}`. Login is rate-limited per user and IP |
| GET | `/api/auth/me` | |
| GET / POST | `/api/boards` | Your boards (paged) / create (you become owner) |
| GET / DELETE | `/api/boards/{id}` | Detail with members and current `eventSeq` / delete (owner) |
| POST / PUT / DELETE | `/api/boards/{id}/members[/{username}]` | Owner manages members; members can remove themselves |
| GET | `/api/boards/{id}/tasks?q=&status=&assignee=&sort=&page=&size=` | Search and paginate (`size` ≤ 200) |
| POST | `/api/boards/{id}/tasks` | Editor+ |
| GET / PUT / DELETE | `/api/boards/{id}/tasks/{taskId}` | PUT body includes `version`; DELETE accepts `?version=` |
| PUT | `/api/boards/{id}/tasks/{taskId}/move` | `{status, version}` |
| GET | `/api/boards/{id}/changes?sinceSeq=N` | Catch-up: tasks changed after N (including tombstones) plus `currentSeq` |
| GET | `/api/boards/{id}/activity?taskId=&page=&size=` | Newest first |
| GET | `/api/notifications?unreadOnly=&page=&size=`, `/api/notifications/unread-count` | |
| POST | `/api/notifications/{id}/read`, `/api/notifications/read-all` | |
| POST | `/api/webhooks/github/{boardId}` | GitHub "issues" webhook, HMAC-SHA256 verified |
| WS | `/ws` (STOMP) | CONNECT with `Authorization` header; subscribe to `/topic/boards/{id}` and `/user/queue/notifications` |

## Project layout

```
backend/    Spring Boot API: controller/ service/ realtime/ security/ model/ repository/
            db/migration (Flyway), src/test/.../it (integration tests)
frontend/   React app: features/ (Redux slices), lib/realtime.js (STOMP client), components/
deploy/     Production-like docker compose (2 replicas + nginx) and .env.example
loadtest/   k6 scenario, runner script, recorded results
docs/       PERFORMANCE.md, DEPLOYMENT.md
```

## Known limitations

* Ordering is restored on the client from `seq`; the server doesn't guarantee arrival order (see
  PERFORMANCE.md finding 1).
* Search is a trigram-indexed substring match, not ranked full-text search.
* Task lists load up to 200 cards per page. Very large boards rely on search and "load more".
* JWTs are stateless: logging out doesn't revoke a token before it expires. Board access *is*
  revoked immediately, because membership is checked on every request.
