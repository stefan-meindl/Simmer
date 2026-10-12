# Simmer

The Simmer recipe app. Users browse built-in and imported recipes, configure servings and variations, cook through step-by-step instructions with timers, and maintain a shopping list.

## Language

### Recipes

**Recipe**:
A named dish with ingredients, steps, variations, and methods. The central entity the app revolves around. Immutable while cooking; editable outside the flow.
_Avoid_: Dish, meal

**Built-in Recipe**:
A recipe shipped as a JSON asset with the app. Can be locally overridden via editing (marking it `edited`), but the original is always restorable.
_Avoid_: System recipe, default recipe

**User Recipe**:
A recipe created by importing text through the AI pipeline. Marked `mine = true`. Can be edited or deleted.
_Avoid_: Custom recipe, imported recipe, my recipe

**Planned Recipe**:
A recipe the user has added to the Shopping List. Stored with its chosen servings and selection so the user can return to the cooking flow.
_Avoid_: Saved recipe, queued recipe

**Planned Choice**:
The servings and Selection saved with a Planned Recipe when the user taps "Add to list" (`PlannedChoice`). The flow shows "On your list" only while the current servings and Selection equal it; an add-in toggled off counts as never toggled.
_Avoid_: Saved choice, plan

**Base**:
The number of servings a recipe's ingredient amounts are originally written for. All displayed quantities are scaled relative to this number.
_Avoid_: Default servings, original servings

**Selection**:
The complete set of a user's current choices for a recipe: a map of method picks (`choice`) and a map of add-in toggle states (`add`). Together with the serving count, it fully determines what gets displayed.
_Avoid_: Options, preferences, config

**Recipes overview**:
The Recipes tab: search, Category tabs, Tag chips, the Planned section and the list of recipe cards that open the cooking flow. Filters AND together; the Planned section ignores them.
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
A single instruction in a recipe, belonging to exactly one phase. Has a title, body text, optional timer duration, and optional dependencies on other steps.
_Avoid_: Instruction, task

**StepTimer**:
The countdown button on a Step that has a timer duration. Cycles idle, running, paused, done. Keyed by recipe and step, so several run at once; a running StepTimer lives in the TimerService foreground service and is restored after process death from its end time.
_Avoid_: Countdown, alarm

**Phase**:
One of two ordered stages of cooking a Step belongs to: `prep` (preparation, no heat) always comes before `cook` (cooking, heat applied). Steps are sorted by phase at display time regardless of authoring order. Not to be confused with a **Flow Phase**.
_Avoid_: Stage, section

**Flow Phase**:
One of the three ordered screens of the cooking flow, shown as the progress indicator (`FLOW_PHASE_COUNT = 3`): 1 Servings, 2 Preparation, 3 Cooking. Code uses `phase` for it too (`Phase1State`, `ProgressSegment.phase`, `flow_phase_*`); it is unrelated to a Step's `prep`/`cook` Phase.
_Avoid_: Stage, page, screen number

**Cooking Progress**:
The Steps a user has marked done while cooking a recipe (`CookingProgressStore`). Kept in memory per recipe and mirrored into the flow's saved state so it survives process death (saved state wins over stale memory on restore). Reset on any change to servings or the Selection, since the Steps themselves change.
_Avoid_: Done state, checklist

**Active Step**:
The first Step in display order that is not yet done (`CookingView.activeId`). It gets the highlighted card and the dark Mark as done button on Flow Phase 3; when none is left, the success panel shows.
_Avoid_: Current step, next step

**Link**:
An ingredient reference embedded in a step's body text, encoded as `{ingredientId:shown text}`. Renders as a tappable underlined span that shows the ingredient's amount and tips.
_Avoid_: Reference, tag, mention

**Needs**:
A list of prep step IDs that must be completed before a given cook step. Displayed as a dependency warning when the referenced prep step is not yet done.
_Avoid_: Dependency, prerequisite

### Variations and Methods

**Variation**:
An optional modification to a recipe. Two kinds: a **swap** replaces one set of ingredients with another (e.g. beef mince vs. plant-based), and an **add-in** adds extra optional ingredients (e.g. extra chocolate).
_Avoid_: Option, customisation, modification

**Method**:
A cooking-technique choice that determines which steps are shown and what timers apply (e.g. hob vs. oven vs. microwave).
_Avoid_: Technique, cooking mode, appliance mode

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
The four-pass on-device AI process that converts pasted recipe text into a structured Recipe: (1) reading ingredients, (2) splitting phases, (3) linking ingredients to steps, (4) setting timers.
_Avoid_: Conversion, AI flow, import process

**Normalize**:
The validation and sanitisation step applied to AI pipeline output before it becomes a user recipe. Strips invalid conditions and variation references, validates step durations, and auto-generates missing IDs.
_Avoid_: Validate, clean, sanitise

**RecipeReader**:
The interface abstracts over the two on-device AI engines (GemmaReader and GeminiNanoReader). The app picks one at runtime based on device capability.
_Avoid_: AI engine, model, backend

### Shopping

**Shopping List**:
The persisted list of ingredients the user intends to buy. Has two distinct sections: recipe items (grouped by aisle, each carrying the source recipe's title as a note) and a separate manual section for items the user adds by hand. Populated from Planned Recipes via "Add to list"; checked-off items move to an "In your basket" section.
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

**Manual Item**:
A shopping list entry created directly by the user rather than sourced from a recipe. Displayed in its own section, separate from recipe items grouped by aisle.
_Avoid_: Custom item, free item
