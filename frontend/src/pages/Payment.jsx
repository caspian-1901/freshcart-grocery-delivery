import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { api, money } from '../api/client'
import { Banner, Spinner } from '../components/States'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'

const POLL_INTERVAL_MS = 3000
/** After this long with no callback, tell the user we're checking directly. */
const FALLBACK_NOTICE_AFTER_MS = 6000

const OUTCOMES = [
  {
    key: 'SUCCESS',
    label: 'Pay successfully',
    hint: 'Gateway confirms and sends the callback immediately.',
    tone: 'bg-brand-600 hover:bg-brand-700',
  },
  {
    key: 'SUCCESS_NO_CALLBACK',
    label: 'Pay, but callback is lost',
    hint: 'Payment succeeds and no callback arrives — the fallback status check resolves it.',
    tone: 'bg-amber-600 hover:bg-amber-700',
  },
  {
    key: 'FAILURE',
    label: 'Fail the payment',
    hint: 'Gateway declines the payment; the order stays a draft so you can retry.',
    tone: 'bg-rose-600 hover:bg-rose-700',
  },
]

export default function Payment() {
  const { gatewayRef } = useParams()
  const { state } = useLocation()
  const navigate = useNavigate()
  const { handleAuthError } = useAuth()
  const { refresh: refreshCart } = useCart()

  const orderId = state?.orderId
  const amount = state?.amount

  const [phase, setPhase] = useState('gateway') // gateway | waiting | failed
  const [waitingSince, setWaitingSince] = useState(null)
  const [elapsed, setElapsed] = useState(0)
  const [error, setError] = useState(null)
  const pollRef = useRef(null)

  useEffect(() => {
    if (!orderId) navigate('/orders', { replace: true })
  }, [orderId, navigate])

  /**
   * Week 3: the screen never assumes the callback arrived. It polls the backend,
   * which queries the gateway directly when the callback window has passed — so
   * a lost callback resolves itself instead of stranding the order in "pending".
   */
  const poll = useCallback(async () => {
    try {
      const order = await api.paymentStatus(orderId)
      const payment = order.payment

      if (payment?.status === 'SUCCESS') {
        clearInterval(pollRef.current)
        await refreshCart()
        navigate(`/orders/${orderId}`, {
          replace: true,
          state: { justPlaced: true, resolvedVia: payment.resolvedVia },
        })
        return
      }

      if (payment?.status === 'FAILED') {
        clearInterval(pollRef.current)
        setPhase('failed')
      }
    } catch (e) {
      if (!handleAuthError(e)) setError(e.message)
    }
  }, [orderId, navigate, refreshCart, handleAuthError])

  useEffect(() => {
    if (phase !== 'waiting') return undefined
    pollRef.current = setInterval(poll, POLL_INTERVAL_MS)
    const ticker = setInterval(() => setElapsed(Date.now() - waitingSince), 500)
    poll()
    return () => {
      clearInterval(pollRef.current)
      clearInterval(ticker)
    }
  }, [phase, poll, waitingSince])

  const pay = async (outcome) => {
    setError(null)
    try {
      await api.mockPay(gatewayRef, outcome)
      setWaitingSince(Date.now())
      setElapsed(0)
      setPhase('waiting')
    } catch (e) {
      setError(e.message)
    }
  }

  if (!orderId) return null

  // ------------------------------------------------------------- waiting
  if (phase === 'waiting') {
    const callbackLate = elapsed > FALLBACK_NOTICE_AFTER_MS
    return (
      <div className="mx-auto max-w-md text-center">
        <div className="rounded-2xl border border-slate-200 bg-white p-10">
          <Spinner className="mx-auto h-10 w-10 text-brand-600" />
          <h1 className="mt-5 text-xl font-bold text-slate-900">Confirming your payment</h1>
          <p className="mt-2 text-sm text-slate-500">
            Please don't close this page — we're waiting for the gateway to confirm.
          </p>

          {callbackLate && (
            <div className="mt-5">
              <Banner tone="info">
                The gateway hasn't called back yet, so we're checking the payment status with it
                directly. Your order will be confirmed as soon as it responds.
              </Banner>
            </div>
          )}

          {error && (
            <div className="mt-4">
              <Banner tone="error">{error}</Banner>
            </div>
          )}

          <p className="mt-6 text-xs text-slate-400">Reference: {gatewayRef}</p>
        </div>
      </div>
    )
  }

  // -------------------------------------------------------------- failed
  if (phase === 'failed') {
    return (
      <div className="mx-auto max-w-md text-center">
        <div className="rounded-2xl border border-rose-200 bg-white p-10">
          <p className="text-4xl">❌</p>
          <h1 className="mt-4 text-xl font-bold text-slate-900">Payment failed</h1>
          <p className="mt-2 text-sm text-slate-500">
            Your order hasn't been placed and you haven't been charged. Your cart is still intact.
          </p>
          <button
            type="button"
            onClick={() => navigate('/checkout')}
            className="mt-6 w-full rounded-lg bg-brand-600 py-2.5 font-semibold text-white hover:bg-brand-700"
          >
            Try again
          </button>
        </div>
      </div>
    )
  }

  // ------------------------------------------------------------- gateway
  return (
    <div className="mx-auto max-w-md">
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div className="bg-slate-900 px-6 py-5 text-white">
          <p className="text-xs uppercase tracking-wide text-slate-400">Secure checkout</p>
          <h1 className="mt-1 text-lg font-bold">Mock Payment Gateway</h1>
        </div>

        <div className="p-6">
          <div className="rounded-xl bg-slate-50 p-4 text-center">
            <p className="text-sm text-slate-500">Amount payable</p>
            <p className="mt-1 text-3xl font-bold text-slate-900">{money(amount)}</p>
            <p className="mt-1 text-xs text-slate-400">Ref: {gatewayRef}</p>
          </div>

          {error && (
            <div className="mt-4">
              <Banner tone="error">{error}</Banner>
            </div>
          )}

          <p className="mt-6 text-xs text-slate-500">
            This stands in for a real gateway's hosted page. Each option below simulates a different
            real-world outcome, including a lost callback.
          </p>

          <div className="mt-3 space-y-3">
            {OUTCOMES.map((outcome) => (
              <div key={outcome.key}>
                <button
                  type="button"
                  onClick={() => pay(outcome.key)}
                  className={`w-full rounded-lg py-2.5 font-semibold text-white transition ${outcome.tone}`}
                >
                  {outcome.label}
                </button>
                <p className="mt-1 px-1 text-xs text-slate-400">{outcome.hint}</p>
              </div>
            ))}
          </div>

          <button
            type="button"
            onClick={() => navigate('/checkout')}
            className="mt-6 w-full rounded-lg py-2 text-sm font-medium text-slate-500 hover:bg-slate-50"
          >
            Cancel and go back
          </button>
        </div>
      </div>
    </div>
  )
}
