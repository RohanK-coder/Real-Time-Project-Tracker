import { useEffect, useMemo, useRef, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { LayoutDashboard, LogOut, Plus, UserRound } from 'lucide-react'
import { logout } from '../features/auth/authSlice'
import { createBoard, fetchBoards, selectBoard } from '../features/boards/boardsSlice'
import { fetchNotifications, receiveNotification } from '../features/notifications/notificationsSlice'
import { accessDenied, statusChanged } from '../features/realtimeSlice'
import { RealtimeClient } from '../lib/realtime'
import BoardView from './BoardView'
import ConnectionBadge from './ConnectionBadge'
import NotificationBell from './NotificationBell'
import { Button } from './ui/button'
import { Input } from './ui/input'
import { cn } from '../lib/utils'

export default function Workspace() {
  const dispatch = useDispatch()
  const { username } = useSelector((s) => s.auth)
  const { list, currentId, hasNext, page } = useSelector((s) => s.boards)
  const [newBoard, setNewBoard] = useState('')
  const realtimeRef = useRef(null)

  // One realtime connection for the whole workspace; boards subscribe/unsubscribe on it.
  const realtime = useMemo(() => new RealtimeClient({
    getToken: () => localStorage.getItem('token'),
    onStatus: (status, attempt) => dispatch(statusChanged({ status, attempt })),
    onAccessDenied: (message) => dispatch(accessDenied(message)),
  }), [dispatch])
  realtimeRef.current = realtime

  useEffect(() => {
    realtime.start()
    const off = realtime.subscribe('/user/queue/notifications',
      (n) => dispatch(receiveNotification(n)),
      () => dispatch(fetchNotifications())) // refetch after reconnects in case pushes were missed
    dispatch(fetchBoards())
    return () => { off(); realtime.stop() }
  }, [realtime, dispatch])

  const submitBoard = (e) => {
    e.preventDefault()
    if (newBoard.trim()) dispatch(createBoard({ name: newBoard.trim() }))
    setNewBoard('')
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="sticky top-0 z-20 border-b border-slate-800 bg-slate-950 text-white">
        <div className="mx-auto flex max-w-[1600px] flex-wrap items-center gap-3 px-4 py-3">
          <LayoutDashboard className="h-5 w-5 text-emerald-300" />
          <span className="font-semibold tracking-tight">Project Tracker</span>
          <ConnectionBadge />
          <div className="ml-auto flex items-center gap-2">
            <NotificationBell onOpenBoard={(id) => dispatch(selectBoard(id))} />
            <span className="hidden items-center gap-1 text-sm text-slate-300 sm:flex"><UserRound className="h-4 w-4" /> {username}</span>
            <Button size="sm" variant="secondary" onClick={() => dispatch(logout())}><LogOut className="mr-1 h-4 w-4" /> Logout</Button>
          </div>
        </div>
      </header>

      <div className="mx-auto grid max-w-[1600px] gap-4 p-4 lg:grid-cols-[240px_1fr]">
        <aside className="space-y-3">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Your boards</h2>
          <nav className="flex gap-2 overflow-x-auto lg:flex-col">
            {list.map((b) => (
              <button key={b.id} onClick={() => dispatch(selectBoard(b.id))}
                className={cn('shrink-0 rounded-lg border px-3 py-2 text-left text-sm transition-colors',
                  b.id === currentId ? 'border-emerald-300 bg-emerald-50 text-emerald-900' : 'border-slate-200 bg-white hover:bg-slate-50')}>
                <div className="font-medium">{b.name}</div>
                <div className="text-xs text-slate-500">{b.myRole?.toLowerCase()}</div>
              </button>
            ))}
            {hasNext && <Button variant="ghost" size="sm" onClick={() => dispatch(fetchBoards({ page: page + 1 }))}>Load more</Button>}
          </nav>
          <form onSubmit={submitBoard} className="flex gap-2">
            <Input aria-label="New board name" placeholder="New board" value={newBoard} onChange={(e) => setNewBoard(e.target.value)} />
            <Button size="icon" aria-label="Create board"><Plus className="h-4 w-4" /></Button>
          </form>
        </aside>

        <main className="min-w-0">
          {currentId
            ? <BoardView key={currentId} boardId={currentId} realtime={realtimeRef.current} />
            : <div className="rounded-xl border border-dashed border-slate-300 p-12 text-center text-slate-500">Create a board to get started, or ask a board owner to add you.</div>}
        </main>
      </div>
    </div>
  )
}
