import { useEffect, useMemo, useRef, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Search, X } from 'lucide-react'
import { fetchBoard } from '../features/boards/boardsSlice'
import { fetchActivity, resetActivity } from '../features/activity/activitySlice'
import { clearAccessDenied } from '../features/realtimeSlice'
import { GAP_GRACE_MS, applySearch, catchUp, clearTaskError, loadBoardTasks, receiveEvent, resetBoard, searchTasks } from '../features/tasks/tasksSlice'
import ActivityPanel from './ActivityPanel'
import ConflictDialog from './ConflictDialog'
import MembersPanel from './MembersPanel'
import NewTaskForm from './NewTaskForm'
import TaskCard from './TaskCard'
import { Button } from './ui/button'
import { Input } from './ui/input'
import { Select } from './ui/select'

export const COLUMNS = [
  { key: 'TODO', title: 'To do', accent: 'border-slate-300' },
  { key: 'IN_PROGRESS', title: 'In progress', accent: 'border-amber-300' },
  { key: 'DONE', title: 'Done', accent: 'border-emerald-300' },
]

export default function BoardView({ boardId, realtime }) {
  const dispatch = useDispatch()
  const board = useSelector((s) => s.boards.current)
  const boardError = useSelector((s) => s.boards.error)
  const { byId, needsSync, loading, hasNext, page, error, search } = useSelector((s) => s.tasks)
  const denied = useSelector((s) => s.realtime.accessDenied)
  const [query, setQuery] = useState('')
  const activityTimer = useRef(null)

  // Load snapshot, then subscribe. Every (re)subscribe triggers a catch-up from lastSeq, so events
  // published while we were loading or disconnected are never lost.
  useEffect(() => {
    dispatch(resetBoard(boardId))
    dispatch(resetActivity(boardId))
    dispatch(clearAccessDenied())
    dispatch(fetchBoard(boardId))
    dispatch(fetchActivity({ boardId }))
    const loaded = dispatch(loadBoardTasks({ boardId }))
    const off = realtime.subscribe(`/topic/boards/${boardId}`, (event) => {
      dispatch(receiveEvent(event))
      if (event.type === 'MEMBERS_CHANGED') dispatch(fetchBoard(boardId))
      clearTimeout(activityTimer.current) // coalesce bursts of events into one activity refresh
      activityTimer.current = setTimeout(() => dispatch(fetchActivity({ boardId })), 400)
    }, () => loaded.then(() => dispatch(catchUp())))
    return () => { off(); clearTimeout(activityTimer.current) }
  }, [boardId, realtime, dispatch])

  // A hole in the seq stream usually closes within milliseconds (out-of-order arrival). If it
  // doesn't, fetch the missing changes.
  useEffect(() => {
    if (!needsSync) return undefined
    const t = setTimeout(() => dispatch(catchUp()), GAP_GRACE_MS)
    return () => clearTimeout(t)
  }, [needsSync, dispatch])

  // Debounced server-side search.
  useEffect(() => {
    const t = setTimeout(() => dispatch(applySearch({ q: query })), 250)
    return () => clearTimeout(t)
  }, [query, dispatch])

  const setFilter = (patch) => dispatch(applySearch(patch))

  const visible = useMemo(() => {
    const tasks = search.active ? search.ids.map((id) => byId[id]).filter(Boolean) : Object.values(byId)
    return tasks.sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))
  }, [byId, search.active, search.ids])

  const grouped = useMemo(() => Object.fromEntries(COLUMNS.map((c) => [c.key, visible.filter((t) => t.status === c.key)])), [visible])

  if (boardError && !board) {
    return <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-red-800">{boardError}</div>
  }
  if (!board) return <div className="p-6 text-slate-500">Loading board…</div>

  const canEdit = board.myRole === 'OWNER' || board.myRole === 'EDITOR'
  const members = board.members.map((m) => m.username)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="truncate text-2xl font-semibold tracking-tight">{board.name}</h1>
          <p className="text-sm text-slate-500">{board.description || 'No description'} · you are <span className="font-medium">{board.myRole.toLowerCase()}</span></p>
        </div>
      </div>

      {denied && (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          Live updates stopped: {denied.replace('ACCESS_DENIED: ', '')}. Your access to this board may have changed.
        </div>
      )}
      {error && (
        <div role="alert" className="flex items-center justify-between rounded-lg border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-800">
          {error}<button aria-label="Dismiss" onClick={() => dispatch(clearTaskError())}><X className="h-4 w-4" /></button>
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        <div className="relative min-w-[200px] flex-1">
          <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-400" />
          <Input aria-label="Search tasks" placeholder="Search title or description…" className="pl-9" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <Select aria-label="Filter by status" className="w-auto" value={search.status} onChange={(e) => setFilter({ status: e.target.value })}>
          <option value="">All statuses</option>
          {COLUMNS.map((c) => <option key={c.key} value={c.key}>{c.title}</option>)}
        </Select>
        <Select aria-label="Filter by assignee" className="w-auto" value={search.assignee} onChange={(e) => setFilter({ assignee: e.target.value })}>
          <option value="">Anyone</option>
          {members.map((m) => <option key={m} value={m}>{m}</option>)}
        </Select>
        {search.active && (
          <Button variant="ghost" onClick={() => { setQuery(''); setFilter({ q: '', status: '', assignee: '' }) }}>Clear</Button>
        )}
      </div>
      {search.active && <p className="text-sm text-slate-500">{search.loading ? 'Searching…' : `${search.total} matching task${search.total === 1 ? '' : 's'}`}</p>}

      <div className="grid gap-4 xl:grid-cols-[1fr_320px]">
        <div className="space-y-4">
          {canEdit && <NewTaskForm members={members} />}
          <div className="grid gap-4 md:grid-cols-3">
            {COLUMNS.map((col) => (
              <section key={col.key} aria-label={col.title} className={`rounded-xl border-t-4 ${col.accent} bg-white p-3 shadow-sm`}>
                <h2 className="mb-3 flex items-center justify-between text-sm font-semibold">
                  {col.title}<span className="rounded-full bg-slate-100 px-2 text-xs text-slate-600">{grouped[col.key].length}</span>
                </h2>
                <div className="space-y-3">
                  {grouped[col.key].map((t) => <TaskCard key={t.id} task={t} canEdit={canEdit} members={members} />)}
                  {grouped[col.key].length === 0 && <p className="py-6 text-center text-xs text-slate-400">{loading ? 'Loading…' : 'Nothing here'}</p>}
                </div>
              </section>
            ))}
          </div>
          {!search.active && hasNext && <Button variant="outline" className="w-full" onClick={() => dispatch(loadBoardTasks({ boardId, page: page + 1 }))}>Load more tasks</Button>}
          {search.active && search.hasNext && <Button variant="outline" className="w-full" onClick={() => dispatch(searchTasks({ page: search.page + 1 }))}>Load more results</Button>}
        </div>
        <div className="space-y-4">
          <MembersPanel board={board} />
          <ActivityPanel boardId={boardId} />
        </div>
      </div>
      <ConflictDialog />
    </div>
  )
}
