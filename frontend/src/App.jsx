import { useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import AuthForm from './components/AuthForm'
import Workspace from './components/Workspace'
import { logout } from './features/auth/authSlice'
import { setUnauthorizedHandler } from './lib/api'

export default function App() {
  const dispatch = useDispatch()
  const token = useSelector((state) => state.auth.token)
  useEffect(() => setUnauthorizedHandler(() => dispatch(logout())), [dispatch])
  return token ? <Workspace key={token} /> : <AuthForm />
}
