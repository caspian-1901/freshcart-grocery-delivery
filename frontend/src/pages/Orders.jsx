import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, money } from '../api/client'
import { EmptyState, ErrorState, Loading } from '../components/States'
import { useAuth } from '../context/AuthContext'

const TONES = {
  PLACED: 'bg-sky-50 text-sky-700',
  PACKED: 'bg-indigo-50 text-indigo-700',
  OUT_FOR_DELIVERY: 'bg-amber-50 text-amber-700',
  DELIVERED: 'bg-brand-50 text-brand-700',
  CANCELLED: 'bg-slate-100 text-slate-500',
}

export default function Orders() {
  const { handleAuthError } = useAuth()
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setOrders(await api.orders())
    } catch (e) {
      if (!handleAuthError(e)) setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [handleAuthError])

  useEffect(() => {
    load()
  }, [load])

  if (loading) return <Loading label="Loading your orders…" />
  if (error) return <ErrorState message={error} onRetry={load} />

  if (orders.length === 0) {
    return (
      <EmptyState
        icon="📦"
        title="No orders yet"
        message="Once you place an order, you'll be able to track it here."
        action={
          <Link to="/" className="rounded-lg bg-brand-600 px-5 py-2.5 font-semibold text-white hover:bg-brand-700">
            Start shopping
          </Link>
        }
      />
    )
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-900">My orders</h1>

      <div className="space-y-3">
        {orders.map((order) => (
          <Link
            key={order.id}
            to={`/orders/${order.id}`}
            className="block rounded-2xl border border-slate-200 bg-white p-5 transition hover:shadow-md"
          >
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="flex items-center gap-3">
                  <span className="font-semibold text-slate-900">{order.reference}</span>
                  <span
                    className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      TONES[order.status] || 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    {order.statusDisplay}
                  </span>
                </div>
                <p className="mt-1 text-sm text-slate-500">
                  {order.items.length} item(s) ·{' '}
                  {order.slot ? `${order.slot.date}, ${order.slot.startTime}` : 'No slot'}
                </p>
              </div>

              <div className="text-right">
                <p className="font-bold text-slate-900">{money(order.total)}</p>
                <p className="text-xs text-brand-700">Track order →</p>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
