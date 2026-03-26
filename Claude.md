# ENCORE NATIVE ANDROID COMMAND CENTER

## 1. Primary Source of Truth
- **Product Vision:** @docs/01_Encore_Product_Overview.md
- **Technical Rules:** @docs/03_Encore_Technical_Specification.md (Follow Section 12 for Repo Structure)
- **Roadmap:** @docs/05_Build_Roadmap.md (Do not skip milestones)

## 2. Definition of Done (DoD)
Before any task or milestone is marked "Complete," the following must be true:
- **Builds:** The app must compile successfully: `./gradlew assembleDebug`.
- **Checklist:** All items for the current milestone in @docs/06_Delivery_Checklist.md must be produced.
- **Test:** The feature must pass the relevant P1 criteria in @docs/07_Acceptance_Test_Plan.md.
- **Git:** Work must be on a feature branch with clean, descriptive commits.

## 3. Technical Constraints
- **Platform:** Native Android (Kotlin + Jetpack Compose).
- **Device:** 11-inch Tablet, Portrait Orientation ONLY.
- **Architecture:** Offline-first. Local Room DB is the master source during performance.
- **Song Model:** Markdown-based. Master version applies globally.

## 4. Operational Protocol
- **Plan First:** Always enter "Plan Mode" to outline technical steps before editing files.
- **No Hallucinations:** If a technical detail isn't in @docs/03_Encore_Technical_Specification.md, use Android "Modern Best Practices" and document the choice in `docs/decisions.md`.
- **Sync Safety:** Never implement "Silent Overwrite." Refer to the Conflict Rules in the Functional Spec.