import { cn } from '../../lib/utils'

export function Tabs({ className, ...props }) {
  return <div className={cn('w-full', className)} {...props} />
}

export function TabsList({ className, ...props }) {
  return <div className={cn('inline-flex h-10 items-center justify-center rounded-md bg-slate-100 p-1 text-slate-500', className)} {...props} />
}

export function TabsTrigger({ className, active, ...props }) {
  return <button className={cn('inline-flex items-center justify-center rounded-sm px-3 py-1.5 text-sm font-medium transition-all', active ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-600 hover:text-slate-950', className)} {...props} />
}
