# Simmer

The Simmer recipe app. Users browse built-in and imported recipes, configure servings and variations, cook through step-by-step instructions with timers, and maintain a shopping list.

## Language

### Recipes

**Recipe**:
A named dish with ingredients, steps, variations, and methods. The central entity the app revolves around. Immutable while cooking; editable outside the flow.
_Avoid_: Dish, meal

**Source URL**:
The optional link a Recipe keeps back to where it came from (`Recipe.sourceUrl`, null when none), e.g. the Reel URL of an imported Reel. Shown on the recipe's Hero as a tappable "Open original recipe" link, hidden when null. Kept through editing and saving. A URL only; nothing else about the source is stored.
_Avoid_: Origin, attribution

**Built-in Recipe**:
A recipe shipped as a JSON asset with the app. Can be locally overridden via editing (marking it `edited`), but the original is always restorable.
_Avoid_: System recipe, default recipe

**User Recipe**:
A recipe created by importing text through the AI pipeline. Marked `mine = true`. Can be edited or deleted.
_Avoid_: Custom recipe, imported recipe, my recipe

**Planned Recipe**:
A recipe the user has added to the Shopping List. Stored with its chosen servings and selection so the user can return to the cooking flow. Leaves the Planned state when the user taps "Unplan" on the flow button or swipes the Planned card away (TalkBack action "Unplan"); both are `removeRecipe`, and its Shopping Items go too, ticked ones included. It also leaves the Planned state when a clear action empties the recipe's items ("Clear ticked off" keeps Planned for recipes that still have items; "Clear all" drops all). Cooking progress is separate and never touched.
_Avoid_: Saved recipe, queued recipe

**Swipe removal**:
Swiping a Planned card (Unplan) or a Cooking now card (Stop cooking) away on the Recipes tab. `RecipeRemovals` performs it and keeps a `Removal` (`Unplanned` with its `ShoppingSnapshot`, or `Stopped` with its `StoppedCooking`) for a 5 s Undo window, shown as a snackbar with "Undo". Undo restores the Shopping Items in their original order, ticked state and Planned Choice, or the done Steps. A newer removal replaces the older one; the window ends on Undo, dismissal or timeout. Shares the 5 s with the Shopping List's clear Undo (`UNDO_WINDOW_MS`).
_Avoid_: Delete, dismiss

**Planned Choice**:
The servings and Selection saved with a Planned Recipe when the user taps "Add to list" (`PlannedChoice`). The flow button reads "Unplan · N servings" (otherwise "Add to list · N servings") only while the current servings and Selection equal it; an add-in toggled off counts as never toggled.
_Avoid_: Saved choice, plan

**Edit recipe**:
The screen that edits one Recipe's title, summary, servings, time, difficulty, Category, Ingredients and Steps. Save writes the result back; Restore original (built-ins with a Local override) and Delete recipe (User Recipes only) live there too. Opens from the cooking flow's Edit button.
_Avoid_: Editor screen, recipe form

**Recipe draft**:
The in-progress state of Edit recipe (`RecipeDraft`): fields as typed text, built from a Recipe and turned back into one by `toRecipe`. Rows with no name or no text are dropped on save; Save is disabled until the title, an Ingredient and a Step are present and every kept amount and timer reads as a number. Kept in saved state, so it survives rotation and process death.
_Avoid_: Form state, edit state

**Add recipe draft**:
The unsaved Recipe the Add recipe review shows (`RecipeDraftStore`). Edit recipe opened without a recipe id works on it and writes back to the store, not to Room. Held in memory, and mirrored as JSON into Add recipe's saved state so it survives process death.
_Avoid_: Pending recipe, import result

