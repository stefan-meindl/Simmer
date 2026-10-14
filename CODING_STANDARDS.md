# Coding standards

Primary reference: `Design/Android build prompt.md` (BP). The rules below are the ones reviewers check on every diff.

## Rules

- **Strings** (BP §1): every user-facing string lives in `values/strings.xml` and `values-de/strings.xml`. Anything with a count is a `<plurals>`, never `%d items` in a plain string.
- **Touch targets** (BP §3): interactive elements are at least 44dp, list rows at least 56dp.
- **Outlines** (BP §3): 2dp, and 3dp for selected or emphasised states, without layout shift.
- **Accent** (BP §3): orange is the only accent, labels on orange are black. One exception: the swipe-to-remove background is destructive red (`SwipeDeleteRed`), the only red in the app; other destructive UI stays neutral or orange.
- **Accessibility** (BP §8): radio and checkbox roles with checked state, labelled icon buttons, live region on the servings number and finished timers.
- **State restore** (BP §8): flow phase, servings, selection, done steps, timers, editor drafts and add-recipe text survive configuration change and process death (`SavedStateHandle` or Room).
- **Theme and language** (BP §8): every screen works in light and OLED dark, in English and German; long German strings wrap.
- **Domain terms**: names match `GLOSSARY.md`. A new concept gets a glossary entry in the same change.
- **Tests** (BP §8): logic gets a unit test. Behaviour that depends on Android (regex, Room, resources) gets an instrumented test in `app/src/androidTest`. Where the platform can't be driven from a test (real thermal status, process kill), extract the decision into a JVM-tested function and add a `docs/manual-testing.md` checklist item for the real service.
- **Lifecycle and concurrency**: check, for every lock, timer and callback, (a) what runs or waits while a lock is held (collectors, delays, I/O), (b) which callback levels or signals fire in normal use (`onTrimMemory` `UI_HIDDEN` fires on every app switch) and that the handler is gated accordingly, (c) that shared counters and flags are atomic or guarded by one lock, (d) that waits are bounded and cancellable, and (e) that `Error`s (OOM) and cancellation propagate.
