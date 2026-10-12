# Manual testing guide

How to run the app and check a change by hand. Unit tests cover logic; this covers the Compose UI.

## 1. Automated checks first

```
export ANDROID_HOME=~/Android/Sdk      # worktrees have no local.properties
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest    # needs a device or emulator; see section 2
```

Unit tests run on the JVM. Android-only behaviour (the ICU regex engine, Room on SQLite, resources) is
only proven by `connectedDebugAndroidTest`. CI runs both (`.github/workflows/ci.yml`).

Worktrees contain only tracked files. `Design/` and `local.properties` live in the main checkout
(`/home/stefan/projects/recipe-app-v2`); copy `local.properties` or set `ANDROID_HOME`, and read `Design/` from there.

## 2. Emulator

```
~/Android/Sdk/emulator/emulator -list-avds          # Medium_Phone_API_37.0
~/Android/Sdk/emulator/emulator -avd Medium_Phone_API_37.0 &
~/Android/Sdk/platform-tools/adb devices            # wait for "device"
./gradlew installDebug
~/Android/Sdk/platform-tools/adb shell am start -n com.simmer.app/.MainActivity
```

Check the package/activity name in `app/build.gradle.kts` if the launch fails.

## 3. Reaching a screen

Screens are reached through the nav graph (`ui/nav/SimmerNavHost.kt`). If nothing links to the
screen yet (its entry point is a later ticket), use a **throwaway branch**: add a temporary button
that navigates to the route, never merge it. Built-in recipe ids: `chilli`, `curry`, `tagliatelle`,
`shakshuka`, `carrots` (`app/src/main/assets/recipes.json`). Use `curry` or `chilli` for variations.

## 4. Always check (every screen)

- Light and dark theme (moon/sun toggle on Recipes).
- English and German (Settings language, or device locale via `adb shell cmd locale set-app-locales com.simmer.app --locales de`).
- Rotate the device: state survives.
- Process death: `adb shell am kill com.simmer.app`, reopen from recents: state restored.
- Large font (Settings > Display > Font size) and TalkBack: nothing clipped, controls labelled.
- Touch targets feel >= 44dp; list rows >= 56dp.
- Compare against the mock in `Design/`.

## 5. Crashes

```
adb logcat -c                       # before reproducing
adb logcat -d -b crash | tail -60   # after: the stack trace and "Caused by"
```

## 6. Reset app data

```
adb shell pm clear com.simmer.app
```

Re-seeds built-in recipes on next launch. Room schema changes: test an upgrade by installing the
previous build first, then `installDebug` over it (no clear) to exercise the migration.

## 7. Per feature checklists

### Recipe flow, Phase 1 (#5)

Open `chilli`:
- Stepper clamps at 1 and 12; amounts rescale live; "1 serving" singular (en + de).
- Swap cards: 2 columns, selected has thick orange outline, ingredient list changes, "Your pick" tag appears.
- Add-in rows: checkbox shows "+ amount" scaled by servings; toggling on then off returns button state to what it was.
- Caption reads "n items · s servings".
- Add to list: toast "Added n items for s servings...", button becomes "On your list"; change servings or a choice and it reverts to "Add to list". Toast "View list" opens Shopping tab.
- Add again: replaces items (Shopping list UI is a later ticket; inspect Room via Android Studio App Inspection > Databases: `shopping_items`, `planned_recipes`).
- Progress bar: 3 segments, only current is active; "Preparation" goes to Phase 2 placeholder.
- Back button and Edit button (placeholder).
- Kill process mid-edit: servings and selection restored.
