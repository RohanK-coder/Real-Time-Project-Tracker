import { useSelector } from 'react-redux'
import AuthForm from './components/AuthForm'
import TaskBoard from './components/TaskBoard'

export default function App() {
  const token = useSelector((state) => state.auth.token)
  return token ? <TaskBoard /> : <AuthForm />
}
