# Encore Android — Working Rules

## Core Constraints
- Platform: Native Android only (Kotlin + Jetpack Compose)
- Device: 11-inch tablet, portrait only
- Architecture: Offline-first; Room is the source of truth during performance
- Song model: Markdown-based master song model

## Workflow Rules
- Plan before editing
- **Source of Truth:** Always refer to `M4_ACTIVE_CONTEXT.md` for current progress and task priority.
- If a technical detail is unspecified, use modern Android best practices and document the choice in `docs/decisions.md`
- Never implement silent overwrite for sync conflicts
- Keep work scoped to the current task; do not review roadmap, checklist, or acceptance docs unless the task explicitly requires planning, review, or release validation
- Before starting any task, explicitly categorize it as a Fix (Surgical) or Feature (Architectural). If it is a Feature requiring >50 lines of code or full file reads, stop and wait for my 'GO' to ensure I want to spend the tokens now

## Session Guidance
- Use fresh sessions for distinct work modes: build, plan, review, and handoff
- Prefer targeted reads of specific sections or files over broad project re-reads
- For milestone execution, rely on the active milestone context file (`M4_ACTIVE_CONTEXT.md`) instead of reloading project-wide governance docs

## Efficiency Rules (Credit Saving)
- **Surgical Fixes:** Skip "Plan Mode" and "Subagents" for bug fixes or minor UI tweaks.
- **Direct Access:** Use Grep/Read on known file paths directly; avoid launching Explore agents.
- **Build Filtering:** Always pipe build output through grep to minimize context: 
  `./gradlew assembleDebug | grep -E "FAILED|error:|BUILD SUCCESSFUL"`

  ## Environment
- **ADB Path:** Always use `~/Library/Android/sdk/platform-tools/adb` for all adb commands.
- **Verification:** Run `~/Library/Android/sdk/platform-tools/adb devices` at the start of every session.