import { cva } from 'class-variance-authority'
import { cn } from '../../lib/utils'

const badgeVariants = cva('inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:ring-offset-2', {
  variants: {
    variant: {
      default: 'border-transparent bg-slate-900 text-slate-50 hover:bg-slate-800',
      secondary: 'border-transparent bg-slate-100 text-slate-900 hover:bg-slate-200',
      outline: 'text-slate-950',
      success: 'border-transparent bg-emerald-100 text-emerald-700',
      muted: 'border-transparent bg-slate-100 text-slate-600',
    },
  },
  defaultVariants: { variant: 'default' },
})

export function Badge({ className, variant, ...props }) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />
}
