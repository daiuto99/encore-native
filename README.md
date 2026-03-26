# Encore Native Android

Native Android tablet application for Encore - A live performance tool for musicians.

## Project Overview

**Version:** 1.0.0-milestone1
**Platform:** Native Android (Kotlin + Jetpack Compose)
**Target Device:** 11-inch Android tablet, Portrait orientation
**Architecture:** Offline-first with manual sync

## Documentation

- [Product Overview](docs/01_Product_Overview.md)
- [Functional Specification](docs/02_Functional_Feature_Specification.md)
- [Technical Specification](docs/03_Technical_Specification.md)
- [Build Roadmap](docs/05_Build_Roadmap.md)
- [Architecture Diagram](docs/architecture/architecture-diagram.md)
- [Data Model](docs/architecture/data-model.md)
- [Navigation Map](docs/architecture/navigation-map.md)
- [API Contracts](docs/api/contracts.md)

## Current Milestone

**Milestone 1: Foundation / Architecture** - ✅ COMPLETE (2026-03-26)

See [MILESTONE_1_COMPLETE.md](MILESTONE_1_COMPLETE.md) for full completion report.

**Key Deliverables:**
- [x] Architecture diagram
- [x] Data model / schema documentation
- [x] Navigation map
- [x] Backend stack decision (Kotlin Ktor)
- [x] Parser/render spike (mikepenz/multiplatform-markdown-renderer)
- [x] Runnable test build with markdown rendering proof
- [x] Known risks / open questions list

**Next Milestone:** Milestone 2 - Core Library + Setlist Management

See [Delivery Checklist](docs/06_Delivery_Checklist.md) for complete milestone requirements.

## Prerequisites

### Development Machine Setup

1. **Java Development Kit (JDK) 17+**
   ```bash
   # Install via Homebrew (recommended)
   brew install openjdk@17

   # Link it for system-wide use
   sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk

   # Verify installation
   java -version
   ```

2. **Android Studio** (Latest stable version)
   - Download from: https://developer.android.com/studio
   - Install Android SDK API 34
   - Install Android Build Tools
   - Configure Android SDK location

3. **Git** (for version control)
   ```bash
   brew install git
   ```

4. **Kotlin** (bundled with Android Studio, but can install standalone)
   ```bash
   brew install kotlin
   ```

### Project Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd encore-native
   ```

2. **Checkout the current milestone branch**
   ```bash
   git checkout milestone-1/foundation
   ```

3. **Build the Android app**
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

4. **Run on device or emulator**
   ```bash
   ./gradlew installDebug
   ```

   Or open the `android` directory in Android Studio and run from the IDE.

## Project Structure

```
encore-native/
├── android/                    # Android application
│   ├── app/                    # Main app module
│   ├── core/                   # Core shared modules
│   │   ├── ui/                 # Compose UI components
│   │   ├── data/               # Room DB, repositories
│   │   └── sync/               # Sync logic
│   └── feature/                # Feature modules
│       ├── auth/               # Authentication
│       ├── library/            # Song library
│       ├── setlists/           # Setlist management
│       ├── performance/        # Performance mode
│       └── edit/               # Song editing
├── backend/                    # Backend API (Kotlin Ktor)
├── docs/                       # Documentation
│   ├── architecture/           # Architecture docs
│   ├── api/                    # API contracts
│   └── decisions/              # Architecture decision records
├── contracts/                  # Shared API contracts
├── samples/                    # Sample markdown songs
└── Claude.md                   # AI assistant instructions
```

## Technology Stack

### Android Client
- **Language:** Kotlin 1.9+
- **UI:** Jetpack Compose (Material 3)
- **Database:** Room (SQLite)
- **Architecture:** MVVM with Repository pattern
- **Navigation:** Jetpack Navigation Compose
- **Async:** Kotlin Coroutines + Flow

### Backend API
- **Language:** Kotlin 1.9+
- **Framework:** Ktor 2.3+
- **Database:** PostgreSQL 15+
- **ORM:** Exposed
- **Auth:** Google OAuth via Google Sign-In SDK

See [Backend Stack Decision](docs/decisions/001-backend-stack-choice.md) for rationale.

## Building the App

### Debug Build
```bash
cd android
./gradlew assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

### Release Build
```bash
cd android
./gradlew assembleRelease
```

### Run Tests
```bash
cd android
./gradlew test
```

### Clean Build
```bash
cd android
./gradlew clean assembleDebug
```

## Development Workflow

1. **Create Feature Branch**
   ```bash
   git checkout -b milestone-X/feature-name
   ```

2. **Make Changes**
   - Follow Kotlin coding conventions
   - Add unit tests for business logic
   - Update documentation if needed

3. **Build and Test**
   ```bash
   ./gradlew assembleDebug test
   ```

4. **Commit Changes**
   ```bash
   git add .
   git commit -m "feat: Add feature description"
   ```

5. **Create Pull Request**
   - Ensure all tests pass
   - Reference related issues or milestones
   - Request code review

## Definition of Done

Before marking any task or milestone as "Complete":

- **Builds:** App compiles successfully (`./gradlew assembleDebug`)
- **Checklist:** All items for the current milestone in the Delivery Checklist are complete
- **Tests:** Feature passes relevant acceptance test criteria
- **Git:** Work is on a feature branch with clean, descriptive commits

## Current Status

**Branch:** `milestone-1/foundation`
**Milestone:** 1 - Foundation
**Status:** In Progress

### Milestone 1: ✅ COMPLETE

- [x] Git branch created (`milestone-1/foundation`)
- [x] Android project initialized with Gradle and Jetpack Compose
- [x] Documentation structure created
- [x] Architecture diagram documented
- [x] Data model defined
- [x] Navigation map created
- [x] API contracts documented
- [x] Backend stack decision (Kotlin Ktor - ADR 001)
- [x] JDK 17 installed
- [x] Android SDK installed and configured
- [x] Build successful (`./gradlew assembleDebug`)
- [x] Markdown parser spike complete (mikepenz library validated)
- [x] Sample song renders with chord alignment
- [x] All deliverables documented

See [MILESTONE_1_COMPLETE.md](MILESTONE_1_COMPLETE.md) for complete report.

### Next Steps: Milestone 2

**Milestone 2: Core Library + Setlist Management**

Key tasks:
- [ ] Implement Room database with full schema
- [ ] Create Song repository and DAO
- [ ] Build import flow UI (file picker, duplicate detection)
- [ ] Build library screen with search
- [ ] Create setlist data structures
- [ ] Build setlist overview and set editor screens
- [ ] Implement markdown edit mode
- [ ] Write unit tests for repositories

## Contributing

See [CLAUDE.md](Claude.md) for AI assistant guidelines and project rules.

## License

TBD

## Contact

TBD
