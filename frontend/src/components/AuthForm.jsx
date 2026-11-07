import { useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { LockKeyhole, ShieldCheck, Users, Zap } from 'lucide-react'
import { login, register } from '../features/auth/authSlice'
import { Badge } from './ui/badge'
import { Button } from './ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './ui/card'
import { Input } from './ui/input'
import { Label } from './ui/label'
import { Tabs, TabsList, TabsTrigger } from './ui/tabs'

const features = [
  [Zap, 'Live board sync', 'STOMP over WebSocket with Redis fan-out across instances, gap detection and catch-up after reconnects.'],
  [ShieldCheck, 'Board-level access control', 'Owner / editor / viewer roles enforced on every REST call and every WebSocket subscription.'],
  [Users, 'Safe concurrent editing', 'Versioned writes: conflicting edits get a 409 and a merge prompt instead of being silently lost.'],
]

export default function AuthForm() {
  const dispatch = useDispatch()
  const { loading, error } = useSelector((state) => state.auth)
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ username: '', email: '', password: '' })

  const submit = (e) => {
    e.preventDefault()
    if (mode === 'login') dispatch(login({ username: form.username, password: form.password }))
    else dispatch(register(form))
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[radial-gradient(circle_at_top,_rgba(16,185,129,0.12),_transparent_35%),linear-gradient(to_bottom,_#f8fafc,_#f1f5f9)] px-4 py-10">
      <div className="mx-auto grid max-w-6xl gap-6 lg:grid-cols-[1.15fr_0.85fr]">
        <Card className="border-0 bg-slate-950 text-white shadow-2xl">
          <CardHeader className="space-y-6 pb-4">
            <Badge variant="success" className="w-fit bg-emerald-500/15 text-emerald-200">Real-time collaboration</Badge>
            <div className="space-y-3">
              <CardTitle className="text-4xl leading-tight sm:text-5xl">Real-Time Collaborative Project Tracker</CardTitle>
              <CardDescription className="max-w-2xl text-base text-slate-300">
                Kanban boards for teams, built with Spring Boot, PostgreSQL, Redis, WebSockets and React.
              </CardDescription>
            </div>
          </CardHeader>
          <CardContent className="grid gap-4">
            {features.map(([Icon, title, body]) => (
              <div key={title} className="flex gap-3 rounded-xl border border-white/10 bg-white/5 p-4">
                <Icon className="mt-0.5 h-5 w-5 shrink-0 text-emerald-300" />
                <div>
                  <div className="font-medium">{title}</div>
                  <p className="text-sm text-slate-400">{body}</p>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card className="shadow-xl">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-2xl"><LockKeyhole className="h-5 w-5" /> {mode === 'login' ? 'Welcome back' : 'Create an account'}</CardTitle>
            <CardDescription>{mode === 'login' ? 'Sign in to your boards.' : 'New accounts start with no boards: create one or ask an owner to add you.'}</CardDescription>
          </CardHeader>
          <CardContent>
            <Tabs className="mb-6">
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger type="button" active={mode === 'login'} onClick={() => setMode('login')}>Login</TabsTrigger>
                <TabsTrigger type="button" active={mode === 'register'} onClick={() => setMode('register')}>Register</TabsTrigger>
              </TabsList>
            </Tabs>

            <form onSubmit={submit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="username">Username</Label>
                <Input id="username" autoComplete="username" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
              </div>
              {mode === 'register' && (
                <div className="space-y-2">
                  <Label htmlFor="email">Email</Label>
                  <Input id="email" type="email" autoComplete="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
                </div>
              )}
              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <Input id="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
                {mode === 'register' && <p className="text-xs text-slate-500">At least 8 characters.</p>}
              </div>
              {error && <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
              <Button disabled={loading} className="w-full">
                {loading ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
              </Button>
            </form>

            <div className="mt-6 rounded-lg border border-emerald-100 bg-emerald-50 p-4 text-sm text-emerald-900">
              <div className="flex items-center gap-2 font-medium"><Users className="h-4 w-4" /> Demo accounts (local / demo mode)</div>
              <p className="mt-1"><span className="font-semibold">admin</span> (owner), <span className="font-semibold">alice</span> &amp; <span className="font-semibold">bob</span> (editors), <span className="font-semibold">carol</span> (viewer)</p>
              <p className="mt-1">Password: <span className="font-semibold">demo1234</span>. Open two browsers as different users to watch edits sync.</p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
