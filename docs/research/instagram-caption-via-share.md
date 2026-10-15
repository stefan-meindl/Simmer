# Getting an Instagram Reel caption when the user shares it to Simmer

Issue: #85. Researched 2026-10-15. Context: Android app, no backend. A plain GET of the reel page and `/embed/captioned/` was reported to return only a generic JS shell.

Labels: **[CONFIRMED]** = read in a primary source (URL given) or reproduced by me with curl on 2026-10-15 (command described). **[INFERRED]** = my reasoning from confirmed facts. **[UNVERIFIED]** = no primary source found / could not test (no Android device or adb in this environment).

## Bottom line

- **The issue's negative result does not reproduce today.** From one desktop connection, two unauthenticated endpoints return the caption for live public reels, with no login and no token (section 3). Whether they work from a phone's mobile network IP is unverified.
- The caption is **not** in the share intent (as far as can be established, section 1). Simmer must fetch it from the shared URL.
- Everything that works is **undocumented, not an official API, and against Instagram's stated automated-collection policy** (section 5). It can break at any time. Treat it as best-effort with a paste fallback.
- The official, documented oEmbed (now tokenless since 2026-06-15) deliberately returns **no caption** (section 3.1).

## 1. What Instagram's Android share puts in the intent

- **Android's contract.** `ACTION_SEND` carries a MIME type plus `EXTRA_TEXT`, `EXTRA_SUBJECT`, `EXTRA_STREAM`; receivers read `EXTRA_TEXT` for shared URLs. [CONFIRMED] https://developer.android.com/training/sharing/receive ("When receiving a URL, make sure to get the `EXTRA_TEXT` field"). The same page does not mention `EXTRA_SUBJECT` at all.
- **What Instagram actually sends.** Instagram is closed source and publishes no spec of its outgoing share intent. I found no Meta, AOSP or issue-tracker document that lists the extras, and I could not capture an intent (no device/adb here). **[UNVERIFIED]** for: `EXTRA_TEXT` contents, `EXTRA_SUBJECT`, `EXTRA_STREAM`, `ClipData`, MIME types. Practical expectation **[INFERRED, weak]**: `text/plain` with a bare `https://www.instagram.com/reel/<id>/?igsh=...` URL, no caption. Supporting hints (secondary, not proof):
  - Instagram adds an `igsh` share-tracker query parameter to links it copies. https://github.com/brave/brave-browser/issues/35094 and https://github.com/Wikidepia/InstaFix/issues/154 (third-party trackers, which describe it as appended to copied/shared reel URLs).
  - Instagram also has `https://www.instagram.com/share/reel/<token>` style links. I could not test one (no real token); the legacy oEmbed endpoint returned 404 "No Media Match" for a made-up `/share/reel/` token, so **resolve redirects before use**. [UNVERIFIED that real share links redirect to `/reel/<id>/`.]
- **Meta's own SDK docs cover only the reverse direction** (sharing *to* Instagram Reels via `com.instagram.share.ADD_TO_REEL` with `EXTRA_STREAM`). Not relevant to receiving. https://developers.facebook.com/docs/android/sharing-to-reels-instagram
- **Recommended capture step before building:** add a debug log of `intent.action`, `type`, `extras.keySet()` with values, `clipData`, from a real device share (Share > Simmer, plus Send in DM / copy link). Spec items to log are exactly the list in the task.

### Other share/copy paths

| Path | Caption present? | Notes |
|---|---|---|
| Share > Simmer (ACTION_SEND) | Unverified, likely link only | See above |
| Copy link, then paste in Simmer | No, link only | Needs a paste/clipboard step. Android 12+ shows a "pasted from your clipboard" toast on `getPrimaryClip()`; reading only `getPrimaryClipDescription()` does not. [CONFIRMED] https://developer.android.com/develop/ui/views/touch-and-input/copy-paste |
| "Send to" in DMs | Not applicable | Goes to another Instagram user, not to an Android intent target. Simmer would not receive it. |
| Share a downloaded video file (`EXTRA_STREAM`) | No | A video file has no caption metadata; Instagram does not offer a public download of other users' reels. [UNVERIFIED] |
| Instagram "Download your information" | No | It exports the signed-in user's own account data. Not a way to read a third party's caption. [UNVERIFIED; no primary page fetched] |
| Sharesheet / Direct Share | No | Only controls which target is offered, not payload. https://developer.android.com/training/sharing/receive |

