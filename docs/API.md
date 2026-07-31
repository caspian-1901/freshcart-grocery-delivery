# API Reference

Base URL: `http://localhost:8080/api`

All authenticated endpoints expect `Authorization: Bearer <jwt>`.

Every error response uses the same shape:

```json
{ "code": "INSUFFICIENT_STOCK", "message": "Only 3 1 kg of Tomatoes left in stock.", "timestamp": "..." }
```

The frontend branches on `code`; `message` is safe to show to the user.

| Code | Meaning |
| --- | --- |
| `VALIDATION_ERROR` | Request body failed validation |
| `BAD_CREDENTIALS` | Wrong email or password |
| `EMAIL_TAKEN` | Sign-up email already registered |
| `TOKEN_EXPIRED` | JWT expired — prompt re-login |
| `UNAUTHENTICATED` | Missing or invalid token |
| `NOT_FOUND` | Resource does not exist or isn't yours |
| `INSUFFICIENT_STOCK` | Requested quantity exceeds live inventory |
| `ITEM_UNAVAILABLE` | Item deactivated or removed |
| `SLOT_FULL` | Delivery slot filled up |
| `EMPTY_CART` | Draft attempted with no items |
| `INCOMPLETE_ORDER` | Missing address or slot |
| `ALREADY_PAID` / `ALREADY_PLACED` | Order already progressed |
| `NO_NEXT_STATUS` | Order already delivered/cancelled |
| `CANNOT_CANCEL` | Too late to cancel |

---

## Auth

### `POST /auth/signup`
```json
{ "name": "Isha Sharma", "email": "isha@example.com", "password": "atleast8chars", "phone": "9876543210" }
```
→ `{ "token": "...", "expiresAt": "...", "user": { "id": 1, "name": "...", "email": "...", "phone": "..." } }`

Password is BCrypt-hashed; a cart is created for the user at sign-up.

### `POST /auth/login`
```json
{ "email": "demo@grocery.test", "password": "demo1234" }
```
→ same shape as sign-up.

### `GET /auth/me` 🔒
→ the current user summary.

### `GET /auth/session` 🔒
→ `{ "valid": true, "expiresAt": "...", "secondsRemaining": 3421 }`

Called by checkout before handing off to payment, so an expiring session prompts a re-login early
rather than failing at the payment step.

---

## Catalog

### `GET /items?q=&category=`
→ list of items with `price`, `availableQuantity`, `inStock`. Public.

### `GET /items/{id}`
### `GET /categories`
→ `["Bakery", "Beverages", ...]`. Public.

---

## Cart 🔒

Every mutation returns the **full recomputed cart**, so the client never has to guess at totals or
stock state.

### `GET /cart`
```json
{
  "lines": [{ "id": 1, "itemId": 4, "name": "Tomatoes", "unitPrice": 40.00, "quantity": 3,
              "lineTotal": 120.00, "availableQuantity": 60, "stockIssue": false }],
  "subtotal": 120.00, "deliveryFee": 25.00, "total": 145.00,
  "itemCount": 3, "hasStockIssue": false
}
```

### `POST /cart/items` — `{ "itemId": 4, "quantity": 2 }`
Adds to the existing quantity. Stock is re-read under a row lock and validated server-side.

### `PUT /cart/items/{itemId}` — `{ "quantity": 5 }`
Sets an **absolute** quantity; `0` removes the line. Re-validated the same way.

### `DELETE /cart/items/{itemId}`

Delivery fee: ₹25, free at or above ₹500.

---

## Delivery slots

### `GET /slots?days=4`
Public. Slots grouped by day, with past windows for today already filtered out.

```json
[{ "date": "2026-08-01", "label": "Tomorrow",
   "slots": [{ "id": 7, "startTime": "8:00 am", "endTime": "10:00 am",
               "capacity": 5, "remaining": 4, "full": false }] }]
```

### `GET /slots/{id}`

---

## Addresses 🔒

### `GET /addresses`
### `POST /addresses`
```json
{ "label": "Home", "line1": "12, Green Park", "line2": "Near City Mall",
  "city": "Pune", "pincode": "411014", "phone": "9876543210", "defaultAddress": true }
```
### `DELETE /addresses/{id}`

---

## Orders 🔒

### `POST /orders/draft` — `{ "addressId": 1, "slotId": 7 }`
Builds the draft from cart + address + slot. Rejects an empty cart, a missing address/slot, or a
slot that is already full. Item name and unit price are snapshotted onto each order line.

### `GET /orders/{id}/review`
The pre-payment re-check.

```json
{ "order": { ... }, "slotStillAvailable": true, "stockStillAvailable": true, "warnings": [] }
```

Slot availability and stock are re-fetched here rather than trusted from the earlier page load.

### `GET /orders`
All non-draft orders, newest first.

### `GET /orders/{id}`
Full order with `timeline` and `payment`. The tracking screen calls this on every view so status
is never served from cached client state.

### `POST /orders/{id}/advance`
Moves to the next tracking status. Stands in for the warehouse/delivery apps in a production
system.

### `POST /orders/{id}/cancel`
Restores stock and releases the slot. Rejected once the order is out for delivery.

---

## Payments

### `POST /payments/initiate` 🔒 — `{ "orderId": 12, "method": "UPI" }`
```json
{ "gatewayRef": "pay_4447286d97fe", "checkoutUrl": "/pay/pay_4447286d97fe",
  "amount": 270.00, "callbackTimeoutSeconds": 45 }
```
Re-checks the slot one last time before taking money. Retrying after a failure reuses the same
order with a fresh gateway reference.

### `POST /payments/callback`
Posted by the gateway. Public (signature-verified), not user-authenticated.
```json
{ "gatewayRef": "pay_4447286d97fe", "status": "SUCCESS", "signature": "..." }
```
On success the order is confirmed: stock decremented under lock, slot reserved, cart cleared, and
a `PLACED` status event timestamped.

### `GET /payments/orders/{orderId}/status` 🔒
**The fallback status check.** Queries the gateway directly when the payment is still pending, so
a lost or delayed callback never strands an order. Returns the current order view. A scheduled job
does the same sweep every 30 seconds for payments older than the callback window.

Resolution is idempotent — whichever path arrives first confirms the order, the other is ignored.

### `POST /payments/mock-gateway/{gatewayRef}/pay?outcome=SUCCESS` *(mock only)*
Stands in for the gateway's hosted page. `outcome` is one of `SUCCESS`, `SUCCESS_NO_CALLBACK`
(pays but withholds the callback, to exercise the fallback), or `FAILURE`.
