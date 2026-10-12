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
- Add again: replaces the recipe's items on the Shopping tab (see Shopping list below), no duplicates.
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


- Skipping Phase 2 (needs the throwaway branch's "No-method test" recipe, or any recipe whose Methods have at most one option each): in Phase 1 the bottom button reads "Start cooking" (de: "Kochen starten") and opens Phase 3 directly; Prep steps show as "Prep n"; Back from Phase 3 (and the Preparation segment) returns to Phase 1. `chilli` still shows "Preparation" and Phase 2. Swap and add-in variations on Phase 1 do not change this. Resume case: from the throwaway recipe's Phase 1, an entry to Phase 2 (restored back stack) pops back to Phase 1 instead of showing an empty "How will you cook it?".

### Recipe flow, Phase 3 (#7)

Open `chilli`, Preparation, Start cooking (Phase 3 has no entry button yet; use the throwaway branch, section 3):
- Progress line "0 of n steps done" (de: "0 von n Schritten erledigt"), selection summary on the right ("4 servings", plus any non-default swap or add-in), 4dp bar fills as steps finish.
- Hint "Tap an underlined ingredient for its amount and tips." with lightbulb.
- Sections: Preparation ("Get everything ready before you turn on the heat.") then Cooking ("Steps marked with a warning need a preparation step first."). Labels "Prep 1..n" / "Step 1..n" (de: "Vorb." / "Schritt").
- First unfinished step has a thick orange outline, orange eyebrow and a dark Mark as done button; others have the outlined secondary button.
- "Soften the onions" shows a cream note with orange outline, alert icon, "Needs Prep 1 first", "Chop the vegetables" and "Go to step", which smooth-scrolls to Prep 1. After marking Prep 1 done the note becomes the quiet "Prep 1 done · Chop the vegetables" line.
- Underlined ingredient words (2dp, offset; each is a 44dp button for TalkBack and touch, even short words like "rice"): tap shows a popover with name, scaled amount in current units (check imperial in Settings), note and tips; tap outside closes it. Change servings in Phase 1 and the amounts follow.
- Mark as done collapses to a row with orange check, struck-through title and Undo; Undo restores the card.
- Timer button: orange, timer icon, duration ("10 min", "1 h"). Behaviour is under StepTimer (#8) below.
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
- Spices: Grams: Preview cumin 5 g, oregano 1 g; the same in the flow ingredient list and popovers. In German, fractional gram amounts use a comma: `chilli`, add the Cinnamon add-in at 4 servings, add-in row reads "+ 1,5 g" (en: "+ 1.5 g").
- Your kitchen: "No appliances selected yet." when empty; Edit your kitchen opens the Your kitchen screen (placeholder until #12), back returns to Settings.
- Kitchen follow-it mode: Strict / Medium (default) / Free for all, radio list; choice survives process death. It only stores a preference; cooking an existing recipe is unaffected.
- Recipe reader: static row "On this phone · Gemma 4 E2B", "Not downloaded · 2.6 GB", lock caption; no buttons.
- Preview: five rows update immediately when Units or Spices change.
- Light and dark theme, large font (cards grow, text wraps), rotate: scroll position and selections intact.
- `connectedDebugAndroidTest` includes `SettingsPrefsTest` (DataStore round trip).

### Shopping list (#10)

Shopping list tab. Needs no throwaway branch: items come from Phase 1 "Add to list" and the text field.
- Fresh install (`adb shell pm clear com.simmer.app`): title "Shopping list." (de: "Einkaufsliste."), "Add an item" field, empty-state text, no badge on the tab icon.
- Open `chilli`, Add to list, tap View list in the toast: items grouped under aisle headings (Produce, Spices, ...), each row with ingredient name, amount on the right, "Chilli con carne" as the note under the name. Tab badge shows the item count.
- Type "Batteries", tap Add (or the keyboard Done): it appears under "Other items" (de: "Weitere Artikel"), apart from the aisle groups; field clears. Add is disabled while the field is blank or spaces.
- Row look (also the add-in rows on Flow Phase 1): checkbox on the left, a 24dp rounded square with a 2dp grey outline; ticked it fills orange with a black check. 16dp gap between box and text. Name 16sp, note below in small grey, amount right-aligned in grey. Ticked rows show a struck-through grey name. Rows are at least 56dp.
- Card look: each aisle, "Other items" and "In your basket" is its own rounded card with a 2dp light outline, thin dividers between rows, and a small uppercase label above. Compare with `Design/shots/`; no progress bar or "Clear checked" by design.
- Aisle order is fixed (Produce, Meat, Chilled, ... Spices, Drinks) whichever recipe you add first: add `curry` then `chilli` and compare with the reverse after `pm clear`.
- Tick an item: it leaves its group, badge drops by 1. "In your basket" (de: "Im Einkaufskorb") row appears collapsed with "1 item"; tap it to expand (chevron turns), tap the item to return it to its aisle. Tick every item: badge disappears (not "0").
- Re-add: open `chilli`, change servings to 2, Add to list again: aisle amounts change, no duplicate rows; manual items stay; items you had ticked and that remain stay ticked. Add `curry` too: both recipes' items show, notes differ.
- `adb shell am kill com.simmer.app`, reopen: list, ticks, badge kept. Typed-but-unsent text and the open basket survive rotation.
- German: switch in Settings, all strings above translate (built-in recipe ingredient names and aisles stay English).
- TalkBack: rows read as checkboxes with state, basket row says "Show items in your basket", badge reads "n items to buy".
- `connectedDebugAndroidTest` includes `ShoppingPersistenceTest` (Room reopen, re-add replaces).

### StepTimer (#8)

Open `chilli`, Preparation, Start cooking (no throwaway branch needed). Prep 2 "Mix the spices" is 2 min, Prep 3 "Drain the beans" is 1 min. On Android 13+ the first tap asks for notification permission: allow it (and test deny once after `pm clear`: timers still run, finishing only vibrates).
- Idle: orange fill, timer icon, "2 min". Tap: orange fill, pause icon, "1:59" counting down, thin dark progress bar inside the bottom of the button growing left to right.
- Tap while running: outline button (orange border and text), play icon, frozen time, bar in orange. A 44dp reset (counter-clockwise arrow) button appears to the left; the timer button does not move or change width. Tap play: resumes from the frozen time. Reset: back to "2 min", reset button gone.
- Let it finish: inverse fill (dark in light theme, light in dark), check icon, "Done"; reset button to the left. Phone buzzes twice (short, pause, short; 200-100-200 ms) and plays the default notification sound; a "Timer done" notification appears. Tap Done: back to idle.
- Start two timers: the notification shade shows one ongoing notification per running timer ("Mix the spices", "Drain the beans") with a countdown clock. Pause one: its notification goes. All paused or done: no ongoing notification left, no foreground service (`adb shell dumpsys activity services com.simmer.app`).
- Leave the app (Home) while both run: notifications keep counting. Reopen: button times match.
- Process death: with a timer running, `adb shell run-as com.simmer.app kill -9 $(adb shell pidof com.simmer.app)` (a plain `am kill` does not touch a foreground service). The service restarts by itself within seconds, notifications return, reopen the step: time is correct. A paused timer restores paused with its remaining time. A timer that ended while the process was dead shows Done and posts the "Timer done" notification.
- Leave the flow (Back to Recipes) and open the recipe again: the running timer is still running. Timers are per recipe and step: `curry` timers are independent.
- Rotate while running: nothing resets.
- Channels: Settings > Apps > Simmer > Notifications lists "Running timers" (silent) and "Finished timers" (sound and vibration).
- Light and dark, German (button "Fertig", reset "Timer zurücksetzen"), large font (the title column shrinks, the timer button stays 44dp tall), TalkBack: button reads "Start 2 min timer", "Pause timer, 1:12 left", "Resume timer, 1:12 left", "Timer finished, tap to reset"; "Done" is announced.
- `connectedDebugAndroidTest` includes `StepTimerAndroidTest` (button states, reset position, channels, SharedPreferences round trip).

### Edit recipe (#13)

Open `chilli`, tap Edit (top right of any flow phase). The Add recipe review does not exist yet, so editing its draft (route `EditRecipe(null)`) and a User Recipe (Delete recipe) need the throwaway branch (section 3).
- Top bar: x, recipe title, "Edit recipe" (de: "Rezept bearbeiten"); bottom bar Cancel and Save changes. x and Cancel discard and return to the same flow phase.
- Save changes is disabled when the title is blank, every ingredient name is blank, or no step has text; re-enabled as you fix it.
- Details: title, 3-line summary, Servings and Total time side by side (digits only), Easy/Medium/Hard option group, wrapping Category options. Selected option has the orange outline; TalkBack reads radio + selected.
- Ingredients: one accordion row open at a time (opening another closes the first). Collapsed: name, "note · aisle", amount + unit. Expanded: Name, Amount, Unit, Note, Shop section, Remove ingredient. Amount accepts `1.5`, `1,5`, `1/2`, `1 1/2`, `½`, `¼`, `¾` (after Save and reopen it shows the decimal, e.g. 0.5). "Beef mince" shows the info line "Only when: Beef", "Plant-based mince" shows "Only when: Plant-based" (de: "Nur wenn: ..."). Add ingredient appends a blank row and opens it.
- Steps: Preparation and Cooking groups; collapsed shows "Prep 1" / "Step 1", title, timer ("2 min"; no timer icon or text when the step has none). Expanded: title, instructions (plain text, no braces), live "Linked: Onions, Garlic" line while you type (or "No ingredients found in this text yet."), Timer in decimal minutes (`1.5` = 1 min 30 s, `0.1` saves as 6 s) with the hint "Optional" (de: "Optional"), then the Phase toggle on its own full-width row (Preparation / Cooking, each half width, labels do not wrap in en or de; the step moves to the other group and renumbers), then Move up / Move down side by side with arrow icons (disabled on the first / last step of the group), Remove step. "Add preparation step" / "Add cooking step" under each group append and open a blank step. Conditional steps show "Only when: ...".
- Relinking: edit "Chop the vegetables" to "Slice the onions and the garlic." and Save; in Phase 3 those words are underlined and tapping shows amount and tips. Edit only its title: the old underlined words stay underlined.
- Remove the "Dark beer" ingredient and Save: the Dark beer add-in row is gone from Phase 1.
- Built-in recipe: after Save the Recipes card shows the "Edited" badge; reopen Edit: "Restore original" shows. Restore original: title, ingredients and steps are back, badge gone, link gone.
- After Save or Restore you land on the flow phase you came from (try Phase 1, 2 and 3), showing the new content; servings and picks are kept.
- User recipe (throwaway branch): Save replaces it in place (no new card, no "Edited" badge). Delete recipe: back on the Recipes tab, the card and its Planned card are gone, and its items are gone from the Shopping list (manual items stay).
- Add recipe draft (throwaway branch): Edit, change the title, Save: the placeholder review shows the new title; nothing is stored in Room (no new card on Recipes).
- Ingredients caption: "1 ingredient" (de: "1 Zutat") with one row, "N ingredients" otherwise.
- Optional timer: clear the Timer field (or type `0`) on a step and Save: the step has no timer chip in the editor, and in Phase 3 it shows no timer button. Add preparation step: the new step's Timer field is empty with the hint "Optional". Typing `0.1` is accepted (6 s, no 30 s minimum). Reopen Edit on a step without a timer: the field is empty.
- Amount or Timer typed as text such as "lots": the field shows the error outline and Save changes is disabled; a number, "1,5" or "1 1/2" enables it again. A nameless ingredient or textless step with a bad value does not block Save (it is dropped).
- Step phase: open a Preparation step, tap Cooking: it appears as the last Cooking step.
- Delete the Garlic ingredient and Save without touching "Chop the vegetables": in Phase 3 "garlic" in that step is no longer underlined; the other links are.
- Rotate and `adb shell am kill com.simmer.app` with edits and an open row: edits and open rows are restored.
- German: all labels translate; categories Suppen, Salate, Backen etc. Light and dark theme. Large font: nothing clipped, accordion rows grow. Touch targets >= 44dp, rows >= 56dp. The keyboard does not cover the focused field or the bottom bar.
- Compare against Design/ (Edit recipe screen in `Recipe App.dc.html`).
