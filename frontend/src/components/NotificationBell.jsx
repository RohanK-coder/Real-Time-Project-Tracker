import { useEffect, useRef, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Bell } from 'lucide-react'
import { fetchNotifications, markAllRead, markRead } from '../features/notifications/notificationsSlice'
import { Button } from './ui/button'
import { cn } from '../lib/utils'

export default function NotificationBell({ onOpenBoard }) {
  const dispatch = useDispatch()
  const { items, unread, hasNext, page } = useSelector((s) => s.notifications)
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    if (!open) return
    const close = (e) => { if (!ref.current?.contains(e.target)) setOpen(false) }
    const esc = (e) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc) }
  }, [open])

  return (
    <div className="relative" ref={ref}>
      <Button size="icon" variant="ghost" aria-label={`Notifications (${unread} unread)`} aria-expanded={open}
        className="relative text-slate-200 hover:bg-white/10 hover:text-white" onClick={() => setOpen((o) => !o)}>
        <Bell className="h-5 w-5" />
        {unread > 0 && <span className="absolute right-1 top-1 min-w-[18px] rounded-full bg-emerald-500 px-1 text-[10px] font-semibold leading-[18px] text-white">{unread > 99 ? '99+' : unread}</span>}
      </Button>
      {open && (
        <div className="absolute right-0 mt-2 w-[min(92vw,360px)] overflow-hidden rounded-xl border border-slate-200 bg-white text-slate-900 shadow-xl">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-2">
            <span className="text-sm font-semibold">Notifications</span>
            {unread > 0 && <button className="text-xs text-emerald-700 hover:underline" onClick={() => dispatch(markAllRead())}>Mark all read</button>}
          </div>
          <ul className="max-h-96 overflow-y-auto">
            {items.length === 0 && <li className="px-4 py-6 text-center text-sm text-slate-500">You're all caught up.</li>}
            {items.map((n) => (
              <li key={n.id}>
                <button className={cn('w-full px-4 py-3 text-left text-sm hover:bg-slate-50', !n.read && 'bg-emerald-50/60')}
                  onClick={() => { if (!n.read) dispatch(markRead(n.id)); if (n.boardId) onOpenBoard(n.boardId); setOpen(false) }}>
                  <div className="flex items-start gap-2">
                    {!n.read && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-emerald-500" />}
                    <div>
                      <div>{n.message}</div>
                      <div className="mt-0.5 text-xs text-slate-500">{new Date(n.createdAt).toLocaleString()}</div>
                    </div>
                  </div>
                </button>
              </li>
            ))}
          </ul>
          {hasNext && <button className="w-full border-t border-slate-100 py-2 text-xs text-slate-600 hover:bg-slate-50" onClick={() => dispatch(fetchNotifications({ page: page + 1 }))}>Load older</button>}
        </div>
      )}
    </div>
  )
}
