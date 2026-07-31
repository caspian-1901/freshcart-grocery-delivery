# FreshCart — Grocery Delivery Application

Full Stack Development capstone project by **Isha Sharma**, built to the scope and design
decisions recorded in the four weekly progress reports.

React + Tailwind CSS frontend · Spring Boot REST API · PostgreSQL · JWT auth · payment gateway
integration with a callback fallback · timestamped order tracking.

---

## MVP scope

The scope finalised in Week 1 (and delivered in full):

| Module | Status |
| --- | --- |
| User sign-up / login (JWT, BCrypt hashing) | ✅ |
| Catalog browsing with godown inventory (quantity + price) | ✅ |
| Cart — add / remove / update quantity with stock validation | ✅ |
| Delivery slot selection by date and time window | ✅ |
| Address management | ✅ |
| Order draft (cart + address + slot) with review re-check | ✅ |
| Payment gateway integration + order confirmation | ✅ |
| Order tracking (Placed → Packed → Out for Delivery → Delivered) | ✅ |

Stretch features (recommendations, loyalty programs) were deliberately not pursued — priority
went to a fully functional and polished core commerce flow, as stated in Week 4.

---

## Quick start

Two terminals. The backend runs on an in-memory H2 database by default, so **no database setup
is needed** to try it.

**1 — Backend** (http://localhost:8080)

```bash
cd backend && mvn spring-boot:run
```

**2 — Frontend** (http://localhost:5173)

```bash
cd frontend && npm install && npm run dev
```

Then open http://localhost:5173 and sign in with the seeded demo account:

```
demo@grocery.test  /  demo1234
```

The catalog, delivery slots for the next 7 days, and one saved address are seeded automatically.

### Running against PostgreSQL

The `postgres` profile switches to the real database designed in Week 1:

```bash
docker compose up -d
```

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

Connection settings default to `localhost:5432/grocerydb` with user/password `grocery`, and can
be overridden with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD`.

### Tests

```bash
cd backend && mvn test
```

11 integration tests cover the full flow: stock re-validation, empty-cart rejection, slot
contention, price snapshotting, successful payment, lost callback resolved by the fallback,
duplicate callbacks, failed payment, and the tracking timeline.

---

## Trying the interesting cases

The mock gateway checkout page exposes three buttons, each reproducing a real-world outcome that
came up during development:

| Button | What it demonstrates |
| --- | --- |
| **Pay successfully** | Normal path — gateway callback confirms the order immediately. |
| **Pay, but callback is lost** | The Week 3 problem. Payment succeeds, no callback arrives; the payment screen polls and the backend queries the gateway directly, confirming the order via `FALLBACK_STATUS_CHECK`. |
| **Fail the payment** | Order stays a draft, stock untouched, cart intact so the user can retry. |

Other behaviours worth exercising:

- **Stock race** — set a cart quantity, then reduce that item's stock; the next update is rejected
  server-side rather than trusted from the client.
- **Stale slot** — fill a slot's capacity after reaching the review step; the review reports it and
  blocks payment.
- **Session expiry** — with a short `APP_JWT_EXPIRY_MINUTES`, checkout prompts a re-login *before*
  the payment step instead of failing at it.

---

## Project layout

```
backend/                     Spring Boot API
  src/main/java/com/isha/grocery/
    config/                  JWT service, auth filter, security + CORS
    domain/                  JPA entities (Users, Items, Orders, Order_Items,
                             Delivery_Slots, Addresses, Payment, status events)
    repo/                    Spring Data repositories (incl. locked reads)
    dto/                     Request/response records
    service/                 Auth, catalog, cart, slots, orders, payments, gateway
    web/                     REST controllers + global error handling
    bootstrap/               Seed data
  src/test/java/             End-to-end integration tests

frontend/                    React + Vite + Tailwind CSS
  src/api/client.js          Typed API client, token handling, error codes
  src/context/               Auth + cart state
  src/components/            Layout, shared loading/error/empty states
  src/pages/                 Catalog, Cart, Checkout, Payment, Orders, Tracking

docs/                        Architecture, ER diagram, API reference, decisions
docker-compose.yml           PostgreSQL for the `postgres` profile
```

---

## Documentation

- [Architecture and ER diagram](docs/ARCHITECTURE.md)
- [API reference](docs/API.md)
- [Design decisions and trade-offs](docs/DECISIONS.md)