## 2. Why the issue saw only a shell

Reproduced 2026-10-15 (curl, one desktop connection, reel `CHOJBJ6pcSx`): `/reel/<id>/embed/captioned/`
- User agents `curl/8`, `okhttp/4.12.0`, bare `Mozilla/5.0`, and empty UA: HTTP 200, ~205 KB server-rendered page, contains `<div class="Caption">` with the full caption text and an embedded JSON (`edge_media_to_caption`).
- Browser-like UAs (Firefox desktop, Chrome desktop, Chrome Android): the generic ~575 KB shell with no caption, matching the issue.
- So the shell is **UA-dependent**, and the issue's UA list was all browser-like (plus Googlebot/facebookexternalhit, which I did not retest). **[INFERRED]** the cause; it can flip at any time. Also note `CGuy20UHflz` (one of the issue's shortcodes) now returns 404 "No Media Match" on every endpoint I tried, so it is probably deleted or not embeddable; the issue's "oEmbed returns No Media Match" was likely partly that.

## 3. On-device fetch options

### 3.1 Official Graph oEmbed (documented): no caption

- Since 2026-06-15 callable without token or App Review: `GET https://graph.facebook.com/v25.0/instagram_oembed?url={content-url}`. [CONFIRMED] https://developers.facebook.com/blog/post/2026/06/15/tokenless-access-to-meta-oembed-apis/ (before that: "Meta oEmbed Read", App Review, business verification: https://developers.facebook.com/docs/features-reference/meta-oembed-read)
- Documented response fields: `version`, `provider_name`, `provider_url`, `html`, `width`, `type`; no caption/title field; `author_name`, thumbnail fields were removed. [CONFIRMED] same blog post; https://developers.facebook.com/docs/graph-api/reference/instagram-oembed/
- Reproduced: `graph.facebook.com/v21.0/instagram_oembed` with no token returned HTTP 200 and the same six fields; the `html` blockquote contains the generic "View this post on Instagram" and **not** the caption text (checked for a known caption string: absent). The embed's caption would only appear after Instagram's `embed.js` renders it in a WebView.
- Intended use: embedding in sites and apps; "only meant to be used for embedding Instagram content"; 1,000 req/hour; no private/age-restricted/embeds-disabled accounts. [CONFIRMED] https://developers.facebook.com/docs/instagram/oembed . Using the data for something else (recipe extraction) is outside that stated purpose.
- Rendering that `html` in a WebView (loads `https://www.instagram.com/embed.js`) would display the caption visually, but Simmer would then need to scrape the DOM. **[UNVERIFIED]**; fragile; not tested.

### 3.2 Legacy `instagram.com/api/v1/oembed/` (undocumented): returns the caption

- `GET https://www.instagram.com/api/v1/oembed/?url=https://www.instagram.com/reel/CHOJBJ6pcSx/` returned HTTP 200 JSON with `title` = the full caption, plus `author_name`, `author_url`, `media_id`, `thumbnail_url`, `html`. Reproduced on 3 live posts/reels (`CHOJBJ6pcSx`, `CpOktRXs7Jh`, `Cr4D2VpuqsE`); the dead `CGuy20UHflz` gave 404 "No Media Match".
- Works with any UA tried (curl default, okhttp, Dalvik, Chrome Android, Firefox). 40 sequential requests in a row: all 200 (no throttling seen from this IP).
- Accepts `/reel/<id>`, `/p/<id>`, `/<user>/reel/<id>/`, `?igsh=` and `?utm_` query strings, no trailing slash. Rejects `/reels/<id>/` (400) and `/share/reel/<token>` (404).
- Long captions: 2,013 characters returned untruncated in one case.
- **No documentation** for this endpoint was found at developers.facebook.com. Not a supported API. [CONFIRMED absence in the docs I fetched; I cannot prove absence everywhere.]

### 3.3 `/embed/captioned/` server-rendered HTML (undocumented behavior)

- It is the iframe target of the official embed, so it is semi-public, but its HTML structure (`class="Caption"`, JSON blob) is not a contract. See section 2 for UA dependence.
- Contains the caption plus "View all N comments"; needs HTML entity decoding and stripping `<br />`.

### 3.4 WebView on the reel page

