import { useEffect, useRef } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { AlertTriangle } from 'lucide-react'
import { deleteTask, dismissConflict, moveTask, updateTask } from '../features/tasks/tasksSlice'
import { Button } from './ui/button'

const FIELDS = [['title', 'Title'], ['description', 'Description'], ['status', 'Status'], ['assignee', 'Assignee']]

/**
 * Shown when the server rejects a write with 409 because someone else saved first.
 * The user sees both versions and chooses explicitly. Nothing is overwritten without consent.
 */
export default function ConflictDialog() {
  const dispatch = useDispatch()
  const conflict = useSelector((s) => s.tasks.conflict)
  const ref = useRef(null)

  useEffect(() => { if (conflict) ref.current?.focus() }, [conflict])
  if (!conflict) return null
  const { mine, current, kind } = conflict

  const keepTheirs = () => dispatch(dismissConflict())
  const applyMine = () => {
    dispatch(dismissConflict())
    const version = current.version // re-apply on top of the latest version
    if (kind === 'update') dispatch(updateTask({ ...mine, version }))
    if (kind === 'move') dispatch(moveTask({ ...mine, version }))
    if (kind === 'delete') dispatch(deleteTask({ ...mine, version }))
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4" role="dialog" aria-modal="true" aria-labelledby="conflict-title">
      <div ref={ref} tabIndex={-1} className="w-full max-w-lg rounded-xl bg-white p-5 shadow-2xl outline-none">
        <h2 id="conflict-title" className="flex items-center gap-2 text-lg font-semibold"><AlertTriangle className="h-5 w-5 text-amber-500" /> Edit conflict</h2>
        <p className="mt-1 text-sm text-slate-600">
          <span className="font-medium">{current.updatedBy}</span> saved this task (now v{current.version}) after you started your {kind}.
          Your change was <span className="font-medium">not</span> applied.
        </p>
        {kind === 'update' && (
          <table className="mt-4 w-full table-fixed text-left text-xs">
            <thead><tr className="text-slate-500"><th className="w-24 pb-1" /><th className="pb-1">Theirs (saved)</th><th className="pb-1">Yours</th></tr></thead>
            <tbody>
              {FIELDS.map(([k, label]) => {
                const theirs = current[k] ?? ''
                const yours = mine[k] ?? ''
                return (
                  <tr key={k} className={theirs !== yours ? 'bg-amber-50' : ''}>
                    <td className="py-1 pr-2 font-medium text-slate-600">{label}</td>
                    <td className="break-words py-1 pr-2">{String(theirs) || '—'}</td>
                    <td className="break-words py-1">{String(yours) || '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
        {kind === 'move' && <p className="mt-3 text-sm">They have it in <b>{current.status}</b>; you tried to move it to <b>{mine.status}</b>.</p>}
        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <Button variant="outline" onClick={keepTheirs}>Keep theirs</Button>
          <Button onClick={applyMine}>{kind === 'delete' ? 'Delete anyway' : 'Apply mine on top'}</Button>
        </div>
      </div>
    </div>
  )
}
