import { Client } from '@stomp/stompjs'

/**
 * One STOMP connection per tab, with:
 *  - JWT sent on every (re)connect via connectHeaders
 *  - exponential backoff with jitter (1s -> 30s), reset after a successful connect
 *  - 10s heartbeats in both directions, so dead connections are noticed and replaced
 *  - immediate retry when the browser comes back online or the tab becomes visible
 *  - subscriptions that are re-established automatically after every reconnect, each
 *    followed by an `onResubscribed` callback so the caller can fetch what it missed
 */
const MIN_DELAY = 1000
const MAX_DELAY = 30000

function defaultUrl() {
  if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${window.location.host}/ws`
}

export class RealtimeClient {
  constructor({ getToken, onStatus, onAccessDenied }) {
    this.getToken = getToken
    this.onStatus = onStatus
    this.onAccessDenied = onAccessDenied
    this.subs = new Map() // destination -> { handler, onResubscribed, stompSub }
    this.attempt = 0
    this.client = new Client({
      brokerURL: defaultUrl(),
      reconnectDelay: MIN_DELAY,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
      beforeConnect: () => {
        this.client.connectHeaders = { Authorization: `Bearer ${this.getToken()}` }
        this.onStatus(this.attempt === 0 ? 'connecting' : 'reconnecting', this.attempt)
      },
      onConnect: () => {
        this.attempt = 0
        this.client.reconnectDelay = MIN_DELAY
        this.onStatus('live', 0)
        for (const [destination, sub] of this.subs) this.#subscribe(destination, sub)
      },
      onWebSocketClose: () => {
        this.attempt += 1
        const backoff = Math.min(MAX_DELAY, MIN_DELAY * 2 ** Math.min(this.attempt, 5))
        this.client.reconnectDelay = backoff / 2 + Math.random() * (backoff / 2) // jitter avoids thundering herds
        if (this.client.active) this.onStatus('reconnecting', this.attempt)
      },
      onStompError: (frame) => {
        const message = frame.headers.message || ''
        if (message.startsWith('ACCESS_DENIED')) this.onAccessDenied?.(message)
      },
    })

    // Retry immediately (instead of waiting out the backoff) when the network or tab comes back.
    this.wake = () => {
      if (this.client.active && !this.client.connected) {
        this.client.reconnectDelay = 0
        this.client.forceDisconnect()
      }
    }
    this.onVisible = () => document.visibilityState === 'visible' && this.wake()
    this.onOffline = () => this.onStatus('offline', this.attempt)
  }

  start() {
    window.addEventListener('online', this.wake)
    window.addEventListener('offline', this.onOffline)
    document.addEventListener('visibilitychange', this.onVisible)
    this.client.activate()
  }

  async stop() {
    window.removeEventListener('online', this.wake)
    window.removeEventListener('offline', this.onOffline)
    document.removeEventListener('visibilitychange', this.onVisible)
    await this.client.deactivate()
    this.onStatus('offline', 0)
  }

  /** Returns an unsubscribe function. `onResubscribed` runs after every (re)subscription. */
  subscribe(destination, handler, onResubscribed) {
    const sub = { handler, onResubscribed, stompSub: null }
    this.subs.set(destination, sub)
    if (this.client.connected) this.#subscribe(destination, sub)
    return () => {
      sub.stompSub?.unsubscribe()
      this.subs.delete(destination)
    }
  }

  #subscribe(destination, sub) {
    sub.stompSub = this.client.subscribe(destination, (msg) => sub.handler(JSON.parse(msg.body)))
    sub.onResubscribed?.()
  }
}
