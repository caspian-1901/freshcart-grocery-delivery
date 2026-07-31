const TOKEN_KEY = 'freshcart.token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

/** Thrown for every non-2xx response, carrying the backend's stable error code. */
export class ApiError extends Error {
  constructor(message, code, status) {
    super(message)
    this.code = code
    this.status = status
  }

  /** Week 4: an expired session is handled distinctly from a plain auth failure. */
  get isSessionExpired() {
    return this.code === 'TOKEN_EXPIRED' || this.code === 'UNAUTHENTICATED'
  }
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (auth && token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError('Could not reach the server. Is the backend running?', 'NETWORK_ERROR', 0)
  }

  if (response.status === 204) return null

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    throw new ApiError(
      data?.message || 'Something went wrong. Please try again.',
      data?.code || 'ERROR',
      response.status,
    )
  }
  return data
}

export const api = {
  signup: (payload) => request('/auth/signup', { method: 'POST', body: payload, auth: false }),
  login: (payload) => request('/auth/login', { method: 'POST', body: payload, auth: false }),
  me: () => request('/auth/me'),
  session: () => request('/auth/session'),

  items: (q, category) => {
    const params = new URLSearchParams()
    if (q) params.set('q', q)
    if (category && category !== 'All') params.set('category', category)
    const qs = params.toString()
    return request(`/items${qs ? `?${qs}` : ''}`, { auth: false })
  },
  categories: () => request('/categories', { auth: false }),

  cart: () => request('/cart'),
  addToCart: (itemId, quantity = 1) =>
    request('/cart/items', { method: 'POST', body: { itemId, quantity } }),
  setQuantity: (itemId, quantity) =>
    request(`/cart/items/${itemId}`, { method: 'PUT', body: { quantity } }),
  removeFromCart: (itemId) => request(`/cart/items/${itemId}`, { method: 'DELETE' }),

  slots: (days = 4) => request(`/slots?days=${days}`, { auth: false }),

  addresses: () => request('/addresses'),
  createAddress: (payload) => request('/addresses', { method: 'POST', body: payload }),

  createDraft: (addressId, slotId) =>
    request('/orders/draft', { method: 'POST', body: { addressId, slotId } }),
  reviewDraft: (orderId) => request(`/orders/${orderId}/review`),
  orders: () => request('/orders'),
  order: (orderId) => request(`/orders/${orderId}`),
  advanceOrder: (orderId) => request(`/orders/${orderId}/advance`, { method: 'POST' }),
  cancelOrder: (orderId) => request(`/orders/${orderId}/cancel`, { method: 'POST' }),

  initiatePayment: (orderId, method) =>
    request('/payments/initiate', { method: 'POST', body: { orderId, method } }),
  /** The fallback status check the payment screen polls (Week 3). */
  paymentStatus: (orderId) => request(`/payments/orders/${orderId}/status`),
  mockPay: (gatewayRef, outcome) =>
    request(`/payments/mock-gateway/${gatewayRef}/pay?outcome=${outcome}`, {
      method: 'POST',
      auth: false,
    }),
}

export const money = (value) =>
  `₹${Number(value ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
