import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, money } from '../api/client'
import { Banner, EmptyState, ErrorState, Loading, Spinner } from '../components/States'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'

const BLANK_ADDRESS = {
  label: 'Home',
  line1: '',
  line2: '',
  city: '',
  pincode: '',
  phone: '',
  defaultAddress: true,
}

export default function Checkout() {
  const { cart } = useCart()
  const { ensureSessionFresh, handleAuthError } = useAuth()
  const navigate = useNavigate()

  const [addresses, setAddresses] = useState([])
  const [slotDays, setSlotDays] = useState([])
  const [addressId, setAddressId] = useState(null)
  const [slotId, setSlotId] = useState(null)

  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [showAddressForm, setShowAddressForm] = useState(false)
  const [addressForm, setAddressForm] = useState(BLANK_ADDRESS)
  const [savingAddress, setSavingAddress] = useState(false)

  const [review, setReview] = useState(null)
  const [reviewing, setReviewing] = useState(false)
  const [paying, setPaying] = useState(false)
  const [actionError, setActionError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      const [fetchedAddresses, fetchedSlots] = await Promise.all([api.addresses(), api.slots(4)])
      setAddresses(fetchedAddresses)
      setSlotDays(fetchedSlots)
      setAddressId((current) => current ?? fetchedAddresses.find((a) => a.defaultAddress)?.id ?? fetchedAddresses[0]?.id ?? null)
      setShowAddressForm(fetchedAddresses.length === 0)
    } catch (e) {
      if (!handleAuthError(e)) setLoadError(e.message)
    } finally {
      setLoading(false)
    }
  }, [handleAuthError])

  useEffect(() => {
    load()
  }, [load])

  const saveAddress = async (event) => {
    event.preventDefault()
    setSavingAddress(true)
    setActionError(null)
    try {
      const created = await api.createAddress(addressForm)
      setAddresses((prev) => [...prev, created])
      setAddressId(created.id)
      setShowAddressForm(false)
      setAddressForm(BLANK_ADDRESS)
    } catch (e) {
      if (!handleAuthError(e)) setActionError(e.message)
    } finally {
      setSavingAddress(false)
    }
  }

  /**
   * Week 2 fix: slot availability is re-fetched here, at the review step, rather
   * than trusted from the initial page load — so a slot that filled up in the
   * meantime is caught before the user pays.
   */
  const goToReview = async () => {
    if (!addressId || !slotId) return
    setReviewing(true)
    setActionError(null)
    try {
      const freshSlots = await api.slots(4)
      setSlotDays(freshSlots)

      const draft = await api.createDraft(addressId, slotId)
      const draftReview = await api.reviewDraft(draft.id)
      setReview(draftReview)
    } catch (e) {
      if (!handleAuthError(e)) setActionError(e.message)
      // A slot that just filled up should disappear from the picker immediately.
      if (e.code === 'SLOT_FULL') {
        setSlotId(null)
        setSlotDays(await api.slots(4).catch(() => slotDays))
      }
    } finally {
      setReviewing(false)
    }
  }

  /**
   * Week 4 fix: the session is checked *before* handing off to payment, so an
   * expiring JWT prompts a re-login here instead of failing at the payment step.
   */
  const payNow = async () => {
    setPaying(true)
    setActionError(null)
    try {
      const fresh = await ensureSessionFresh(120)
      if (!fresh) {
        setPaying(false)
        return
      }

      // Last re-check right before taking money.
      const latest = await api.reviewDraft(review.order.id)
      if (!latest.slotStillAvailable || !latest.stockStillAvailable) {
        setReview(latest)
        setPaying(false)
        return
      }

      const initiation = await api.initiatePayment(review.order.id, 'UPI')
      navigate(`/pay/${initiation.gatewayRef}`, {
        state: { orderId: review.order.id, amount: initiation.amount, timeout: initiation.callbackTimeoutSeconds },
      })
    } catch (e) {
      if (!handleAuthError(e)) setActionError(e.message)
      setPaying(false)
    }
  }

  if (loading) return <Loading label="Preparing checkout…" />
  if (loadError) return <ErrorState message={loadError} onRetry={load} />

  if (cart.lines.length === 0 && !review) {
    return (
      <EmptyState
        icon="🛒"
        title="Nothing to check out"
        message="Your cart is empty — add a few items first."
        action={
          <Link to="/" className="rounded-lg bg-brand-600 px-5 py-2.5 font-semibold text-white hover:bg-brand-700">
            Browse the catalog
          </Link>
        }
      />
    )
  }

  const inputClass =
    'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100'
  const addressField = (name) => ({
    value: addressForm[name],
    onChange: (e) => setAddressForm((prev) => ({ ...prev, [name]: e.target.value })),
  })

  // ---------------------------------------------------------------- review
  if (review) {
    const { order, slotStillAvailable, stockStillAvailable, warnings } = review
    const blocked = !slotStillAvailable || !stockStillAvailable

    return (
      <div className="mx-auto max-w-2xl">
        <button
          type="button"
          onClick={() => setReview(null)}
          className="mb-4 text-sm font-medium text-slate-500 hover:text-slate-700"
        >
          ← Back to delivery details
        </button>

        <h1 className="mb-2 text-2xl font-bold text-slate-900">Review your order</h1>
        <p className="mb-6 text-sm text-slate-500">
          Order {order.reference} · availability re-checked just now.
        </p>

        {warnings.length > 0 && (
          <div className="mb-4 space-y-2">
            {warnings.map((warning) => (
              <Banner key={warning} tone="warning">
                {warning}
              </Banner>
            ))}
          </div>
        )}

        {actionError && (
          <div className="mb-4">
            <Banner tone="error">{actionError}</Banner>
          </div>
        )}

        <div className="space-y-4">
          <section className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="mb-3 font-semibold text-slate-900">Items</h2>
            <ul className="divide-y divide-slate-100">
              {order.items.map((line) => (
                <li key={line.id} className="flex justify-between py-2 text-sm">
                  <span className="text-slate-600">
                    {line.itemName} <span className="text-slate-400">× {line.quantity}</span>
                  </span>
                  <span className="font-medium">{money(line.lineTotal)}</span>
                </li>
              ))}
            </ul>
          </section>

          <section className="grid gap-4 sm:grid-cols-2">
            <div className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="mb-2 font-semibold text-slate-900">Delivering to</h2>
              <p className="text-sm text-slate-600">{order.deliveryAddress}</p>
            </div>
            <div
              className={`rounded-2xl border bg-white p-5 ${
                slotStillAvailable ? 'border-slate-200' : 'border-amber-300'
              }`}
            >
              <h2 className="mb-2 font-semibold text-slate-900">Delivery slot</h2>
              {order.slot ? (
                <p className="text-sm text-slate-600">
                  {order.slot.date} · {order.slot.startTime} – {order.slot.endTime}
                </p>
              ) : (
                <p className="text-sm text-slate-400">No slot selected</p>
              )}
              <p className="mt-1 text-xs">
                {slotStillAvailable ? (
                  <span className="text-brand-600">Still available</span>
                ) : (
                  <span className="text-amber-700">No longer available</span>
                )}
              </p>
            </div>
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-5">
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-500">Subtotal</dt>
                <dd>{money(order.subtotal)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">Delivery fee</dt>
                <dd>{Number(order.deliveryFee) === 0 ? 'FREE' : money(order.deliveryFee)}</dd>
              </div>
              <div className="flex justify-between border-t border-slate-100 pt-2 text-base font-bold">
                <dt>Total payable</dt>
                <dd>{money(order.total)}</dd>
              </div>
            </dl>
          </section>

          <button
            type="button"
            disabled={blocked || paying}
            onClick={payNow}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-brand-600 py-3 font-semibold text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
          >
            {paying && <Spinner className="h-4 w-4" />}
            {paying ? 'Contacting payment gateway…' : `Pay ${money(order.total)}`}
          </button>

          {blocked && (
            <button
              type="button"
              onClick={() => {
                setReview(null)
                load()
              }}
              className="w-full rounded-xl border border-slate-200 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50"
            >
              Change delivery details
            </button>
          )}
        </div>
      </div>
    )
  }

  // ------------------------------------------------------- address + slot
  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-6 text-2xl font-bold text-slate-900">Checkout</h1>

      {actionError && (
        <div className="mb-4">
          <Banner tone="error">{actionError}</Banner>
        </div>
      )}

      <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-5">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-semibold text-slate-900">1. Delivery address</h2>
          {addresses.length > 0 && (
            <button
              type="button"
              onClick={() => setShowAddressForm((value) => !value)}
              className="text-sm font-medium text-brand-700 hover:underline"
            >
              {showAddressForm ? 'Cancel' : '+ Add new'}
            </button>
          )}
        </div>

        <div className="space-y-2">
          {addresses.map((address) => (
            <label
              key={address.id}
              className={`flex cursor-pointer gap-3 rounded-xl border p-4 transition ${
                addressId === address.id ? 'border-brand-500 bg-brand-50' : 'border-slate-200 hover:bg-slate-50'
              }`}
            >
              <input
                type="radio"
                name="address"
                className="mt-1"
                checked={addressId === address.id}
                onChange={() => setAddressId(address.id)}
              />
              <span className="text-sm">
                <span className="font-semibold text-slate-900">{address.label}</span>
                <span className="block text-slate-600">
                  {address.line1}
                  {address.line2 ? `, ${address.line2}` : ''}, {address.city} — {address.pincode}
                </span>
                <span className="block text-xs text-slate-400">📞 {address.phone}</span>
              </span>
            </label>
          ))}
        </div>

        {showAddressForm && (
          <form onSubmit={saveAddress} className="mt-4 grid gap-3 rounded-xl bg-slate-50 p-4 sm:grid-cols-2">
            <input required placeholder="Label (Home / Office)" className={inputClass} {...addressField('label')} />
            <input required placeholder="Phone" className={inputClass} {...addressField('phone')} />
            <input
              required
              placeholder="Flat / House / Building"
              className={`${inputClass} sm:col-span-2`}
              {...addressField('line1')}
            />
            <input
              placeholder="Area / Landmark (optional)"
              className={`${inputClass} sm:col-span-2`}
              {...addressField('line2')}
            />
            <input required placeholder="City" className={inputClass} {...addressField('city')} />
            <input required placeholder="Pincode" className={inputClass} {...addressField('pincode')} />
            <button
              type="submit"
              disabled={savingAddress}
              className="flex items-center justify-center gap-2 rounded-lg bg-slate-900 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60 sm:col-span-2"
            >
              {savingAddress && <Spinner className="h-4 w-4" />}
              Save address
            </button>
          </form>
        )}
      </section>

      <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="mb-1 font-semibold text-slate-900">2. Delivery slot</h2>
        <p className="mb-4 text-xs text-slate-500">
          Availability is re-checked when you review the order.
        </p>

        {slotDays.length === 0 ? (
          <EmptyState
            icon="🗓️"
            title="No slots available"
            message="All delivery windows are currently booked. Please try again later."
          />
        ) : (
          <div className="space-y-5">
            {slotDays.map((day) => (
              <div key={day.date}>
                <h3 className="mb-2 text-sm font-semibold text-slate-700">{day.label}</h3>
                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                  {day.slots.map((slot) => (
                    <button
                      key={slot.id}
                      type="button"
                      disabled={slot.full}
                      onClick={() => setSlotId(slot.id)}
                      className={`rounded-xl border px-3 py-2.5 text-left text-sm transition ${
                        slotId === slot.id
                          ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-500'
                          : slot.full
                            ? 'cursor-not-allowed border-slate-100 bg-slate-50 text-slate-300'
                            : 'border-slate-200 hover:bg-slate-50'
                      }`}
                    >
                      <span className="block font-medium">
                        {slot.startTime} – {slot.endTime}
                      </span>
                      <span className={`text-xs ${slot.full ? '' : 'text-slate-400'}`}>
                        {slot.full ? 'Fully booked' : `${slot.remaining} left`}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <div className="sticky bottom-4 rounded-2xl border border-slate-200 bg-white px-5 py-4 shadow-lg">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-sm text-slate-500">{cart.itemCount} item(s)</p>
            <p className="text-lg font-bold">{money(cart.total)}</p>
          </div>
          <button
            type="button"
            disabled={!addressId || !slotId || reviewing}
            onClick={goToReview}
            className="flex items-center justify-center gap-2 rounded-lg bg-brand-600 px-6 py-2.5 font-semibold text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
          >
            {reviewing && <Spinner className="h-4 w-4" />}
            {reviewing ? 'Checking availability…' : 'Review order'}
          </button>
        </div>
        {(!addressId || !slotId) && (
          <p className="mt-2 text-xs text-slate-400">
            {!addressId ? 'Choose a delivery address' : 'Choose a delivery slot'} to continue.
          </p>
        )}
      </div>
    </div>
  )
}
