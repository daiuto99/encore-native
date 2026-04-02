---
name: Feedback — Session Efficiency & Credit Conservation
description: Rules for minimizing token usage per session
type: feedback
---

Read only what you need. Never read full files speculatively.

**Why:** User hits credit limits fast. Session costs balloon from large file reads and large old_string replacements.

**How to apply:**
- Use `grep -n` to find exact line numbers before reading; then `Read` only those 20-30 lines.
- Combine related edits into one replacement instead of multiple small passes on the same file.
- Skip confirmation tables and plan summaries unless the user explicitly asks. One sentence is enough.
- Never retry gesture fixes without logcat first.
- Always confirm which screen the user is on before debugging UI issues.
- Propose arrow buttons before a 2nd gesture attempt.
- For Feature tasks >50 lines: state category + wait for GO. Do not elaborate.
