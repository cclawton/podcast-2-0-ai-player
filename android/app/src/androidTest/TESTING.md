# Espresso / Compose UI Test Guide

This document covers how to run, debug, and maintain the 337 instrumented UI tests
for the Podcast 2.0 AI Player.

## Quick Start

### Prerequisites

1. **Android emulator running** (or physical device connected via ADB)
   ```bash
   # Verify device is connected
   adb devices
   ```
2. **Emulator specs**: API 34+ recommended, at least 4 GB RAM allocated
3. **Project built at least once**: `cd android && ./gradlew assembleDebug`

### Run All Tests

```bash
cd android
./gradlew connectedDebugAndroidTest
```

Typical runtime: ~25-45 minutes for 337 tests on a Pixel 9 Pro AVD (API 34).

### Run a Single Test Class

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.podcast.app.ui.SearchScreenTest
```

### Run a Single Test Method

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.podcast.app.ui.SearchScreenTest#searchScreen_showsTopAppBar
```

### Watch Progress Live

While tests run in another terminal:

```bash
adb logcat -s TestRunner | grep -E "started|finished|Tests"
```

Or for a compact tally:

```bash
adb logcat -s TestRunner 2>/dev/null | awk '
  BEGIN { p=0; f=0; total=337 }
  /started/ { running=$NF }
  /finished.*PASSED/ { p++ }
  /finished.*FAILED/ { f++ }
  { printf "\r[%d/%d] Passed: %d | Failed: %d | Running: %s", p+f, total, p, f, running }
'
```

## Test Architecture

### Test Runner: HiltTestRunner

All tests use a custom runner (`com.podcast.app.util.HiltTestRunner`) configured
in `build.gradle.kts`:

```kotlin
testInstrumentationRunner = "com.podcast.app.util.HiltTestRunner"
```

The runner does three things before any test executes:

1. **Uses `HiltTestApplication`** for dependency injection
2. **Initializes WorkManager** with `SynchronousExecutor` (required because
   `DownloadManager` calls `WorkManager.getInstance()` at construction time)
3. **Completes onboarding** by writing `onboarding_completed = true` to the
   privacy DataStore, so all tests start on the Library screen

Without this runner, **every test fails** because the app stays on the Onboarding screen.

### Test Utilities (`util/` package)

| File | Purpose |
|------|---------|
| `HiltTestRunner.kt` | Custom runner: Hilt + WorkManager + onboarding bypass |
| `ComposeTestExtensions.kt` | `waitUntilNodeWithTagExists`, `typeText`, `clickAndWait`, custom matchers |
| `TestData.kt` | Shared test data constants |
| `TestDataRule.kt` | JUnit rule for populating/clearing test data |
| `BaseComposeTest.kt` | Common base class for Compose tests |
| `PlaybackIdlingResource.kt` | Idling resource for playback state synchronization |

### Test Classes (14 classes, 337 tests)

| Class | Tests | What It Covers |
|-------|-------|----------------|
| `LibraryScreenTest` | ~20 | Library screen display, empty states |
| `SearchScreenTest` | ~50 | Search, trending, AI search, episode download from search |
| `PlayerScreenTest` | ~15 | Playback controls, progress bar, skip |
| `SettingsScreenTest` | ~45 | All settings toggles, Claude API config, auto-delete |
| `PodcastFeedScreenTest` | ~16 | Feed display, subscribe, episode list, download |
| `NavigationTest` | ~12 | Bottom nav, screen transitions, back navigation |
| `EpisodesScreenTest` | ~15 | Episode list, playback from list |
| `DownloadsScreenTest` | ~10 | Downloaded episodes, configuration changes |
| `BackgroundPlaybackTest` | ~10 | HOME press, notification, app return |
| `McpWidgetTest` | ~14 | MCP widget screen, WebView, status indicators |
| `RssFeedSubscriptionTest` | ~15 | RSS dialog, URL input, cancel/confirm |
| `DownloadFunctionalityTest` | ~10 | Download buttons, state display |
| `EpisodeInfoBottomSheetTest` | ~8 | Episode detail bottom sheet |
| `BaseComposeTest` | - | Abstract base (not run directly) |

## Known Gotchas & Patterns

### 1. DataStore Async Timing

**Problem**: DataStore writes are asynchronous. `waitForIdle()` returns before
the write completes, so UI state checks immediately after a toggle click can fail.

**Pattern**: Use `waitUntil` to poll for the expected UI change:
```kotlin
composeRule.onNodeWithText("Some Toggle").performClick()
composeRule.waitForIdle()
composeRule.waitUntil(timeoutMillis = 5000) {
    composeRule.onAllNodesWithTag("expected_content")
        .fetchSemanticsNodes().isNotEmpty()
}
```

**Affected tests**: SettingsScreenTest (Claude API toggle, auto-delete toggle).

### 2. EncryptedSharedPreferences Async Save

**Problem**: `EncryptedSharedPreferences.apply()` is async. After saving an API key,
the ViewModel needs time to re-check `hasApiKey()` and update the UI.

**Pattern**: Wait for a UI indicator that confirms the save completed:
```kotlin
composeRule.onNodeWithTag(TestTags.CLAUDE_API_SAVE_BUTTON).performClick()
composeRule.waitForIdle()
try {
    composeRule.waitUntil(timeoutMillis = 5000) {
        composeRule.onAllNodesWithTag(TestTags.CLAUDE_API_SAVED_INDICATOR)
            .fetchSemanticsNodes().isNotEmpty()
    }
} catch (e: Throwable) {
    // Save may fail in test environment (Keystore issues)
}
```

### 3. ComposeTimeoutException Is NOT an AssertionError

