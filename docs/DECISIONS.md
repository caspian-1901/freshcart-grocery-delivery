# Design Decisions

Structured around the decision rather than a chronological log — for each: what was decided, what
else was considered, and how it turned out. This is the format chosen in Week 4 after finding a
week-by-week narrative harder for a reviewer to use.

---

## 1. Scoping a mixed-domain brief (Week 1)

**Decision.** Build only the grocery delivery MVP: catalog browsing, cart, delivery slot
selection, payment, order tracking. Features belonging to the other domains in the brief (vehicle
fitment lookup, restaurant menu management) were explicitly marked out of scope.

**Alternatives considered.** Building a generic system flexible enough for all three domains. It
was rejected as over-engineering for a 4-week timeline — the abstraction cost would have come out
of the core commerce flow.

**Outcome.** Every feature was tagged core / stretch / not-applicable and the remaining list was
checked against the deadline before any code was written. The MVP shipped complete, and stretch
features (recommendations, loyalty) were dropped on purpose rather than left half-built.

---

## 2. Normalisation of Orders / Order_Items (Week 1)

**Decision.** Keep `ORDER_ITEMS` as a separate table, but snapshot `item_name`, `unit` and
`unit_price` onto each row.

**Alternatives considered.**
- *Fully normalised* — join back to `ITEMS` for name and price. Rejected: order history would show
  today's price instead of the price paid.
- *Flat* — store a serialised item list on the order. Rejected: item-wise quantity and price
  become impossible to query.

**Outcome.** Both candidate schemas were tested against real sample queries ("show order history
with prices at the time of purchase") before implementation. The snapshot design answers that
query with a simple join and no historical drift. The same reasoning was later applied to the
delivery address, which is also snapshotted onto the order.

---

## 3. Where stock is validated (Week 2)

**Decision.** Validate stock server-side at the point of write, re-reading the item row under a
`PESSIMISTIC_WRITE` lock inside the writing transaction. Client-submitted quantities are never
trusted.

**Problem it solves.** Concurrent cart updates could each pass an availability check made against
a stale read, letting the combined quantity exceed real stock.

**Alternatives considered.** Checking at read time and trusting the client's quantity (fast, but
exactly the race that caused the bug); optimistic locking with a version column and retry
(workable, but pessimistic locking is simpler to reason about at this scale).

**Outcome.** One shared `requireStock` gate is used by cart writes *and* by order confirmation, so
the final decrement is always guarded even if the cart check passed minutes earlier. Covered by
integration tests.

---

## 4. Slot availability and staleness (Week 2)

**Decision.** Store `capacity` and `booked` on the slot, reserve a place only at order
confirmation under a row lock, and re-fetch availability at the review step rather than trusting
the picker's initial load.

**Problem it solves.** The UI could show a slot as available after it had filled up, letting a
user select an already-full window.

**Outcome.** A slot that fills up mid-checkout is caught at review with a clear warning, and
payment is blocked until another slot is chosen. The reservation itself can't oversell because the
capacity check and increment happen under the same lock. The general lesson — anything touching
shared availability must be re-validated at write time, not just read time — is the same one
behind decision 3.

---

## 5. Payment callback reliability (Week 3)

**Decision.** Treat the gateway callback as unreliable. Two paths can resolve a payment:

1. the callback (`resolvedVia = CALLBACK`), and
2. a direct status query, run on demand by the payment screen and by a scheduled sweep once the
   callback window has passed (`resolvedVia = FALLBACK_STATUS_CHECK`).

Resolution is idempotent — whichever arrives first wins, the other is a no-op.

**Problem it solves.** Callbacks did not always arrive promptly, leaving orders stuck in "pending"
even though the payment had gone through.

**Alternatives considered.** Waiting longer on the callback and showing a spinner (doesn't fix a
callback that never arrives); polling the gateway from the frontend (leaks gateway credentials to
the client and stops the moment the user closes the tab).

**Outcome.** No order can be stranded by a lost callback. `resolvedVia` is persisted so it's
visible which path confirmed each order — the tracking screen even tells the user when the
fallback was used. Both paths are covered by tests, including a duplicate callback arriving after
the fallback already confirmed.

---

## 6. Order confirmation is one transaction (Week 3)

**Decision.** An order stays in `DRAFT` until payment is verified. Confirmation then decrements
stock, reserves the slot, records the `PLACED` status event and clears the cart — all in a single
transaction.

**Outcome.** A failed payment leaves inventory, the slot and the cart untouched, so the user can
retry without losing anything. This also keeps "was this paid for?" and "was inventory taken?"
from ever disagreeing.

---

## 7. Order status as an event log (Week 3)

**Decision.** Record each transition as a row in `ORDER_STATUS_EVENTS` with its own timestamp and
note, rather than keeping only a current status and a single `updated_at`.

**Alternatives considered.** A status column plus `updated_at` — smaller, but it can't render a
timeline, and once a status changes the previous timing is lost.

**Outcome.** The tracking screen renders the full timeline directly from the event rows. Status is
also re-fetched on every view of the screen instead of being read from cached state, which is what
fixed the stale-status bug found during end-to-end testing.

---

## 8. Session expiry caught before payment (Week 4)

**Decision.** Check session validity at the review step, before handing off to the gateway, and
surface an expired token as a distinct `TOKEN_EXPIRED` code with a clear re-authenticate prompt.

**Problem it solves.** A JWT expiring mid-checkout produced a confusing failure at the payment
step — the worst possible place for it.

**Outcome.** The user is asked to sign in again *before* any payment is attempted. The frontend
also treats `TOKEN_EXPIRED` differently from a generic 401 everywhere else, so the message is
always "your session expired" rather than "something went wrong".

---

## 9. Mock payment gateway instead of a live one

**Decision.** Ship a `MockPaymentGateway` component that reproduces gateway semantics — order
creation, hosted-page outcomes, signature verification, and a status-query API — including the
ability to deliberately withhold a callback.

**Rationale.** A capstone reviewer can't be expected to hold live gateway credentials, and the
lost-callback case that drove the Week 3 design is nearly impossible to trigger on demand against
a real sandbox. Swapping in a real SDK means reimplementing `createOrder`, `fetchStatus` and real
HMAC signature verification; nothing in `PaymentService` or the order flow changes.

---

## 10. Consistent loading, error and empty states (Week 4)

**Decision.** One shared set of `Loading` / `ErrorState` / `EmptyState` / `Banner` components, and
one error shape (`{code, message, timestamp}`) from every backend failure.

**Outcome.** Every screen gives the same kind of feedback, and the empty catalog, empty cart and
no-orders cases are handled explicitly rather than rendering a blank page. Consistency problems
across modules were only obvious once the whole flow was used end to end — which is why this pass
was left until the final week, and why it was worth doing.