**Add recipe**:
The screen that turns pasted text into a User Recipe. It opens on the start stage, "How do you want to add this recipe?", with two cards: "On this phone" (shows the Reader model's state; leads to the paste stage) and "With a chatbot" (leads to the chatbot screen). Back from the paste stage or the chatbot screen returns to the start stage. The start stage shows on every visit; only a Working conversion or a never-reviewed finished recipe from an earlier visit (see Import job) opens it on Working or review instead. On the phone path: a paste stage (text, Paste/Clear, "Try an example", the kitchen line), a Working stage while the Pipeline runs (or the error stage if it fails), then a review stage showing the Add recipe draft, then Save recipe (stores it as a User Recipe and lands on the Recipes overview, scrolled to the top where the new card is listed first). The review's top row stays pinned with the "Edit details" action. The pencil in the paste stage's "What happens next" title row ("Change kitchen settings") opens Settings on top, scrolled to its Recipe creator section, with a Back banner; a bottom tab tap there abandons Add recipe and its draft. On the chatbot path: the chatbot screen has step 1 (an optional recipe text field, kept in saved state, and "Copy prompt", which copies the Reader prompt) and step 2, a "Chatbot reply" field (Paste/Clear, kept in saved state) and an "Add recipe" button; the Reply parser turns the reply straight into the Add recipe draft and the review takes over, with no Pipeline, Working stage or Import job. A reply that is not a usable recipe changes nothing: the screen keeps the pasted text and shows "That doesn't look like a recipe from the prompt." with a "Copy fix prompt" button (the Fix prompt) until the reply is edited. Convert runs the Pipeline once the Reader model is ready; the built-in example still converts at once to its fixed result (`stubConvert`), and other text shows an inactive Download recipe reader button until the model is downloaded.
_Avoid_: Import screen, new recipe screen

**Reel import**:
Sharing an Instagram Reel (or post) link to Simmer (`ReelImport`, `readSharedText`). The share target (`ACTION_SEND`, `text/plain`) opens Add recipe on the paste stage with the Reel card above the text field; the link is cleaned to `https://www.instagram.com/<reel|p>/<id>/` (`igsh`/`utm` parameters dropped, `/reels/` read as `/reel/`). The card shows the shortened link, the caption fetch status (fetching, found with its line count, or "No caption found", which is a status, not an error) and the Listen button. The link becomes the saved recipe's Source URL. Any other shared text goes into the field as if pasted and shows no card. The fetch (`CaptionFetcher`) asks Instagram's undocumented oEmbed endpoint, then the embed page, with a non-browser User-Agent; every failure reads as no caption. State survives process death (a fetch still loading is asked again).
_Avoid_: Instagram import, link import

**Caption**:
The text of the Reel's post, as fetched by Reel import. It is added to the Add recipe text field under a "Caption" line ("Beschreibung" in German), after any text already there, and is then ordinary editable text: the user may edit or delete it, and Simmer never judges whether it is a full recipe.
_Avoid_: Description, post text

**Listen**:
The button on the Reel card that turns the Reel's sound into text on this phone (`ListenSession`). After an explainer ("Simmer records only the Reel's sound, not your microphone"), the RECORD_AUDIO request and the system screen-capture consent (fresh every time), a foreground service (`ListenService`, type `mediaProjection`) captures the Reel's playback audio (`AudioPlaybackCapture`, never the microphone) while the user plays the Reel in Instagram, which Simmer opens. Capture ends on Stop (button or notification), after 3 minutes, or when the projection ends (screen lock, status-bar chip). The audio is kept in memory, transcribed by whisper.cpp with the base model (`ggml-base-q5_1`, language detected, German or English) and then discarded. States: idle, consent, capturing, transcribing, done, silent ("No audio captured": Retry or Paste, no microphone fallback), cancelled, plus interrupted (process death mid-run). The Whisper model downloads on the first Listen after a size confirmation, through the same `DownloadWork` flow as the Reader model. A finished Transcript survives process death until it is put in the field. The native code always ships as an optimised Release build (a debug build is about 17 times slower).
_Avoid_: Record, dictate, voice import

**Transcript**:
The text Listen produced from the Reel's sound. It is added to the Add recipe text field under a "Transcript" line ("Transkript" in German), after any text already there (a Caption stays above it), and is then ordinary editable text. Whisper's bracketed non-speech tags (`[BLANK_AUDIO]`, `[MUSIC]`) are removed; a result with no words counts as silence.
_Avoid_: Subtitles, speech text

**Chatbot path**:
The way of adding a recipe where an outside chatbot does the reading instead of the Reader model: the user copies the Reader prompt (optionally with the recipe text), runs it in a chatbot of their choice with a photo, link or text, and pastes the reply back. Where the Gemini app is installed, "Open in Gemini" hands the same Reader prompt to it (shared text, and also put on the clipboard); Gemini has no way to return a result, so the user still copies its reply and comes back. On return to Simmer, a clipboard text that the Reply parser reads as a recipe prefills the "Chatbot reply" field with a toast (`autofillReply`); it never taps "Add recipe", and anything else on the clipboard is ignored. The Reply parser turns the reply into the Add recipe draft; there is no Pipeline, Working stage or Import job, and no Reader model download. Chosen with the "With a chatbot" card; the other way is "On this phone".
_Avoid_: Chatbot import, cloud path, manual import

**Pasted reply**:
The text the user pastes into the chatbot screen's "Chatbot reply" field (kept in saved state). Tapping "Add recipe" hands it to the Reply parser; if it is not a usable recipe the screen keeps it, shows the error and offers the Fix prompt until it is edited.
_Avoid_: Chatbot output, response

**Reply parser**:
Turns a pasted chatbot reply into a User Recipe (`readChatbotReply`): takes the first JSON object that parses (code fences and chatter ignored, balanced braces in prose skipped, `readModelJson`), applies the Kitchen rule in code for Strict and Medium, runs `normalize()`, and rejects a reply that has no JSON, is cut off, is not valid JSON, or lacks a title, an Ingredient or a Step (`readChatbotReply` names which, as a `ReplyError`). Instant; it shares those rules with the Pipeline.
_Avoid_: Chatbot importer, reply converter

**Reader prompt**:
What the chatbot screen's "Copy prompt" puts on the clipboard (`readerPrompt`): instructions, the enrichment rules for the **Import style** (Faithful keeps to the source and adds detail and tips; Liberty also suggests extra ingredients as `suggest` variations ("Extra suggestion", off by default), offers other methods for owned appliances and may restructure steps), the recipe JSON schema with a short example, the Kitchen line with the appliance rule for the Kitchen mode, and the optional recipe text (or a note that a photo or link is attached in the chatbot). Its prose follows the Settings language (string resources `reader_prompt_*`); JSON keys stay English. The reply it asks for is what the Reply parser reads.
_Avoid_: Chatbot prompt, import prompt

**Fix prompt**:
What the chatbot screen's "Copy fix prompt" puts on the clipboard after a bad paste (`fixPrompt`): the exact parse error (`ReplyError`, with the parser's message or the position of the cut-off object) plus "Return only valid JSON matching this shape" and the Reader prompt's schema, worded like the Pipeline's Repair retry. Its prose follows the Settings language (string resources `fix_prompt_*`). The user sends it back to the chatbot and pastes the corrected reply.
_Avoid_: Repair prompt, retry prompt

