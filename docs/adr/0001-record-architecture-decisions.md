# 1. Record architecture decisions

Date: 2026-07-14

## Status

Accepted

## Context

We need to record the architectural decisions made on this project. This is a
legacy migration where the *process* matters as much as the result: every
consequential mapping from a J2EE-era construct to its modern replacement
(entity beans to documents, JMS to change streams, session auth to JWT) should
leave a written trail of what was decided, what it cost, and what the
at-scale alternative would be.

## Decision

We will use Architecture Decision Records, as described by Michael Nygard in
[Documenting Architecture Decisions](http://thinkrelevance.com/blog/2011/11/15/documenting-architecture-decisions).

## Consequences

See Michael Nygard's article, linked above.
