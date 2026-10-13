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


- Skipping Phase 2 (needs the throwaway branch's "No-method test" recipe, or any recipe whose Methods have at most one option each): in Phase 1 the bottom button reads "Start cooking" (de: "Kochen starten") and opens Phase 3 directly; Prep steps show as "Prep n"; Back from Phase 3 returns to Phase 1. The chrome has 2 phases on Phases 1 and 3: captions "Step 1 of 2 · Servings" and "Step 2 of 2 · Cooking" (de: "Schritt 1 von 2 · ..."), and the progress bar shows only two equal-width segments, Servings and Cooking, with no greyed Preparation segment; on Phase 3 tapping the Servings segment returns to Phase 1. `chilli` keeps 3 phases ("Step 3 of 3 · Cooking"). `chilli` still shows "Preparation" and Phase 2. Swap and add-in variations on Phase 1 do not change this. Resume case: from the throwaway recipe's Phase 1, an entry to Phase 2 (restored back stack) pops back to Phase 1 instead of showing an empty "How will you cook it?".

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
- Title "What are we cooking?" (de: "Was kochen wir heute?"). Top right: + (opens Add recipe, see below) and moon/sun (toggles theme, icon flips).
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

Open `chilli`, tap Edit (top right of any flow phase). Editing the Add recipe draft is under Add recipe (#14) below; Delete recipe needs a User Recipe (save the Add recipe example first).
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
- Add recipe draft: see Add recipe (#14), "Edit details".
- Ingredients caption: "1 ingredient" (de: "1 Zutat") with one row, "N ingredients" otherwise.
- Optional timer: clear the Timer field (or type `0`) on a step and Save: the step has no timer chip in the editor, and in Phase 3 it shows no timer button. Add preparation step: the new step's Timer field is empty with the hint "Optional". Typing `0.1` is accepted (6 s, no 30 s minimum). Reopen Edit on a step without a timer: the field is empty.
- Amount or Timer typed as text such as "lots": the field shows the error outline and Save changes is disabled; a number, "1,5" or "1 1/2" enables it again. A nameless ingredient or textless step with a bad value does not block Save (it is dropped).
- Step phase: open a Preparation step, tap Cooking: it appears as the last Cooking step.
- Delete the Garlic ingredient and Save without touching "Chop the vegetables": in Phase 3 "garlic" in that step is no longer underlined; the other links are.
- Rotate and `adb shell am kill com.simmer.app` with edits and an open row: edits and open rows are restored.
- German: all labels translate; categories Suppen, Salate, Backen etc. Light and dark theme. Large font: nothing clipped, accordion rows grow. Touch targets >= 44dp, rows >= 56dp. The keyboard does not cover the focused field or the bottom bar.
- Compare against Design/ (Edit recipe screen in `Recipe App.dc.html`).

### Add recipe: choose how to add (#47)

Recipes tab, tap +. Every Add recipe item below that mentions the paste stage starts with "On this phone".
- Start stage on every visit: title "How do you want to add this recipe?" (de: "Wie möchtest du dieses Rezept hinzufügen?"), two cards. Open Add recipe, leave (Back), open it again: the start stage shows, never the paste stage (the ViewModel is new for each visit). Exception, as before this change: a conversion still running or a finished recipe never reviewed (the "Recipe ready to review" notification) is picked up, so the screen opens on Working or Review instead.
- "On this phone" card: line "Paste the recipe and your phone reads it..." and the Reader model's state under it, the same text as Settings > Recipe reader (e.g. "Not downloaded · 2.6 GB", "Ready"). Download the reader in Settings, come back: it reads "Ready".
- Tap "On this phone": the paste stage, unchanged: "Download recipe reader (2.6 GB)" for other text, Try an example then Convert recipe, the Working stage, review and Save recipe as under Add recipe (#14) below.
- Tap "With a chatbot": the chatbot screen with a back button and the title "Add with a chatbot." (de: "Mit einem Chatbot hinzufügen."); its reply field is under "Add recipe: paste a chatbot reply (#48)" below. Card text names no specific chatbot.
- Back: from the paste stage (arrow and system back) and from the chatbot screen (arrow and system back) returns to the start stage; the paste text is kept when you pick "On this phone" again. Back from the start stage closes Add recipe. Working, error and review stages keep their own Back.
- Rotate on the start stage, the chatbot screen and the paste stage: you stay on the same step.
- Process death: on the chatbot screen run `adb shell am kill com.simmer.app` and reopen from Recents: still on the chatbot screen. Same on the start stage and the paste stage.
- English and German: strings above in both.

### Add recipe: copy the Reader prompt (#49)

Recipes tab, + , "With a chatbot". Step 1 is on top of the chatbot screen, step 2 (the reply, #48 below) under it. Needs a way to read the clipboard (paste into any app's text field, or into a real chatbot).
- Layout: title "Add with a chatbot." (de: "Mit einem Chatbot hinzufügen."), heading "1. Copy the prompt" (de: "1. Prompt kopieren"), field "Recipe text (optional)" (de: "Rezepttext (optional)") with Paste/Clear, the note "You can also attach a photo or a link in the chatbot instead." (de: "Du kannst im Chatbot stattdessen auch ein Foto oder einen Link anhängen."), button "Copy prompt" (de: "Prompt kopieren"), heading "2. Paste the reply", then the reply field.
- Copy prompt with the text field empty: a "Prompt copied" toast, and pasting elsewhere gives the full prompt: instructions, "Reply in this shape:" with the schema, "Example of a finished reply:" with the sheet-pan chicken example, a "Kitchen:" line, and "The recipe is not written here: it is in a photo or a link I attach to this message."
- Recipe text: type or Paste "Pancakes: flour, milk, egg. Whisk and fry." and Copy prompt: the pasted prompt ends with "Recipe text:" and that text, and has no "not written here" line.
- Kitchen line: Settings > Your kitchen with Hob, Oven ticked. Strict: "Kitchen: the cook owns only these appliances: Hob, Oven. ..." plus "Be strict: ...". Medium: the same list plus "Common small tools ...". Free for all: "Kitchen: the cook can use any appliance." and no "Never offer" sentence. Add a custom appliance: it appears by name. Untick all: "none".
- Language: Settings > Language Deutsch, copy again: the whole prompt is German (instructions, "Küche: ...", appliance names such as Herd, Backofen, "Rezepttext:"), while JSON keys, ids and category/tag/icon words stay English. Do it with the device language English and the app language German, and the other way round.
- Real chatbot: paste the prompt (with a recipe text, and again with a photo or link attached and an empty field) into a real chatbot, copy its reply, paste it into "Chatbot reply", Add recipe: the review shows the recipe. Try English and German.
- Rotate with text in the recipe field: it stays. Process death (`adb shell am kill com.simmer.app`, reopen from Recents): still on the chatbot screen with the recipe text. Back to the start stage and "With a chatbot" again: the text is still there.
- Large font: nothing clipped, the keyboard does not cover the focused field or the bottom bar.
- Automated (JVM): `ReaderPromptTest` (builder: Kitchen line and rule per mode, recipe text, empty field, schema and example), `ReaderPromptLanguageTest` (the shipped English and German strings, and that the prompt's example parses with the #48 parser and follows the Kitchen rule). Not covered by tests: the clipboard write, the Toast and the saved-state wiring (this checklist).

### Add recipe: paste a chatbot reply (#48)

Recipes tab, + , "With a chatbot". Needs a way to put text on the clipboard (type it into any app and copy, or `adb shell input text`). Valid reply to paste:
`{"title":"Pancakes","summary":"Thin and easy.","category":"Breakfast","time":20,"base":4,"ingredients":[{"id":"i1","name":"Flour","q":200,"u":"g"},{"id":"i2","name":"Milk","q":300,"u":"ml"}],"steps":[{"id":"p1","ph":"prep","t":"Whisk","b":"Whisk the {i1:flour} with the {i2:milk}."},{"id":"c1","ph":"cook","t":"Fry","b":"Fry until golden.","d":300,"needs":["p1"]}]}`
- Empty field: label "Chatbot reply" (de: "Chatbot-Antwort"), Paste button, placeholder, and a disabled "Add recipe" (de: "Rezept hinzufügen"). Paste fills the field and the button to Clear; "Add recipe" enables. Clear empties it again.
- Valid JSON, Add recipe: the review shows "Pancakes" at once (no Working stage, no notification), Ingredients and Steps tabs filled, "Whisk the flour..." links work. Save recipe stores it; it is first in Recipes, tagged "Added by you", and opens in the cooking flow.
- Same JSON inside a ```json fence, and wrapped in prose ("Sure! Here is your recipe: ... Enjoy!"): the same review.
- Kitchen rule: reply with a Method "m1" of options Oven (`oven`), Air fryer (`air`), Hob (`hob`) and steps tagged `"if":"m1=oven"` etc. In Settings > Your kitchen set Strict or Medium with no air fryer: the review shows Oven and Hob only, and no air fryer step. With only the oven: no Method is shown and the oven steps carry no condition. Free for all keeps all three.
- Not a recipe: "hello", a cut-off JSON, `{"title":"X"}`, or a reply with an empty title, empty `ingredients` or empty `steps`: Add recipe does nothing, the screen stays on the chatbot screen with the text kept (an error message is a later ticket).
- Back from review (Edit text): returns to the chatbot screen with the reply still in the field. Back from the chatbot screen goes to the start stage; picking "With a chatbot" again still shows the reply.
- Rotate with a reply in the field, and after Add recipe on the review: both are restored.
- Process death: with a reply in the field `adb shell am kill com.simmer.app`, reopen from Recents: still on the chatbot screen with the reply. Same on the review stage (the draft is restored).
- English and German strings. Large font: nothing clipped. Keyboard does not cover the field or the bottom bar.

### Add recipe (#14)

Recipes tab, tap + (no throwaway branch needed), then "On this phone" (#47). There is no on-device recipe reader yet: only the built-in example converts.
- Paste stage: back button, caption "New recipe", title "Add a recipe." (de: "Rezept hinzufügen."), label "Recipe text", helper "Include the ingredients and the method.", text area about 13 lines tall.
- Paste / Clear: empty field shows Paste (copy something in another app first; it fills the field; with an empty clipboard it focuses the field). Once there is text the button becomes Clear and empties it.
- "What happens next": four rows. Kitchen line reads "Methods only use your appliances: ..." with the Settings appliances in catalogue order, e.g. "Hob, Oven, Microwave" (Strict or Medium), "no appliances selected" when none, "Methods can use any appliance." in Free for all. Change opens Settings on top; Back returns to Add recipe with the text kept; tapping a bottom tab instead abandons Add recipe and its text.
- Bottom bar for text that is not the example (type "Pancakes: flour, milk, egg"): cpu icon line "Runs on your phone. Downloads once over Wi-Fi." and a grey, inactive "Download recipe reader (2.6 GB)" button that does nothing when tapped. Convert (example) is grey below 30 characters (type 29 letters, then one more with a non-example text to see it stay grey).
- Try an example fills the text and the bar changes to Convert recipe (sparkles). Edit one word of the example: it becomes Download again.
- Convert recipe goes straight to Review (no Working screen). Caption "New recipe · not saved yet". Hero: MAINS, "Sheet-pan lemon chicken", summary, "1 h", "Easy", "4 servings".
- "Check before saving" cream note with orange outline lists the three notes. "Choices found": "The chicken" (Oven · Air fryer) and "Chilli flakes" (Optional add-in · ...).
- Underline tabs Ingredients / Steps and caption "9 ingredients · 3 preparation and 3 cooking steps". Ingredients: 9 rows with note · aisle and amount; Chilli flakes has the orange "Optional" tag. Settings units imperial: potatoes read "1¾ lb".
- Steps: "Shown for Oven. Other choices change these steps." Groups Preparation (3) and Cooking (3: roast potatoes, roast chicken, finish). Cards show Prep 1.. / Step 1.. labels, timer icon and duration, title, "Needs Prep 2 and Prep 3 first" on "Roast the chicken", and body text with underlined ingredients; tap one for the popover (amount, note, tips).
- Edit details opens Edit recipe on the draft: change the title, Save: back on Review with the new title, nothing on Recipes yet. Cancel returns unchanged.
- Edit text returns to the paste stage with the text kept; Convert again works.
- Save recipe: Add recipe closes and you land on the Recipes tab (not Phase 1): the new card is first in the list with "Added by you", the list is scrolled to the top (scroll the list down and filter by "feta" before saving: after saving the search is cleared and the top is shown). Back from Recipes leaves the app, not Add recipe. Open the card: Phase 1 shows the Chilli flakes add-in, 4 servings. Save a second recipe: it is above the first. Open it, edit and save it (it keeps its place in the list), and Delete recipe (Edit recipe) works. Editing a built-in does not move it either.
- Sticky Edit details: on Review a top row stays pinned while the content scrolls (scroll the Steps tab down): back button, "New recipe · not saved yet" on the left, pencil icon + "Edit details" (de: "Details bearbeiten") on the right, >= 44dp tall. The hero card has no Edit details link any more. German and large font: the caption wraps, the action stays on the right.
- Pencil icon on Edit: in recipe flow Phases 1 to 3 the top-right "Edit" (de: "Bearbeiten") has the same pencil icon + underlined text as "Edit details", and still opens Edit recipe.
- Tab switch after Change (the bug fix): on the paste stage tap Change (kitchen line, scroll down). Settings opens on top of Add recipe. Tap Shopping list: it opens. Tap Recipes: the Recipes tab shows (not Settings on top of Add recipe again); + opens an empty Add recipe (draft discarded). Repeat with Settings > Back first: Add recipe returns with the text intact. Repeat tapping the Settings tab itself: nothing changes and Back still returns to Add recipe. Repeat tapping Recipes first. Other pushed screens that go to a tab: Phase 1 toast "View list" (Shopping), Phase 3 "Back to recipes", Edit recipe > Delete recipe (Recipes), Your kitchen Back (Settings): each lands on the tab, and the tab bar works afterwards.
- Back from Review without saving drops the draft and the text: + again starts on an empty paste stage.
- Rotate on both stages: text, tab and review stay. `adb shell am kill com.simmer.app` on Review (also after Edit details changes): the review and its edits are restored; on the paste stage the text is restored.
- German: all strings translate ("Rezept umwandeln", "Vor dem Speichern prüfen", "Gefundene Optionen", "Gezeigt für Ofen. Andere Optionen ändern diese Schritte."). Light and dark, large font, TalkBack (text area reads "Recipe text"). The keyboard does not cover the bottom bar.
- Compare against Design/ (Add recipe screens in `Recipe App.dc.html`; the design uses segmented tabs, this app uses underline tabs per the ticket).
- `connectedDebugAndroidTest` includes `RecipeOrderTest` (Recipes list order and that editing keeps a recipe's place, Room) and `TabNavigationTest` (back stack when a bottom tab is tapped over Add recipe > Settings, Save recipe landing).

### Recipe reader — model download (#15)

Settings tab, scroll to "Recipe reader". The download is a real 2.0 GB file from Hugging Face, so use Wi-Fi and an emulator or phone with 3.5 GB free (emulator: at least 4 GB RAM, see "Not supported" below). The model lives in `files/models/` (`adb shell run-as com.simmer.app ls -l files/models`: `*.part` while downloading, `gemma-4-E2B-it-web.litertlm` once verified). `adb shell am start -n com.simmer.app/.MainActivity --ez open_settings true` opens Settings directly. Start from `adb shell pm clear com.simmer.app`.

- Not downloaded: "On this phone · Gemma 4 E2B", "Not downloaded · 2.0 GB" (the real size, derived from the model constant), orange Download button, "Download on Wi-Fi only" switch on (default) with "The model is 2.0 GB.", lock line.
- Download: tap Download (Android 13+ asks for notification permission once). The state line reads "Downloading · n%", a progress bar fills, the button becomes "Cancel download". Rotate and `adb shell am kill com.simmer.app`: the download keeps going and Settings shows the live percentage after reopening.
- Notification: pull down the shade while it downloads: "Downloading recipe reader", progress bar, n%. Leave the app (Home): it keeps going. Tap the notification: the app opens on the Settings tab (also when the app was swiped away; when already open on another tab or screen).
- Wi-Fi only: with the switch on, turn Wi-Fi off and keep mobile data (`adb shell svc wifi disable`): the state reads "Waiting for connection · n%" and the file stops growing. Turn Wi-Fi back on (`adb shell cmd wifi set-wifi-enabled enabled`): it continues. Switch the toggle off while waiting: it continues over mobile data (the toggle re-queues the task, the partial file is kept and the percentage does not drop to 0). Kill the app, reopen Settings: the switch keeps its position (DataStore).
- Resume after airplane mode: while downloading note the size of the `.part` file, switch airplane mode on (swipe the quick settings; or turn Wi-Fi off), wait 20 s, switch it off again. The file size must not go back to 0 and keeps growing from the old size; the percentage continues rather than restarting.
- Cancel: tap "Cancel download": status returns to "Not downloaded · 2.0 GB", the notification disappears, the `.part` file stays. Download again resumes from its size (the first request has a `Range` header). "Remove model" (when Ready) deletes the model and any `.part`.
- Permanent failure: a 4xx from the server (e.g. point the URL at a missing file) ends as "Download failed: the server refused the request", not an endless retry; Download tries again.
- Ready: wait for completion (about 10 minutes at 25 Mbit/s). After 100% there is a pause while the SHA-256 is checked, then "Ready" with "Remove model" in place of Download. Reopen the app and reboot-free restart (`am kill`): still Ready. The file is exactly 2008432640 bytes.
- Remove model: tap it: "Not downloaded · 2.6 GB", the file is gone. (No confirmation by design: it can be downloaded again.)
- Not enough storage: fill the phone until under 3.5 GB is free (emulator: `adb shell run-as com.simmer.app dd if=/dev/zero of=files/filler bs=1M count=<n>` until `adb shell df /data` shows < 3.5 GB available) and reopen Settings: "Not enough storage. Needs 3.5 GB free." and a greyed Download button that does nothing. `adb shell run-as com.simmer.app rm files/filler` brings the Download button back. With exactly 3.5 GB or more the button is active.
- SHA mismatch: temporarily change `ReaderModel.SHA256` in `domain/ReaderStatus.kt` (flip one hex digit), install, download to 100%: the file and `.part` are deleted, the state reads "Download failed: the file was damaged and was deleted. Try again." with Download shown again. Do not commit the change. (A shorter variant: serve a different file by pointing `ReaderModel.URL` at a small file.)
- Not supported on this phone: reported when the phone has no 64-bit ABI or under about 3.5 GB RAM. Emulator: start with `-memory 2048` (the AVD default of 4 GB passes). Expect "Not supported on this phone" and no buttons or switch.
- German (Settings language Deutsch): "Rezept-Leser", "Nicht geladen · 2,6 GB", "Wird geladen · n %", "Warte auf Verbindung · n %", "Bereit", "Nicht genug Speicher. 3,5 GB frei benötigt.", "Auf diesem Handy nicht unterstützt", "Laden", "Download abbrechen", "Modell entfernen", "Nur über WLAN laden"; the notification is German as well. Light and dark theme, large font (the state line and German texts wrap, nothing clipped), TalkBack (the switch reads "Download on Wi-Fi only, switch, on"; the state line is announced when it changes). Touch targets: buttons and the switch row >= 44dp / 56dp.
- Add recipe still shows the inactive "Download recipe reader (2.6 GB)" button: the reader engine exists since #16 but is not wired to the UI yet (#17).
- Automated: `ReaderStatusTest` (which state shows), `ModelDownloaderTest` (resume, SHA, storage; no network), and `ReaderControllerTest` in `connectedDebugAndroidTest` (Wi-Fi-only persistence, installed => Ready, Remove).

### Recipe reader — GemmaReader engine (#16)

Add recipe calls `generate()` through the Pipeline since #17; to exercise the engine alone use a throwaway branch or a debug test that calls `GemmaReader.generate("You answer in one word.", "Say hello", 32)` and collects the flow. Needs the model Ready (see #15 above). Watch `adb logcat | grep -i litert`.
- Manifest: `aapt dump xmltree app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml | grep -i native-library` lists `libvndksupport.so` and `libOpenCL.so`, both `required=false`.
- First call: returns within seconds without freezing the UI (init is off the main thread; check no "Skipped frames" / ANR). Tokens arrive one by one, not all at once; output is stable across runs (temperature 0.2).
- GPU first: on a phone with OpenCL (Pixel 7 Pro) the log shows the GPU backend; on an emulator or a phone without it generation still works (CPU fallback).
- Single instance: call generate twice in a row; the second starts at once (no reload) and memory stays flat (`adb shell dumpsys meminfo com.simmer.app`).
- Idle unload: wait 60 s after the last call, memory drops by about the model size; the next call reloads and works.
- onTrimMemory: with the engine loaded run `adb shell am send-trim-memory com.simmer.app MODERATE`; memory drops and the next call reloads. Switching to another app and back (UI_HIDDEN) must NOT unload: `adb shell am send-trim-memory com.simmer.app HIDDEN` leaves memory unchanged. Before the first generate call, a trim must not create the reader (no model load, no log line).
- Thermal: `adb shell cmd thermalservice override-status 4` (severe): a generate call does not start until `adb shell cmd thermalservice reset`; it then runs. If left severe for 5 minutes the call fails with "The phone stayed too hot". A trim sent while it waits unloads at once. Needs Android 10+.
- Not covered by an androidTest: `AndroidThermalMonitor` (the device's real thermal status cannot be set from a test without shell access); the severe-or-worse mapping is a JVM test, the real service is the manual check above.
- Not covered on device: Gemini Nano selection (no GeminiNanoReader yet, so Gemma is always used).
- Automated (JVM, fakes for engine, thermal, time): `GemmaReaderTest` (temperature, GPU to CPU fallback, single instance, off-thread init, idle unload, trim, thermal wait, download progress) and `ReaderSelectorTest`.

### Your kitchen (#12)

Reach it: Settings tab > "Edit your kitchen" (also Add recipe > Change > Settings > Edit your kitchen). Fresh state: `adb shell pm clear com.simmer.app`.
- First run: title "Your kitchen.", intro, search field, "3 selected" with Hob, Oven, Microwave ticked and 25 others unticked (28 rows in the spec order, Hob first, Dehydrator last), Kitchen mode on Medium (Settings).
- Toggle: tick Wok, count reads "4 selected" at once; untick it, "3 selected". Tick one, `adb shell am kill com.simmer.app`, reopen: still ticked (saved on each tap, no Save button). Settings pills and Add recipe's kitchen line show the same appliances.
- Untick everything: "0 selected"; reopen: stays empty (the defaults do not come back); Settings says "No appliances selected yet."
- Search (English UI): "blend" lists Blender and Hand blender; clearing the text lists all 28. German UI: "Herd" finds Hob's row, and "hob" also finds it (English names match too); "Backofen" and "oven" both find Oven.
- Add your own: type "Thermomix" (no match): first row "Add 'Thermomix'" with "Not in the list? Add it as your own."; type "H": no add row; type "Hob" or "herd": no add row (exact match). Tap the add row: the search clears, the list ends with "Thermomix" labelled "Added by you", ticked, count +1. Untick it: the row stays. Kill and reopen: it is still listed. Add recipe's kitchen line names it while ticked.
- Done and Back (arrow, "Settings") both return to Settings; the tab bar is hidden on this screen. From Add recipe > Change > Edit your kitchen > Done, Back returns to Settings and then Add recipe with its text.
- Rotate and process death with text in the search field: the text survives.
- German (Settings language Deutsch): "Deine Küche.", "n ausgewählt", "„Thermomix“ hinzufügen", "Von dir hinzugefügt", "Fertig"; long German intro wraps. Light and dark theme, large font, TalkBack (rows read as checkboxes with state, the count is announced when it changes, back button labelled). Rows >= 56dp, buttons >= 44dp.
- Kitchen mode never filters cooking an existing recipe: with only Hob ticked, open Chilli con carne > Preparation: all its Methods are still offered.
- Automated: `YourKitchenSearchTest`, `YourKitchenChoiceTest` (JVM), `YourKitchenPrefsTest` (DataStore defaults and persistence, `connectedDebugAndroidTest`).

### AI pipeline, 4-pass conversion (#17)

Needs the Reader model Ready (Settings, see #15) for real text; the built-in example still converts at once without a model. Without a model on the device use the `throwaway/issue-17-manual-test` branch (a scripted fake reader with delays, see the PR) to see every screen below; the model-quality items (marked **model**) need the real model on a phone.
- Working stage: paste any recipe (or one of `app/src/androidTest/assets/fixtures/*.txt`), tap Convert recipe. The screen reads "Reading your recipe." with the 1 to 3 minute hint and four rows. The current row has an orange ring, finished rows an orange check, later rows a grey ring; the ring moves down as each pass finishes. Rows are at least 56dp, TalkBack reads "Reading ingredients, in progress".
- Cancel: tap Cancel mid-generation. You are back on the paste stage with the text kept, no notification is left, and `adb shell dumpsys activity services com.simmer.app` shows no `ImportService`. Convert again works.
- Leave the screen: press Back (or Home) during generation. An ongoing notification "Reading your recipe." shows the current stage and updates as passes finish; tapping it opens Add recipe on the Working stage. `adb shell dumpsys activity services com.simmer.app | grep ImportService` shows it as a foreground service while it runs.
- Done: when generation ends while you are away, the notification becomes "Recipe ready to review"; tapping it opens Add recipe on the review stage with the recipe. When you are on the screen, the review appears by itself.
- Process death while generating: `adb shell am kill com.simmer.app` is not enough while the service holds the process, so use `adb shell am force-stop` only to see the failure case, and `adb shell kill <pid>` (from `adb shell pidof com.simmer.app`) to simulate the system. Expected: the system restarts the service, generation starts again from pass 1 and ends in the review (or a "Recipe ready to review" notification). Kill it a second time during the restarted run: the next start shows the error stage instead of looping.
- Process death after it finished but before you opened it: kill the process; opening Add recipe (or the notification) shows the review.
- Error stage (**model**, or the throwaway fake set to fail): text such as "asdf asdf asdf asdf asdf asdf asdf asdf" shows "We couldn't read that recipe on this phone. Try a shorter or cleaner version." and a button back to the text, which is kept.
- Long text: paste more than about 6,000 characters of recipe text; the review's "Check before saving" notes start with "This recipe is long. Only the first part was read." (in German: "Dieses Rezept ist lang. Nur der erste Teil wurde gelesen.", also when the device language is English but the app language is German).
- Leave Add recipe while the recipe finishes (Back during Working, wait for the notification): the notification opens the review, not an empty paste stage.
- Cancel while the model is generating (**model**): `adb logcat | grep -i litert` shows inference stopping and CPU/GPU load drops at once (the Conversation is cancelled with `cancelProcess()` before it is closed; not testable without the model).
- Review (**model**): ingredient ids and amounts are metric, optional items show as "Optional add-in" choices, Methods only name appliances in Your kitchen, steps have links in their text, and steps with no wait (chopping) have no timer in the cooking flow while roasting or simmering steps do.
- Notifications permission (Android 13+): the permission prompt shows on the first Convert; if denied, generation still runs and the review appears when you come back.
- Always check (section 4) on the Working and error stages: light and dark, English and German (long German row labels wrap), rotate while Working (state stays), and `adb shell am kill` on a phone where the service is not running.
- Fixture suite on a device (**model**): download the Reader model in the app, then `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.simmer.app.PipelineFixtureDeviceTest`. It converts all 22 texts with the real Gemma model and fails unless at least 90% pass `normalize()` without a repair retry and each takes under 3 minutes. The per-recipe table is in `adb logcat -s PipelineFixtures` and `adb shell run-as com.simmer.app cat files/pipeline-fixtures.txt`. Target phone is a Pixel 7 Pro; without the model the test is skipped, not failed. Peak memory: run `adb shell dumpsys meminfo com.simmer.app` while it runs.
- Automated (JVM): `TrimRecipeTextTest`, `ModelJsonTest`, `RecipePipelineTest` (scripted reader: four passes, stage order, token caps, repair retry and its size bound, failure stage, cancel, stall timeout, kitchen-rule filtering, truncation note), `KitchenMethodsTest`, `NewRecipeIdTest`, `ImportJobTest` (state, cancel, resume once, hand-over), `StageMarksTest`, `KitchenRuleForTest`, `FixtureSetTest`; instrumented: `ImportPersistenceTest`, `PipelineRegexAndroidTest` (fixtures through trimming and the link markup on the device's regex engine).
- Not covered by a test: the real foreground service and notifications (`ImportService`, `ImportNotifications`), the real process kill (the resume decision is the JVM-tested `ImportJob.restore`), and everything that depends on the quality of the real model.
