# Claude Design → Claude Code: Brief & Workflow Checklist

A one-page template for getting consistent, production-ready output from Claude Design and a clean handoff to Claude Code.

---

## Part 1 — Project Brief

Fill this out *before* opening Claude Design. The quality of the output tracks the quality of this brief.

**Project name:**
*e.g., "Q3 onboarding flow redesign"*

**Goal — what are we building, and why?**
*One or two sentences. What changes for the user/business when this ships?*

**Audience — who uses this?**
*Role, context, technical comfort level, key constraints (mobile-first, accessibility, regulated industry, etc.)*

**Primary user actions / jobs to be done:**
*The 2–4 things a user must be able to accomplish. Order by priority.*
1.
2.
3.

**Layout and structure preferences:**
*e.g., "single-column mobile flow," "dashboard with metrics in top row," "marketing page with hero + 3 feature blocks + pricing"*

**Content that must appear:**
*Specific copy, data fields, CTAs, legal disclosures, brand elements*

**References:**
*Links to existing pages, competitors, screenshots, inspiration. Upload to the project.*

**Out of scope / what NOT to include:**
*Equally important. Prevents drift in iteration.*

**Definition of done:**
*What does "ready for handoff" look like? Which screens, which states (empty / loading / error / success), which breakpoints?*

---

## Part 2 — Pre-flight Checklist (one-time setup per organization)

- [ ] Organization design system set up in Claude Design (colors, typography, components imported from codebase or design files)
- [ ] Enterprise admin has enabled Claude Design (off by default on Enterprise plans)
- [ ] Naming conventions agreed on for components (so prototype names match codebase names)
- [ ] Designated owner for the design system who maintains and refines it over time

---

## Part 3 — Per-project Checklist

### Before you start
- [ ] Brief above is filled out
- [ ] Codebase or relevant subdirectory linked to the project (link subdirectories, not full monorepos)
- [ ] Reference materials uploaded (screenshots, existing decks, competitor examples)
- [ ] Stakeholders and reviewers identified

### During design
- [ ] Use **chat** for broad changes ("make it more minimal," "swap the layout")
- [ ] Use **inline comments** for targeted, element-specific changes
- [ ] Use **sliders** for fine-tuning spacing, color, and type
- [ ] Document key decisions and *the reasoning* in chat (this travels to handoff)
- [ ] Refer to components by their real names ("ProductCard," "PrimaryButton")
- [ ] If an inline comment vanishes before Claude responds, paste the text into chat as a backup
- [ ] Stay in visual exploration mode — do not try to write production code here

### Before handoff
- [ ] All required states designed: empty, loading, error, success, edge cases
- [ ] Accessibility review requested from Claude ("audit this for WCAG 2.1 AA issues")
- [ ] Stakeholder review complete; feedback incorporated
- [ ] Design decisions and tradeoffs are visible in the chat history
- [ ] Project includes any motion/interactivity specs needed

### Handoff to Claude Code
- [ ] Export → **Handoff to Claude Code** to generate the bundle
- [ ] Share the bundle URL with engineering, plus a link to this brief
- [ ] Include acceptance criteria: which behaviors must match exactly, where there's flexibility
- [ ] Specify any non-obvious constraints (analytics events, feature flags, A/B test wiring)
- [ ] Engineer runs Claude Code against the linked codebase using the bundle

---

## Part 4 — Anti-patterns to Avoid

- **Skipping the design system step.** Default output is generic. Seed the brand once, save hours forever.
- **Vague briefs.** "A nice landing page" produces a nice generic landing page. Be specific about goal, audience, and content.
- **Mixing design and code.** Claude Design is for visual exploration; Claude Code is for production. Don't try to do both in one conversation.
- **Linking entire monorepos.** Causes lag. Link the relevant subdirectory.
- **One-shot expectations.** First generation is a starting point. The value is in iteration.
- **Undocumented decisions.** If the reasoning behind a design choice isn't in the chat, it won't make it to engineering.

---

## Part 5 — Plan & Access Notes

- Available on **Pro, Max, Team, and Enterprise** plans (research preview)
- **Off by default on Enterprise** — admin must enable
- Usage is **metered separately** from chat and Claude Code — heavy design weeks may hit limits
- Exports: Claude Code handoff bundle, Canva, PPTX, PDF, HTML, internal share URL, local folder
- No direct Figma export currently
