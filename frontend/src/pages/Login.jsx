import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Banner, Spinner } from '../components/States'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'

export default function Login() {
  const { signIn } = useAuth()
  const { refresh } = useCart()
  const navigate = useNavigate()
  const location = useLocation()

  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await signIn(form)
      await refresh()
      navigate(location.state?.from || '/', { replace: true })
    } catch (e) {
      setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  const field = (name) => ({
    value: form[name],
    onChange: (e) => setForm((prev) => ({ ...prev, [name]: e.target.value })),
  })

  return (
    <div className="mx-auto max-w-md">
      <div className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">Welcome back</h1>
        <p className="mt-1 text-sm text-slate-500">Sign in to continue shopping.</p>

        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          {error && <Banner tone="error">{error}</Banner>}

          <div>
            <label htmlFor="email" className="mb-1 block text-sm font-medium text-slate-700">
              Email
            </label>
            <input
              id="email"
              type="email"
              required
              autoComplete="email"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
              {...field('email')}
            />
          </div>

          <div>
            <label htmlFor="password" className="mb-1 block text-sm font-medium text-slate-700">
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              autoComplete="current-password"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
              {...field('password')}
            />
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand-600 py-2.5 font-semibold text-white transition hover:bg-brand-700 disabled:opacity-60"
          >
            {submitting && <Spinner className="h-4 w-4" />}
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          New here?{' '}
          <Link to="/signup" className="font-medium text-brand-700 hover:underline">
            Create an account
          </Link>
        </p>

        <div className="mt-6 rounded-xl bg-slate-50 p-4 text-xs text-slate-500">
          <p className="font-medium text-slate-600">Demo account</p>
          <p className="mt-1">demo@grocery.test · demo1234</p>
        </div>
      </div>
    </div>
  )
}
