# Claude Design Brief — Encore
*Professional Chord Chart System · Android Tablet + Web*

---

## Part 1 — Project Brief

**Project name:**
Encore — Professional Chord Chart System (Android tablet app + companion web manager)

**Goal — what are we building, and why?**
Encore is a two-platform system for professional musicians and worship leaders to manage, organize, and perform from chord charts on a large-screen Android tablet. The design package covers the full Android tablet UI and the companion Firebase web app. When this ships polished, musicians have a distraction-free, stage-ready environment that replaces printed charts and generic note apps.

**Audience — who uses this?**
- **Primary:** Working musicians, worship leaders, and music directors performing live — using a large-screen Android tablet (landscape orientation, arm's length or music stand distance)
- **Secondary:** The same user managing their library and building setlists at a desk via the companion web app
- Technical comfort: moderate to high. Minimal onboarding patience — they are often setting up minutes before a performance.
- Key constraints: must be readable in dim stage lighting, no small touch targets, no accidental taps during performance

**Primary user actions / jobs to be done:**
1. Open a song and read/follow the chord chart during a live performance — with no distractions
2. Browse and search the song library, find the right song fast
3. Build a setlist for an upcoming service or show and navigate through it in order during performance
4. Import, normalize, and manage chord charts (add new songs, clean up messy imports, change key)

**Layout and structure preferences:**

*Android Tablet (landscape, large screen — ~10–13" tablet):*
- Command Center: persistent left-side nav/library panel + main content area (split-pane landscape layout)
- Library panel: search bar at top, song list below, set filter tabs along the top edge
- Performance Mode: full-screen chart view — no chrome, no nav, just the chart and a minimal floating HUD
- Set Builder: drag-and-drop reorder list, set number badge per song

*Web App (desktop browser — Firebase hosted):*
- Single-page app, sidebar navigation
- Block-based song editor (left panel = blocks, right panel = live preview)
- Performance view: full-screen chart, same aesthetic as tablet
- Library management dashboard with health scanner and sort/filter controls

**Content that must appear:**

*Android App screens:*
- Library list: song title, artist, key badge, BPM badge, set number badge (if in a set)
- Song detail / Performance view: YAML front matter header (title, artist, key, tempo), chord chart body with inline chord notation `[G]word`, section headers (## Verse, ## Chorus, etc.)
- Set Builder: set name, ordered song list, drag handles, add/remove controls
- Key Change HUD: current key display, up/down half-step buttons, original key indicator, sharp/flat toggle
- Confirm dialogs: "Remove from Set" vs "Delete from Library" (distinct actions, distinct visual weight)
- Swipe-to-action affordance on song list items (swipe left = delete/remove, swipe right = edit)

*Web App screens:*
- Block editor with live preview pane
- Setlist Architect: drag-and-drop setlist builder, export button
- Library Health scanner: flagged songs list with issue type labels
- ChordSidekick AI: input form (title, artist, key), streaming output display, save button
- Chart Normalizer: upload/paste input, before/after comparison, save button
- Performance View: full-screen chart, key transposition controls

**Design system — Encore brand tokens:**

```
Background:     #07070f   (near-black, deep space)
Surface:        #0f1020
Surface-2:      #171830
Border:         #1e2040
Border-2:       #262850
Text:           #e8eaf2   (off-white)
Text Muted:     #64748b
Text Dim:       #2d3055
Primary/Blue:   #3b82f6   (glow: #3b82f640)
Accent/Amber:   #f59e0b   (glow: #f59e0b20)
Purple:         #8b5cf6   (glow: #8b5cf620)
Green:          #10b981
Red:            #ef4444
Border radius:  14px (cards), 8px (small elements)
```

Color semantics:
- **Blue** — library, navigation, primary actions
- **Amber** — performance mode, key change, live/active state
- **Purple** — AI features (ChordSidekick, Chart Normalizer), set builder
- **Green** — sync, cloud, connected state
- **Red** — destructive actions (delete from library)

Typography:
- Headlines: system-ui / -apple-system, weight 800, letter-spacing -0.03em to -0.04em
- Body: same font stack, weight 400–500, line-height 1.6–1.7
- Code / chord notation: monospace, readable at distance
- Section labels: 11px, weight 700, uppercase, letter-spacing 0.12em

**References:**
- Existing presentation: `encore-presentation.html` in this folder — the full visual language is defined there
- KAI presentation: `kai-presentation.html` — sibling project, same dark aesthetic
- Live web app: `https://encore-cloud-leo-2026-8a467.web.app` (soon: `encore.sonicink.space`)

**Out of scope / what NOT to include:**
- Light mode — Encore is always dark, always stage-ready
- Onboarding flow / empty state walkthroughs (not needed yet)
- Mobile phone layout — tablet and desktop only
- Settings/preferences screens
- Authentication/login screens
- Any generic SaaS patterns (pricing tables, marketing footers, plan comparison)

**Definition of done:**
Ready for handoff when the following screens are designed in all required states:

| Screen | States needed |
|--------|--------------|
| Library List (Android) | default list, search active, set filter active, empty state |
| Performance Mode (Android) | chart loaded, key transposed, set navigation visible |
| Set Builder (Android) | populated set, drag in progress, empty set |
| Song Editor (Web) | editing state, preview state |
| ChordSidekick AI (Web) | idle, generating (streaming), result ready |
| Chart Normalizer (Web) | idle, processing, before/after result |
| Library Health (Web) | issues found, all clear |

---

## Part 2 — Pre-flight Checklist

- [ ] Design system seeded in Claude Design: paste the color tokens above, set font to system-ui/sans-serif, weight 800 headlines
- [ ] Component names aligned to codebase: `SongListItem`, `SongList`, `LibraryScreen`, `PerformanceView`, `SetBuilderSheet`, `KeyChangeHUD`, `ChordSidekickPanel`
- [ ] Reference files uploaded to Claude Design project: `encore-presentation.html`, screenshots of current app if available
- [ ] Stakeholder / reviewer identified (Leo)

---

## Part 3 — Per-project Checklist

### Before you start
- [ ] Brief above is reviewed and confirmed
- [ ] Android feature/library subdirectory linked (not the full monorepo): `android/feature/library/src/main/kotlin/com/encore/feature/library/`
- [ ] Web app subdirectory linked: `Encore-Firebase/Encore-Firebase/src/`
- [ ] Presentation HTML uploaded as visual reference

### During design
- [ ] Use **chat** for broad changes ("make it feel more stage-ready," "increase density of the library list")
- [ ] Use **inline comments** for element-specific tweaks ("this key badge should use amber, not blue")
- [ ] Refer to components by real codebase names (`SongListItem`, `SwipeToDismissBox`, `PerformanceView`)
- [ ] Keep performance mode ruthlessly minimal — anything that distracts a performer is a bug
- [ ] Amber = on stage / active. Do not use amber for library/management UI — reserve it for performance context
- [ ] Chord chart body must use monospace font — chords must align visually with syllables

### Before handoff
- [ ] All required states listed in the table above are designed
- [ ] Performance Mode reviewed at simulated arm's-length distance (zoom out to 75% and check readability)
- [ ] Touch targets reviewed: minimum 48dp on all interactive elements (Android HIG)
- [ ] Accessibility: sufficient contrast on chord notation against dark background
- [ ] Destructive actions (Delete from Library) clearly distinguished from safe actions (Remove from Set) — red vs outlined-neutral
- [ ] Leo has reviewed and signed off

### Handoff to Claude Code
- [ ] Export → Handoff to Claude Code bundle generated
- [ ] Bundle shared with Leo + link to this brief
- [ ] Acceptance criteria noted: chord chart rendering must be pixel-accurate, swipe gesture affordances must match current behavior, key badge colors must use exact hex tokens above
- [ ] Non-obvious constraints noted: `activeSetFilter == null` controls library vs set builder context — affects which action buttons appear in swipe confirm dialog

---

## Part 4 — Anti-patterns to Avoid for Encore Specifically

- **Do not add light mode.** Every design decision assumes a dark stage environment.
- **Do not use color arbitrarily.** Blue/amber/purple each have semantic meaning — see color semantics above.
- **Do not shrink the chord chart font.** Legibility at arm's length is the #1 performance requirement.
- **Do not add decorative chrome to Performance Mode.** If it's not helping the musician follow the chart, it doesn't belong on that screen.
- **Do not conflate "Remove from Set" and "Delete from Library."** These are different destructive actions at different severity levels — they must look different and be clearly labeled.
- **Do not design generic AI chat UI for ChordSidekick.** It's a domain tool, not a chatbot — the input is always title + artist + key, the output is always a chord chart.

---

## Part 5 — Key Technical Context for Design Decisions

- **Chord chart format:** Inline notation `[G]Amazing [C]grace` — chords appear inline with lyrics. The rendered view shows chords above the corresponding word. Monospace alignment is critical.
- **Section headers:** `## Verse 1`, `## Chorus` — rendered as styled section dividers in the chart view
- **YAML front matter:** Appears as a formatted header block at the top of every chart (title, artist, key, tempo, time signature)
- **Key transposition:** Real-time — the displayed chart re-renders immediately when key is changed. The transposition HUD stays accessible during performance without obscuring the chart.
- **Set navigation:** When in a set, a persistent HUD shows current song position (e.g., "3 of 8"), prev/next song controls
- **Swipe gestures on song list:** Swipe right = edit sheet, swipe left = confirm dialog (remove from set OR delete from library depending on context). The confirm dialog content changes based on `activeSetFilter` state.
- **GCS sync indicator:** Shows sync status (synced / syncing / conflict) per song — a small badge on the song list item