**Kitchen line**:
The line on Add recipe's paste stage saying which Appliances the generated Methods may use: the user's Appliances in catalogue order (custom ones after) for Strict and Medium, or "any appliance" for Free for all (`KitchenLine`).
_Avoid_: Kitchen summary, appliance note

**Choices found**:
The review's list of what the Pipeline found that the user will pick before cooking: each Method and each add-in Variation (`ReviewChoice`).
_Avoid_: Options found, variations list

**Local override**:
A built-in Recipe's saved edit, stored in Room with `edited = true`. Restore original removes it and brings the shipped version back.
_Avoid_: Customised recipe, fork

**Base**:
The number of servings a recipe's ingredient amounts are originally written for. All displayed quantities are scaled relative to this number.
_Avoid_: Default servings, original servings

**Selection**:
The complete set of a user's current choices for a recipe: a map of method picks (`choice`) and a map of add-in toggle states (`add`). Together with the serving count, it fully determines what gets displayed.
_Avoid_: Options, preferences, config

**Recipes overview**:
The Recipes tab: search, Category tabs, Tag chips, the Planned section and the list of recipe cards that open the cooking flow. Filters AND together; the Planned section ignores them. User Recipes are listed first, newest first, then built-ins (the order comes from `RecipeDao.observeAll`).
_Avoid_: Home, library, browse screen

