import { useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { UserPlus, X } from 'lucide-react'
import { addMember, clearBoardError, removeMember, updateMember } from '../features/boards/boardsSlice'
import { Button } from './ui/button'
import { Input } from './ui/input'
import { Select } from './ui/select'

const ROLES = ['OWNER', 'EDITOR', 'VIEWER']

export default function MembersPanel({ board }) {
  const dispatch = useDispatch()
  const me = useSelector((s) => s.auth.username)
  const error = useSelector((s) => s.boards.error)
  const [form, setForm] = useState({ username: '', role: 'EDITOR' })
  const isOwner = board.myRole === 'OWNER'

  const add = async (e) => {
    e.preventDefault()
    if (!form.username.trim()) return
    const res = await dispatch(addMember({ boardId: board.id, username: form.username.trim(), role: form.role }))
    if (!res.error) setForm({ ...form, username: '' })
  }

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="mb-3 text-sm font-semibold">Members ({board.members.length})</h2>
      <ul className="space-y-2">
        {board.members.map((m) => (
          <li key={m.username} className="flex items-center gap-2 text-sm">
            <span className="min-w-0 flex-1 truncate">{m.username}{m.username === me && <span className="text-slate-400"> (you)</span>}</span>
            {isOwner ? (
              <Select aria-label={`Role of ${m.username}`} className="h-8 w-28 text-xs" value={m.role}
                onChange={(e) => dispatch(updateMember({ boardId: board.id, username: m.username, role: e.target.value }))}>
                {ROLES.map((r) => <option key={r} value={r}>{r.toLowerCase()}</option>)}
              </Select>
            ) : <span className="text-xs text-slate-500">{m.role.toLowerCase()}</span>}
            {(isOwner || m.username === me) && (
              <button aria-label={m.username === me ? 'Leave board' : `Remove ${m.username}`} className="text-slate-400 hover:text-red-600"
                onClick={() => dispatch(removeMember({ boardId: board.id, username: m.username }))}><X className="h-4 w-4" /></button>
            )}
          </li>
        ))}
      </ul>
      {isOwner && (
        <form onSubmit={add} className="mt-3 flex gap-2">
          <Input aria-label="Username to add" placeholder="username" className="h-9" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
          <Select aria-label="Role" className="h-9 w-24 text-xs" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
            {ROLES.map((r) => <option key={r} value={r}>{r.toLowerCase()}</option>)}
          </Select>
          <Button size="sm" aria-label="Add member"><UserPlus className="h-4 w-4" /></Button>
        </form>
      )}
      {error && <p role="alert" className="mt-2 flex items-start justify-between gap-2 text-xs text-red-700">{error}<button onClick={() => dispatch(clearBoardError())} aria-label="Dismiss"><X className="h-3 w-3" /></button></p>}
    </section>
  )
}
