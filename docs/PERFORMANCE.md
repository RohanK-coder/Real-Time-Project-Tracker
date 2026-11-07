# Performance and concurrency testing

This document records how response time and concurrent-user behavior were measured, the raw
results, and what they do (and do not) show. All numbers below come from the result files in
[`loadtest/results/`](../loadtest/results); nothing is estimated.

## What was measured

| Question | Metric | How |
|---|---|---|
| How fast is the REST API under concurrent use? | `http_req_duration` per operation (create / versioned update / move / search) | k6 HTTP requests through nginx, tagged by operation |
| How long until other users *see* a change? | `ws_delivery_ms` | The writer embeds `t0=<ms>` in the task when it **starts** the HTTP request; every viewer subtracts that from its receive time. This covers the whole path: HTTP, DB transaction, commit, Redis pub/sub, cross-replica hop and WebSocket push. Writer and viewers run in one k6 process, so they share a clock. |
| Is any update lost under load? | `ws_missing_events` | Each viewer records every board `seq` it receives. At the end it fetches the board's final `seq` and counts any number between its first event and the final seq that never arrived. **Must be 0.** |
| Are updates delivered in order? | `ws_out_of_order` | Count of events that arrived after a higher seq. Reordering is tolerated by design (see [Findings](#findings)), but it is measured. |
| Do concurrent edits clobber each other? | `write_conflicts` | Every 5th writer iteration deliberately sends a stale `version`. The server must answer **409** (counted, not treated as a failure), never 200. |
| Do sockets spread across replicas? | `tracker_websocket_sessions` gauge | Sampled from each replica's Prometheus endpoint every 10s during the run. |

## Test setup

```
                         ┌──────────────── docker network ────────────────┐
 k6 (grafana/k6:0.54.0)  │                                                │
   writers/readers ──────┼──► nginx (web) ──least_conn──► backend-1 ─┐    │
                         │                         └────► backend-2 ─┤    │
   viewers (WebSocket) ──┼──► backend-1 (½ of sockets)    │          ├──► Postgres 16
                         │    backend-2 (½ of sockets)    └─ Redis 7 ◄┘   (pub/sub + cache)
                         └────────────────────────────────────────────────┘
```

* Stack: [`deploy/docker-compose.yml`](../deploy/docker-compose.yml). 2 backend replicas (Spring Boot 3.5,
  Java 21, `prod` profile, Hikari pool 20 each), Postgres 16, Redis 7, nginx.
* Viewers connect **directly** to the two replicas, alternating, so ~half of all events must cross
  Redis from the replica that handled the write to the replica holding the socket. Sampled session
  counts confirm the split: 101/99 at 200 viewers, and 500/500 or 501/499 at 1,000.
* Writers and readers go through nginx, the same path a browser uses.
* Each test registers fresh users and one fresh board. All users are on **the same board**: the
  worst case for this design, since writers to one board serialize on the board row lock and every
  event fans out to every viewer.
* Writer loop: create → versioned edit → move (20% with a stale version), then 0.5–1.5s think time.
  Reader loop: paginated search (`size=50`, random page and query), same think time.

**Hardware:** one Apple M4 laptop (10 cores, 16 GB); Docker Desktop with 10 CPUs and 8 GB.
k6, both replicas, Postgres, Redis and nginx all share it. Treat the absolute numbers as
**relative** (useful for regressions and comparisons), not as production capacity.

## Results

### Run 1: 200 concurrent viewers (baseline), 2 minutes

`VIEWERS=200 WRITERS=10 READERS=10 DURATION=2m`. **All thresholds passed.**

| Operation | median | p95 | p99 | max | requests |
|---|---:|---:|---:|---:|---:|
| create task | 4.7 ms | 13.8 ms | 58.1 ms | 316 ms | 1,186 |
| update (versioned) | 5.4 ms | 13.9 ms | 37.0 ms | 238 ms | 1,186 |
| move (versioned) | 4.4 ms | 10.6 ms | 25.3 ms | 238 ms | 1,186 |
| search (paginated) | 4.1 ms | 10.2 ms | 27.0 ms | 536 ms | 1,182 |
| **write → seen by other users** | **6 ms** | **15 ms** | **47 ms** | 356 ms | 474,400 samples |

* 664,000 WebSocket events delivered (200 viewers × 3,320 board changes), about 4,400 per second.
* **Missing events: 0.** Out of order: 298 (0.045%).
* HTTP errors: 0 of 5,383. Stale writes rejected with 409: 238, and none were accepted.
* 200/200 sockets connected (101 on backend-1, 99 on backend-2).

### Run 2: 1,000 concurrent viewers, 1 minute

`VIEWERS=1000 WRITERS=20 READERS=20 DURATION=1m`. **All thresholds passed.**

| Operation | median | p95 | p99 | max |
|---|---:|---:|---:|---:|
| create | 16.2 ms | 105.9 ms | 284.1 ms | 610 ms |
| update | 17.8 ms | 92.3 ms | 195.4 ms | 359 ms |
| move | 16.4 ms | 80.6 ms | 181.0 ms | 228 ms |
| search | 11.3 ms | 55.3 ms | 100.2 ms | 357 ms |
| **write → seen by other users** | **49 ms** | **472 ms** | **813 ms** | 1,951 ms |

* 2,934,000 events delivered, about 28,000 per second. **Missing: 0.** Out of order: 38,076 (1.3%).
* HTTP errors: 0 of 7,378. 1000/1000 sockets connected (501 / 499 across the replicas).

CPU per container, sampled every 5s during this run (`docker stats`; 100% = one core):

| Container | avg CPU | peak CPU |
|---|---:|---:|
| **k6 (load generator)** | **330%** | **679%** |
| backend-1 | 82% | 252% |
| backend-2 | 83% | 277% |
| Postgres | 11% | 29% |
| Redis | 1.4% | 3.4% |
| nginx | 2.1% | 7.8% |

### Run 3: 1,000 concurrent viewers, 2 minutes (sustained)

`VIEWERS=1000 WRITERS=20 READERS=20 DURATION=2m`. **The delivery-latency threshold failed**
(p95 under 1s). Everything else passed.

| Operation | median | p95 | p99 | max |
|---|---:|---:|---:|---:|
| create | 26.7 ms | 129.5 ms | 213.5 ms | 453 ms |
| update | 26.8 ms | 121.9 ms | 234.8 ms | 479 ms |
| move | 23.6 ms | 107.9 ms | 172.3 ms | 299 ms |
| search | 18.8 ms | 86.9 ms | 166.0 ms | 372 ms |
| **write → seen by other users** | **130 ms** | **1,193 ms** ❌ | **2,193 ms** | 6,263 ms |

* 5,476,000 events delivered (about 33,000/s). **Missing: 0.** Out of order: 91,733 (1.7%).
* HTTP errors: 0 of 11,155. 1000/1000 sockets connected.

### How to read runs 2 and 3

Correctness held at every load level: **zero lost updates and zero accepted stale writes in all
runs**. Latency degraded at 1,000 viewers, and the CPU samples show why. The **load generator** used
about 3.3 cores on average (it parses roughly 30,000 JSON WebSocket frames per second in JavaScript),
while each backend replica averaged under one core. Generator and server competed for the same 10 cores,
so the 1,000-viewer delivery latency is an upper bound that includes client-side queueing in k6. It
is **not** a measured server limit.

Establishing a real ceiling would need the load generator on separate machines (for example k6 Cloud
or several k6 hosts), plus server-side delivery timing. That has not been done, so this document makes
no claim about it.

## Findings

Load tests, browser tests and failure injection found five real problems. All five are fixed, and each
was re-tested after the fix.

1. **Events could arrive out of order (found by the 200-viewer run).** The first baseline reported
   6 sequence "gaps". All 641,600 expected events had arrived, so these were reorderings, not losses.
   Cause: the board lock is released at commit, and each request thread then publishes to Redis
   independently, so two back-to-back commits can publish in swapped order (on one replica or across
   replicas). Publishing *before* commit would preserve order but could announce changes that later
   roll back, so instead:
   * the client parks early arrivals by seq and applies them in order once the hole fills, and calls
     `/changes?sinceSeq=` only if a hole stays open for more than 750 ms
     ([`tasksSlice.js`](../frontend/src/features/tasks/tasksSlice.js));
   * the integration test now asserts what the server actually guarantees (every event exactly
     once, contiguous seqs), not arrival order;
   * k6 reports *missing* and *out-of-order* separately.
2. **An open edit form was wiped when someone else moved the card (found in the browser).** The
   draft lived in the card component's state, and moving the card to another column remounted it.
   Drafts now live in the Redux store, keyed by task id; the form shows a "changed while you were
   editing" warning and Save opens the conflict dialog.
3. **A dead replica caused 60-second hangs (found by killing a replica).** nginx kept routing to the
   killed container's IP and waited out its default 60s connect timeout (one request returned 504
   after the full wait). Fixed with `proxy_connect_timeout 2s`, passive health checks
   (`max_fails=3 fail_timeout=5s`) and `proxy_next_upstream error timeout`. Re-tested by killing a
   replica:
   * A GET in flight to the dead replica was retried on the healthy one and returned 200 after 2.03 s.
   * A POST (create) in flight returned **504 after 2.0 s**. nginx deliberately does not replay
     non-idempotent requests, and it would be wrong to: the dead replica might already have committed
     it. The UI shows the error and the user retries. Every following request went to the healthy
     replica in about 10 ms. Versioned PUTs are safe to retry because a replayed duplicate gets 409.

   An earlier setting of `max_fails=1` caused a different outage: during a rolling restart, nginx's
   cached DNS still held the old container IPs, both replicas were ejected after one failure each, and
   requests got `502 no live upstreams`. Hence `max_fails=3` and a 5s DNS re-resolve.
4. **Both replicas seeded demo data at the same time.** A race on first boot. The seeder now takes
   a Postgres advisory lock (`pg_advisory_xact_lock`). Verified on a fresh volume: exactly one
   "Seeded" log line from two replicas.

5. **A Redis outage made every request stall for seconds.** The Redis client (Lettuce) queues commands
   while disconnected and only fails them at the 2s command timeout, so the fallbacks worked but
   slowly. Lettuce now rejects commands immediately while disconnected. Re-tested with Redis stopped:
   * login returned 200 (rate limiter fails open)
   * three creates returned 201 in 10–80 ms (events delivered locally; `tracker_realtime_fanout_fallback_total` = 3 across the replicas)
   * search returned 200 in 15 ms
   * both replicas stayed healthy (Redis is deliberately not part of readiness)

   After Redis restarted, the next create went through Redis again with no restart needed.

## Concurrent-edit behavior (integration tests)

The load test exercises conflicts statistically; the integration tests prove the guarantees
deterministically against real Postgres and Redis (Testcontainers). See
[`ConcurrentEditingIT`](../backend/src/test/java/com/example/tracker/it/ConcurrentEditingIT.java):

* **Two users save the same version at the same instant** (released together by a `CyclicBarrier`):
  exactly one gets 200; the other gets 409 with the winner's state in the body.
* **8 users × 5 read-modify-write edits on one task, in parallel, retrying on 409:** all 40 edits
  are present in the final text, `version == 40`, exactly 41 activity entries, and the test asserts
  that conflicts really occurred (so it can't pass vacuously).
* **4 writers creating 40 tasks in parallel while a viewer is subscribed:** the viewer receives all
  40 events exactly once, and the seqs form a contiguous range with no gaps or duplicates.
* **A move racing a delete:** the task is never resurrected; the loser gets 404 or 409.

[`ReconnectCatchUpIT`](../backend/src/test/java/com/example/tracker/it/ReconnectCatchUpIT.java)
disconnects a client, makes a create, edit, move and delete while it is offline, and checks that
`/changes?sinceSeq=N` returns exactly the final state, including a deletion tombstone. The resumed
live stream then continues at `currentSeq + 1`.

## Reproducing

```bash
cp deploy/.env.example deploy/.env      # set secrets; SEED_DEMO is not needed for load tests
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build

./loadtest/run.sh                                            # 200 viewers, 10 writers, 10 readers, 2m
VIEWERS=1000 WRITERS=20 READERS=20 DURATION=1m ./loadtest/run.sh
```

Each run writes these files to `loadtest/results/`:

* `summary-<run>.json`: the numbers above
* `k6-<run>.txt`: raw k6 output
* `sessions-<run>.log`: WebSocket sessions per replica over time
* `docker-stats-<run>.log`: CPU and memory per container
* `server-metrics-<run>.txt`: Prometheus counters from each replica after the run

`run.sh` exits non-zero if any threshold fails, so it can gate CI or a release.

Server-side histograms (`http_server_requests_seconds_bucket`) are exposed on each replica's
management port (8081, not published) at `/actuator/prometheus` for a Prometheus/Grafana setup.