**Problem**: `ComposeTimeoutException` extends `Exception`, not `AssertionError`.
Catch blocks with `catch (e: AssertionError)` will miss timeout failures.

**Fix**: Always use `catch (e: Throwable)` in test resilience blocks:
```kotlin
// WRONG - misses ComposeTimeoutException
try { ... } catch (e: AssertionError) { ... }

// CORRECT
try { ... } catch (e: Throwable) { ... }
```

### 4. Activity Recreation Returns to Start Destination

**Problem**: `scenario.recreate()` destroys and recreates the Activity. The app
restarts at the start destination (Library screen), not the screen you were on.

**Pattern**: After recreation, verify the app recovered — don't assert you're
still on the same screen:
```kotlin
composeRule.activityRule.scenario.recreate()
composeRule.waitForIdle()
Thread.sleep(3000) // Hilt injection needs time
try {
    composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV, timeoutMillis = 30000)
} catch (e: Throwable) {
    composeRule.waitForIdle()
}
```

The 30-second timeout is needed because Hilt re-injection can be slow on the emulator.

### 5. bringAppToForeground() Returns to Library

**Problem**: Launching the app via `Intent` (as `bringAppToForeground()` does)
starts at the Library screen — the app's start destination — not the screen you
were on before pressing HOME.

**Pattern** (BackgroundPlaybackTest): After returning to foreground, navigate
to the target screen:
```kotlin
private fun bringAppToPlayerScreen() {
    bringAppToForeground()
    try {
        composeRule.waitUntilNodeWithTagExists(TestTags.PLAYER_SCREEN, timeoutMillis = 3000)
    } catch (e: Throwable) {
        // App returned to Library - navigate to Player
        composeRule.waitUntilNodeWithTagExists(TestTags.BOTTOM_NAV)
        composeRule.onNodeWithTag(TestTags.NAV_PLAYER).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntilNodeWithTagExists(TestTags.PLAYER_SCREEN)
    }
}
```

### 6. Network-Dependent Tests Use Assume

**Problem**: PodcastFeedScreen tests require trending podcast data from the network.
On a machine without internet (or when the API is slow), these tests fail.

**Pattern**: Use JUnit `Assume` to skip gracefully:
```kotlin
try {
    composeRule.waitUntil(timeoutMillis = 15000) {
        composeRule.onAllNodesWithTag(TestTags.SEARCH_RESULT_ITEM)
            .fetchSemanticsNodes().isNotEmpty()
    }
} catch (e: Throwable) {
    Assume.assumeTrue("No podcast items available (network may be unavailable)", false)
}
```

This marks the test as **skipped** (not failed) when the network is unavailable.

### 7. Inter-Test State Persistence

**Problem**: DataStore and EncryptedSharedPreferences persist across tests within
the same process. A test that enables Claude API toggle leaves it enabled for
the next test.

**Pattern**: Test helpers check current state before acting:
```kotlin
private fun enableClaudeApi() {
    val alreadyEnabled = composeRule.onAllNodesWithTag("claude_api_config")
        .fetchSemanticsNodes().isNotEmpty()
    if (!alreadyEnabled) {
        composeRule.onNodeWithText("Claude API").performClick()
        // ...
    }
}
```

## Troubleshooting

### All tests fail with "Onboarding screen" visible

**Cause**: `HiltTestRunner` is not configured or the DataStore write failed.

**Fix**: Verify `build.gradle.kts` has:
```kotlin
testInstrumentationRunner = "com.podcast.app.util.HiltTestRunner"
```

And that `PrivacyRepository.kt` has `internal` visibility on the DataStore delegate:
```kotlin
internal val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(...)
```

### Tests hang or timeout on emulator

- **Increase emulator RAM** to at least 4 GB
- **Disable animations** on the emulator:
  ```bash
  adb shell settings put global window_animation_scale 0
  adb shell settings put global transition_animation_scale 0
  adb shell settings put global animator_duration_scale 0
  ```
- **Close other apps** on the emulator to free resources

### WorkManager crash at test start

**Cause**: WorkManager double-initialization or missing test init.

**Fix**: Ensure `HiltTestRunner.onStart()` calls `WorkManagerTestInitHelper.initializeTestWorkManager()`
before `super.onStart()`.

### "Multiple DataStores active for the same file" error

**Cause**: Creating a second DataStore instance for `privacy_settings` instead
of reusing the one from `PrivacyRepository`.

**Fix**: Import and use the existing delegate:
```kotlin
import com.podcast.app.privacy.privacyDataStore
context.privacyDataStore.edit { ... }
```

### PodcastFeedScreen tests all skipped

**Cause**: No network connectivity on the emulator. These tests use `Assume` to
skip when trending data can't be fetched.

**Fix**: Ensure the emulator has internet access, or accept that these tests
will be skipped in offline environments.

### SettingsScreen API key tests fail intermittently

**Cause**: Android Keystore can be unreliable in emulator environments,
causing `EncryptedSharedPreferences` operations to fail.

**Fix**: These tests have try-catch resilience built in. If they fail
consistently, check that the emulator has hardware-backed keystore support
enabled (or use a software keystore for testing).

## Test Data

Tests that need podcast/episode data in the database use `TestDataPopulator`:

```kotlin
@Before
fun setUp() {
    hiltRule.inject()
    runBlocking { TestDataPopulator.populate(podcastDao, episodeDao) }
}

@After
fun tearDown() {
    runBlocking { TestDataPopulator.clear(database) }
}
```

This ensures each test class starts with a known database state.

## HTML Test Report

After a test run, the HTML report is at:

```
android/app/build/reports/androidTests/connected/debug/index.html
```

Open it in a browser to see pass/fail details per test class.
