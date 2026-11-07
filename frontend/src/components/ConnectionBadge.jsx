import { useSelector } from 'react-redux'
import { cn } from '../lib/utils'

const styles = {
  live: ['bg-emerald-400', 'Live'],
  connecting: ['bg-amber-400 animate-pulse', 'Connecting…'],
  reconnecting: ['bg-amber-400 animate-pulse', 'Reconnecting'],
  offline: ['bg-red-400', 'Offline'],
}

export default function ConnectionBadge() {
  const { status, attempt } = useSelector((s) => s.realtime)
  const syncing = useSelector((s) => s.tasks.syncing)
  const [dot, label] = styles[status] || styles.offline
  return (
    <span role="status" aria-live="polite" className="flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-2.5 py-0.5 text-xs text-slate-200">
      <span className={cn('h-2 w-2 rounded-full', dot)} />
      {syncing ? 'Syncing…' : label}
      {status === 'reconnecting' && attempt > 1 && <span className="text-slate-400">(attempt {attempt})</span>}
    </span>
  )
}
