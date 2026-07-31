/**
 * Shared loading / error / empty states.
 *
 * Week 4 polish: every screen uses these same three components so feedback is
 * consistent across catalog, cart, slots, payment and tracking.
 */

export function Spinner({ className = 'h-5 w-5' }) {
  return (
    <svg className={`animate-spin ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle className="opacity-20" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path
        className="opacity-90"
        fill="currentColor"
        d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z"
      />
    </svg>
  )
}

export function Loading({ label = 'Loading…' }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-slate-500">
      <Spinner className="h-8 w-8 text-brand-600" />
      <p className="text-sm">{label}</p>
    </div>
  )
}

export function SkeletonGrid({ count = 8 }) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="animate-pulse rounded-2xl border border-slate-200 bg-white p-4">
          <div className="mb-4 h-16 rounded-xl bg-slate-100" />
          <div className="mb-2 h-4 w-3/4 rounded bg-slate-100" />
          <div className="mb-4 h-3 w-1/2 rounded bg-slate-100" />
          <div className="h-9 rounded-lg bg-slate-100" />
        </div>
      ))}
    </div>
  )
}

export function ErrorState({ message, onRetry, title = 'Something went wrong' }) {
  return (
    <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-center">
      <p className="text-2xl">⚠️</p>
      <h3 className="mt-2 font-semibold text-rose-800">{title}</h3>
      <p className="mt-1 text-sm text-rose-700">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-4 rounded-lg bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700"
        >
          Try again
        </button>
      )}
    </div>
  )
}

export function EmptyState({ icon = '🧺', title, message, action }) {
  return (
    <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center">
      <p className="text-4xl">{icon}</p>
      <h3 className="mt-3 text-lg font-semibold text-slate-800">{title}</h3>
      {message && <p className="mx-auto mt-1 max-w-sm text-sm text-slate-500">{message}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function Banner({ tone = 'warning', children }) {
  const tones = {
    warning: 'border-amber-200 bg-amber-50 text-amber-900',
    error: 'border-rose-200 bg-rose-50 text-rose-900',
    success: 'border-brand-200 bg-brand-50 text-brand-700',
    info: 'border-sky-200 bg-sky-50 text-sky-900',
  }
  return (
    <div className={`rounded-xl border px-4 py-3 text-sm ${tones[tone]}`} role="status">
      {children}
    </div>
  )
}
