# Deployment and operations

The app ships as two images: `backend` (Spring Boot) and `web` (nginx serving the React build and
reverse-proxying `/api` and `/ws`). It needs PostgreSQL 14+ (with the `pg_trgm` extension available)
and Redis 6+.

## Option A: single host with Docker Compose (tested)

This is the exact stack used for the measurements in [PERFORMANCE.md](PERFORMANCE.md).

```bash
cp deploy/.env.example deploy/.env
# Fill in secrets:  openssl rand -base64 48
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
open http://localhost:8088
```

* 2 backend replicas (`BACKEND_REPLICAS`), each a non-root container with a readiness health check;
  nginx only routes to a replica once it is healthy.
* Flyway migrates the schema on startup (it takes a Postgres lock, so concurrent replica starts are safe).
* Put TLS in front of `web` (Caddy, Traefik, a cloud load balancer). Set `PUBLIC_ORIGIN` to the
  public `https://` URL; it is the allow-list for WebSocket origins.

## Option B: managed platform

Any platform that runs a container image and supports WebSockets works. The checklist:

1. **Postgres:** a managed instance. Set `DATABASE_URL=jdbc:postgresql://host:5432/db`,
   `DATABASE_USERNAME` and `DATABASE_PASSWORD`. Enable the `pg_trgm` extension if the platform requires
   it to be allow-listed.
2. **Redis:** managed Redis (Upstash, ElastiCache, Render Key Value...). Set `REDIS_HOST`, `REDIS_PORT`,
   `REDIS_PASSWORD`, and `REDIS_SSL=true` for TLS endpoints. Pub/sub must be supported; it is on all of these.
3. **Backend service:** build `backend/Dockerfile`. Set `SPRING_PROFILES_ACTIVE=prod`, `JWT_SECRET`
   and `CORS_ALLOWED_ORIGINS`. Expose port 8080, and use `/actuator/health/readiness` on port 8081 as the
   health check (or set `MANAGEMENT_PORT=8080` if the platform can only probe one port).
4. **Frontend:** either deploy `frontend/Dockerfile` with nginx (edit `upstream backend` to point at the
   backend's internal hostname), or build static files with `VITE_API_URL=https://api.example.com/api`
   and `VITE_WS_URL=wss://api.example.com/ws` and host them on any static host. In the second case,
   add that origin to `CORS_ALLOWED_ORIGINS`.
5. **Scaling out:** no sticky sessions are needed. Each replica only pushes to its own sockets, and
   Redis pub/sub fans every event out to all replicas.

## Configuration reference

| Variable | Default (dev) | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | (none) | `prod`: no JWT default, demo seed off, actuator on a separate port, trusts `X-Forwarded-*` |
| `DATABASE_URL` / `_USERNAME` / `_PASSWORD` | `localhost:5434/tracker`, `tracker` / `tracker` | |
| `DB_POOL_SIZE` | 20 | Hikari max pool per replica |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_SSL` | `localhost` / 6379 / (empty) / false | |
| `JWT_SECRET` | dev-only value | **Required in prod**, at least 32 bytes; startup fails on a weak key |
| `JWT_EXPIRATION_MS` | 86400000 (24h) | After expiry, a socket can't subscribe to anything new; existing subscriptions keep receiving until it reconnects, which needs a fresh token. Board removal closes sockets immediately. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | Comma-separated; also the WebSocket origin allow-list |
| `GITHUB_WEBHOOK_SECRET` | (empty, webhook disabled) | Shared secret for `X-Hub-Signature-256` |
| `REALTIME_REDIS_FANOUT` | true | `false` = single-instance mode, local delivery only |
| `BOARD_ROLES_CACHE_TTL` | 10m | Membership cache TTL (evicted explicitly on change; the TTL is a safety net) |
| `LOGIN_RATE_LIMIT` | 10 | Login attempts per minute per (username, IP); 0 disables |
| `SEED_DEMO` / `SEED_DEMO_PASSWORD` | true / `demo1234` (dev), false (prod) | Demo users admin, alice, bob, carol |
| `MANAGEMENT_PORT` | 8081 (prod) | Actuator port; do not expose publicly |

## Operations

* **Health:** `/actuator/health/liveness` and `/actuator/health/readiness` on the management port.
  Readiness includes the database but deliberately not Redis, because the app keeps working without
  Redis (see failure modes) and a Redis outage must not drain every replica.
* **Metrics:** `/actuator/prometheus` on the management port. Useful series:
  * `http_server_requests_seconds_bucket`: latency histograms per endpoint
  * `tracker_websocket_sessions`: open sockets per replica
  * `tracker_websocket_denied_total{command}`: rejected CONNECT/SUBSCRIBE/SEND frames (a spike means probing)
  * `tracker_realtime_published_total{kind}`: events fanned out
  * `tracker_realtime_fanout_fallback_total`: Redis publish failures. Should be 0; if it grows,
    clients on other replicas depend on catch-up.
  * `hikaricp_connections_pending`: requests waiting for a DB connection
* **Graceful shutdown:** `server.shutdown=graceful` with a 20s drain. Compose gives containers 30s.
  Clients on a stopping replica reconnect to another one and catch up from their last seq.
* **Failure modes:**
  * *Redis down (verified, PERFORMANCE.md finding 5):* writes still succeed with normal latency, membership checks fall back to the database, and events are
    delivered to the local replica only. Clients on other replicas detect the seq gap on the next
    event they do receive (or on reconnect) and call `/changes` to fill it. The login rate limiter fails open.
  * *A replica dies:* nginx fails over within about 2s. GETs and versioned PUTs in flight are retried on
    another replica. A POST in flight returns 504, because it is not replayed (it might have committed).
    Clients on that replica reconnect (1–30s backoff with jitter) and catch up from their last seq.
    Verified by killing a replica; see PERFORMANCE.md finding 3.
  * *Postgres down:* the API returns 5xx and readiness goes DOWN, so the load balancer drains the replica.
* **Backups:** the only state is Postgres (Redis holds cache, rate-limit counters and pub/sub). Back up
  Postgres with your platform's PITR or `pg_dump`.
* **Schema changes:** add `V2__*.sql` under `backend/src/main/resources/db/migration`. Hibernate runs
  with `ddl-auto: validate`, so a mismatched entity fails at startup instead of drifting.

## GitHub webhook

Repository → Settings → Webhooks → Add webhook:

* **Payload URL:** `https://<host>/api/webhooks/github/<boardId>`
* **Content type:** `application/json`
* **Secret:** the same value as `GITHUB_WEBHOOK_SECRET`
* **Events:** "Issues". Opened issues become TODO tasks on that board; unsigned or mis-signed requests get 401.
