# CLAUDE.md

## Project context — READ FIRST

**`status.md` is the single source of truth for this project.** Read it at the start of every session before doing anything else. It contains: the challenge definition, all decisions (§3 — these are FINAL, do not re-litigate or re-derive them), what's done, key findings, and the current next steps. When work completes or a decision is made, **update status.md in the same session** — it's how context survives between sessions.

Supporting docs (referenced from status.md):
- `ARCHITECTURE.md` — legacy system analysis
- `TARGET_ARCHITECTURE.md` — the target design (kept in sync with status.md decisions)
- `DEMO_FLOW.md` — legacy user flows = the acceptance spec for the migration
- `CODE_TOUR_SIMPLE.md` — plain-words legacy code tour with flow diagrams

The mission: migrate Java Pet Store 1.3.1 (J2EE) → Spring Boot 4.x/Java 21 + MongoDB + Angular, as a graded take-home for a MongoDB interview. The *migration process* is graded, not just the result — so tests-first, vertical slices, CI from commit #1, and ADRs matter as much as working code.

## Build workflow — HARD RULES (agreed 2026-07-14)

1. **Build in small parts, review-gated.** After each part, STOP and present the code to Bony for his own review — he reads everything to familiarize himself with the codebase (he must present this at a panel). Never build ahead of an unreviewed part.
2. **Sonnet builds, Fable reviews.** Dispatch code generation to Sonnet subagents; the main session (Fable) reviews the output before handing it to Bony.
3. **NEVER commit or push.** Bony checks in all code himself. When a part passes review, prompt him that it's ready to check in and suggest a commit message.
4. **Code location:** `/Users/bonyjohn/Documents/GitHub/petstoremodern` (GitHub repo: `petstore-modern`). This CLAUDE.md gets copied there in part 1.
5. **JSON everywhere.** The legacy app is XML-everything (DTDs, deployment descriptors, XML-over-JMS); the modern app is JSON-everything: REST+JSON APIs, JSON documents in Mongo. Spring config in **`.properties` files, not YAML** (Bony's preference). No XML except unavoidable build metadata (Maven `pom.xml`).
6. Every part must leave the app deployable/runnable.

---

# Behavioral guidelines

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
