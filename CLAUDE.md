# CLAUDE.md

## Read these before writing any code
- `spec.md` — design decisions and invariants. These are fixed. Do not change them or work around them.
- `stories.md` — the user stories, in build order. Implement only the story I name.

If something you need is not in either file, stop and ask. Do not invent requirements.

## Stack
- Java 17, Maven, JUnit 5. No framework.
- In-memory repositories behind interfaces, so a real database can replace them later.
- Build and test with `./mvnw -q test`. There is no global `mvn`.

## How we work
1. **Plan.** Classes, method signatures, the flow in order, and the tests. Wait for my go-ahead.
2. **Red.** Write the tests and stubs only. Run them and show me they fail.
3. **Green.** Implement until they pass. Keep changes small and touch only what the story needs.

## Planning rules
- We have one planning round. Get the plan right the first time.
- The plan must fit about 20 minutes of building. If it's bigger, propose what to cut.
- List assumptions as one line each so I can accept or reject them.
- Only ask about ambiguities that block the design. For each, give a recommended default.
- If a decision of mine creates a new edge case, name it rather than silently choosing.

## Design non-negotiables
- Money is an integer in the currency's minor units, always paired with a currency code. Never `double` or `float`. Never add amounts across currencies without an explicit conversion.
- If the design has a ledger: every posting is balanced (debits equal credits), and zero-amount entries are omitted. Don't introduce a ledger the spec doesn't call for.
- Validation is read-only and runs before any write. A rejected request writes nothing.
- The idempotency record is the first write. Everything else happens after it exists.
- Uniqueness is enforced in the data layer, atomically — not with a check followed by a separate write.
- Mark state *before* any irreversible external call, never after. A crash mid-call must leave a state that reads as "possibly done."
- A timeout or unexpected exception from an external call is UNKNOWN, not a failure. Never retry blindly.
- If anything fails after a partial write, unwind it and move to a terminal state. No state is ever stuck.
- Use compare-and-set for state transitions, never a plain overwrite.
- No lock is held across an external call.

## Testing rules
- A test is red for the right reason: the root-cause exception, not a compile error or a downstream assertion.
- Concurrency tests call `get()` on every future before any count assertions, so thread exceptions fail the test.
- Run race-condition tests with `@RepeatedTest(50)`.
- Compare records and value objects with `equals`, never `==` or `!=`.
- Test unwind paths by injecting a dependency that fails, not by bending inputs.
- Never special-case a test's trigger to make it pass. Fix the behavior the test stands for.
- Every assertion must be able to fail. If it can't, say so.

## Out of scope unless I ask
Authentication, persistence, HTTP layer, real external integrations, ML models.