**Category**:
The single group a Recipe belongs to (e.g. Mains, Pasta), shown as a badge and as one underline tab per distinct category present, after "All".
_Avoid_: Type, course, section

**Tag**:
A descriptor a Recipe can carry several of (Vegetarian, Quick, Spicy, One-pot, Make-ahead). The Recipes overview filters on them with multi-select chips.
_Avoid_: Label, keyword, filter

### Ingredients and Steps

**Ingredient**:
A single item used in a recipe, with a quantity, unit, and optional aisle and tips. May be conditional on the current selection.
_Avoid_: Item, component

**Aisle**:
The shopping-list category an ingredient belongs to (e.g. Produce, Dairy, Spices). Used to group shopping list entries. Aisle groups on the Shopping List follow a fixed shop-walk order (Produce, Meat, Chilled, Dairy, Baking, Pantry, Tins & jars, World foods, Spices, Drinks); unknown aisles follow alphabetically. Aisle names are recipe data and stay English.
_Avoid_: Section, category, shop section

**Step**:
A single instruction in a recipe, belonging to exactly one phase. Has a title, body text, an optional timer duration (0 seconds = no timer, so no StepTimer shows), and optional dependencies on other steps.
_Avoid_: Instruction, task

**StepTimer**:
The countdown button on a Step that has a timer duration; a Step without one shows no StepTimer. Cycles idle, running, paused, done. Keyed by recipe and step, so several run at once; a running StepTimer lives in the TimerService foreground service and is restored after process death from its end time.
While running it has an ongoing notification (progress bar, countdown, public on the lockscreen; a Live Update on API 36+) with a **Pause** action. Pause from the notification gives the *paused-from-notification* state: still Paused, but the notification stays with **Resume** (continues from the remaining time) and **Reset** (back to idle at full duration, notification dismissed), and the service stays alive. Pausing in the app clears the notification instead, and Resume and Reset never show for it. Notification actions that no longer match the state (stale taps) are ignored.
_Avoid_: Countdown, alarm

**Phase**:
One of two ordered stages of cooking a Step belongs to: `prep` (preparation, no heat) always comes before `cook` (cooking, heat applied). Steps are sorted by phase at display time regardless of authoring order. Not to be confused with a **Flow Phase**.
_Avoid_: Stage, section

**Flow Phase**:
One of the three ordered screens of the cooking flow, shown as the progress indicator (`FLOW_PHASE_COUNT = 3`): 1 Servings, 2 Preparation, 3 Cooking. Code uses `phase` for it too (`Phase1State`, `ProgressSegment.phase`, `flow_phase_*`); it is unrelated to a Step's `prep`/`cook` Phase.
_Avoid_: Stage, page, screen number

**Cooking Progress**:
The Steps a user has marked done while cooking a recipe (`CookingProgressStore`), together with the servings and Selection they were marked under. Kept in memory per recipe, written to SharedPreferences on every change (so it survives process death even after the flow was left) and mirrored into the flow's saved state (saved state wins over stale memory on restore). Marked Steps are reset on any change to servings or the Selection, since the Steps themselves change, and by Stop cooking; the servings and Selection are kept.
_Avoid_: Done state, checklist

**In-progress recipe**:
A recipe with at least one StepTimer running or paused, or at least one Step marked done (Cooking Progress), and not every Step done (that is a finished recipe: the success panel). Leaving Flow Phase 3 backwards asks what to do while the recipe is in progress (Stop cooking, Keep cooking, Go to recipes); Stop cooking cancels its timers and clears its Cooking Progress. The Recipes tab lists every in-progress recipe in a "Cooking now" card; Continue opens Flow Phase 3 with the servings and Selection the Steps were marked under (`CookingSessions`).
A recipe whose last Step gets marked done is finished and leaves progress even if a StepTimer is still running; that timer keeps running and alerting, only the card is hidden.
_Avoid_: Active recipe, running recipe

**Cooking now card**:
The Recipes tab card for an In-progress recipe: "Cooking now" heading, Planned-card look (title, servings and picks, Continue). Continue opens Flow Phase 3 directly. Disappears when the recipe is finished or Stop cooking is chosen. Swiping it sideways past about 40% of its width (or the TalkBack action "Stop cooking") is Stop cooking, with a 5 s "Undo" snackbar.
_Avoid_: Resume card

