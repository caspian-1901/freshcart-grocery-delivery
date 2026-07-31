import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { money } from '../api/client'
import { Banner, EmptyState, Loading, Spinner } from '../components/States'
import { useCart } from '../context/CartContext'

export default function CartPage() {
  const { cart, loading, error, setQuantity, removeItem, clearError } = useCart()
  const navigate = useNavigate()
  const [busyItem, setBusyItem] = useState(null)

  const change = async (itemId, quantity) => {
    setBusyItem(itemId)
    await setQuantity(itemId, quantity)
    setBusyItem(null)
  }

  const drop = async (itemId) => {
    setBusyItem(itemId)
    await removeItem(itemId)
    setBusyItem(null)
  }

  if (loading && cart.lines.length === 0) return <Loading label="Loading your cart…" />

  if (cart.lines.length === 0) {
    return (
      <EmptyState
        icon="🛒"
        title="Your cart is empty"
        message="Add some fresh items from the catalog to get started."
        action={
          <Link
            to="/"
            className="rounded-lg bg-brand-600 px-5 py-2.5 font-semibold text-white hover:bg-brand-700"
          >
            Start shopping
          </Link>
        }
      />
    )
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-900">Your cart</h1>

      {error && (
        <div className="mb-4">
          <Banner tone="error">
            <div className="flex items-center justify-between gap-3">
              <span>{error}</span>
              <button type="button" onClick={clearError} className="font-medium underline">
                Dismiss
              </button>
            </div>
          </Banner>
        </div>
      )}

      {/* The server flags lines whose quantity now exceeds live stock. */}
      {cart.hasStockIssue && (
        <div className="mb-4">
          <Banner tone="warning">
            Some items in your cart are no longer available in the quantity you selected. Please
            adjust them before checking out.
          </Banner>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="space-y-3">
          {cart.lines.map((line) => (
            <div
              key={line.id}
              className={`flex items-center gap-4 rounded-2xl border bg-white p-4 ${
                line.stockIssue ? 'border-amber-300' : 'border-slate-200'
              }`}
            >
              <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-slate-50 text-3xl">
                {line.emoji || '🛍️'}
              </div>

              <div className="min-w-0 flex-1">
                <h3 className="truncate font-semibold text-slate-900">{line.name}</h3>
                <p className="text-xs text-slate-500">
                  {money(line.unitPrice)} · {line.unit}
                </p>
                {line.stockIssue && (
                  <p className="mt-1 text-xs font-medium text-amber-700">
                    Only {line.availableQuantity} left in stock
                  </p>
                )}
              </div>

              <div className="flex items-center gap-1 rounded-lg border border-slate-200">
                <button
                  type="button"
                  aria-label={`Decrease ${line.name}`}
                  disabled={busyItem === line.itemId}
                  onClick={() => change(line.itemId, line.quantity - 1)}
                  className="px-3 py-1.5 text-lg leading-none text-slate-600 hover:bg-slate-50 disabled:opacity-40"
                >
                  −
                </button>
                <span className="w-8 text-center text-sm font-semibold">
                  {busyItem === line.itemId ? <Spinner className="mx-auto h-4 w-4" /> : line.quantity}
                </span>
                <button
                  type="button"
                  aria-label={`Increase ${line.name}`}
                  disabled={busyItem === line.itemId}
                  onClick={() => change(line.itemId, line.quantity + 1)}
                  className="px-3 py-1.5 text-lg leading-none text-slate-600 hover:bg-slate-50 disabled:opacity-40"
                >
                  +
                </button>
              </div>

              <div className="w-24 text-right">
                <p className="font-semibold text-slate-900">{money(line.lineTotal)}</p>
                <button
                  type="button"
                  onClick={() => drop(line.itemId)}
                  className="text-xs text-slate-400 hover:text-rose-600"
                >
                  Remove
                </button>
              </div>
            </div>
          ))}
        </div>

        <aside className="h-fit rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="font-semibold text-slate-900">Order summary</h2>

          <dl className="mt-4 space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Subtotal</dt>
              <dd className="font-medium">{money(cart.subtotal)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Delivery fee</dt>
              <dd className="font-medium">
                {Number(cart.deliveryFee) === 0 ? (
                  <span className="text-brand-600">FREE</span>
                ) : (
                  money(cart.deliveryFee)
                )}
              </dd>
            </div>
            <div className="mt-3 flex justify-between border-t border-slate-100 pt-3 text-base">
              <dt className="font-semibold">Total</dt>
              <dd className="font-bold">{money(cart.total)}</dd>
            </div>
          </dl>

          {Number(cart.deliveryFee) > 0 && (
            <p className="mt-3 rounded-lg bg-brand-50 px-3 py-2 text-xs text-brand-700">
              Add {money(500 - Number(cart.subtotal))} more for free delivery.
            </p>
          )}

          <button
            type="button"
            onClick={() => navigate('/checkout')}
            className="mt-5 w-full rounded-lg bg-brand-600 py-2.5 font-semibold text-white transition hover:bg-brand-700"
          >
            Proceed to checkout
          </button>

          <Link
            to="/"
            className="mt-2 block w-full rounded-lg py-2 text-center text-sm font-medium text-slate-500 hover:bg-slate-50"
          >
            Continue shopping
          </Link>
        </aside>
      </div>
    </div>
  )
}
