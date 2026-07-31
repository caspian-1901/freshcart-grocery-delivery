# System Architecture and Data Model

## System architecture

The three-tier design settled on in Week 1: a React SPA talking to a stateless Spring Boot REST
API over JWT-authenticated HTTP, with PostgreSQL for persistence and an external payment gateway
reached over HTTPS.

```mermaid
flowchart LR
    subgraph Client
        UI["React + Tailwind SPA<br/>Vite dev server :5173"]
    end

    subgraph Server["Spring Boot API :8080"]
        SEC["JWT filter<br/>+ Spring Security"]
        CTRL["REST controllers"]
        SVC["Services<br/>cart · slots · orders · payments"]
        JOB["Scheduled reconciliation job"]
        REPO["Spring Data JPA repositories"]
    end

    DB[("PostgreSQL<br/>(H2 in dev)")]
    PG["Payment gateway"]

    UI -->|"/api/** + Bearer token"| SEC
    SEC --> CTRL --> SVC --> REPO --> DB
    SVC -->|initiate payment| PG
    PG -->|callback| CTRL
    JOB -->|"fallback status query<br/>when no callback"| PG
    JOB --> SVC
```

Two things are worth calling out, because both came out of problems hit during development:

1. **The gateway is reached two ways.** The callback is the fast path; the scheduled job (and the
   on-demand check from the payment screen) is the safety net. Whichever resolves the payment
   first wins, and the other becomes a no-op.
2. **Writes that touch shared inventory take a row lock.** Stock and slot capacity are re-read
   under `PESSIMISTIC_WRITE` inside the writing transaction rather than trusted from an earlier
   read.

## ER diagram

```mermaid
erDiagram
    USERS ||--o{ ADDRESSES : "has"
    USERS ||--|| CARTS : "owns"
    USERS ||--o{ ORDERS : "places"
    CARTS ||--o{ CART_ITEMS : "contains"
    ITEMS ||--o{ CART_ITEMS : "listed in"
    ITEMS ||--o{ ORDER_ITEMS : "referenced by"
    ORDERS ||--o{ ORDER_ITEMS : "contains"
    ORDERS ||--o| PAYMENTS : "paid by"
    ORDERS ||--o{ ORDER_STATUS_EVENTS : "tracked by"
    ORDERS }o--|| DELIVERY_SLOTS : "delivered in"
    ORDERS }o--o| ADDRESSES : "delivered to"

    USERS {
        bigint id PK
        varchar name
        varchar email UK
        varchar password_hash
        varchar phone
        timestamp created_at
    }

    ADDRESSES {
        bigint id PK
        bigint user_id FK
        varchar label
        varchar line1
        varchar line2
        varchar city
        varchar pincode
        varchar phone
        boolean default_address
    }

    ITEMS {
        bigint id PK
        varchar name
        varchar category
        varchar description
        varchar emoji
        varchar unit
        decimal price
        int available_quantity
        boolean active
    }

    CARTS {
        bigint id PK
        bigint user_id FK "unique"
    }

    CART_ITEMS {
        bigint id PK
        bigint cart_id FK
        bigint item_id FK
        int quantity
    }

    DELIVERY_SLOTS {
        bigint id PK
        date slot_date
        time start_time
        time end_time
        int capacity
        int booked
    }

    ORDERS {
        bigint id PK
        varchar reference UK
        bigint user_id FK
        bigint address_id FK
        varchar delivery_address_snapshot
        bigint slot_id FK
        varchar status
        decimal subtotal
        decimal delivery_fee
        decimal total
        boolean slot_reserved
        timestamp created_at
        timestamp placed_at
    }

    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        bigint item_id FK
        varchar item_name "snapshot"
        varchar unit "snapshot"
        decimal unit_price "snapshot"
        int quantity
        decimal line_total
    }

    PAYMENTS {
        bigint id PK
        bigint order_id FK "unique"
        varchar gateway_ref UK
        varchar status
        decimal amount
        varchar method
        varchar resolved_via
        timestamp initiated_at
        timestamp completed_at
        timestamp last_polled_at
    }

    ORDER_STATUS_EVENTS {
        bigint id PK
        bigint order_id FK
        varchar status
        varchar note
        timestamp occurred_at
    }
```

### Notes on the schema

- **`ORDER_ITEMS` snapshots name, unit and unit price.** This is the normalisation trade-off
  resolved in Week 1: order history has to show prices *at the time of purchase*, so those columns
  are copied rather than joined live from `ITEMS`. The `item_id` foreign key is kept for
  reporting, but nothing in order history depends on the current catalog row.
- **`ORDERS.delivery_address_snapshot`** exists for the same reason — editing or deleting an
  address must not rewrite past orders.
- **`ORDER_STATUS_EVENTS` is a separate table** rather than a single timestamp column, because the
  tracking screen needs every transition with its own timestamp, not just the latest one.
- **`DELIVERY_SLOTS.booked` vs `capacity`** rather than counting orders per slot: it keeps the
  availability check to a single row read, and that row is the one that gets locked at booking
  time.
- **`PAYMENTS.resolved_via`** records whether a payment was confirmed by the callback or by the
  fallback status check — useful for demonstrating the Week 3 fix and for diagnosing gateway
  behaviour in production.

## Order lifecycle

```mermaid
stateDiagram-v2
    [*] --> DRAFT: cart + address + slot
    DRAFT --> DRAFT: payment failed (retry)
    DRAFT --> PLACED: payment verified<br/>(callback or fallback)
    PLACED --> PACKED
    PACKED --> OUT_FOR_DELIVERY
    OUT_FOR_DELIVERY --> DELIVERED
    PLACED --> CANCELLED
    PACKED --> CANCELLED
    DELIVERED --> [*]
    CANCELLED --> [*]
```

An order only leaves `DRAFT` once payment is verified. Stock decrement, slot reservation and cart
clearing all happen in that single confirming transaction, so a failed payment leaves inventory
and the cart untouched.