**Stop cooking**:
The destructive answer of the leave-cooking dialog, also a swipe on the Cooking now card: cancels all of the recipe's StepTimers, whether running, paused or paused-from-notification (notifications removed, TimerService stops when none remain) and clears its Cooking Progress; servings and Selection are kept. The other answers are Keep cooking (default, also the dismiss action) and Go to recipes (keeps timers and marked Steps). After a swipe, Undo restores the done Steps (`StoppedCooking`) but not the cancelled StepTimers.
_Avoid_: Reset, abandon

**Active Step**:
The first Step in display order that is not yet done (`CookingView.activeId`). It gets the highlighted card and the dark Mark as done button on Flow Phase 3; when none is left, the success panel shows.
_Avoid_: Current step, next step

**Link**:
An ingredient reference embedded in a step's body text, encoded as `{ingredientId:shown text}`. Renders in the step text as the shown word followed by the ingredient's scaled amount in parentheses ("garlic (3 cloves)", following servings and units; a blank amount shows the word only), with an orange underline under both. Only a Link whose ingredient has tips is tappable: it shows a lightbulb after the amount and opens a "Tips" tooltip with the tips list (no amount, note or tabs). Other Links are underlined with their amount and do nothing on tap.
_Avoid_: Reference, tag, mention

**Needs**:
A list of prep step IDs that must be completed before a given cook step. Displayed as a dependency warning when the referenced prep step is not yet done.
_Avoid_: Dependency, prerequisite

### Variations and Methods

**Variation**:
An optional modification to a recipe. Two kinds: a **swap** replaces one set of ingredients with another (e.g. beef mince vs. plant-based), and an **add-in** adds extra optional ingredients (e.g. extra chocolate).
_Avoid_: Option, customisation, modification

**Method**:
A cooking-technique choice that determines which steps are shown and what timers apply (e.g. hob vs. oven vs. microwave). Flow Phase 2 is skipped when no Method offers more than one option (`hasSelectableMethod`); Flow Phase 1 then starts cooking directly.
_Avoid_: Technique, cooking mode, appliance mode

**Side**:
A self-contained mini-recipe (a label, ingredients, optional Methods and prep and cook Steps) offered with a main recipe, such as rice with a curry. Written only by the Liberty import style (at most 3 per recipe, about 6 ingredients and 2 prep plus 2 cook steps each); Faithful keeps only sides the source lists. Ticked in Flow Phase 1 (`Selection.sides`), where "No sides" unticks all of them; an unticked side contributes nothing. A ticked side scales with the main's servings, its ingredients join the shopping entries, its steps join the cook phases after the main's (titles prefixed with the side label) and its own Methods are chosen inline in Phase 1. Its ids are namespaced `s_<sideId>_<id>` (side ids are letters and digits only). Not a Variation (which modifies the main) and its Methods and conditions never see the main's.
_Avoid_: Accompaniment, extra, add-in

**Step plan**:
The count of prep and cook Steps the current servings and Selection lead to (`StepPlan`), shown on Flow Phase 2.
_Avoid_: Step count, summary

**Condition**:
A visibility rule attached to an ingredient or step. Expressed as a string or list of clauses (`key=value`, `key!=value`, `add:id`, `!add:id`) that are AND'd together. Exactly one among steps sharing an id is active for any given selection.
_Avoid_: Rule, filter, visibility flag

### Appliances

**Appliance**:
A piece of kitchen equipment the user owns, picked from a catalogue or added by the user.
_Avoid_: Tool, gadget, equipment

**Back banner**:
The pinned full-width bar (48dp or more, arrow plus label, whole bar tappable) at the top of a tab screen (Recipes, Shopping list, Settings) when a Back origin sits under that tab on the back stack. Tapping it, or device Back, pops to the origin with its state intact (draft text, flow selection). Not shown on other pushed screens such as Your kitchen. Tapping a different tab abandons the origin and its state; tapping the shown tab does nothing. Derived from the previous back-stack entry's route (`backOriginFor`), no nav args.
_Avoid_: Breadcrumb, return bar

