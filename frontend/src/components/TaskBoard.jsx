import { useEffect, useMemo, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import SockJS from 'sockjs-client'
import Stomp from 'stompjs'
import { Activity, ArrowRight, Github, LayoutDashboard, LogOut, Plus, UserRound } from 'lucide-react'
import { logout } from '../features/auth/authSlice'
import { createTask, deleteTask, fetchTasks, moveTask, receiveTaskDelete, receiveTaskUpdate } from '../features/tasks/tasksSlice'
import { Badge } from './ui/badge'
import { Button } from './ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './ui/card'
import { Input } from './ui/input'
import { Label } from './ui/label'
import { Select } from './ui/select'
import { Separator } from './ui/separator'
import { Textarea } from './ui/textarea'

const columns = [
  { key: 'TODO', title: 'To do', accent: 'bg-slate-100 text-slate-800 border-slate-200' },
  { key: 'IN_PROGRESS', title: 'In progress', accent: 'bg-amber-50 text-amber-700 border-amber-200' },
  { key: 'DONE', title: 'Done', accent: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
]

function prettyRole(role) {
  return role?.replace('ROLE_', '') || 'UNKNOWN'
}

function formatTimestamp(value) {
  if (!value) return 'Just now'
  return new Date(value).toLocaleString()
}

export default function TaskBoard() {
  const dispatch = useDispatch()
  const { items, loading, error } = useSelector((state) => state.tasks)
  const { username, role } = useSelector((state) => state.auth)
  const [form, setForm] = useState({ title: '', description: '', status: 'TODO', assignee: '' })

  useEffect(() => {
    dispatch(fetchTasks())
    const socket = new SockJS('http://localhost:8080/ws')
    const stompClient = Stomp.over(socket)
    stompClient.debug = null
    stompClient.connect({}, () => {
      stompClient.subscribe('/topic/tasks', (message) => {
        dispatch(receiveTaskUpdate(JSON.parse(message.body)))
      })
      stompClient.subscribe('/topic/tasks/deleted', (message) => {
        dispatch(receiveTaskDelete(Number(message.body)))
      })
    })

    return () => {
      try {
        stompClient.disconnect()
      } catch {
        // ignore disconnect errors in local dev
      }
      socket.close()
    }
  }, [dispatch])

  const grouped = useMemo(
    () => columns.reduce((acc, column) => {
      acc[column.key] = items.filter((item) => item.status === column.key)
      return acc
    }, {}),
    [items],
  )

  const canEdit = role === 'ROLE_ADMIN' || role === 'ROLE_MEMBER'
  const stats = {
    total: items.length,
    todo: grouped.TODO?.length || 0,
    doing: grouped.IN_PROGRESS?.length || 0,
    done: grouped.DONE?.length || 0,
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!form.title.trim()) return
    dispatch(createTask(form))
    setForm({ title: '', description: '', status: 'TODO', assignee: '' })
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-9xl p-4 sm:p-6 lg:p-8">
        <Card className="overflow-hidden border-0 shadow-lg">
          <div className="bg-slate-950 px-6 py-8 text-white">
            <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <Badge variant="success" className="bg-emerald-500/15 text-emerald-200">Live workspace</Badge>
                <h1 className="mt-4 text-3xl font-semibold tracking-tight sm:text-4xl">Real-Time Collaborative Project Tracker</h1>
                <p className="mt-3 max-w-3xl text-sm text-slate-300 sm:text-base">
                  WebSocket-powered Kanban updates, JWT-secured APIs, Redis pub/sub hooks, and GitHub issue automation in one clean Spring Boot + React app.
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <div className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm backdrop-blur">
                  <div className="text-slate-400">Signed in as</div>
                  <div className="mt-1 flex items-center gap-2 font-medium"><UserRound className="h-4 w-4" /> {username}</div>
                  <div className="text-xs uppercase tracking-wide text-emerald-300">{prettyRole(role)}</div>
                </div>
                <Button variant="secondary" className="bg-white text-slate-900 hover:bg-slate-100" onClick={() => dispatch(logout())}>
                  <LogOut className="mr-2 h-4 w-4" /> Logout
                </Button>
              </div>
            </div>
          </div>

          <CardContent className="space-y-6 p-6">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {[
                ['Total tasks', stats.total, <LayoutDashboard className="h-4 w-4" />],
                ['To do', stats.todo, <Plus className="h-4 w-4" />],
                ['In progress', stats.doing, <Activity className="h-4 w-4" />],
                ['Completed', stats.done, <Github className="h-4 w-4" />],
              ].map(([label, value, icon]) => (
                <Card key={label} className="shadow-none">
                  <CardContent className="flex items-center justify-between p-5">
                    <div>
                      <p className="text-sm text-slate-500">{label}</p>
                      <p className="mt-2 text-3xl font-semibold tracking-tight">{value}</p>
                    </div>
                    <div className="rounded-full bg-slate-100 p-3 text-slate-700">{icon}</div>
                  </CardContent>
                </Card>
              ))}
            </div>

            <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
              <div className="space-y-6">
                <Card>
                  <CardHeader className="pb-3">
                    <CardTitle className="text-xl">Architecture snapshot</CardTitle>
                    <CardDescription>Built to match your resume project and interview demo story.</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="grid gap-3 md:grid-cols-3">
                      {[
                        ['Backend', ['Spring Boot 3', 'Spring Security + JWT', 'WebSocket + STOMP', 'Spring Data JPA']],
                        ['Frontend', ['React 18', 'Redux Toolkit', 'SockJS + STOMP', 'Tailwind + shadcn-style UI']],
                        ['Infra', ['PostgreSQL', 'Redis pub/sub', 'Docker Compose', 'GitHub Webhooks']],
                      ].map(([title, pills]) => (
                        <div key={title} className="rounded-xl border border-emerald-100 bg-emerald-50/60 p-4">
                          <h3 className="font-semibold text-emerald-900">{title}</h3>
                          <div className="mt-3 flex flex-wrap gap-2">
                            {pills.map((pill) => <Badge key={pill} variant="success">{pill}</Badge>)}
                          </div>
                        </div>
                      ))}
                    </div>
                    <p className="text-sm leading-7 text-slate-600">
                      The React client fetches tasks over REST and subscribes to live updates over STOMP. Spring persists tasks in PostgreSQL, broadcasts updates to connected users, and accepts GitHub issue webhooks that automatically create TODO items.
                    </p>
                  </CardContent>
                </Card>

                <div className="grid gap-5 lg:grid-cols-3">
                  {columns.map((column) => (
                    <Card key={column.key} className="bg-slate-50/70">
                      <CardHeader className="pb-4">
                        <div className="flex items-center justify-between gap-3">
                          <div>
                            <CardTitle className="text-lg">{column.title}</CardTitle>
                            <CardDescription>{grouped[column.key]?.length || 0} task{grouped[column.key]?.length === 1 ? '' : 's'}</CardDescription>
                          </div>
                          <Badge className={column.accent}>{grouped[column.key]?.length || 0}</Badge>
                        </div>
                      </CardHeader>
                      <CardContent className="space-y-4">
                        {loading && <p className="text-sm text-slate-500">Loading board…</p>}
                        {grouped[column.key]?.map((task) => (
                          <Card key={task.id} className="border-slate-200 shadow-sm">
                            <CardContent className="p-4">
                              <div className="flex items-start justify-between gap-3">
                                <div className="space-y-1">
                                  <h4 className="font-semibold text-slate-900">{task.title}</h4>
                                  <p className="text-xs uppercase tracking-wide text-slate-400">Created by {task.createdBy}</p>
                                </div>
                                {task.githubIssueUrl && (
                                  <a href={task.githubIssueUrl} target="_blank" rel="noreferrer" className="text-emerald-700 hover:underline">
                                    <Github className="h-4 w-4" />
                                  </a>
                                )}
                              </div>
                              {task.description && <p className="mt-3 text-sm leading-6 text-slate-600">{task.description}</p>}
                              <div className="mt-4 flex flex-wrap gap-2">
                                {task.assignee && <Badge variant="secondary">Assignee: {task.assignee}</Badge>}
                                <Badge variant="muted">Updated: {formatTimestamp(task.updatedAt)}</Badge>
                              </div>
                              {canEdit && (
                                <div className="mt-4 flex flex-wrap gap-2">
                                  {columns.filter((c) => c.key !== column.key).map((next) => (
                                    <Button key={next.key} variant="outline" size="sm" onClick={() => dispatch(moveTask({ id: task.id, status: next.key }))}>
                                      Move <ArrowRight className="mx-1 h-3.5 w-3.5" /> {next.title}
                                    </Button>
                                  ))}
                                  <Button size="sm" variant="destructive" onClick={() => dispatch(deleteTask(task.id))}>Delete</Button>
                                </div>
                              )}
                            </CardContent>
                          </Card>
                        ))}
                        {!grouped[column.key]?.length && !loading && (
                          <div className="rounded-lg border border-dashed border-slate-200 bg-white p-5 text-sm text-slate-400">
                            No tasks in {column.title.toLowerCase()} yet.
                          </div>
                        )}
                      </CardContent>
                    </Card>
                  ))}
                </div>
              </div>

              <div className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle className="text-xl">Create task</CardTitle>
                    <CardDescription>Admins and members can create, move, and delete cards.</CardDescription>
                  </CardHeader>
                  <CardContent>
                    {canEdit ? (
                      <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="space-y-2">
                          <Label htmlFor="task-title">Title</Label>
                          <Input id="task-title" placeholder="Build websocket activity feed" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
                        </div>
                        <div className="space-y-2">
                          <Label htmlFor="task-description">Description</Label>
                          <Textarea id="task-description" placeholder="Describe the scope, acceptance criteria, or GitHub issue details" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
                        </div>
                        <div className="grid gap-4 sm:grid-cols-2">
                          <div className="space-y-2">
                            <Label htmlFor="task-status">Column</Label>
                            <Select id="task-status" value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                              {columns.map((c) => <option key={c.key} value={c.key}>{c.title}</option>)}
                            </Select>
                          </div>
                          <div className="space-y-2">
                            <Label htmlFor="task-assignee">Assignee</Label>
                            <Input id="task-assignee" placeholder="rohan" value={form.assignee} onChange={(e) => setForm({ ...form, assignee: e.target.value })} />
                          </div>
                        </div>
                        <Button className="w-full">Add task</Button>
                      </form>
                    ) : (
                      <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">
                        Your current role is read-only. Viewers can watch the live board but cannot change tasks.
                      </div>
                    )}
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle className="text-xl">Local demo checklist</CardTitle>
                    <CardDescription>Use these after startup to verify the app quickly.</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-3 text-sm text-slate-600">
                    <div>1. Login with <span className="font-medium">admin / admin123</span>.</div>
                    <Separator />
                    <div>2. Create a TODO card and move it across columns.</div>
                    <Separator />
                    <div>3. Open another browser tab to confirm live WebSocket sync.</div>
                    <Separator />
                    <div>4. POST a GitHub issue webhook payload to create a task automatically.</div>
                    {error && <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-red-700">{error}</p>}
                  </CardContent>
                </Card>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
