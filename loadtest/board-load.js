// k6 load test: many users on one board, measuring REST latency and real-time fan-out.
//
// Run (from the repo root, with the deploy/ stack up):
//   ./loadtest/run.sh                     # defaults below
//   VIEWERS=500 WRITERS=20 DURATION=3m ./loadtest/run.sh
//
// Scenarios (all against the same board, all at once):
//   viewers : VIEWERS VUs, each holds one STOMP WebSocket subscribed to the board for the whole
//             test. Sockets alternate between WS_TARGETS (default: the two backend replicas
//             directly) so events written via one replica must cross Redis to reach the other.
//   writers : WRITERS VUs doing create -> edit (versioned) -> move loops through nginx.
//   readers : READERS VUs doing paginated search through nginx.
//
// Custom metrics:
//   ws_delivery_ms      time from the writer *starting* the HTTP request to a viewer receiving the
//                       event (includes the write itself, commit, Redis hop and WS push). Writer and
//                       viewers run in the same k6 process, so they share one clock.
//   ws_out_of_order     events that arrived before a lower seq (reordered, not lost). Clients
//                       buffer by seq, so this costs nothing unless a hole lasts > 750ms.
//   ws_missing_events   seqs a viewer never received: holes between its first and last event,
//                       plus any tail between its last event and the board's final seq. Must be 0.
//   ws_events_received  total events delivered to all viewers
//   write_conflicts     409s from deliberately racing edits (expected, not errors)
import http from 'k6/http'
import ws from 'k6/ws'
import { check, sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'
import exec from 'k6/execution'

const BASE = __ENV.BASE_URL || 'http://web'
const WS_TARGETS = (__ENV.WS_TARGETS || 'ws://tracker-backend-1:8080/ws,ws://tracker-backend-2:8080/ws').split(',')
const VIEWERS = Number(__ENV.VIEWERS || 200)
const WRITERS = Number(__ENV.WRITERS || 10)
const READERS = Number(__ENV.READERS || 10)
const DURATION = __ENV.DURATION || '2m'
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`

const deliveryMs = new Trend('ws_delivery_ms', true)
const outOfOrder = new Counter('ws_out_of_order')
const missing = new Counter('ws_missing_events')
const eventsReceived = new Counter('ws_events_received')
const wsConnected = new Rate('ws_connected')
const conflicts = new Counter('write_conflicts')

// 409 is an expected outcome of racing edits, not a failure.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409))

export const options = {
  setupTimeout: '5m',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  scenarios: {
    viewers: { executor: 'per-vu-iterations', vus: VIEWERS, iterations: 1, maxDuration: '30m', exec: 'viewer' },
    writers: { executor: 'constant-vus', vus: WRITERS, duration: DURATION, exec: 'writer', startTime: '15s' },
    readers: { executor: 'constant-vus', vus: READERS, duration: DURATION, exec: 'reader', startTime: '15s' },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{op:create}': ['p(95)<500'],
    'http_req_duration{op:update}': ['p(95)<500'],
    'http_req_duration{op:move}': ['p(95)<500'],
    'http_req_duration{op:search}': ['p(95)<500'],
    'ws_delivery_ms': ['p(95)<1000'],
    'ws_missing_events': ['count==0'],
    'ws_connected': ['rate>0.99'],
  },
}

const json = (token) => ({ headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } })

export function setup() {
  const tag = `${RUN_ID}${Math.random().toString(36).slice(2, 6)}`.replace(/[^a-zA-Z0-9]/g, '').slice(-20)
  const register = (name) => ({
    method: 'POST', url: `${BASE}/api/auth/register`,
    body: JSON.stringify({ username: name, email: `${name}@load.test`, password: 'loadtest123' }),
    params: { headers: { 'Content-Type': 'application/json' }, tags: { op: 'setup' } },
  })
  const owner = http.post(`${BASE}/api/auth/register`, register(`own${tag}`).body, register('x').params).json()
  if (!owner.token) throw new Error(`owner registration failed: ${JSON.stringify(owner)}`)
  const board = http.post(`${BASE}/api/boards`, JSON.stringify({ name: `load ${tag}` }),
    { ...json(owner.token), tags: { op: 'setup' } }).json()

  const total = VIEWERS + WRITERS + READERS
  const users = []
  for (let i = 0; i < total; i += 25) {
    const batch = []
    for (let j = i; j < Math.min(total, i + 25); j++) batch.push(register(`u${tag}x${j}`))
    http.batch(batch).forEach((r) => users.push({ name: r.json('username'), token: r.json('token') }))
  }
  users.forEach((u, i) => {
    const role = i < VIEWERS ? 'VIEWER' : 'EDITOR'
    http.post(`${BASE}/api/boards/${board.id}/members`, JSON.stringify({ username: u.name, role }),
      { ...json(owner.token), tags: { op: 'setup' } })
  })
  return { boardId: board.id, users, startSeq: http.get(`${BASE}/api/boards/${board.id}`, json(owner.token)).json('eventSeq') }
}

function frame(command, headers, body = '') {
  return `${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n')}\n\n${body}\u0000`
}

export function viewer(data) {
  const idx = exec.vu.idInTest - 1
  const user = data.users[idx % VIEWERS]
  const url = WS_TARGETS[idx % WS_TARGETS.length]
  const holdMs = (parseDuration(DURATION) + 25) * 1000
  const seen = new Set()
  let maxSeq = -1
  let minSeq = Infinity

  const res = ws.connect(url, {}, (socket) => {
    socket.on('open', () => socket.send(frame('CONNECT', { 'accept-version': '1.2', 'heart-beat': '0,0', Authorization: `Bearer ${user.token}` })))
    socket.on('message', (raw) => {
      const command = raw.slice(0, raw.indexOf('\n'))
      if (command === 'CONNECTED') {
        wsConnected.add(1)
        socket.send(frame('SUBSCRIBE', { id: 'sub-0', destination: `/topic/boards/${data.boardId}` }))
      } else if (command === 'ERROR') {
        wsConnected.add(0)
        socket.close()
      } else if (command === 'MESSAGE') {
        const body = raw.slice(raw.indexOf('\n\n') + 2, raw.lastIndexOf('\u0000'))
        const ev = JSON.parse(body)
        eventsReceived.add(1)
        if (ev.seq < maxSeq) outOfOrder.add(1)
        seen.add(ev.seq)
        maxSeq = Math.max(maxSeq, ev.seq)
        minSeq = Math.min(minSeq, ev.seq)
        // Only create/update events carry a fresh t0 (a move keeps the previous description).
        const m = ev.task && ev.type !== 'TASK_MOVED' && /t0=(\d+)/.exec(ev.task.description || '')
        if (m) deliveryMs.add(Date.now() - Number(m[1]))
      }
    })
    socket.setTimeout(() => {
      // Writers finished ~10s ago: everything up to the board's final seq must have arrived.
      const finalSeq = http.get(`${BASE}/api/boards/${data.boardId}`, { ...json(user.token), tags: { op: 'setup' } }).json('eventSeq')
      if (seen.size > 0) missing.add((finalSeq - minSeq + 1) - seen.size)
      else missing.add(finalSeq - data.startSeq)
      socket.close()
    }, holdMs)
  })
  check(res, { 'ws upgraded (101)': (r) => r && r.status === 101 })
}

export function writer(data) {
  const user = data.users[VIEWERS + ((exec.vu.idInTest - 1) % WRITERS)]
  const base = `${BASE}/api/boards/${data.boardId}/tasks`
  const stamp = () => `t0=${Date.now()}`

  const created = http.post(base, JSON.stringify({ title: `load ${user.name}`, description: stamp(), status: 'TODO' }),
    { ...json(user.token), tags: { op: 'create' } })
  if (!check(created, { 'created 201': (r) => r.status === 201 })) return
  let task = created.json()

  const edited = http.put(`${base}/${task.id}`, JSON.stringify({
    title: task.title, description: stamp(), status: 'TODO', version: task.version,
  }), { ...json(user.token), tags: { op: 'update' } })
  if (edited.status === 200) task = edited.json()

  // Every 5th iteration deliberately races a stale version to exercise the 409 path under load.
  const staleVersion = exec.scenario.iterationInTest % 5 === 0 ? task.version - 1 : task.version
  const moved = http.put(`${base}/${task.id}/move`, JSON.stringify({ status: 'IN_PROGRESS', version: staleVersion }),
    { ...json(user.token), tags: { op: 'move' } })
  if (moved.status === 409) conflicts.add(1)
  check(moved, { 'move 200 or expected 409': (r) => r.status === 200 || (r.status === 409 && staleVersion !== task.version) })
  sleep(0.5 + Math.random())
}

export function reader(data) {
  const user = data.users[VIEWERS + WRITERS + ((exec.vu.idInTest - 1) % READERS)]
  const q = ['load', 'u', ''][Math.floor(Math.random() * 3)]
  const page = Math.floor(Math.random() * 3)
  const r = http.get(`${BASE}/api/boards/${data.boardId}/tasks?q=${q}&size=50&page=${page}`, { ...json(user.token), tags: { op: 'search' } })
  check(r, { 'search 200': (res) => res.status === 200 })
  sleep(0.5 + Math.random())
}

function parseDuration(d) {
  const m = /^(\d+)(s|m)$/.exec(d)
  return m ? Number(m[1]) * (m[2] === 'm' ? 60 : 1) : 120
}

export function handleSummary(data) {
  const pick = (name, tag) => {
    const key = tag ? `${name}{${tag}}` : name
    const v = data.metrics[key]?.values
    return v ? { avg: v.avg, med: v.med, p90: v['p(90)'], p95: v['p(95)'], p99: v['p(99)'], max: v.max, count: v.count } : null
  }
  const summary = {
    config: { VIEWERS, WRITERS, READERS, DURATION, WS_TARGETS, BASE },
    http: Object.fromEntries(['create', 'update', 'move', 'search'].map((op) => [op, pick('http_req_duration', `op:${op}`)])),
    http_reqs: data.metrics.http_reqs?.values,
    http_req_failed: data.metrics.http_req_failed?.values,
    ws_delivery_ms: pick('ws_delivery_ms'),
    ws_events_received: data.metrics.ws_events_received?.values,
    ws_out_of_order: data.metrics.ws_out_of_order?.values ?? { count: 0 },
    ws_missing_events: data.metrics.ws_missing_events?.values ?? { count: 0 },
    ws_connected: data.metrics.ws_connected?.values,
    write_conflicts: data.metrics.write_conflicts?.values,
    thresholds_passed: Object.fromEntries(Object.entries(data.metrics).filter(([, m]) => m.thresholds)
      .map(([k, m]) => [k, Object.values(m.thresholds).every((t) => t.ok)])),
  }
  return {
    stdout: JSON.stringify(summary, null, 2) + '\n',
    [`/results/summary-${RUN_ID}.json`]: JSON.stringify(summary, null, 2),
  }
}