- Mobile-UA page is the 575 KB JS shell titled "Instagram" (no `og:description`). A WebView runs the JS, but whether it renders the caption or a login wall for an anonymous session was **not tested [UNVERIFIED]**. Heavier, slower, more fragile than 3.2/3.3, and no extra benefit if 3.2 works. Instagram's robots.txt applies to all agents (section 5).

### 3.5 Accessibility, clipboard, screen

- **AccessibilityService** reading the Instagram app UI: Play policy bans autonomous use and lists "assistants, automation tools, monitoring apps" as non-qualifying; requires an in-app prominent disclosure and a Play Console declaration, with account-termination risk. [CONFIRMED] https://support.google.com/googleplay/android-developer/answer/10964491 . Poor fit for a recipe app: high rejection risk, invasive, breaks on every Instagram UI change.
- **Clipboard**: Instagram's "Copy link" copies the link only. [UNVERIFIED for any caption copying]; the user can long-press the caption and copy it manually, then Simmer's paste field already handles it.
- **Audio capture (MediaProjection / "Listen")** is already the plan in #85 for the audio track; it does not yield the caption.

## 4. Summary table

| Option | Caption? | Needs login/token/backend | Official? | Robustness |
|---|---|---|---|---|
| Share intent extras | Unverified, probably no | No | n/a | n/a |
| Graph `instagram_oembed` tokenless | No | No | Yes | Stable, but useless for caption |
| `/api/v1/oembed/` legacy | Yes (`title`) | No | No | Works today for UA-agnostic GET; may change or be rate-limited per IP |
| `/embed/captioned/` HTML | Yes | No | No (iframe target) | UA-dependent, HTML scrape |
| WebView page | Unverified | Maybe login wall | No | Poor |
| AccessibilityService | Yes in theory | No | Play policy hostile | Poor |
| Manual paste | Yes | No | n/a | Always works |

## 5. ToS / policy / privacy

- Instagram's robots.txt says: "Collection of data on Instagram through automated means is prohibited unless you have express written permission from Instagram", points to Meta's Automated Data Collection Terms, and ends with `User-agent: *` `Disallow: /`. [CONFIRMED, fetched] https://www.instagram.com/robots.txt . (The Terms pages themselves rendered as empty shells to my fetcher, so I did not read the clause text. Terms: https://help.instagram.com/581066165581870 and https://www.facebook.com/legal/automated_data_collection_terms . **[UNVERIFIED]** wording.)
- Per-user, on-demand fetch of one URL the user just shared is much closer to a link preview than to crawling, but sections 3.2 and 3.3 are still automated access to an undocumented interface. Risk is mainly to Simmer's reliability (blocking by IP, endpoint removal), and a possible ToS argument, not to Play listing. No Play policy on it was found; Play's concern is with AccessibilityService use. Simmer would send only the reel URL from the user's device to Instagram, as a browser would; no Simmer-side server or extra personal data. The fetch goes from the user's own IP, which avoids a shared-IP backend being blocked.
- Meta Platform Terms limit use of *Platform Data* "received through the Platform" to documented purposes (https://developers.facebook.com/terms/, section 3.a). The tokenless Graph endpoint's data would fall under that; the undocumented endpoints arguably do not (not "Platform Data"), but that is **[INFERRED]**, not legal advice.

## 6. Recommendation

1. Keep the share target; parse the first `instagram.com/(reel|p|tv)/<id>` out of `EXTRA_TEXT` (strip `igsh`/`utm`); follow redirects for `/share/` links. Log the real intent extras on a device once to close section 1.
2. Fetch the caption with `GET https://www.instagram.com/api/v1/oembed/?url=<clean url>` and use `title`, with a short timeout. Optional second attempt: `/embed/captioned/` with a non-browser UA and parse `.Caption`. Do not use the official Graph oEmbed for captions (it has none).
3. Treat any failure (404, 400, non-JSON, empty `title`, rate limit) as the "No caption found" state already in the card design, with paste as the fallback. Never block on it. Put the fetcher behind an interface with fixture-based tests so a breaking change is a one-file fix.
4. Do not build AccessibilityService or WebView scraping.
5. Before shipping, re-test from a phone on mobile data (IP reputation differs from this desktop test) and record the date; revisit if Meta documents a caption-bearing oEmbed.
