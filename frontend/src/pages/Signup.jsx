import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Banner, Spinner } from '../components/States'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'

export default function Signup() {
  const { register } = useAuth()
  const { refresh } = useCart()
  const navigate = useNavigate()

  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '' })
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await register(form)
      await refresh()
      navigate('/', { replace: true })
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

  const inputClass =
    'w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100'

  return (
    <div className="mx-auto max-w-md">
      <div className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">Create your account</h1>
        <p className="mt-1 text-sm text-slate-500">Fresh groceries, delivered in your chosen slot.</p>

        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          {error && <Banner tone="error">{error}</Banner>}

          <div>
            <label htmlFor="name" className="mb-1 block text-sm font-medium text-slate-700">
              Full name
            </label>
            <input id="name" required className={inputClass} {...field('name')} />
          </div>

          <div>
            <label htmlFor="email" className="mb-1 block text-sm font-medium text-slate-700">
              Email
            </label>
            <input id="email" type="email" required className={inputClass} {...field('email')} />
          </div>

          <div>
            <label htmlFor="phone" className="mb-1 block text-sm font-medium text-slate-700">
              Phone
            </label>
            <input id="phone" className={inputClass} {...field('phone')} />
          </div>

          <div>
            <label htmlFor="password" className="mb-1 block text-sm font-medium text-slate-700">
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              minLength={8}
              className={inputClass}
              {...field('password')}
            />
            <p className="mt-1 text-xs text-slate-400">At least 8 characters.</p>
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand-600 py-2.5 font-semibold text-white transition hover:bg-brand-700 disabled:opacity-60"
          >
            {submitting && <Spinner className="h-4 w-4" />}
            {submitting ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-brand-700 hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
