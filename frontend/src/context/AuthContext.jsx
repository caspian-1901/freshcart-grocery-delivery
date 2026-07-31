import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, getToken, setToken } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [sessionExpired, setSessionExpired] = useState(false)

  useEffect(() => {
    const token = getToken()
    if (!token) {
      setLoading(false)
      return
    }
    api
      .me()
      .then(setUser)
      .catch(() => {
        setToken(null)
        setUser(null)
      })
      .finally(() => setLoading(false))
  }, [])

  const signIn = useCallback(async (credentials) => {
    const auth = await api.login(credentials)
    setToken(auth.token)
    setUser(auth.user)
    setSessionExpired(false)
    return auth
  }, [])

  const register = useCallback(async (payload) => {
    const auth = await api.signup(payload)
    setToken(auth.token)
    setUser(auth.user)
    setSessionExpired(false)
    return auth
  }, [])

  const signOut = useCallback(() => {
    setToken(null)
    setUser(null)
  }, [])

  /**
   * Week 4 fix: checkout calls this *before* sending the user to payment, so an
   * expiring session prompts a re-login early rather than failing at the
   * payment step. Returns true when the session is good to continue.
   */
  const ensureSessionFresh = useCallback(
    async (minimumSecondsLeft = 120) => {
      try {
        const session = await api.session()
        if (!session.valid || session.secondsRemaining < minimumSecondsLeft) {
          setSessionExpired(true)
          return false
        }
        return true
      } catch {
        setSessionExpired(true)
        return false
      }
    },
    [],
  )

  /** Called by any screen that catches a TOKEN_EXPIRED response. */
  const handleAuthError = useCallback(
    (error) => {
      if (error?.isSessionExpired) {
        setSessionExpired(true)
        setToken(null)
        setUser(null)
        return true
      }
      return false
    },
    [],
  )

  const value = useMemo(
    () => ({
      user,
      loading,
      sessionExpired,
      setSessionExpired,
      signIn,
      register,
      signOut,
      ensureSessionFresh,
      handleAuthError,
    }),
    [user, loading, sessionExpired, signIn, register, signOut, ensureSessionFresh, handleAuthError],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
