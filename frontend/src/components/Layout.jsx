import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'

function navClass({ isActive }) {
  return `rounded-lg px-3 py-2 text-sm font-medium transition ${
    isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
  }`
}

export default function Layout({ children }) {
  const { user, signOut, sessionExpired, setSessionExpired } = useAuth()
  const { cart, reset } = useCart()
  const navigate = useNavigate()

  const handleSignOut = () => {
    signOut()
    reset()
    navigate('/login')
  }

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3">
          <Link to="/" className="flex items-center gap-2 text-lg font-bold text-brand-700">
            <span className="text-2xl">🥬</span> FreshCart
          </Link>

          <nav className="ml-4 hidden items-center gap-1 sm:flex">
            <NavLink to="/" className={navClass} end>
              Shop
            </NavLink>
            {user && (
              <NavLink to="/orders" className={navClass}>
                My Orders
              </NavLink>
            )}
          </nav>

          <div className="ml-auto flex items-center gap-2">
            <Link
              to="/cart"
              className="relative rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
            >
              🛒 Cart
              {cart.itemCount > 0 && (
                <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-600 px-1 text-xs font-semibold text-white">
                  {cart.itemCount}
                </span>
              )}
            </Link>

            {user ? (
              <div className="flex items-center gap-2">
                <span className="hidden text-sm text-slate-500 sm:inline">Hi, {user.name.split(' ')[0]}</span>
                <button
                  type="button"
                  onClick={handleSignOut}
                  className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50"
                >
                  Sign out
                </button>
              </div>
            ) : (
              <Link
                to="/login"
                className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
              >
                Sign in
              </Link>
            )}
          </div>
        </div>
      </header>

      {/* Week 4: expiry is surfaced as a clear prompt, not a silent failure. */}
      {sessionExpired && (
        <div className="border-b border-amber-200 bg-amber-50">
          <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3 text-sm text-amber-900">
            <span>⏱️</span>
            <p className="flex-1">Your session has expired. Please sign in again to continue.</p>
            <button
              type="button"
              onClick={() => {
                setSessionExpired(false)
                navigate('/login')
              }}
              className="rounded-lg bg-amber-600 px-3 py-1.5 font-medium text-white hover:bg-amber-700"
            >
              Sign in
            </button>
          </div>
        </div>
      )}

      <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>

      <footer className="border-t border-slate-200 py-6 text-center text-xs text-slate-400">
        FreshCart — Full Stack Capstone Project by Isha Sharma
      </footer>
    </div>
  )
}
