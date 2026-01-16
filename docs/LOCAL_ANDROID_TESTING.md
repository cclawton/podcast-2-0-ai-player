# Local Android Testing Setup (macOS M2)

This guide sets up Android command-line tools for running Espresso tests locally.

## 1. Install Android Command-Line Tools

```bash
# Install via Homebrew (recommended)
brew install --cask android-commandlinetools

# Or download manually from:
# https://developer.android.com/studio#command-tools
```

## 2. Set Environment Variables

Add to your `~/.zshrc` or `~/.bash_profile`:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export PATH="$ANDROID_HOME/emulator:$PATH"
```

Then reload:
```bash
source ~/.zshrc
```

## 3. Accept Licenses & Install Components

```bash
# Create SDK directory
mkdir -p ~/Library/Android/sdk

# Accept all licenses
yes | sdkmanager --licenses

# Install required components
sdkmanager "platform-tools" \
           "platforms;android-34" \
           "build-tools;34.0.0" \
           "emulator" \
           "system-images;android-34;google_apis;arm64-v8a"
```

## 4. Create an ARM64 Emulator (optimized for M2)

```bash
# Create AVD (Android Virtual Device)
avdmanager create avd \
    --name "test_device" \
    --package "system-images;android-34;google_apis;arm64-v8a" \
    --device "pixel_6" \
    --force

# Verify it was created
avdmanager list avd
```

## 5. Start the Emulator

```bash
# Start emulator (in background)
emulator -avd test_device -no-snapshot-save -no-audio &

# Wait for it to boot (check with)
adb wait-for-device
adb shell getprop sys.boot_completed  # Returns "1" when ready
```

## 6. Run the Espresso Tests

From the project directory:

```bash
cd /path/to/podcast-2-0-ai-player/android

# Run all instrumented tests
./gradlew connectedDebugAndroidTest

# Run a specific test class
./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.podcast.app.ui.LibraryScreenTest

# Run tests with verbose output
./gradlew connectedDebugAndroidTest --info
```

## 7. View Test Results

Test results are saved to:
- HTML Report: `android/app/build/reports/androidTests/connected/index.html`
- XML Results: `android/app/build/outputs/androidTest-results/connected/`

Open the HTML report:
```bash
open app/build/reports/androidTests/connected/index.html
```

## Quick Reference Commands

```bash
# List connected devices/emulators
adb devices

# Stop emulator
adb emu kill

# List available AVDs
avdmanager list avd

# Delete an AVD
avdmanager delete avd --name test_device

# Clear app data between test runs
adb shell pm clear com.podcast.app.debug

# View device logs during tests
adb logcat | grep -E "(podcast|TestRunner)"
```

## Troubleshooting

### Emulator won't start
```bash
# Check if hardware acceleration is available (should show "yes")
emulator -accel-check

# Try with software rendering if needed
emulator -avd test_device -gpu swiftshader_indirect
```

### Tests timeout
```bash
# Increase Gradle timeout
./gradlew connectedDebugAndroidTest -Pandroid.testOptions.execution=ANDROIDX_TEST_ORCHESTRATOR
```

### "No connected devices" error
```bash
# Ensure emulator is running and detected
adb devices
# Should show: emulator-5554   device

# If not, restart adb
adb kill-server && adb start-server
```

## Estimated Setup Time
- Download & install: ~10-15 minutes
- First emulator boot: ~2-3 minutes
- Subsequent boots: ~30 seconds (with snapshots)

## Storage Requirements
- Command-line tools: ~150MB
- Platform tools: ~50MB
- Android 34 platform: ~200MB
- System image (ARM64): ~1.5GB
- Emulator: ~300MB
- **Total: ~2.2GB**
