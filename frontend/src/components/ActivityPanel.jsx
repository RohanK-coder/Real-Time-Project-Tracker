import { useDispatch, useSelector } from 'react-redux'
import { History } from 'lucide-react'
import { fetchActivity } from '../features/activity/activitySlice'
import { Button } from './ui/button'

function timeAgo(iso) {
  const s = Math.round((Date.now() - new Date(iso)) / 1000)
  if (s < 60) return 'just now'
  if (s < 3600) return `${Math.floor(s / 60)}m ago`
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`
  return new Date(iso).toLocaleDateString()
}

export default function ActivityPanel({ boardId }) {
  const dispatch = useDispatch()
  const { items, hasNext, page, loading } = useSelector((s) => s.activity)
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold"><History className="h-4 w-4" /> Activity</h2>
      <ol className="space-y-3">
        {items.map((a) => (
          <li key={a.id} className="border-l-2 border-slate-200 pl-3 text-sm">
            <div><span className="font-medium">{a.actor}</span> {a.summary}</div>
            <div className="text-xs text-slate-400" title={new Date(a.createdAt).toLocaleString()}>{timeAgo(a.createdAt)} · #{a.seq}</div>
          </li>
        ))}
        {!loading && items.length === 0 && <li className="text-sm text-slate-500">No activity yet.</li>}
      </ol>
      {hasNext && <Button variant="ghost" size="sm" className="mt-2 w-full" disabled={loading} onClick={() => dispatch(fetchActivity({ boardId, page: page + 1 }))}>Load older</Button>}
    </section>
  )
}
