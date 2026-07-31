import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { api, money } from '../api/client'
import { Banner, ErrorState, Loading, Spinner } from '../components/States'
import { useAuth } from '../context/AuthContext'

const FLOW = [
  { key: 'PLACED', label: 'Placed', icon: '📝' },
  { key: 'PACKED', label: 'Packed', icon: '📦' },
  { key: 'OUT_FOR_DELIVERY', label: 'Out for Delivery', icon: '🚚' },
  { key: 'DELIVERED', label: 'Delivered', icon: '🏠' },
]

const formatTime = (value) =>
  value ? new Date(value).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : ''

export default function OrderTracking() {
  const { orderId } = useParams()
  const { state } = useLocation()
  const { handleAuthError } = useAuth()

  const [order, setOrder] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [advancing, setAdvancing] = useState(false)

  /**
   * Week 3 fix: status is fetched fresh on every view of this screen rather than
   * read from cached state, so the timeline can never show a stale status.
   */
  const load = useCallback(async () => {
    setError(null)
    try {
      setOrder(await api.order(orderId))
    } catch (e) {
      if (!handleAuthError(e)) setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [orderId, handleAuthError])

  useEffect(() => {
    load()
  }, [load])

  const advance = async () => {
    setAdvancing(true)
    try {
      setOrder(await api.advanceOrder(orderId))
    } catch (e) {
      if (!handleAuthError(e)) setError(e.message)
    } finally {
      setAdvancing(false)
    }
  }

  if (loading) return <Loading label="Fetching the latest status…" />
  if (error && !order) return <ErrorState message={error} onRetry={load} />
  if (!order) return null

  const cancelled = order.status === 'CANCELLED'
  const reachedIndex = FLOW.findIndex((step) => step.key === order.status)
  const timelineFor = (key) => order.timeline.find((event) => event.status === key)

  return (
    <div className="mx-auto max-w-3xl">
      <Link to="/orders" className="mb-4 inline-block text-sm font-medium text-slate-500 hover:text-slate-700">
        ← All orders
      </Link>

      {state?.justPlaced && (
        <div className="mb-4">
          <Banner tone="success">
            🎉 Your order is confirmed!
            {state.resolvedVia === 'FALLBACK_STATUS_CHECK' && (
              <span className="mt-1 block text-xs">
                The gateway callback never arrived — this order was confirmed by the fallback status
                check instead.
              </span>
            )}
          </Banner>
        </div>
      )}

      {error && (
        <div className="mb-4">
          <Banner tone="error">{error}</Banner>
        </div>
      )}

      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">{order.reference}</h1>
          <p className="text-sm text-slate-500">Placed on {formatTime(order.placedAt || order.createdAt)}</p>
        </div>
        <button
          type="button"
          onClick={load}
          className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50"
        >
          ↻ Refresh status
        </button>
      </div>

      <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-6">
        {cancelled ? (
          <Banner tone="error">This order was cancelled.</Banner>
        ) : (
          <ol className="relative space-y-6">
            {FLOW.map((step, index) => {
              const event = timelineFor(step.key)
              const done = index <= reachedIndex
              const current = index === reachedIndex
              return (
                <li key={step.key} className="flex gap-4">
                  <div className="flex flex-col items-center">
                    <span
                      className={`flex h-10 w-10 items-center justify-center rounded-full text-lg transition ${
                        done ? 'bg-brand-100 text-brand-700' : 'bg-slate-100 text-slate-300'
                      } ${current ? 'ring-4 ring-brand-100' : ''}`}
                    >
                      {step.icon}
                    </span>
                    {index < FLOW.length - 1 && (
                      <span className={`mt-1 h-8 w-0.5 ${index < reachedIndex ? 'bg-brand-300' : 'bg-slate-200'}`} />
                    )}
                  </div>

                  <div className="pt-1.5">
                    <p className={`font-semibold ${done ? 'text-slate-900' : 'text-slate-400'}`}>
                      {step.label}
                    </p>
                    {event ? (
                      <>
                        <p className="text-xs text-slate-400">{formatTime(event.occurredAt)}</p>
                        {event.note && <p className="mt-0.5 text-sm text-slate-500">{event.note}</p>}
                      </>
                    ) : (
                      <p className="text-xs text-slate-300">Pending</p>
                    )}
                  </div>
                </li>
              )
            })}
          </ol>
        )}

        {!cancelled && order.status !== 'DELIVERED' && (
          <div className="mt-6 rounded-xl bg-slate-50 p-4">
            <p className="text-xs text-slate-500">
              Demo control — in production this is driven by the warehouse and delivery apps.
            </p>
            <button
              type="button"
              onClick={advance}
              disabled={advancing}
              className="mt-2 flex items-center gap-2 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {advancing && <Spinner className="h-4 w-4" />}
              Advance to next status
            </button>
          </div>
        )}
      </section>

      <div className="grid gap-4 sm:grid-cols-2">
        <section className="rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="mb-3 font-semibold text-slate-900">Delivery</h2>
          <p className="text-sm text-slate-600">{order.deliveryAddress}</p>
          {order.slot && (
            <p className="mt-2 text-sm text-slate-500">
              🗓️ {order.slot.date} · {order.slot.startTime} – {order.slot.endTime}
            </p>
          )}
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="mb-3 font-semibold text-slate-900">Payment</h2>
          {order.payment ? (
            <div className="space-y-1 text-sm text-slate-600">
              <p>
                Status:{' '}
                <span className={order.payment.status === 'SUCCESS' ? 'text-brand-700' : 'text-slate-500'}>
                  {order.payment.status}
                </span>
              </p>
              <p className="text-xs text-slate-400">Method: {order.payment.method || '—'}</p>
              <p className="text-xs text-slate-400">Ref: {order.payment.gatewayRef}</p>
              <p className="text-xs text-slate-400">
                Confirmed via:{' '}
                {order.payment.resolvedVia === 'FALLBACK_STATUS_CHECK'
                  ? 'fallback status check'
                  : 'gateway callback'}
              </p>
            </div>
          ) : (
            <p className="text-sm text-slate-400">No payment recorded.</p>
          )}
        </section>
      </div>

      <section className="mt-4 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="mb-3 font-semibold text-slate-900">Items</h2>
        <ul className="divide-y divide-slate-100">
          {order.items.map((line) => (
            <li key={line.id} className="flex justify-between py-2 text-sm">
              <span className="text-slate-600">
                {line.itemName}
                <span className="text-slate-400">
                  {' '}
                  × {line.quantity} ({line.unit})
                </span>
              </span>
              <span className="font-medium">{money(line.lineTotal)}</span>
            </li>
          ))}
        </ul>

        <dl className="mt-4 space-y-1 border-t border-slate-100 pt-3 text-sm">
          <div className="flex justify-between">
            <dt className="text-slate-500">Subtotal</dt>
            <dd>{money(order.subtotal)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-slate-500">Delivery fee</dt>
            <dd>{Number(order.deliveryFee) === 0 ? 'FREE' : money(order.deliveryFee)}</dd>
          </div>
          <div className="flex justify-between pt-1 text-base font-bold">
            <dt>Total paid</dt>
            <dd>{money(order.total)}</dd>
          </div>
        </dl>
      </section>
    </div>
  )
}
