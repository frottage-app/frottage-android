# https://github.com/casey/just

# List available recipes in the order in which they appear in this file
_default:
  @just --list --unsorted

test:
  ./gradlew testDebug

test-watch:
  rg --files build.gradle.kts app | entr -crn ./gradlew testDebug

dev:
  rg --files build.gradle.kts app | entr -crn just install

fix:
  @echo "Groovy! Fixing and Formatting your awesome Frottage Kotlin code (Compose rules included)..."
  @if [ ! -f ktlint-compose.jar ]; then \
    echo "ktlint-compose.jar not found, downloading it for Compose-specific fixing!"; \
    wget https://github.com/mrmans0n/compose-rules/releases/download/v0.4.16/ktlint-compose-0.4.16-all.jar -O ktlint-compose.jar; \
  fi
  ktlint -R ktlint-compose.jar --format "app/src/**/*.kt" "app/build.gradle.kts" "build.gradle.kts" "settings.gradle.kts"

lint:
  @echo "Groovy! Linting your awesome Frottage code..."
  @if [ ! -f ktlint-compose.jar ]; then \
    echo "ktlint-compose.jar not found, downloading it for some frottage action!"; \
    wget https://github.com/mrmans0n/compose-rules/releases/download/v0.4.16/ktlint-compose-0.4.16-all.jar -O ktlint-compose.jar; \
  fi
  ktlint -R ktlint-compose.jar "app/src/**/*.kt" "app/build.gradle.kts" "build.gradle.kts" "settings.gradle.kts"

check: lint
  @echo "Groovy! Building your awesome Frottage debug APK after linting..."
  ./gradlew assembleDebug

# run ci checks locally
ci:
  (git ls-files && git ls-files --others --exclude-standard) | entr -cnr just check

# Build APK for specified variant (debug/release)
apk variant="debug":
  ./gradlew assemble{{variant}}

devices:
  adb devices

# Install APK for specified variant (debug/release)
install variant="debug": (apk variant)
  adb devices
  adb install app/build/outputs/apk/{{variant}}/frottage-{{variant}}.apk || (adb uninstall com.frottage && adb install app/build/outputs/apk/{{variant}}/frottage-{{variant}}.apk)
  adb shell monkey -p com.frottage -c android.intent.category.LAUNCHER 1

# Run app for specified variant (debug/release)
run variant="debug": (install variant)
  scripts/logcat com.frottage

deploy:
  mkdir -p keys
  echo "$KEYSTORE_BASE64" | base64 --decode > keys/keystore.jks
  echo "$PLAY_SERVICE_ACCOUNT_JSON" > keys/play-service-account.json
  fastlane playstore
  rm -rf keys