**Back origin**:
A non-tab screen a tab was opened from, named by the Back banner: Add recipe ("Back to recipe creator", de "Zurück zum Rezept-Assistenten") or the recipe flow's Phase 1 ("Back to recipe", de "Zurück zum Rezept"). Reached by Add recipe's pencil (Settings) and the flow's "View list" (Shopping list, which no longer pops the flow). Back from Your kitchen is a real pop to Settings, so with Add recipe underneath Settings still shows its banner. A new origin is one `when` branch in `backOriginFor`.

**Recipe creator (Settings section)**:
The Settings group, after Language, Units and Spices, holding what Add recipe depends on: Your kitchen (with the kitchen mode) and the Recipe reader. Arriving from Add recipe's pencil scrolls to it.

**Your kitchen**:
The Settings sub-screen where the user ticks their Appliances: a searchable checklist of the 28 catalogue Appliances, with "n selected", where a name that matches nothing can be added as the user's own ("Added by you", kept in the list even when unticked). Every tap is saved at once; first-run defaults are Hob, Oven and Microwave.
_Avoid_: Kitchen screen, equipment list

**Kitchen mode**:
Strict, Medium or Free for all: a setting that instructs recipe creation (the Pipeline) how tightly to limit the Methods it generates to the user's Appliances. Has no effect on cooking an existing Recipe.
_Avoid_: Appliance mode, filter mode

**Settings preview**:
The cream panel at the bottom of Settings: Chilli con carne at 4 servings with beef mince, stock, ground cumin, dried oregano and the oven temperature, formatted live with the current Units and Spices settings (`settingsPreview`).
_Avoid_: Sample, demo

**Spices display**:
The Settings choice of showing spice and dried-herb amounts as Spoons (tsp/tbsp) or Grams (converted with per-spice densities, 1 tbsp = 3 tsp). Applies only to ingredients in the Spices Aisle; German shows a decimal comma.
_Avoid_: Spice mode, spice units

### AI Import

**Pipeline**:
The four-pass on-device AI process that converts pasted recipe text into a structured Recipe (`RecipePipeline`): (1) reading ingredients and metadata, (2) splitting preparation from cooking, with Methods (filtered by the kitchen rule, both in the prompt and in code: options for a catalogue appliance the cook lacks are dropped with their steps), (3) linking ingredients to steps (`link()` over each step body, no model, then a tiny model pass for `needs`), (4) setting timers (optional: steps with no meaningful wait get none) and notes. The text is trimmed first (blog preamble and comments dropped, capped at about 6,000 characters; a cut adds "This recipe is long. Only the first part was read." to the notes) and each pass asks for 1,200 to 2,000 tokens. The merged result goes through Normalize.
_Avoid_: Conversion, AI flow, import process

**Normalize**:
The validation and sanitisation step applied to AI pipeline output before it becomes a user recipe. Strips invalid conditions and variation references, validates step durations, and auto-generates missing IDs.
_Avoid_: Validate, clean, sanitise

**Pipeline stage**:
One of the four passes as the Working stage and the notification name it (`PipelineStage`): Reading ingredients, Splitting preparation from cooking, Linking ingredients to steps, Setting timers. Shown with an orange check (done), orange ring (current) or grey ring (pending) (`stageMarks`).
_Avoid_: Step, phase (reserved for prep and cook)

**Repair retry**:
The one extra attempt a Pipeline pass gets when its reply is not valid JSON: the model is asked to "Return only valid JSON matching this shape" with the broken output attached. A second invalid reply ends the run in the error stage. `PipelineResult.Done.repairs` counts them; the fixture target is none for 90% of recipes.
_Avoid_: Retry, fallback

**Error stage**:
What Add recipe shows when the Pipeline fails: "We couldn't read that recipe on this phone. Try a shorter or cleaner version.", with a button back to the paste stage and the text kept. Also shown when a run was interrupted by process death twice.
_Avoid_: Failure screen

**Import request**:
What one conversion works from (`ImportRequest`): the pasted text, the app language, the cook's Appliances and the Kitchen mode. It is saved with the Import job so a resumed run reads the same kitchen.
_Avoid_: Conversion input

