# Simmer

Simmer is an Android recipe app. Browse built-in and imported recipes, set servings and pick variations, cook step by step with timers, and keep a shopping list. See the [project site](https://stefan-meindl.github.io/Simmer/).

## Screenshots

### Recipes

Plan a recipe and start cooking: it shows up under **Cooking now** and **Planned**.

<img src="docs/screenshots/recipes.png" alt="Recipes tab with Cooking now and Planned cards" width="280">

### Recipe detail

Set servings and pick swaps and add-ins. Amounts and steps follow your choices.

<img src="docs/screenshots/recipe-detail.png" alt="Recipe detail with servings and variations" width="280">

### Cooking

Work through the steps and start a timer on any step that needs one.

<img src="docs/screenshots/cooking-step.png" alt="Cooking step with a running timer" width="280">

### Shopping list

Planned recipes add their ingredients, grouped by aisle.

<img src="docs/screenshots/shopping-list.png" alt="Shopping list grouped by aisle" width="280">

### Add a recipe

Paste a recipe into a chatbot, paste its reply back, and review the result before saving.

<img src="docs/screenshots/import.png" alt="Add a recipe with a chatbot" width="280">
<img src="docs/screenshots/recipe-created.png" alt="The imported recipe, ready to save" width="280">

## Install

Simmer is installed by sideloading the APK.

1. Download `simmer-vX.Y.Z.apk` from the [releases page](https://github.com/stefan-meindl/Simmer/releases).
2. Allow "install unknown apps" for the browser or file manager you used, then open the APK.

Requires Android 8.0 or newer (minSdk 26).

If the installer refuses a final release over a release candidate, uninstall the candidate first. See [docs/releasing.md](docs/releasing.md).

## Development

- [Manual testing](docs/manual-testing.md)
- [Releasing](docs/releasing.md)
- [Coding standards](CODING_STANDARDS.md)
- [Glossary](GLOSSARY.md)

## License

[MIT](LICENSE)
