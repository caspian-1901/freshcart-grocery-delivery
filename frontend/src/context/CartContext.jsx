import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'
import { useAuth } from './AuthContext'

const CartContext = createContext(null)

const EMPTY = { lines: [], subtotal: 0, deliveryFee: 0, total: 0, itemCount: 0, hasStockIssue: false }

export function CartProvider({ children }) {
  const { user, handleAuthError } = useAuth()
  const [cart, setCart] = useState(EMPTY)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const refresh = useCallback(async () => {
    if (!user) {
      setCart(EMPTY)
      return
    }
    setLoading(true)
    try {
      setCart(await api.cart())
      setError(null)
    } catch (e) {
      if (!handleAuthError(e)) setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [user, handleAuthError])

  useEffect(() => {
    refresh()
  }, [refresh])

  /**
   * Every mutation returns the server's recomputed cart, so the UI always shows
   * server-validated quantities rather than an optimistic local guess — this is
   * the Week 2 lesson about not trusting client-side stock checks.
   */
  const run = useCallback(
    async (operation) => {
      setError(null)
      try {
        setCart(await operation())
        return { ok: true }
      } catch (e) {
        if (handleAuthError(e)) return { ok: false, message: 'Your session expired.' }
        setError(e.message)
        return { ok: false, message: e.message }
      }
    },
    [handleAuthError],
  )

  const value = useMemo(
    () => ({
      cart,
      loading,
      error,
      refresh,
      clearError: () => setError(null),
      addItem: (itemId, quantity = 1) => run(() => api.addToCart(itemId, quantity)),
      setQuantity: (itemId, quantity) => run(() => api.setQuantity(itemId, quantity)),
      removeItem: (itemId) => run(() => api.removeFromCart(itemId)),
      reset: () => setCart(EMPTY),
    }),
    [cart, loading, error, refresh, run],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart() {
  const context = useContext(CartContext)
  if (!context) throw new Error('useCart must be used inside CartProvider')
  return context
}