**Import job**:
The one running Pipeline conversion (`ImportJob`), owned by the app and not by the Add recipe screen, so it keeps going when the screen is left. Its state is Idle, Working (a Pipeline stage), Ready (the recipe is in the Add recipe draft) or Failed. It runs in a foreground service (`ImportService`) that shows the current stage, then "Recipe ready to review", and it is saved so that a process death resumes it once from pass 1 (a second death shows the Error stage). Cancel stops it and returns to the paste stage. Leaving Add recipe drops a finished recipe only if the review was shown; one that was never shown stays for the "Recipe ready to review" notification.
_Avoid_: Import task, conversion service

**Fixture set**:
The 22 recipe texts (English and German: blog-style, terse, imperial, optional ingredients, appliances the cook may not own) in `app/src/androidTest/assets/fixtures`. A JVM test checks the set and its trimming; `PipelineFixtureDeviceTest` runs it through the real model on a phone and measures repairs and time.
_Avoid_: Test data, samples

**RecipeReader**:
The interface abstracts over the two on-device AI engines (GemmaReader and GeminiNanoReader): `status()`, `download()` (a flow of `DownloadProgress`) and `generate()` (a flow of tokens). `ReaderSelector` picks one at runtime: Gemini Nano (`GeminiNanoReader`, ML Kit GenAI Prompt API) when ML Kit reports it Available or Downloadable, else Gemma (Unavailable, Downloading, or a failed check). Both generate at the shared `READER_TEMPERATURE` 0.2.
_Avoid_: AI engine, model, backend

**GemmaReader**:
The RecipeReader that runs the Reader model through LiteRT-LM. Holds one engine per app lifetime: loaded on first use (GPU first, CPU as fallback, off the main thread), closed after 60 s idle or under real memory pressure (not when the app merely goes to the background), and waits (up to 5 minutes, then fails) before a pass while the phone's thermal status is severe. One generation runs at a time.
_Avoid_: Gemma engine, LLM

