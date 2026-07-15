# 3. One customer document + stateless JWT auth

Date: 2026-07-15

## Status

Accepted

## Context

The legacy shreds one customer across six entity beans/tables (User, Account,
Profile, ContactInfo, CreditCard, Address) and authenticates against a `User`
table storing **plaintext passwords** (`<Password>j2ee</Password>` in the seed
data), with server-side HTTP sessions. The modern app is a stateless REST API
consumed by an SPA; it needs an auth mechanism and a customer shape that
doesn't require six joins to render an account page.

## Decision

**One `customers` document per shopper**, `_id` = the legacy user id
(`j2ee`), embedding the whole aggregate: `account.contactInfo`
(name/address/email/phone — `address.street` is a list because the legacy
allows two `StreetName` lines), `account.creditCard`, and `profile`
(language/category/preferences). The aggregate is always read and written
whole; nothing else references its parts, so embedding is the natural shape.

**Roles live on the document** (`roles: ["USER"]`, admin gets
`["USER","ADMIN"]`). The legacy's separate admin webapp user (`jps_admin`)
becomes just another customer document with an extra role — one app,
role-gated.

**Passwords are BCrypt-hashed at migration time.** The migration parser is the
only code that ever reads the legacy plaintext; it hashes immediately and the
hash is never serialized out of any API response. Tests assert both that the
hash is not the plaintext and that migrated legacy credentials still log in.

**Auth is a stateless JWT (HS256)** issued by `/api/auth/login` using Spring
Security's own OAuth2 resource-server support (Nimbus, symmetric key) — no
third-party JWT library. Subject = username, a `roles` claim maps to
`ROLE_*` authorities. Catalog browsing stays anonymous; everything else under
`/api/**` requires a token; `/api/admin/**` requires `ROLE_ADMIN`.

## Consequences

- No joins to render the account page; customer reads/writes are one document.
- The HS256 secret must be shared (identically configured) by any service that
  validates tokens — accepted for a two-service system; an asymmetric key or
  an identity provider is the growth path.
- Stateless tokens can't be revoked before expiry; TTL (`petstore.jwt.ttl`)
  bounds the exposure.
- Credit-card data is stored as-is from the legacy seed (fake numbers); a real
  deployment would tokenize with a payment provider instead.
