import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, money } from '../api/client'
import { Banner, EmptyState, ErrorState, SkeletonGrid, Spinner } from '../components/States'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'

export default function Catalog() {
  const { user } = useAuth()
  const { addItem, cart } = useCart()
  const navigate = useNavigate()

  const [items, setItems] = useState([])
  const [categories, setCategories] = useState([])
  const [category, setCategory] = useState('All')
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [toast, setToast] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [fetchedItems, fetchedCategories] = await Promise.all([
        api.items(query, category),
        api.categories(),
      ])
      setItems(fetchedItems)
      setCategories(['All', ...fetchedCategories])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [query, category])

  // Debounced so typing in search doesn't fire a request per keystroke.
  useEffect(() => {
    const timer = setTimeout(load, query ? 300 : 0)
    return () => clearTimeout(timer)
  }, [load, query])

  const quantityInCart = (itemId) => cart.lines.find((line) => line.itemId === itemId)?.quantity ?? 0

  const handleAdd = async (item) => {
    if (!user) {
      navigate('/login', { state: { from: '/' } })
      return
    }
    setBusyId(item.id)
    const result = await addItem(item.id, 1)
    setBusyId(null)
    setToast(
      result.ok
        ? { tone: 'success', message: `${item.name} added to cart.` }
        : { tone: 'error', message: result.message },
    )
    setTimeout(() => setToast(null), 3000)
  }

  return (
    <div>
      <div className="mb-6 rounded-2xl bg-gradient-to-r from-brand-600 to-brand-500 px-6 py-8 text-white">
        <h1 className="text-2xl font-bold sm:text-3xl">Fresh groceries, delivered on your schedule</h1>
        <p className="mt-2 max-w-xl text-sm text-brand-50">
          Browse the godown inventory, pick a delivery slot that suits you, and track your order all
          the way to your door.
        </p>
      </div>

      {toast && (
        <div className="mb-4">
          <Banner tone={toast.tone}>{toast.message}</Banner>
        </div>
      )}

      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search for items…"
          className="w-full rounded-lg border border-slate-300 px-4 py-2.5 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 sm:max-w-sm"
        />
        <div className="flex flex-wrap gap-2">
          {categories.map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => setCategory(option)}
              className={`rounded-full px-3 py-1.5 text-sm font-medium transition ${
                category === option
                  ? 'bg-brand-600 text-white'
                  : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50'
              }`}
            >
              {option}
            </button>
          ))}
        </div>
      </div>

      {loading && <SkeletonGrid />}

      {!loading && error && <ErrorState message={error} onRetry={load} />}

      {/* Week 4: the empty catalog state is handled explicitly. */}
      {!loading && !error && items.length === 0 && (
        <EmptyState
          icon="🔍"
          title="No items found"
          message={
            query || category !== 'All'
              ? 'Try a different search term or category.'
              : 'The catalog is currently empty. Please check back shortly.'
          }
          action={
            (query || category !== 'All') && (
              <button
                type="button"
                onClick={() => {
                  setQuery('')
                  setCategory('All')
                }}
                className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
              >
                Clear filters
              </button>
            )
          }
        />
      )}

      {!loading && !error && items.length > 0 && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {items.map((item) => {
            const inCart = quantityInCart(item.id)
            return (
              <article
                key={item.id}
                className="flex flex-col rounded-2xl border border-slate-200 bg-white p-4 transition hover:shadow-md"
              >
                <div className="mb-3 flex h-20 items-center justify-center rounded-xl bg-slate-50 text-5xl">
                  {item.emoji || '🛍️'}
                </div>

                <h3 className="font-semibold text-slate-900">{item.name}</h3>
                <p className="text-xs text-slate-500">{item.unit}</p>

                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-lg font-bold text-slate-900">{money(item.price)}</span>
                </div>

                <p className="mt-1 text-xs">
                  {item.inStock ? (
                    <span className={item.availableQuantity <= 5 ? 'text-amber-600' : 'text-slate-400'}>
                      {item.availableQuantity <= 5
                        ? `Only ${item.availableQuantity} left`
                        : `${item.availableQuantity} in stock`}
                    </span>
                  ) : (
                    <span className="text-rose-600">Out of stock</span>
                  )}
                </p>

                <button
                  type="button"
                  disabled={!item.inStock || busyId === item.id}
                  onClick={() => handleAdd(item)}
                  className="mt-4 flex items-center justify-center gap-2 rounded-lg bg-brand-600 py-2 text-sm font-semibold text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
                >
                  {busyId === item.id && <Spinner className="h-4 w-4" />}
                  {!item.inStock ? 'Unavailable' : inCart > 0 ? `Add more (${inCart})` : 'Add to cart'}
                </button>
              </article>
            )
          })}
        </div>
      )}

      {cart.itemCount > 0 && (
        <div className="sticky bottom-4 mt-8 flex items-center justify-between rounded-2xl border border-brand-200 bg-white px-5 py-4 shadow-lg">
          <div>
            <p className="text-sm text-slate-500">{cart.itemCount} item(s) in your cart</p>
            <p className="text-lg font-bold text-slate-900">{money(cart.total)}</p>
          </div>
          <Link
            to="/cart"
            className="rounded-lg bg-brand-600 px-5 py-2.5 font-semibold text-white hover:bg-brand-700"
          >
            View cart →
          </Link>
        </div>
      )}
    </div>
  )
}
