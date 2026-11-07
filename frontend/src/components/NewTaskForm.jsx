import { useState } from 'react'
import { useDispatch } from 'react-redux'
import { Plus } from 'lucide-react'
import { createTask } from '../features/tasks/tasksSlice'
import { Button } from './ui/button'
import { Input } from './ui/input'
import { Select } from './ui/select'

const empty = { title: '', description: '', status: 'TODO', assignee: '' }

export default function NewTaskForm({ members }) {
  const dispatch = useDispatch()
  const [form, setForm] = useState(empty)
  const submit = async (e) => {
    e.preventDefault()
    if (!form.title.trim()) return
    const res = await dispatch(createTask({ ...form, assignee: form.assignee || null }))
    if (!res.error) setForm(empty)
  }
  return (
    <form onSubmit={submit} className="flex flex-wrap gap-2 rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
      <Input aria-label="New task title" placeholder="New task title" className="min-w-[200px] flex-[2]" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
      <Input aria-label="Description" placeholder="Description (optional)" className="min-w-[200px] flex-[3]" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
      <Select aria-label="Assignee" className="w-auto" value={form.assignee} onChange={(e) => setForm({ ...form, assignee: e.target.value })}>
        <option value="">Unassigned</option>
        {members.map((m) => <option key={m} value={m}>{m}</option>)}
      </Select>
      <Button><Plus className="mr-1 h-4 w-4" /> Add</Button>
    </form>
  )
}