**GeminiNanoReader**:
The RecipeReader that runs Gemini Nano through the ML Kit GenAI Prompt API (Pixel 9 and other supported phones). The system (AICore) owns the model, so there is nothing to load, unload, cool down, cancel or remove. All ML Kit calls go through `NanoClient`; the real one is `MlKitNanoClient`, whose mappings (`FeatureStatus`, `DownloadStatus`, BUSY) are pure, JVM-tested functions. Requests ask for at most 4,096 output tokens (ML Kit's limit; the Pipeline never asks for more than 2,000).
_Avoid_: Nano engine

**NanoClient**:
The seam between GeminiNanoReader and ML Kit: `availability()` (Available, Downloadable, Downloading, Unavailable), `download()` and `generate()`. Tests use a fake.
_Avoid_: Nano adapter

**BUSY wait-and-retry**:
Gemini Nano has a per-app quota; when it is hit, ML Kit fails with `ErrorCode.BUSY` (`NanoBusyException`). Before any token has been emitted, GeminiNanoReader waits (ML Kit's retry hint, else 2 s) and retries once; a second BUSY, or BUSY after tokens, reaches the caller because a retry would repeat tokens.
_Avoid_: Quota handling

**ReaderEngine**:
Which RecipeReader runs on this phone: Nano or Gemma. Chosen by `ReaderSelector` (`select()` returns the engine with its reader). Settings differs per engine in one place (`ReaderEngine.ui()`): Gemini Nano has no Cancel, Remove or "Download on Wi-Fi only" and no model size.
_Avoid_: Backend, provider

**ReaderState** and **ReaderStateSource**:
`ReaderState` is the ReaderEngine plus the ReaderStatus the UI shows. `ReaderStateSource` supplies it to Settings and Add recipe and routes Settings' Download, Cancel and Remove to the engine in use. Gemma: the model file's status. Nano: ML Kit's status, polled again on every collection; a Nano download runs in an app-lifetime scope and its progress overrides the polled status (a failure until the next collection, a finished download until the next poll).
_Avoid_: Reader view model

**DownloadProgress**:
What `RecipeReader.download()` reports: Running (percent, possibly waiting for a connection; no percent for Gemini Nano, shown as an indeterminate bar), then Complete or Failed (carrying a `DownloadFailure`). It is `ReaderStatus` reduced to the download's own steps.
_Avoid_: Download state

**Reader model**:
The model the active ReaderEngine runs. On Gemma phones, the Gemma 4 E2B file (`gemma-4-E2B-it-web.litertlm`, `ReaderModel`). It is downloaded once by a unique WorkManager task (Wi-Fi only by default, resumable, SHA-256 checked, needs 3.5 GB free) into `filesDir/models/`. The file only exists under its final name once verified; until then the bytes sit in a `.part` file. On Gemini Nano phones it is the system's Gemini Nano, fetched by ML Kit when Download is tapped; Simmer holds no file.
_Avoid_: AI model file, weights

**DownloadWork**:
What the unique WorkManager download task is doing, reduced to what the status needs: Idle, Queued (waiting for a connection or between retries), Running, or Failed with a `DownloadFailure`. Mapped from WorkManager by `toDownloadWork`.
_Avoid_: Download state

**DownloadFailure**:
Why a download ended for good without a model: ChecksumMismatch (file deleted), NotEnoughStorage (also when the disk fills mid-download; partial kept), ServerError (HTTP 4xx other than 408/429), Unknown. Network drops and 5xx are not failures; the task retries them.
_Avoid_: Error code

**ReaderStatus**:
The state of the Reader model that Settings shows, derived by `readerStatus`: Not downloaded, Downloading (percent, possibly waiting for a connection; no percent for Gemini Nano), Ready, Not enough storage, Not supported, or Failed (carrying a `DownloadFailure`: checksum mismatch, not enough storage, server error, unknown). Not persisted. Gemma: recomputed from the file, the download task and free storage. Gemini Nano: polled from ML Kit, with a running Nano download (or its failure) held in `ReaderStateSource` meanwhile.
_Avoid_: Model state, download state

### Shopping

**Shopping List**:
The persisted list of ingredients the user intends to buy. Has two distinct sections: recipe items (grouped by aisle, each carrying the source recipe's title as a note) and a separate manual section for items the user adds by hand. Populated from Planned Recipes via "Add to list"; checked-off items move to an "In your basket" section. The header three-dot menu offers "Clear ticked off" and "Clear all" (each hidden when there is nothing to clear); both show a ~5s "Cleared N items · Undo" snackbar, and Undo restores items, their `checked` state and Planned choices from a repository snapshot (`ShoppingSnapshot`, `restore`).
_Avoid_: Grocery list, cart

**Shopping Item**:
One entry on the Shopping List, either sourced from a recipe ingredient (id `<recipeId>-<ingredientId>`, with the recipe title as note) or a Manual Item (id `manual-<uuid>`, no recipe, aisle or note). Persisted as `ShoppingItemEntity`; `ShoppingEntry` is the not-yet-persisted form derived from a recipe's resolved ingredients. Re-adding a recipe keeps the `checked` state of items that remain.
_Avoid_: Grocery item, product

**Aisle group**:
The unchecked recipe items of one Aisle on the Shopping List, shown under the aisle name (`AisleGroup`). `ShoppingListView` is the arrangement of the whole tab: Aisle groups, Manual Items and In your basket.
_Avoid_: Section (reserved for Manual Items and the basket)

**In your basket**:
The collapsed Shopping List section holding every checked Shopping Item, recipe or manual. Unchecking an item returns it to its aisle or the manual section. Only unchecked items count toward the Shopping list tab badge, which is hidden at 0.
_Avoid_: Done, bought, cart

**Grace period**:
The 3-second UI-only delay after ticking a Shopping Item: the row stays in its aisle or Other items card, struck through, then fades out into In your basket. The tick is saved and the tab badge updated at once; only the row's move is delayed. Unticking during it is the undo. Each item has its own countdown, and leaving the tab or backgrounding the app ends all of them. Held by `GracePeriod`.
_Avoid_: Undo window, debounce

**Manual Item**:
A shopping list entry created directly by the user rather than sourced from a recipe. Displayed in its own section, separate from recipe items grouped by aisle.
_Avoid_: Custom item, free item
