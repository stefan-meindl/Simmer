# Why the StepTimer notification is not on the always-on display (AOD)

Branch `feat/issue-37-timer-notification` (PR #69, issue #37). Researched 2026-10-14.

## Outcome (verified on a Pixel 7 Pro, Android 17 / API 37.1)

**Root cause: the `timers_running` channel was IMPORTANCE_LOW (silent).** Promotion worked all along: `dumpsys notification --noredact` showed `PROMOTED_ONGOING`, `ProgressStyle`, the extra, `vis=PUBLIC`, and Live updates enabled. With Settings > Notifications > Notifications on lock screen > "Show silent conversations and notifications" off (`secure lock_screen_show_silent_notifications=0`), the lockscreen/AOD hid the notification. Turning that setting on made the timer show on AOD (confirmed by the user). Fix: new channel `timers_running_v2` at IMPORTANCE_DEFAULT with no sound or vibration (channel importance cannot be raised after creation, so a new id; the old channel is deleted). With the setting off again, the timer shows on AOD (confirmed on the device). The Android 36.0 explanation below (section 3) does **not** apply to this device; it stays as background for API 36.0 devices.

Background sections below were written before the device was known.

Labels: **[CONFIRMED]** = read in a primary source (URL given). **[INFERRED]** = my reasoning from confirmed facts. **[UNVERIFIED]** = no primary source found.

AOSP source URLs below are `https://android.googlesource.com/platform/frameworks/base/+/refs/heads/<branch>/<path>`. Branches: `android16-release` = API 36.0 (QPR0/QPR1), `android16-qpr2-release` = API 36.1, `android17-release` = API 37.

## 1. What our code does (read from the repo)

- `TimerNotifications.ongoing()` builds with `NotificationCompat` (channel `timers_running`, IMPORTANCE_LOW, ongoing, title set, no custom views, `VISIBILITY_PUBLIC`, `CATEGORY_PROGRESS`, `setProgress`, chronometer countdown with `setWhen(endAtMs)`, actions). On `SDK_INT >= 36` it wraps with `Notification.Builder.recoverBuilder`, sets `Notification.ProgressStyle`, adds the extra `android.requestPromotedOngoing=true` by hand and sets `setShortCriticalText(clock)`. It does **not** call `setColorized`.
- Manifest declares `POST_NOTIFICATIONS` and `POST_PROMOTED_NOTIFICATIONS`.
- `TimerService` posts one notification per timer (`manager.notify`), and one of them doubles as the foreground-service notification (`startForeground`, type `specialUse`). `targetSdk=37`, `compileSdk=37`.
- `docs/manual-testing.md` already says AOD "cannot be verified on the emulator" and expects the Live Update on AOD on Android 16+.

## 2. Promotion criteria (what makes a notification a Live Update)

- **[CONFIRMED]** Docs list: style must be standard, BigText, Call, Progress or Metric; manifest permission `POST_PROMOTED_NOTIFICATIONS`; request via `EXTRA_REQUEST_PROMOTED_ONGOING` / `setRequestPromotedOngoing`; ongoing; `contentTitle`; no custom content view; not a group summary; not `setColorized(true)`; channel not IMPORTANCE_MIN. `POST_PROMOTED_NOTIFICATIONS` is a non-runtime (install-time) permission. `canPostPromotedNotifications()` reports the user toggle; `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` opens it. `hasPromotableCharacteristics()` ignores the user toggle. Source: https://developer.android.com/develop/ui/views/notifications/live-update
- The docs page says promoted notifications show "at the top of the notification drawer and the lock screen, and as a chip in the status bar". **It never mentions AOD.** Same URL.
- Our notification meets every item on that list **[INFERRED from code review]**, so by the docs alone it should be promoted on 36.1+.

## 3. API 36.0 vs 36.1 (the most likely cause)

- **[CONFIRMED]** In `android16-release` (36.0) `Notification.java` has no `EXTRA_REQUEST_PROMOTED_ONGOING` and no `setRequestPromotedOngoing`. Its `hasPromotableCharacteristics()` is:
  `!ongoing || groupSummary || customViews || !title -> false; ongoing CallStyle -> true; else return isColorizedRequested() && hasPromotableStyle();`
  i.e. on 36.0 a non-call notification is promotable **only if `setColorized(true)` is set** (plus ProgressStyle etc.). The hand-set extra is ignored.
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/Notification.java (around line 3297)
- **[CONFIRMED]** `android16-qpr2-release` (36.1) adds `EXTRA_REQUEST_PROMOTED_ONGOING` and `Builder.setRequestPromotedOngoing` (flag `opt_in_rich_ongoing`, "v0.2 promotion logic: explicit opt-in and no colorization"). With `Flags.uiRichOngoing()` on, promotable = `isRequestPromotedOngoing && ongoing && hasTitle && promotable style && !groupSummary && !customViews && !colorizedRequested`. If that flag is off the old colorized rule still applies.
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/core/java/android/app/Notification.java (lines ~3428, 5652)
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/core/java/android/app/notification.aconfig (flag description)
- **[CONFIRMED]** `android17-release` (37) has the opt-in rule (`isRequestPromotedOngoing() && ... && !isColorizedRequested()`).
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/java/android/app/Notification.java (line ~3738)
- **[CONFIRMED]** QPR2 is the first minor SDK release; check it with `Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1`. https://developer.android.com/blog/posts/android-16-qpr-2-is-released
- The comment in our code ("the extra works on 36") is therefore **wrong for 36.0**. The code gate is `SDK_INT >= 36`, which includes 36.0.
- **Setting the extra by hand works where the API exists.** `setRequestPromotedOngoing` is only `getExtras().putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, v)` and `isRequestPromotedOngoing()` reads that key (qpr2 Notification.java lines 5652-5654, 8400-8402) **[CONFIRMED]**. So on 36.1+/37 the manual extra is equivalent, but you can call the real method on `Notification.Builder` once `compileSdk` is 37 (it is); `NotificationCompat` may also expose it **[UNVERIFIED]**.
- **recoverBuilder** copies the notification's extras into the new builder (`new Builder(builderContext, n)`; qpr2 Notification.java ~7514-7534) and `addExtras` merges on top, so the extra survives **[CONFIRMED that extras are carried; INFERRED that nothing strips ours]**. The system re-derives `FLAG_PROMOTED_ONGOING` itself; apps cannot set it (NotificationManagerService clears it in fixNotification).
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/services/core/java/com/android/server/notification/NotificationManagerService.java (around lines 9129, 9249-9262)

## 4. System-side gates that can silently stop promotion

- **[CONFIRMED]** NMS grants `FLAG_PROMOTED_ONGOING` only if the notification is promotable, the channel importance is above MIN, and the app holds `POST_PROMOTED_NOTIFICATIONS` (36.1+: permission check; 36.0: `PreferencesHelper.canBePromoted`, the user toggle). The user toggle is Settings > Apps > Simmer > Notifications > Live updates (Pixel help: https://support.google.com/pixelphone/answer/16785344). A user who turned it off, or who demoted/dismissed the live update, gets a plain notification. Docs: "Users can temporarily dismiss or demote a live update notification to a standard notification." https://developer.android.com/design/ui/mobile/guides/home-screen/live-updates
- Dismissing the notification card (swipe) is a possible cause of "not shown" for an ongoing FGS notification only if the user demoted it **[INFERRED]**.

## 5. What AOD renders

- **[CONFIRMED]** AOSP SystemUI has a dedicated promoted-notification-on-AOD path: `AODPromotedNotification.kt`, `AODPromotedNotificationInteractor`, gated by resource `config_showPromotedNotificationsOnAOD`, default `true` in both qpr2 and 37. It logs "not displaying promoted notif with ineligible style on AOD" when the style is not Base/BigText/Call/Progress/Metric-like (`Style.Ineligible`).
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/packages/SystemUI/src/com/android/systemui/statusbar/notification/promoted/ShowPromotedNotificationOnAOD.kt
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/packages/SystemUI/src/com/android/systemui/statusbar/notification/promoted/AODPromotedNotification.kt
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/packages/SystemUI/res/values/config.xml (line 1125)
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/packages/SystemUI/res/values/config.xml (line 1144)
- **[CONFIRMED]** `PromotedNotificationsInteractor.aodPromotedNotification` picks **exactly one** notification: the top status-bar-chip promoted notification, else the first AOD-eligible promoted top-level notification. With two timers running only one appears on AOD.
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/packages/SystemUI/src/com/android/systemui/statusbar/notification/promoted/domain/interactor/PromotedNotificationsInteractor.kt (lines ~204-240)
- **[CONFIRMED]** `AODPromotedNotificationInteractor` shows the private version only when the keyguard has trust (`hasTrust`), no screen-share protection, and no biometric unlock in progress; otherwise it shows the **public version**. If the app has no `publicVersion`, SystemUI builds a default public version (`Style.Base`, only icon/app name/time, no title or progress). With a normal locked screen and no trust agent, AOD is therefore expected to show the redacted card, not the progress bar. `VISIBILITY_PUBLIC` only avoids redaction when the redaction type is NONE (the user's lockscreen setting decides).
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/packages/SystemUI/src/com/android/systemui/statusbar/notification/promoted/domain/interactor/AODPromotedNotificationInteractor.kt
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/packages/SystemUI/src/com/android/systemui/statusbar/notification/promoted/PromotedNotificationContentExtractor.kt (lines ~140-205)
- **[CONFIRMED]** The Metric style page shows an AOD rendering of a promoted notification (Figure 2), which confirms AOD cards exist as a design concept. Text does not describe it. https://developer.android.com/develop/ui/views/notifications/metric-style
- The stock `config_showPromotedNotificationsOnAOD` is an overlayable resource, so OEMs (Pixel, Samsung) may differ. **[INFERRED]** Whether the shipped Pixel build enables the `ui_rich_ongoing` flag and this path is **[UNVERIFIED]** (server-side aconfig). I found no primary Google page that documents AOD behaviour for Live Updates (searched developer.android.com, support.google.com).
- Plain (non-promoted) notifications on AOD: Android stock shows only status icons, if anything; Pixel help only says "on some phones" the screen can light up for notifications and does not describe AOD notifications in detail. https://support.google.com/pixelphone/answer/6111294 **[UNVERIFIED for AOD content]**. I found no primary source for a user setting named "Show notifications on AOD"; Pixel's lock-screen notification setting is described at https://support.google.com/pixelphone/answer/16520562 (mentions AOD only for wallpaper/clock on Pixel 10 Pro).

## 6. Samsung One UI 8 (Now Bar)

- **[CONFIRMED]** Samsung help: live notifications appear on the lock screen, status bar and top of the panel; Now bar needs a screen lock; settings path Lock screen and AOD > Now bar > View more, per-app switches; availability varies by model/region/version. The page does not say whether third-party apps are supported or whether live notifications show on AOD. https://www.samsung.com/us/support/answer/ANS10004605/
- No Samsung developer documentation on third-party Live Updates / Now Bar eligibility was found (searched developer.samsung.com). Community and press posts say One UI 8 maps Android 16 Live Updates into Now Bar behind a toggle and that support for arbitrary third-party apps was partial; these are not primary sources. **[UNVERIFIED]**
- One UI is a heavily modified SystemUI; it will not use AOSP's `AODPromotedNotification`. AOD content, if any, is Samsung's own (Now Bar on AOD is for apps Samsung whitelists). **[INFERRED]** Expect a Samsung device to need the per-app Now bar / "Live notifications" switch and may never show a generic app on AOD.

## 7. Ranked likely causes

1. **Device is on API 36.0 (Android 16 QPR0/QPR1)**: the hand-set extra is ignored, the notification is not promotable without `setColorized(true)`, so it is a plain notification (no chip, no lockscreen card, no AOD card). Strong cause if the phone is not on QPR2 or later. [CONFIRMED mechanism; device version unknown]
2. **User toggle off or demoted**: Settings > Apps > Simmer > Notifications > Live updates. Cheap to check. [CONFIRMED mechanism]
3. **AOD shows the redacted public version, or nothing, on a locked device**: AOSP shows private content only with trust. Our notification has no `publicVersion`, so AOD would get a generic card. Also only one promoted notification is chosen when two timers run. [CONFIRMED in AOSP; actual device behaviour INFERRED]
4. **OEM/device does not render promoted notifications on AOD** (Samsung: own Now Bar rules; Pixel: depends on shipped flags/overlay). [UNVERIFIED]
5. **Always-on display setting off / device without AOD / "Show notifications" style options** on the device. [UNVERIFIED]
6. Less likely: foreground-service notification dedupe (the FGS notification is one of the per-timer notifications, so it is promotable too; the placeholder is not promoted and has no title issue) [INFERRED]; channel importance LOW is fine (only MIN blocks) [CONFIRMED].

## 8. Fixes

1. Version-gate the promotion request:
   - `SDK_INT_FULL >= BAKLAVA_1` (36.1+) or `SDK_INT >= 37`: `setRequestPromotedOngoing(true)` (or the extra), **no** `setColorized`.
   - 36.0 only: `setColorized(true)` plus ProgressStyle (no extra needed). Do **not** set colorized on 36.1+, because it makes the notification non-promotable.
   Fix the comment in `TimerNotifications.kt` ("works on 36").
2. Add an app-defined `setPublicVersion(...)` (title, text, progress) so a redacted AOD/lockscreen card still has useful content, or document that a locked AOD shows a generic card.
3. Optionally check `NotificationManager.canPostPromotedNotifications()` and show a one-time hint with `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` if false.
4. Show at most one promoted timer (e.g. only the nearest-ending) if AOD must show a specific one; AOSP shows just one anyway.
5. Update `docs/manual-testing.md` AOD bullet: state the minimum (API 36.1 / Android 16 QPR2 or Android 17 on a Pixel) and that a secure lock shows a redacted AOD card.

## 9. How to verify (adb)

```
adb shell getprop ro.build.version.sdk ro.build.version.release_or_codename
adb shell getprop ro.build.version.sdk_minor      # 1 on QPR2+ (property name is an assumption, [UNVERIFIED])
adb shell dumpsys notification --noredact | grep -A40 "pkg=com.simmer.app"
```
In the record look for `flags=0x...` containing `0x00040000` (FLAG_PROMOTED_ONGOING, https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/core/java/android/app/Notification.java line 797), and in extras `android.requestPromotedOngoing=true`, `android.template=...ProgressStyle`. No `0x00040000` means the system did not promote: check API level, toggle (`adb shell cmd notification` has no promoted verb that I could confirm; use Settings), colorized extra, channel importance.
Also: `adb shell cmd appops get com.simmer.app POST_PROMOTED_NOTIFICATIONS` (op name from NMS import `OP_POST_PROMOTED_NOTIFICATIONS`, CLI form unverified), `adb logcat -s NotificationService SystemUI | grep -i promot`, and the SystemUI log tag `PromotedNotif` / "not displaying promoted notif with ineligible style on AOD" in AODPromotedNotification. Then lock the device with a trusted state (Smart Lock / unlocked trust) vs untrusted to compare private vs public AOD content. Test on a Pixel on QPR2+/Android 17 first; then Samsung with Now bar switched on for the app.
