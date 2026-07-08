import { useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { BriefcaseBusiness, LockKeyhole, UserPlus, Users } from 'lucide-react'
import { login, register } from '../features/auth/authSlice'
import { Badge } from './ui/badge'
import { Button } from './ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './ui/card'
import { Input } from './ui/input'
import { Label } from './ui/label'
import { Select } from './ui/select'
import { Tabs, TabsList, TabsTrigger } from './ui/tabs'

const roles = ['ROLE_ADMIN', 'ROLE_MEMBER', 'ROLE_VIEWER']

export default function AuthForm() {
  const dispatch = useDispatch()
  const { loading, error } = useSelector((state) => state.auth)
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ username: '', email: '', password: '', role: 'ROLE_MEMBER' })

  const submit = (e) => {
    e.preventDefault()
    if (mode === 'login') {
      dispatch(login({ username: form.username, password: form.password }))
    } else {
      dispatch(register(form))
    }
  }

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top,_rgba(16,185,129,0.12),_transparent_35%),linear-gradient(to_bottom,_#f8fafc,_#f1f5f9)] px-4 py-10 flex items-center justify-center">
      <div className="mx-auto grid max-w-6xl gap-6 lg:grid-cols-[1.15fr_0.85fr]">
        <Card className="border-0 bg-slate-950 text-white shadow-2xl">
          <CardHeader className="space-y-6 pb-4">
            <Badge variant="success" className="w-fit bg-emerald-500/15 text-emerald-200">FS — 01</Badge>
            <div className="space-y-3">
              <CardTitle className="text-4xl leading-tight sm:text-5xl">Real-Time Collaborative Project Tracker</CardTitle>
              <CardDescription className="max-w-2xl text-base text-slate-300">
                A Jira-style collaboration app powered by Spring Boot, JWT role-based access, PostgreSQL, Redis pub/sub, WebSockets, and a React dashboard with live Kanban updates.
              </CardDescription>
            </div>
          </CardHeader>
          <CardContent className="grid gap-4 pb-8 md:grid-cols-2">
            {[
              ['Secure auth', 'Spring Security + JWT + role-based API access'],
              ['Live updates', 'STOMP over SockJS broadcasts task changes to all clients'],
              ['Workflow automation', 'GitHub issue webhooks create tasks automatically'],
              ['Production-minded', 'Docker Compose for infra and a CI workflow starter'],
            ].map(([title, body]) => (
              <div key={title} className="rounded-xl border border-white/10 bg-white/5 p-4 backdrop-blur">
                <h3 className="font-semibold text-white">{title}</h3>
                <p className="mt-2 text-sm text-slate-300">{body}</p>
              </div>
            ))}
            <div className="md:col-span-2 flex flex-wrap gap-2 pt-2">
              {['Spring Boot 3', 'React 18', 'Redux Toolkit', 'PostgreSQL', 'Redis', 'WebSocket', 'Docker Compose'].map((item) => (
                <Badge key={item} variant="secondary" className="bg-white/10 text-slate-100">{item}</Badge>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card className="border-slate-200/80 bg-white/90 shadow-xl backdrop-blur">
          <CardHeader>
            <div className="flex items-center gap-3">
              <div className="rounded-full bg-emerald-100 p-2 text-emerald-700">
                {mode === 'login' ? <LockKeyhole className="h-5 w-5" /> : <UserPlus className="h-5 w-5" />}
              </div>
              <div>
                <CardTitle className="text-2xl">{mode === 'login' ? 'Welcome back' : 'Create your account'}</CardTitle>
                <CardDescription>Use the seeded admin or register a new teammate profile.</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <Tabs>
              <TabsList className="mb-6 grid w-full grid-cols-2">
                <TabsTrigger type="button" active={mode === 'login'} onClick={() => setMode('login')}>Login</TabsTrigger>
                <TabsTrigger type="button" active={mode === 'register'} onClick={() => setMode('register')}>Register</TabsTrigger>
              </TabsList>
            </Tabs>

            <form onSubmit={submit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="username">Username</Label>
                <Input id="username" placeholder="rohankommathoti" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
              </div>

              {mode === 'register' && (
                <div className="space-y-2">
                  <Label htmlFor="email">Email</Label>
                  <Input id="email" type="email" placeholder="rohan@example.com" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <Input id="password" type="password" placeholder="••••••••" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
              </div>

              {mode === 'register' && (
                <div className="space-y-2">
                  <Label htmlFor="role">Role</Label>
                  <Select id="role" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
                    {roles.map((role) => <option key={role} value={role}>{role}</option>)}
                  </Select>
                </div>
              )}

              {error && <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

              <Button disabled={loading} className="w-full">
                {loading ? 'Please wait...' : mode === 'login' ? 'Login to dashboard' : 'Create account'}
              </Button>
            </form>

            <div className="mt-6 rounded-lg border border-emerald-100 bg-emerald-50 p-4 text-sm text-emerald-900">
              <div className="flex items-center gap-2 font-medium"><Users className="h-4 w-4" /> Demo credentials</div>
              <p className="mt-1">Username: <span className="font-semibold">admin</span> · Password: <span className="font-semibold">admin123</span></p>
              <p className="mt-1 text-emerald-700">Open the frontend at <span className="font-medium">http://localhost:5173</span>, not the backend root URL.</p>
            </div>

            <div className="mt-5 flex items-center gap-2 text-xs text-slate-500">
              <BriefcaseBusiness className="h-4 w-4" /> Designed with shadcn-inspired local UI components and Tailwind.
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
