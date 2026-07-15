# 4. Client-side cart, server-side re-pricing at checkout

Date: 2026-07-15

## Status

Accepted

## Context

The legacy keeps the shopping cart in a stateful session EJB tied to the HTTP
session. The modern backend is deliberately stateless (JWT, no sessions), so
the cart has to live somewhere else — and wherever it lives, its prices reach
the server from an untrustworthy place.

## Decision

**The cart is client state**: an Angular service backed by signals and
persisted to `localStorage` (survives reloads, works anonymously before
sign-in). No cart endpoints exist on the server.

**Checkout sends only `{itemId, qty}` lines.** The order service re-prices
every line server-side from the `items` collection — requested locale's
`listPrice`, whole-block en_US fallback, the same rule catalog reads use —
and pulls `productId`/`categoryId` server-side too. Client-supplied prices
are never accepted, so a tampered cart can change nothing but quantities.

## Consequences

- No server storage, no cart cleanup jobs, no anonymous-session merging.
- Carts don't roam across devices — accepted; a server-side cart is only
  worth its state if that becomes a requirement.
- A price change between add-to-cart and checkout silently wins at checkout
  (the confirmation page shows the server's prices) — same behavior a shopper
  gets from most real stores.
- The cart's displayed prices are advisory; the order document is the
  authoritative record.
