# Milestone 1 Risks and Open Questions

**Milestone:** 1 - Foundation
**Status:** Active Tracking
**Last Updated:** 2026-03-26

## Known Risks

### High Priority

#### 1. Markdown Parser Choice
**Risk:** Choosing the wrong parser could require significant rework later.

**Impact:**
- Chord alignment may not work correctly
- Performance issues on large songs
- Memory usage concerns

**Mitigation:**
- Complete parser spike early in Milestone 1
- Test with representative song files (100+ lines)
- Measure render performance on target tablet hardware
- Have fallback plan (custom parser)

**Status:** Mitigation in progress (spike planned)

---

#### 2. Google Sign-In Integration Complexity
**Risk:** OAuth flow on Android may have unexpected complications.

**Impact:**
- Delayed authentication implementation
- Security vulnerabilities if done incorrectly
- Single-device policy enforcement challenges

**Mitigation:**
- Use official Google Sign-In SDK
- Follow Android best practices documentation
- Test single-device session flow early
- Plan for device revocation edge cases

**Status:** Requires research in Milestone 1

---

#### 3. Room Database Performance at Scale
**Risk:** Local database queries may be slow with large catalogs (500+ songs).

**Impact:**
- Laggy search and library browsing
- Poor user experience
- May need query optimization or indexing strategy

**Mitigation:**
- Profile database queries early
- Add proper indexes (title, artist, sync_status)
- Test with large sample datasets
- Consider pagination for library views

**Status:** Will validate during Milestone 2 implementation

---

### Medium Priority

#### 4. Jetpack Compose Tablet Optimization
**Risk:** Compose layouts may not optimize well for 11-inch portrait tablets.

**Impact:**
- Wasted screen space
- Poor readability in performance mode
- Navigation issues

**Mitigation:**
- Design tablet-specific layouts (not phone-scaled)
- Test on physical tablet early and often
- Use Compose preview with tablet dimensions
- Follow Material Design tablet guidelines

**Status:** Active design consideration

---

#### 5. Backend Stack Maturity
**Risk:** Kotlin Ktor is less mature than Node/Express or Spring Boot.

**Impact:**
- Fewer code examples and libraries
- More manual implementation required
- Potential learning curve

**Mitigation:**
- Ktor has strong Kotlin community support
- Keep backend simple and focused
- Document decisions in decision log
- Have contingency plan for migration if needed

**Status:** Accepted risk (Kotlin stack consistency outweighs maturity concerns)

---

#### 6. Sync Conflict UI Complexity
**Risk:** Conflict resolution UI may be confusing for users.

**Impact:**
- Users may choose wrong version
- Data loss perception
- Support burden

**Mitigation:**
- Design clear side-by-side comparison UI
- Show diffs visually
- Provide "undo" or "revert" capability
- Beta test conflict flows extensively

**Status:** Design and UX research needed (Milestone 4)

---

### Low Priority

#### 7. App Icon and Branding
**Risk:** Placeholder icon may look unprofessional in beta.

**Impact:**
- Beta tester perception
- Branding consistency

**Mitigation:**
- Create simple, recognizable icon early
- Use Material Design icon guidelines
- Can refine in later milestones

**Status:** Deferred to Milestone 5

---

#### 8. Crash Reporting Setup
**Risk:** Debugging production issues without crash logs is difficult.

**Impact:**
- Slow bug resolution
- Poor beta testing feedback loop

**Mitigation:**
- Set up Firebase Crashlytics early (Milestone 1 or 2)
- Include basic logging and diagnostics
- Have beta testers report issues with device info

**Status:** Plan for Milestone 2

---

## Open Questions

### 1. Backend Hosting Strategy
**Question:** Where will the backend API be hosted?

**Options:**
- Google Cloud Platform (Cloud Run + Cloud SQL)
- AWS (ECS + RDS)
- Heroku (simple, managed PostgreSQL)
- Fly.io (Kotlin-friendly, affordable)

**Decision Needed By:** Milestone 4 (before sync implementation)

**Current Thinking:** Fly.io or Google Cloud Run for simplicity.

---

### 2. Markdown Metadata Format
**Question:** Should we use YAML front matter or custom syntax for song metadata?

**Current Format:**
```markdown
---
title: Song Title
artist: Artist Name
key: G
---
```

**Alternatives:**
- Custom format: `[Title: Song Title]`
- No metadata (parse from content)

**Decision Needed By:** Milestone 1 (parser spike)

**Current Thinking:** YAML front matter is standard and parseable.

---

### 3. Offline Storage Limits
**Question:** What's the maximum library size we should support?

**Scenarios:**
- 100 songs: ~5MB
- 500 songs: ~25MB
- 1000 songs: ~50MB

**Decision Needed By:** Milestone 2 (before import flow implementation)

**Current Thinking:** Target 500-1000 songs comfortably; test at 2000+ for stress testing.

---

### 4. Beta Distribution Method
**Question:** How will we distribute beta builds to testers?

**Options:**
- Google Play Internal Testing
- Firebase App Distribution
- Direct APK sharing

**Decision Needed By:** Milestone 5 (beta hardening)

**Current Thinking:** Google Play Internal Testing for simplicity and security.

---

### 5. Future iOS Sharing Strategy
**Question:** How much architecture should be shared with future iOS build?

**Options:**
- Shared backend API (already planned)
- Shared Kotlin Multiplatform code (data models, business logic)
- Completely separate iOS codebase

**Decision Needed By:** Post-V1 (out of scope for Android V1)

**Current Thinking:** Defer to post-V1. Focus on solid Android implementation first.

---

### 6. Performance Mode Gesture Sensitivity
**Question:** What swipe threshold should trigger navigation?

**Concern:** Too sensitive = accidental swipes; too insensitive = feels sluggish.

**Decision Needed By:** Milestone 3 (performance mode implementation)

**Current Thinking:** Test multiple thresholds on physical tablet; make configurable in settings if needed.

---

## Risk Review Cadence

- **Weekly:** Review high-priority risks during Milestone 1
- **Milestone End:** Update risk status before moving to next milestone
- **Ad Hoc:** Add new risks as they emerge

---

## Risk Escalation

If any risk becomes a blocker:
1. Document in this file with "BLOCKER" status
2. Notify project stakeholder immediately
3. Propose mitigation plan or scope adjustment
4. Re-evaluate milestone timeline if necessary

---

**Next Review Date:** End of Milestone 1 (post-foundation completion)
