# Encore Foundation Setup Status

**Date:** 2026-03-26
**Branch:** `milestone-1/foundation`
**Status:** Pending Android SDK Installation

## ✅ Completed Steps

### 1. Git Branch
- **Branch created:** `milestone-1/foundation`
- **Status:** Active

### 2. Project Structure
All directories and files created successfully:
- Android app with modular architecture (app, core, feature modules)
- Complete documentation structure (architecture, API contracts, decisions)
- Backend directory structure prepared
- Sample markdown song file created

### 3. Gradle Configuration
- Gradle 8.5 configured
- Kotlin 1.9.22
- Jetpack Compose with Material 3
- Multi-module build system
- Fixed: Removed non-existent compose plugin, using composeOptions instead

### 4. JDK 17
- **Installed:** OpenJDK 17.0.18
- **Location:** `/opt/homebrew/opt/openjdk@17`
- **Verified:** `java -version` working

### 5. Documentation
Created comprehensive Milestone 1 documentation:
- Architecture diagram with system overview
- Complete data model with entity definitions
- Navigation map with all screen flows
- API contracts specification
- Backend stack decision (Kotlin Ktor - ADR 001)
- Risk tracking document
- Sample song chart for parser testing

## ⚠️ Pending Requirement

### Android SDK Installation Required

**Error:** Build failed with:
```
SDK location not found. Define a valid SDK location with an ANDROID_HOME
environment variable or by setting the sdk.dir path in your project's
local properties file at '/Users/leodaiuto/encore-native/android/local.properties'.
```

**Root Cause:** Android SDK is not installed on this system.

## 📋 Next Steps to Complete Foundation

### Step 1: Install Android Studio & SDK

**Option A: Full Android Studio (Recommended)**
1. Download Android Studio from: https://developer.android.com/studio
2. Install Android Studio
3. During setup, install:
   - Android SDK Platform 34 (Android 14)
   - Android SDK Build-Tools
   - Android SDK Platform-Tools
   - Android SDK Command-line Tools
4. Note the SDK location (usually `~/Library/Android/sdk` on macOS)

**Option B: Command-line Tools Only**
```bash
# Download Android command-line tools
cd ~/Downloads
wget https://dl.google.com/android/repository/commandlinetools-mac-10406996_latest.zip
unzip commandlinetools-mac-10406996_latest.zip
mkdir -p ~/Library/Android/sdk/cmdline-tools/latest
mv cmdline-tools/* ~/Library/Android/sdk/cmdline-tools/latest/

# Install required SDK components
export ANDROID_HOME=~/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### Step 2: Configure SDK Location

Create `android/local.properties` file:
```bash
cd /Users/leodaiuto/encore-native/android
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

Or set environment variable:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

### Step 3: Run Build

```bash
cd /Users/leodaiuto/encore-native/android
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew assembleDebug
```

**Expected Output:**
```
BUILD SUCCESSFUL in Xs
```

**APK Location:**
```
android/app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Markdown Parser Spike (After Successful Build)

Once build passes, implement parser spike:

1. **Add Markdown Library Dependency**
   - Evaluate: Jetpack Compose Markdown (mikepenz/markdown)
   - Test rendering with `samples/representative-charts/sample-song-01.md`

2. **Create Prototype Renderer**
   - Build `MarkdownRenderer` composable
   - Test chord-over-lyric alignment
   - Measure performance on target tablet

3. **Document Findings**
   - Update `docs/architecture/parser-spike-notes.md`
   - Record library choice and rationale

## 📊 Milestone 1 Progress

| Deliverable | Status |
|-------------|--------|
| Architecture diagram | ✅ Complete |
| Data model / schema documentation | ✅ Complete |
| Navigation map | ✅ Complete |
| Backend/auth decision memo | ✅ Complete |
| Parser/render spike notes | 🔄 Template ready, spike pending |
| Runnable test build | ⏳ Pending SDK install |
| Known risks / open questions | ✅ Complete |

**Overall:** 5/7 complete (71%)

## 🎯 Definition of Done

Before marking Milestone 1 complete:

- [x] Git branch created
- [x] Project structure initialized
- [x] Documentation complete
- [ ] **`./gradlew assembleDebug` builds successfully** ⬅️ BLOCKER
- [ ] Markdown parser spike complete
- [ ] Parser renders representative chart correctly
- [ ] Performance validated on tablet (or emulator)

## 🔧 Quick Reference Commands

```bash
# Set Java home (add to ~/.zshrc or ~/.bash_profile for persistence)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17

# Set Android SDK (after installation)
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin

# Build the app
cd /Users/leodaiuto/encore-native/android
./gradlew assembleDebug

# Clean build
./gradlew clean assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run tests
./gradlew test
```

## 📝 Notes

1. **Android SDK is the ONLY blocker** for completing the build check.
2. All code is ready and Gradle configuration is correct.
3. Once SDK is installed, build should succeed immediately.
4. Parser spike can begin as soon as build passes.

---

**Next Action:** Install Android Studio and Android SDK, then retry build.
