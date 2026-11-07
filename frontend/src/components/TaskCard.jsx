import { useDispatch, useSelector } from 'react-redux'
import { ArrowLeft, ArrowRight, Github, Pencil, Trash2 } from 'lucide-react'
import { deleteTask, moveTask, setDraft, updateTask } from '../features/tasks/tasksSlice'
import { Badge } from './ui/badge'
import { Button } from './ui/button'
import { Input } from './ui/input'
import { Select } from './ui/select'
import { Textarea } from './ui/textarea'

const ORDER = ['TODO', 'IN_PROGRESS', 'DONE']

export default function TaskCard({ task, canEdit, members }) {
  const dispatch = useDispatch()
  // Draft includes the version it was based on; it lives in the store so it survives column moves.
  const editing = useSelector((s) => s.tasks.drafts[task.id]) || null
  const setEditing = (draft) => dispatch(setDraft({ id: task.id, draft }))

  const idx = ORDER.indexOf(task.status)
  const move = (dir) => dispatch(moveTask({ id: task.id, status: ORDER[idx + dir], version: task.version }))
  const startEdit = () => setEditing({ title: task.title, description: task.description || '', assignee: task.assignee || '', status: task.status, version: task.version })
  const save = async (e) => {
    e.preventDefault()
    const res = await dispatch(updateTask({ id: task.id, ...editing, assignee: editing.assignee || null }))
    if (!res.error || res.payload?.conflict) setEditing(null) // conflicts are resolved in the dialog
  }

  if (editing) {
    // Someone else saved while this form was open: say so before the user hits Save.
    const stale = task.version !== editing.version
    return (
      <form onSubmit={save} className="space-y-2 rounded-lg border border-emerald-300 bg-emerald-50/40 p-3">
        {stale && <p className="rounded bg-amber-100 px-2 py-1 text-xs text-amber-900">{task.updatedBy} changed this task while you were editing. Saving will ask you to resolve the conflict.</p>}
        <Input aria-label="Title" value={editing.title} onChange={(e) => setEditing({ ...editing, title: e.target.value })} />
        <Textarea aria-label="Description" rows={3} value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} />
        <div className="flex gap-2">
          <Select aria-label="Status" value={editing.status} onChange={(e) => setEditing({ ...editing, status: e.target.value })}>
            {ORDER.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
          </Select>
          <Select aria-label="Assignee" value={editing.assignee} onChange={(e) => setEditing({ ...editing, assignee: e.target.value })}>
            <option value="">Unassigned</option>
            {members.map((m) => <option key={m} value={m}>{m}</option>)}
          </Select>
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(null)}>Cancel</Button>
          <Button size="sm">Save</Button>
        </div>
      </form>
    )
  }

  return (
    <article className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <h3 className="break-words text-sm font-medium">{task.title}</h3>
        {task.githubIssueUrl && <a href={task.githubIssueUrl} target="_blank" rel="noreferrer" aria-label="GitHub issue"><Github className="h-4 w-4 text-slate-500" /></a>}
      </div>
      {task.description && <p className="mt-1 line-clamp-3 whitespace-pre-line break-words text-xs text-slate-600">{task.description}</p>}
      <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-slate-500">
        {task.assignee ? <Badge variant="secondary">@{task.assignee}</Badge> : <Badge variant="muted">unassigned</Badge>}
        <span title={new Date(task.updatedAt).toLocaleString()}>v{task.version} · {task.updatedBy}</span>
      </div>
      {canEdit && (
        <div className="mt-2 flex items-center gap-1">
          <Button size="sm" variant="ghost" className="h-7 px-2" disabled={idx === 0} onClick={() => move(-1)} aria-label="Move left"><ArrowLeft className="h-3.5 w-3.5" /></Button>
          <Button size="sm" variant="ghost" className="h-7 px-2" disabled={idx === ORDER.length - 1} onClick={() => move(1)} aria-label="Move right"><ArrowRight className="h-3.5 w-3.5" /></Button>
          <Button size="sm" variant="ghost" className="ml-auto h-7 px-2" onClick={startEdit} aria-label="Edit"><Pencil className="h-3.5 w-3.5" /></Button>
          <Button size="sm" variant="ghost" className="h-7 px-2 text-red-600" aria-label="Delete"
            onClick={() => dispatch(deleteTask({ id: task.id, version: task.version }))}><Trash2 className="h-3.5 w-3.5" /></Button>
        </div>
      )}
    </article>
  )
}
