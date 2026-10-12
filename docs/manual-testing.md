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
- Progress bar: 3 segments, only current is active; "Preparation" opens Phase 2 (see below); the Cooking segment does nothing (Phase 3 is reached only via Start cooking).
- Back button and Edit button (placeholder).
- Kill process mid-edit: servings and selection restored.

### Recipe flow, Phase 2 (#6)

Open `chilli`, tap Preparation:
- Title "How will you cook it?", caption "...for s servings." (en + de, singular at 1).
- Every option of every Method is shown, whatever Appliances Settings holds: chilli offers Pot, Oven and Slow cooker; rice offers Pot, Microwave and Rice cooker.
- Radio cards: icon tile, label, description, time; selected has thick orange outline; TalkBack reads radio + selected.
- Settings units imperial: oven card reads "Covered at 300°F."
- Cream panel "x preparation steps, then y cooking steps." changes when picking another method.
- Tapping the Servings segment or Back returns to Phase 1 with state kept; changing servings or a swap in Phase 1 shows the new counts here.
- Start cooking opens Phase 3 (see below). Rotate and kill the process on this screen: picks restored.
- Changing servings or any pick (Phase 1 or 2) clears Cooking Progress; steps marked done in Phase 3 start over.


### Recipe flow, Phase 3 (#7)

Open `chilli`, Preparation, Start cooking (Phase 3 has no entry button yet; use the throwaway branch, section 3):
- Progress line "0 of n steps done" (de: "0 von n Schritten erledigt"), selection summary on the right ("4 servings", plus any non-default swap or add-in), 4dp bar fills as steps finish.
- Hint "Tap an underlined ingredient for its amount and tips." with lightbulb.
- Sections: Preparation ("Get everything ready before you turn on the heat.") then Cooking ("Steps marked with a warning need a preparation step first."). Labels "Prep 1..n" / "Step 1..n" (de: "Vorb." / "Schritt").
- First unfinished step has a thick orange outline, orange eyebrow and a dark Mark as done button; others have the outlined secondary button.
- "Soften the onions" shows a cream note with orange outline, alert icon, "Needs Prep 1 first", "Chop the vegetables" and "Go to step", which smooth-scrolls to Prep 1. After marking Prep 1 done the note becomes the quiet "Prep 1 done · Chop the vegetables" line.
- Underlined ingredient words (2dp, offset; each is a 44dp button for TalkBack and touch, even short words like "rice"): tap shows a popover with name, scaled amount in current units (check imperial in Settings), note and tips; tap outside closes it. Change servings in Phase 1 and the amounts follow.
- Mark as done collapses to a row with orange check, struck-through title and Undo; Undo restores the card.
- Timer button: orange, timer icon, duration ("10 min", "1 h"); tapping does nothing (no countdown yet).
- Finish all steps (TalkBack announces "Enjoy your meal."): gradient panel with circle-check, "Enjoy your meal." (de: "Guten Appetit."), "title · selection summary", Back to recipes (dark) goes to the Recipes tab.
- Rotate and `adb shell am kill com.simmer.app` with some steps done: servings, selection and done steps are restored. Changing servings or a swap in Phase 1/2 clears done steps.

### Recipes overview (#9)

The Recipes tab is the start screen. "Added by you" and "Edited" tags need a User Recipe or edited built-in, which no UI creates yet (see the throwaway branch in the PR).
- Title "What are we cooking?" (de: "Was kochen wir heute?"). Top right: + (opens the Add recipe placeholder) and moon/sun (toggles theme, icon flips).
- Search: type "feta" shows only Shakshuka (ingredient match); "chilli" matches by title; list filters per keystroke, no submit.
- Category tabs: All, Mains, Pasta, Breakfast, Sides (de: Alle, Hauptgerichte, Pasta, Frühstück, Beilagen); the row scrolls sideways when it does not fit. Active tab has a 3dp orange underline.
- Tag chips Vegetarian, Quick, Spicy, One-pot, Make-ahead (de: Vegetarisch, Schnell, Scharf, Eintopf, Vorkochen): multi-select, selected chip is dark with a check. Quick + Vegetarian shows only Tagliatelle; adding category Mains shows the empty state.
- Count below filters: "5 recipes", "1 recipe" (singular), updates as you filter; TalkBack announces changes. "Clear filters" link appears only while a filter is active and resets search, category and tags.
- Empty state: dashed box, "No recipes match these filters." and a Clear filters button.
- Card: gradient icon tile (flame, chef hat, utensils, egg, carrot), title, category badge, "35 min · Easy" (de: "35 Min. · Einfach"), accent "3 variations" only when the recipe has variations, tags joined with " · ". Tapping opens Flow Phase 1 for that recipe.
- Planned section: absent on a fresh install. Open `chilli`, switch Mince to Plant-based, toggle Dark beer, tap Add to list, go back: a cream "Planned" card reads "4 servings · Plant-based · Dark beer". Continue reopens Phase 1 with those servings and picks, button shows "On your list". Planned cards ignore search and filters.
- Filters survive rotation and `adb shell am kill com.simmer.app`.
- Bottom tab bar does not cover the last card.

### Settings (#11)

Settings tab. "Your kitchen" has no editor yet (#12), so pills need the throwaway branch (section 3) or `adb` to seed DataStore; the empty state is what a fresh install shows.
- Title "Settings." (de: "Einstellungen."). Sections in order: Language, Units, Spices and dried herbs, Your kitchen (+ "How closely to follow it"), Recipe reader, Preview.
- Option cards are radios (TalkBack: radio, selected); selected has a 3dp orange outline and a check dot, no layout shift.
- Language: tap Deutsch: whole UI switches at once (tabs, Recipes title, flow strings) with no manual restart; tap English to go back. Kill the process and reopen: language kept. Caption mentions that built-in recipes stay in English.
- Units: Imperial (US): Preview reads 1 lb, 1¼ cups, oven 300°F. Open `chilli` Phase 1 and 3: amounts in oz/lb/cups; Phase 2 oven card "Covered at 300°F."; ingredient popovers and step text temperatures in °F. Back to Metric restores.
- Spices: Grams: Preview cumin 5 g, oregano 1 g; the same in the flow ingredient list and popovers. In German, fractional gram amounts use a comma (cinnamon add-in on `chilli`: "1,5 g" for 4 servings is not reached, check 2 servings "0,5 g" / `curry` as relevant).
- Your kitchen: "No appliances selected yet." when empty; Edit your kitchen opens the Your kitchen screen (placeholder until #12), back returns to Settings.
- Kitchen follow-it mode: Strict / Medium (default) / Free for all, radio list; choice survives process death. It only stores a preference; cooking an existing recipe is unaffected.
- Recipe reader: static row "On this phone · Gemma 4 E2B", "Not downloaded · 2.6 GB", lock caption; no buttons.
- Preview: five rows update immediately when Units or Spices change.
- Light and dark theme, large font (cards grow, text wraps), rotate: scroll position and selections intact.
- `connectedDebugAndroidTest` includes `SettingsPrefsTest` (DataStore round trip).
