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
- Key constraints: must be readable in both dim stage lighting (dark mode) and bright daylight rehearsal rooms (light mode); no small touch targets; no accidental taps during performance

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
- Settings: two-panel layout (220dp left nav + scrollable right content)

*Web App (desktop browser — Firebase hosted):*
- Single-page app, sidebar navigation
- Block-based song editor (left panel = blocks, right panel = live preview)
- Performance view: full-screen chart, same aesthetic as tablet
- Library management dashboard with health scanner and sort/filter controls

**Content that must appear:**

*Android App screens:*
- Library list: song title, artist, key badge, BPM badge, set number badge (if in a set), sync status badge (synced/syncing/conflict)
- Song detail / Performance view: YAML front matter header (title, artist, key, tempo), chord chart body with inline chord notation `[G]word`, section headers (## Verse, ## Chorus, etc.)
- Set Builder: set name, ordered song list, drag handles, add/remove controls
- Key Change HUD: current key display, up/down half-step buttons, original key indicator, sharp/flat toggle
- Confirm dialogs: "Remove from Set" vs "Delete from Library" (distinct actions, distinct visual weight)
- Swipe-to-action affordance on song list items (swipe left = delete/remove, swipe right = edit)
- Sun/Moon toggle: switches entire UI between dark and light mode simultaneously

*Settings screen — all 4 categories are required, positions may be redesigned for better usability but no functionality may be removed:*

**Theme (category 1)**
- Dark Mode / Light Mode tabs — each tab is independently configurable
- Built-in presets (chip strip, scrollable): Dark: Zen Studio · Analog Luxe · Midnight Mainstage · Neon Night-Shift; Light: Zen Studio · Analog Luxe · Studio Daylight · Bourbon & Vinyl · Solar Flare
- User presets: save current theme as named preset, delete saved presets
- Background color picker (swatch + hex input + HSV wheel dialog)
- Lead Guitar Icon color picker
- Capo Badge color picker
- Per-section style matrix — one row per section type (intro, verse, pre-chorus, chorus, bridge, solo, outro, interlude, instrumental); each row has: color swatch (tap to open picker), quick-select swatches (10 colors), hex input field, bold toggle switch, font size slider (10–24sp)

**Typography & Rhythm (category 2)**
- Lyric size slider (10–24sp)
- Chord spacing slider (0–24dp)
- Font family selector: Sans-Serif vs Monospace

**Performance HUD (category 3)**
- Lead Guitar Indicator toggle — show guitar icon badge when song is marked lead guitar
- Transposition Warning toggle — badge when song is in a different key than original
- Show Chords toggle — render chord lines in chart viewer
- Show Key Info toggle — display key and BPM in song header
- Title Color Override — hex input (blank = use set color)
- Artist Color Override — hex input (blank = use set color)

**Library Tools (category 4)**
- Cloud Sync: Sync Now button, last synced timestamp, in-progress counter (N/total), success state
- Clear All Sets: destructive action with confirmation dialog (songs not affected, sets cleared)
- Library Health scanner: Run Scan button, issue count summary, issue list (song title + artist + validation error list per song, tap row to open editor)

*Web App screens:*
- Block editor with live preview pane
- Setlist Architect: drag-and-drop setlist builder, export button
- Library Health scanner: flagged songs list with issue type labels
- ChordSidekick AI: input form (title, artist, key), streaming output display, save button
- Chart Normalizer: upload/paste input, before/after comparison, save button
- Performance View: full-screen chart, key transposition controls

---

**Design system — Encore color profiles:**

Encore has two complete color profiles. The Sun/Moon toggle switches both simultaneously. All components use `LocalEncoreColors.current` — no hardcoded hex values in components.

```
── Dark Profile ──────────────────────────────────────────────────
screenBackground:    #060E1F   (deep navy black)
cardBackground:      #0D1829   (slightly lighter card surface)
cardElevation:       0dp       (flat on dark — invisible shadow)
titleText:           #FFFFFF
artistText:          #FFFFFF @ 60% opacity
separatorText:       #FFFFFF @ 25% opacity
iconTint:            #FFFFFF @ 70% opacity
subtleText:          #FFFFFF @ 45% opacity
searchBarBackground: #2C2C2E
lyricText:           #FFFFFF
divider:             #FFFFFF @ 12% opacity

── Light Profile ─────────────────────────────────────────────────
screenBackground:    #F2F2F7   (Apple system gray)
cardBackground:      #FFFFFF   (white cards)
cardElevation:       2dp       (visible shadow on light)
titleText:           #000000
artistText:          #000000 @ 60% opacity
separatorText:       #000000 @ 25% opacity
iconTint:            #000000 @ 70% opacity
subtleText:          #000000 @ 45% opacity
searchBarBackground: #FFFFFF
lyricText:           #000000
divider:             #000000 @ 12% opacity

── Semantic accent colors (both profiles) ────────────────────────
Primary/Blue:        #3b82f6   (glow: #3b82f640)  — library, navigation, primary actions
Accent/Amber:        #f59e0b   (glow: #f59e0b20)  — performance mode, key change, live/active
Purple:              #8b5cf6   (glow: #8b5cf620)  — AI features, set builder
Green:               #10b981                       — sync, cloud, connected state
Red:                 #ef4444                       — destructive actions (delete from library)
```

Default built-in theme tokens (Zen Studio — loaded on first launch):
```
── Zen Studio Dark ───────────────────────────────────────────────
bgColor:       #0F1115    lyricColor:   #E5E7EB
chordColor:    #7DD3FC    harmonyColor: #5B4A1A
leadIconColor: #7DD3FC    capoColor:    #F59E0B
Sections: intro #94A3B8 · verse #60A5FA · pre-chorus #818CF8
          chorus #22D3EE · bridge #34D399 · solo #F59E0B
          outro #A3E635 · interlude #C084FC · instrumental #F472B6

── Zen Studio Light ──────────────────────────────────────────────
bgColor:       #F6F7F4    lyricColor:   #1E293B
chordColor:    #1D4ED8    harmonyColor: #FFF3BF
leadIconColor: #2563EB    capoColor:    #B45309
Sections: intro #475569 · verse #2563EB · pre-chorus #4F46E5
          chorus #0369A1 · bridge #0F766E · solo #B45309
          outro #4D7C0F · interlude #7C3AED · instrumental #BE123C
```

Typography:
- Headlines: system-ui / -apple-system, weight 800, letter-spacing -0.03em to -0.04em
- Body: same font stack, weight 400–500, line-height 1.6–1.7
- Chord notation: monospace (user-selectable), readable at arm's length
- Section labels: 11px, weight 700, uppercase, letter-spacing 0.12em

Border radius: 14px (cards), 8px (small elements), 20px (chips/presets)

**References:**
- Existing presentation: `encore-presentation.html` in this folder — visual language reference
- Live web app: `https://encore-cloud-leo-2026-8a467.web.app` (soon: `encore.sonicink.space`)

**Out of scope / what NOT to include:**
- Onboarding flow / empty state walkthroughs (not needed yet)
- Mobile phone layout — tablet and desktop only
- Authentication/login screens
- Any generic SaaS patterns (pricing tables, marketing footers, plan comparison)

**Definition of done:**
Ready for handoff when the following screens are designed in all required states, for **both dark and light mode**:

| Screen | States needed |
|--------|--------------|
| Library List (Android) | default list, search active, set filter active, empty state |
| Performance Mode (Android) | chart loaded, key transposed, set navigation visible |
| Set Builder (Android) | populated set, drag in progress, empty set |
| Settings — Theme (Android) | dark tab, light tab, preset selected |
| Settings — Typography (Android) | default state |
| Settings — Performance HUD (Android) | default state |
| Settings — Library Tools (Android) | idle, scanning, issues found |
| Song Editor (Web) | editing state, preview state |
| ChordSidekick AI (Web) | idle, generating (streaming), result ready |
| Chart Normalizer (Web) | idle, processing, before/after result |
| Library Health (Web) | issues found, all clear |

---

## Part 2 — Pre-flight Checklist

- [ ] Design system seeded in Claude Design: both dark and light profiles above, font system-ui/sans-serif, weight 800 headlines
- [ ] Component names aligned to codebase: `SongListItem`, `SongList`, `LibraryScreen`, `PerformanceView`, `SetBuilderSheet`, `KeyChangeHUD`, `ChordSidekickPanel`, `SettingsScreen`, `ThemePanel`, `TypographyPanel`, `PerformanceHudPanel`, `LibraryHealthPanel`
- [ ] Reference files uploaded to Claude Design project: `encore-presentation.html`, screenshots of current app if available
- [ ] Stakeholder / reviewer identified (Leo)

---

## Part 3 — Per-project Checklist

### Before you start
- [ ] Brief above is reviewed and confirmed
- [ ] Android feature/library subdirectory linked (not the full monorepo): `android/feature/library/src/main/kotlin/com/encore/feature/library/`
- [ ] Settings subdirectory linked: `android/app/src/main/kotlin/com/encore/tablet/settings/`
- [ ] Web app subdirectory linked: `Encore-Firebase/Encore-Firebase/src/`
- [ ] Presentation HTML uploaded as visual reference

### During design
- [ ] Use **chat** for broad changes ("make it feel more stage-ready," "increase density of the library list")
- [ ] Use **inline comments** for element-specific tweaks ("this key badge should use amber, not blue")
- [ ] Refer to components by real codebase names (`SongListItem`, `SwipeToDismissBox`, `PerformanceView`, `SettingsScreen`)
- [ ] Design both dark and light states for every screen — they are equally first-class
- [ ] Verify chord chart is equally readable in both modes — dark for dim stage, light for bright rehearsal room
- [ ] Keep performance mode ruthlessly minimal — anything that distracts a performer is a bug
- [ ] Amber = on stage / active. Reserve for performance context only.
- [ ] Chord chart body must use monospace font — chords must align visually with syllables
- [ ] Settings screen: all 4 categories must be present; layout/placement may be redesigned but zero functionality may be removed

### Before handoff
- [ ] All required states listed in the table above are designed in both dark AND light mode
- [ ] Performance Mode reviewed at simulated arm's-length distance (zoom out to 75%) in both modes
- [ ] Touch targets reviewed: minimum 48dp on all interactive elements (Android HIG)
- [ ] Accessibility: sufficient contrast on chord notation in both modes
- [ ] Destructive actions (Delete from Library) clearly distinguished from safe actions (Remove from Set) — red vs outlined-neutral
- [ ] Color picker dialog (HSV wheel + hex input) designed as a reusable component — used in 3+ places in settings
- [ ] Leo has reviewed and signed off

### Handoff to Claude Code
- [ ] Export → Handoff to Claude Code bundle generated
- [ ] Bundle shared with Leo + link to this brief
- [ ] Acceptance criteria noted: chord chart rendering must be pixel-accurate, swipe gesture affordances must match current behavior, key badge colors must use exact hex tokens above, both theme profiles must match `DarkEncoreColors` / `LightEncoreColors` token values
- [ ] Non-obvious constraints noted: `activeSetFilter == null` controls library vs set builder context — affects which action buttons appear in swipe confirm dialog; `LocalEncoreColors.current` is the single source of truth for all surface/text colors

---

## Part 4 — Anti-patterns to Avoid for Encore Specifically

- **Do not design only dark mode.** Both profiles are equally used and equally important — dark for stage, light for rehearsal and bright environments.
- **Do not use color arbitrarily.** Blue/amber/purple each have semantic meaning — see color semantics above.
- **Do not shrink the chord chart font.** Legibility at arm's length is the #1 performance requirement.
- **Do not add decorative chrome to Performance Mode.** If it's not helping the musician follow the chart, it doesn't belong on that screen.
- **Do not conflate "Remove from Set" and "Delete from Library."** These are different destructive actions at different severity levels — they must look different and be clearly labeled.
- **Do not design generic AI chat UI for ChordSidekick.** It's a domain tool, not a chatbot — the input is always title + artist + key, the output is always a chord chart.
- **Do not remove or consolidate settings categories.** All 4 categories (Theme, Typography & Rhythm, Performance HUD, Library Tools) are required with all their controls. Layout may improve but nothing can be dropped.
- **Do not hardcode surface or text colors.** Every component must be designed to respect both `DarkEncoreColors` and `LightEncoreColors` — if a design decision only works on one profile, it's wrong.

---

## Part 5 — Key Technical Context for Design Decisions

- **Two-profile theme system:** `EncoreColors` data class holds all surface/text tokens. `DarkEncoreColors` and `LightEncoreColors` are the two profiles. Provided via `LocalEncoreColors` composition local at the root. The Sun/Moon toggle in the top bar switches profiles. Dark is the default for previews and tests.
- **Theme presets:** `ThemePreset` configures the *content rendering* colors (bg, lyric, chord, harmony, per-section styles) independently of the structural `EncoreColors` tokens. A preset only affects the chart viewer, not the app chrome.
- **Chord chart format:** Inline notation `[G]Amazing [C]grace` — chords appear inline with lyrics. The rendered view shows chords above the corresponding word. Monospace alignment is critical.
- **Section headers:** `## Verse 1`, `## Chorus` — rendered as styled section dividers with per-section color + bold + font size from the active `ThemePreset`.
- **YAML front matter:** Appears as a formatted header block at the top of every chart (title, artist, key, tempo, time signature).
- **Key transposition:** Real-time — the displayed chart re-renders immediately when key is changed. The transposition HUD stays accessible during performance without obscuring the chart.
- **Set navigation:** When in a set, a persistent HUD shows current song position (e.g., "3 of 8"), prev/next song controls.
- **Swipe gestures on song list:** Swipe right = edit sheet, swipe left = confirm dialog (remove from set OR delete from library depending on context). The confirm dialog content changes based on `activeSetFilter` state.
- **GCS sync indicator:** Shows sync status (synced / syncing / conflict) per song — a small badge on the song list item.
- **Color picker dialog:** Full-screen HSV wheel (hue ring + saturation/value square), before/after preview swatch, and hex input field — bidirectionally synced. Used for background color, section colors, lead icon color, capo badge color.
- **Section font size range:** 10sp–24sp per section, independently configurable.
- **Chord spacing:** 0dp–24dp gap between chord row and lyric row, user-adjustable.
