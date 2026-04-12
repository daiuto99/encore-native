# Encore Android — Working Rules

## Core Constraints
- Platform: Native Android only (Kotlin + Jetpack Compose)
- Device: 11-inch tablet, portrait only
- Architecture: Offline-first; Room is the source of truth during performance
- Song model: Markdown-based master song model

---

## Session Start Protocol

When the user says **"get up to speed"**, **"sync up"**, or starts a session without context, do the following in order — no other work until this is done:

1. Read `MEMORY.md` at `/Users/leodaiuto/.claude/projects/-Users-leodaiuto-sonicink-encore-native/memory/MEMORY.md`
2. Read `docs/v1.1-plan.md` — check current track statuses, identify what was last in progress
3. Read `M4_ACTIVE_CONTEXT.md` — confirm DB version, known facts, and any in-flight work
4. Report back in one short paragraph: current track, last completed task, next task up, and any blockers

Do not load `docs/code-review-findings.md` on startup unless the session is explicitly a code review session. It is large and rarely needed mid-build.

---

## Active Plan
- **V1.1 Plan:** `docs/v1.1-plan.md` — 6 tracks: Foundation → Transposition → BPM → UI → Web → Security
- **Code Review Log:** `docs/code-review-findings.md` — all findings across 3 sessions; 18 fixed, 9 open, 2 release gates

## Source of Truth Hierarchy
1. `docs/v1.1-plan.md` — what track we are on and what is next
2. `M4_ACTIVE_CONTEXT.md` — implementation details, DB schema, known facts
3. `docs/code-review-findings.md` — open issues that must be resolved

---

## Plan Status Rule

**After every build that completes or meaningfully advances a task, update `docs/v1.1-plan.md`:**
- Change the task's Status cell to `IN PROGRESS` or `COMPLETE`
- Add a one-line note in the Notes column (what was done, or what is left)
- If a new finding surfaces during implementation, add it to `docs/code-review-findings.md`

Do not batch status updates. Update the plan immediately after each completed build so the next session starts from accurate state.

---

## Workflow Rules
- Plan before editing
- Before starting any task, explicitly categorize it as a Fix (Surgical) or Feature (Architectural). If it is a Feature requiring >50 lines of code or full file reads, stop and wait for my 'GO' to ensure I want to spend the tokens now
- Never implement silent overwrite for sync conflicts
- Keep work scoped to the current task; do not review roadmap, checklist, or acceptance docs unless the task explicitly requires planning, review, or release validation
- If a technical detail is unspecified, use modern Android best practices and document the choice in `docs/decisions.md`

## Session Guidance
- Use fresh sessions for distinct work modes: build, plan, review, and handoff
- Prefer targeted reads of specific sections or files over broad project re-reads
- For milestone execution, rely on `docs/v1.1-plan.md` for what to do next; rely on `M4_ACTIVE_CONTEXT.md` for how to do it

## Efficiency Rules (Credit Saving)
- **Surgical Fixes:** Skip "Plan Mode" and "Subagents" for bug fixes or minor UI tweaks.
- **Direct Access:** Use Grep/Read on known file paths directly; avoid launching Explore agents.
- **Build Filtering:** Always pipe build output through grep to minimize context:
  `./gradlew assembleDebug 2>&1 | grep -E "FAILED|error:|BUILD SUCCESSFUL"`

## Environment
- **ADB Path:** Always use `~/Library/Android/sdk/platform-tools/adb` for all adb commands.
- **Verification:** Run `~/Library/Android/sdk/platform-tools/adb devices` at the start of every session.
