# Coding standards

Primary reference: `Design/Android build prompt.md` (BP). The rules below are the ones reviewers check on every diff.

## Rules

- **Strings** (BP §1): every user-facing string lives in `values/strings.xml` and `values-de/strings.xml`. Anything with a count is a `<plurals>`, never `%d items` in a plain string.
- **Touch targets** (BP §3): interactive elements are at least 44dp, list rows at least 56dp.
- **Outlines** (BP §3): 2dp, and 3dp for selected or emphasised states, without layout shift.
- **Accent** (BP §3): orange is the only accent, labels on orange are black.
- **Accessibility** (BP §8): radio and checkbox roles with checked state, labelled icon buttons, live region on the servings number and finished timers.
- **State restore** (BP §8): flow phase, servings, selection, done steps, timers, editor drafts and add-recipe text survive configuration change and process death (`SavedStateHandle` or Room).
- **Theme and language** (BP §8): every screen works in light and OLED dark, in English and German; long German strings wrap.
- **Domain terms**: names match `GLOSSARY.md`. A new concept gets a glossary entry in the same change.
- **Tests** (BP §8): logic gets a unit test. Behaviour that depends on Android (regex, Room, resources) gets an instrumented test in `app/src/androidTest`.
