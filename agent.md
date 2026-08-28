# agent.md — How to work on Jarvix

**Read this file first. Every session. Before touching any code.**

---

## 0. Who owns what (don't get confused by two files)

| File | What it owns |
|---|---|
| **agent.md** (this file) | How the AI agent should behave, the model-switching rules, and the **actually verified current state** of the build. |
| **CLAUDE.md** | The vision, the design language (colors/vibe), and the golden rules. |
| **system_design.md** | The original architecture write-up. Historical — parts of it are out of date. |

**If agent.md and CLAUDE.md disagree about what is built, agent.md wins.** It gets verified against real code.

---

## 1. Who you're working with

The developer is a **student who learns by building with AI** — a "vibe coder", not a professional Android engineer.

**How to talk to them:**
- Explain things like they're smart but new. No jargon dumps.
- When you use a technical word (coroutine, WebSocket, foreground service), add a half-sentence of plain English right after it.
- Show *why* a change matters before showing the code.
- Never assume they know Gradle, Room migrations, or Android lifecycle rules — say what will break and how you'd fix it.
- Don't hand them 400 lines and say "paste this." Say what file it goes in, what it replaces, and what they should see afterwards.

**The goal right now:** win an **agent expo**. That means a live, on-stage demo has to work. A feature that only works on the laptop is worth zero.

---

## 2. What Jarvix is

An Android app that is **J.A.R.V.I.S. for normal people.**

You hold the mic and say *"remind me to buy the physics record before tomorrow's class."*
Jarvix checks your timetable, works out that class is at 10:00 AM, and sets an alarm for 9:30 AM by itself.
You never pick a time. The AI decides "when."

- **Platform:** Android native, Kotlin + Jetpack Compose
- **Brain:** Google Gemini, called directly over HTTP from the phone
- **Backend:** none — the phone talks straight to Google
- **Storage:** Room database on the phone
- **Budget:** free Gemini tier + **$300 of Google Cloud credits** (currently unused)

---

## 3. MODEL SWITCHING PROTOCOL

Two models are used on this project. **Opus 5 for thinking, Sonnet 5 for typing.**

### STOP and tell the user to switch to **Opus 5** before doing ANY of these:

- Touching **any file under `ui/`, `theme/`, or any `@Composable`** — all visual design work
- Designing or restyling a **new screen**
- Changing **how Gemini is called** (the request/response path, streaming, model choice)
- Changing the **data flow** (what gets sent to the AI, how the JSON comes back)
- Adding or changing a **Room entity, DAO, or database version**
- Anything to do with **alarms, notifications, foreground services, or wake-word listening**
- Adding a **new library or Gradle dependency**
- Any decision that would be **painful to undo later**

**How to stop:** don't just start working. Say this:

> **Switch to Opus 5 before we do this.** This is [visual design / an architecture decision], and it's the kind of thing that's painful to redo. Type `/model opus` and then tell me to continue.

Then wait.

### **Sonnet 5** is fine for:

- Fixing a bug in logic that already exists
- Changing text or colors that are already decided
- Renaming things, tidying imports, formatting
- Writing tests
- Reading code and explaining it
- Small edits inside one function

### Rule of thumb
**Deciding = Opus. Doing what's already decided = Sonnet.**

### The user's stated policy (their words, 2026-08-26)
- **Opus 5** for frontend design, core architecture, "and for what you feel best" — so if you judge a task needs Opus, say so even if it isn't on the trigger list above.
- **Opus 5 is also the escalation path:** if Sonnet gets stuck, fails a task, or keeps going in circles, stop and tell the user to switch to Opus rather than flailing.

### Project bar (changed 2026-08-26)
This is **not** a demo any more. The target is a **fully working, production-ready app** — installable and usable by a real person, just not on the Play Store yet. API keys get stripped before any public release.

That means: **no fake data, no stubbed features, no "good enough for the demo" patches.** A known bug is not acceptable just because it probably won't show. If something can't be done properly, say so instead of faking it.

---

## 4. Verified facts (tested against the live API on 2026-08-26)

Don't guess model names. These were checked by actually calling Google with this project's key:

| Model ID | Status | What it's for |
|---|---|---|
| `gemini-3.6-flash` | works — **currently used** | Reasoning + audio + image input |
| `gemini-3.7-flash` | available | Newer. Drop-in upgrade candidate. |
| `gemini-3.1-flash-tts-preview` | **tested, returns real audio** | Premium cinematic voice. **NOT wired up yet.** |
| `gemini-3.1-flash-live-preview` | available (`bidiGenerateContent`) | Real-time two-way voice over WebSocket. The sci-fi upgrade. |
| `gemini-omni-flash-preview` | available | Omni-modal |
| `gemini-3-pro-image` / `nano-banana-pro-preview` | available | Image generation |
| `veo-3.1-generate-preview` | available | Video generation |

**TTS verified working.** A test call to `gemini-3.1-flash-tts-preview` with voice `Charon` returned HTTP 200 and roughly 354 KB of audio.
Format returned: `audio/l16; rate=24000; channels=1` — raw PCM, base64-encoded. On Android this plays through `AudioTrack`, **not** MediaPlayer.

### Vertex AI — this is where the $300 Google Cloud credits actually live (verified 2026-08-27)

**Critical distinction:** the app's `GEMINI_API_KEY` is an **AI Studio** key hitting
`generativelanguage.googleapis.com`. That endpoint has its own free-tier quotas and **does not
touch the $300 Cloud credits at all**. On that key every generative *media* model is hard-blocked:

```
"limit: 0, model: gemini-3-pro-image"   <- not "used up", but "free tier grants zero, ever"
```
Veo returns the same. Waiting does not help; the "retry in 57s" message is misleading.

**Vertex AI is the path that spends the credits, and it is already set up on this machine:**
- gcloud CLI installed, ADC credentials present, account `mounishsai.ai@gmail.com`
- Project **`placement-agent-22587`**, billing linked (`01573C-ED4F11-44CBC4`), `aiplatform.googleapis.com` enabled
- Mint a token with: `gcloud auth application-default print-access-token`

**Verified working image generation (tested with real calls, 2026-08-27):**

| Model | Endpoint | Result |
|---|---|---|
| `gemini-3-pro-image` | **global** (`https://aiplatform.googleapis.com`) | ✅ 200, ~3.0 MB image |
| `gemini-2.5-flash-image` | `us-central1` **or** global | ✅ 200, ~0.5-1 MB, faster/cheaper |

Request shape (note: `generateContent`, NOT `predict`):
```
POST {HOST}/v1/projects/{PROJECT}/locations/{LOC}/publishers/google/models/{MODEL}:generateContent
Authorization: Bearer $(gcloud auth application-default print-access-token)
{"contents":[{"role":"user","parts":[{"text":"..."}]}],
 "generationConfig":{"responseModalities":["IMAGE"]}}
```
Response: `candidates[0].content.parts[0].inlineData.data` (base64).

**Confirmed NOT available on this project (all 404):** every `imagen-*` name tried
(`imagen-3.0-generate-002`, `imagen-3.0-generate`, `imagen-4.0-generate`, `imagen-3.0-fast-generate`,
`imagegeneration@006`, `imagegeneration`) and `nano-banana-pro-preview`. Imagen uses `:predict`;
the Gemini image models use `:generateContent`. **Do not burn time guessing Imagen names again** —
use the two verified rows above.

**Location matters:** `gemini-3-pro-image` is 404 on `us-central1` but 200 on `global`. If a model
404s, try the other location before concluding it is unavailable.

**How to check model names yourself (don't trust memory, including your own):**

```bash
curl -s "https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_KEY"
```

---

## 5. What is ACTUALLY built right now

**Build status — verified by actually running Gradle on 2026-08-26:**

- ✅ `./gradlew assembleDebug` → **BUILD SUCCESSFUL**. Produces a working ~16.7 MB APK.
- ✅ `./gradlew build` (full build + lint) → **BUILD SUCCESSFUL, 0 lint errors.** Was failing before 2026-08-26.
- ⚠️ **Nothing below has been tested on a physical device yet** — no phone was connected. Everything is compile-verified only.

**Working and verified in code:**
- Room database — `ActiveTask` + `DailyRoutine` tables, repositories, DAOs
- Hold-to-talk mic, records `.m4a`, sends audio to Gemini, parses the reply
- The `---JSON---` split trick (spoken part above the line, structured data below)
- Task list dashboard with JARVIS HUD colors and animated cards
- `AlarmUtils.scheduleExactAlarm` using `setAlarmClock`
- `AlarmActivity` — full-screen alarm with a drag-the-bubble snooze (5/10/15 min)
- **Routine screen** — add/delete weekly timetable entries. *CLAUDE.md wrongly says "Phase 3 NOT STARTED" — it IS built.*
- **Timetable photo upload** — photograph a college timetable, Gemini reads it and fills the database. *Not documented in CLAUDE.md at all — and it is a great demo moment.*
- Assistant launch — `ACTION_ASSIST` intent filter, auto-starts recording

**Claimed somewhere but NOT actually built:**
- **Gemini premium voice.** `NativeTTSHelper.kt` uses Android's built-in robot voice. CLAUDE.md claims Gemini TTS with the Charon voice. It does not do that. This is the biggest gap between the promise and reality.
- **Full-screen-intent notification.** The manifest requests the `USE_FULL_SCREEN_INTENT` permission, but no code ever creates a notification channel or posts a notification.
- **Context pruning / garbage collection** of old tasks — described in system_design.md, never implemented.
- Settings screen.

---

## 6. Tier 0 hardening — DONE on 2026-08-26 (needs device testing)

All of the following were fixed. **None have been verified on real hardware yet** — that is the
next job, and the list at the bottom says exactly how to test each one.

### ✅ Fixed: alarm now uses a full-screen-intent notification
`AlarmReceiver` used to call `context.startActivity()` straight from a broadcast receiver, which
Android 10+ blocks from the background. Now there is `alarms/AlarmNotifier.kt`, which owns a
high-importance notification channel and posts a notification carrying `setFullScreenIntent(...)`.
The direct `startActivity` call is kept as a secondary path (`setAlarmClock` grants a temporary
exemption). `AlarmActivity` is now `singleTask` so a double launch is harmless.

### ✅ Fixed: alarm is no longer silent on a silent phone
`AlarmActivity` used `RingtoneManager...play()`, which defaults to the **ringer** stream. Replaced
with `MediaPlayer` on `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`, looping. Also added:
- **Vibration** — looping waveform, using `VibrationAttributes` on API 33+ and the older
  `AudioAttributes` overload below that. The `VIBRATE` permission was granted but never used.
- **Volume floor** — if the alarm stream is under 30% it is raised to 60%, so a muted stream
  cannot silently kill an alarm.

### ✅ Fixed: robot voice replaced with Gemini's cinematic voice
New `audio/GeminiTTSHelper.kt` calls `gemini-3.1-flash-tts-preview` with the `Charon` voice.
The API returns **raw PCM** (`audio/l16`), which MediaPlayer cannot play — it goes through
`AudioTrack` instead. Notes:
- The sample rate is **parsed from the response mimeType**, not hardcoded.
- After `write()` returns, playback is **drained** by watching `playbackHeadPosition`, otherwise
  the last word gets cut off.
- A new reply **interrupts** the previous one.
- If the network call fails it **falls back to the device voice** so the user never gets silence.

### ✅ Fixed: re-uploading a timetable no longer duplicates it
Two layers:
1. `DailyRoutine` now has a **unique index** on `(dayOfWeek, startTime, activityName)`, so
   `OnConflictStrategy.REPLACE` finally does something.
2. `processTimetableImage` calls `deleteByDay()` the first time it sees each day in an import, so
   a corrected re-upload **replaces** that day rather than merging into it.

DB version went **1 → 2** with a real `MIGRATION_1_2` (no destructive fallback). It de-duplicates
existing rows before creating the index, because a unique index cannot be built over duplicates.
`exportSchema` is now **true** and schemas are written to `app/schemas/` — the migration SQL was
checked byte-for-byte against Room's generated schema.

### ✅ Fixed: audio format now genuinely matches what we tell Gemini
`AudioRecorderHelper` recorded an **MPEG-4 container** (`.m4a`) while the request declared
`audio/aac`. If Gemini could not decode it there would be **no error** — it would just answer from
the text prompt and invent a task. Now it records **ADTS AAC** (`OutputFormat.AAC_ADTS`), so the
file really is `audio/aac`, and the MIME string comes from `AudioRecorderHelper.MIME_TYPE` so the
two can never drift apart again. Recordings are also deleted after upload.

### ✅ Fixed: dead test files deleted
Both test files were untouched project-template scaffolding (`FAKE_DATA = listOf("Sample1")`,
`"Hello $it!"`, a `DataRepository` that no longer exists). They broke `./gradlew build`. Deleted.
**There are now zero tests** — worth adding real ones later.

### ✅ Fixed: minSdk was lying (would crash on Android 7 and 8.0)
`minSdk` was **24**, but the code needs `java.time` (26), `NotificationChannel` (26),
`VibrationEffect` (26) and `setShowWhenLocked`/`setTurnScreenOn` (27). Lint found **19 errors**.
Raised to **27** (Android 8.1) — the version the app actually requires.

### ✅ Fixed: ViewModel leaked the Activity
`MainScreenViewModel` was handed `this@MainActivity`. A ViewModel outlives the screen, so that
leaks it on every rotation. Now given `applicationContext`.

### ✅ Added: Android 14 full-screen-intent permission
From API 34 the `USE_FULL_SCREEN_INTENT` permission is no longer auto-granted to most apps.
`MainActivity` now checks `canUseFullScreenIntent()` and sends the user to the toggle if missing.

---

## 6b. Hardware verification checklist — updated 2026-08-27

1. ✅ **Alarm on a locked, silent phone.** Verified 2026-08-27 — screen woke, full-screen alarm
   UI appeared, vibration fired. See §7c for the full account, including the silent-mode-sound
   design decision this test led to.
2. ✅ **The Gemini voice.** Not directly watched, but strong indirect confirmation: zero
   `GeminiTTSHelper` warning/error logs across a real session (a failure always logs there), plus
   the user-observed ~7s of extra latency, which matches a real network TTS call and would not
   occur with the instant on-device fallback.
3. ✅ **Timetable upload twice.** User-tested 2026-08-27 — no duplicates found. The unique
   index + `deleteByDay()` fix holds.
4. ⬜ **Upgrade path** — low priority, not blocking. There is no real installed base to migrate
   from yet (pre-launch, no users), so there is nothing to synthetically test against except this
   same dev phone, which already has the current schema. Revisit this once there is an actual
   prior release to upgrade from.
5. ✅ **Audio actually reaching Gemini.** Confirmed via normal use — replies track what was
   actually said.

## 7. Tier 2 — the visual layer (done 2026-08-26)

Rebuilt `MainScreen.kt` around one signature element instead of a flat task list, per the HUD
brief in `CLAUDE.md`. Everything below is real, running Kotlin, not a mockup.

### The reactor (`ReactorCore` in MainScreen.kt)
A Canvas-drawn arc reactor: segmented outer ring, ringed housing, a triangular core motif. It is
not decorative-only - it reflects real state:
- **Idle** — slow ambient breathing.
- **Recording** — scale and glow are driven by `micLevel`, which comes from
  `MediaRecorder.getMaxAmplitude()` polled every 50ms in the ViewModel. This is the actual
  microphone level, not a simulated animation.
- **Processing** — the segmented ring visibly rotates. Honest about the ~15s wait rather than
  hiding it behind a small spinner — see the latency note below.
- **Speaking** — driven by `GeminiTTSHelper.isSpeaking`, which is wired to real playback state on
  both the Gemini path (AudioTrack) and the device-voice fallback (`TextToSpeech`'s
  `UtteranceProgressListener.onDone`), not a fixed delay guess.

### The task orbit (`OrbitHero`)
The nearest 6 pending tasks are arranged in a ring around the reactor - **soonest deadline
closest to the core**, per the original brief. Positions are computed from each task's real
`targetTimeIso8601` via sort order (the DB query already returns them ascending), not faked.
Tapping a node scrolls the real task list below to that item.
The full interactive list (mark-done, reasoning, exact time) stays below the orbit — the orbit is
the at-a-glance visualization, the list is where you act on it.

### Boot sequence (`BootOverlay`)
Plays once per cold start (`BootFlag`, a process-lifetime flag - resets naturally on relaunch,
does not replay every time you navigate to Routines and back). A system-check crawl
("ARC REACTOR ... ONLINE" etc.) next to the same reactor motif, unifying the boot moment with the
persistent hero.

### HUD chrome (`HudChrome`)
A screen-wide background layer: faint scanlines, a slow vertical sweep band, and targeting-reticle
corner brackets - all very low alpha so they read as atmosphere, not noise, and never intercept
touch (it's pure Canvas drawing, no pointer input).

### Also fixed while in there
- TopAppBar literally said **"JARVIS"** (the Marvel spelling) instead of **"JARVIX"** (this
  product's name). Corrected.
- Removed the duplicate wordmark that repeated the app name a second time below the top bar
  (`StatusStrip` now just shows the live status text).

### Build status
`./gradlew build` (full build + lint) → **BUILD SUCCESSFUL, 0 errors.**

---

## 7a. Latency root cause found + fixed (2026-08-27) — it was never architecture

The ~15s reasoning + ~7s TTS measured on 2026-08-26 (below, kept for history) turned out to be
**free-tier request queueing on the AI Studio key, not a slow model or a bad architecture.**
Measured directly, with the app's own key, on 2026-08-27:

- Plain network RTT to Google: **0.35s**.
- A trivial "Say OK" call (1 token out) on the AI Studio key: **67s, then 16s, then 2.4s** across
  three back-to-back tries — a 1-token reply cannot take 67 seconds; that is a request sitting in
  a queue, not computing.
- The exact same request against **Vertex AI** (the endpoint the $300 Google Cloud credit
  actually pays for — see §4): a consistent **4.3–5.3s**, every time.
- The user independently hit the free-tier wall live in the app mid-investigation: HTTP 429,
  `generate_content_free_tier_requests, limit: 20`. That's the AI Studio key, confirmed outside
  of any test call.
- Minting a *second* AI Studio key inside the billed project did **not** help — it returned
  `"Your prepayment credits are depleted."` Google's own docs confirm why: the $300 GCP credit
  explicitly **cannot** be applied to Gemini Developer API (AI Studio) costs, only to Vertex AI
  and other Cloud services. There is no way to make the AI Studio key fast without paying it
  separately.

**Streaming and `thinkingConfig.thinkingBudget` were the wrong lever.** Both were tried and both
measurably help (budget=128 cut ~14s to ~9.5s) but neither touches the actual bottleneck, which
is queue time before the model ever starts. This corrects the previous version of this section,
which assumed streaming was "the real fix" — it wasn't wrong that streaming helps, it was wrong
about *why* the call was slow.

### What was verified before writing any code
- Vertex AI accepts a single **API key** (no OAuth flow, no service-account JSON on the device) —
  but only a Vertex Express *authorization key*: an API key created with
  `gcloud services api-keys create --api-target=service=aiplatform.googleapis.com
  --service-account=<SA>`, bound server-side to a service account holding
  `roles/aiplatform.user`. A plain AI-Studio-style key gets HTTP 401
  `"API keys are not supported by this API."` on that endpoint.
- The Charon TTS voice (`gemini-3.1-flash-tts-preview`) exists on Vertex and was tested directly:
  HTTP 200, ~500KB audio, same `audio/l16; rate=24000; channels=1` format the app already knows
  how to play through `AudioTrack`. No format change needed.
- Reasoning (`gemini-3.6-flash`) needs an explicit `"role": "user"` on `contents` on Vertex — AI
  Studio tolerates its absence, Vertex does not. Both call sites in
  `MainScreenViewModel.kt` already set it, so no change was needed there.
- Vertex's `generateContent`/`streamGenerateContent` request/response shape is otherwise
  identical to AI Studio's. This meant **no new SDK dependency** — Firebase AI Logic was
  considered and rejected in favor of this, since the existing code already talks to Google in
  raw `HttpURLConnection` + JSON (a deliberate earlier choice — see §4), and this fits that
  pattern with zero new libraries.

### What changed
- **New**: `ai/VertexAi.kt` — builds Vertex request URLs (`generateContentUrl` /
  `streamGenerateContentUrl`), reading `BuildConfig.VERTEX_PROJECT_ID` / `VERTEX_API_KEY`.
- **`build.gradle.kts`**: two new `buildConfigField`s sourced from `local.properties`
  (`VERTEX_API_KEY`, `VERTEX_PROJECT_ID`), same pattern as the existing `GEMINI_API_KEY` — never
  hardcoded, never committed.
- **`MainScreenViewModel.kt`**: both Gemini calls (audio→task, image→timetable) now hit
  `VertexAi.generateContentUrl("gemini-3.6-flash")` instead of the AI Studio endpoint. Also
  dropped the `"reasoning"` field from the JSON the model was asked to return — dead output; the
  code already sets `reasoning = spokenPart` directly and never read `json.optString("reasoning")`.
  Free tokens saved on every single call, no behavior change.
- **`GeminiTTSHelper.kt`**: rewritten around `streamGenerateContent` (SSE) instead of one blocking
  call. Vertex TTS streams ~200 audio chunks; first chunk lands in ~1.5-2s. The helper now:
  buffers ~1.2s of audio, then calls `track.play()` and flips `isSpeaking = true` at that point —
  not when the network call is fired, which is what the old code did (so the reactor used to
  claim "speaking" through several seconds of actual silence). Chunks after the prebuffer window
  stream straight into the `AudioTrack` (`MODE_STREAM` blocking `write()` gives backpressure for
  free). Drain-wait no longer depends on a known total-frame count (streaming means the total
  isn't known until the stream ends) — it just watches `playState` with a timeout instead.
- The AI Studio key (`GEMINI_API_KEY`) is no longer used by either call site. `NativeTTSHelper`
  (the on-device voice fallback for genuine network/API failures) is untouched.

### Real-world effect (measured, not estimated)
- First spoken word: **~22s (reasoning+TTS sequential, cold) → ~6.5s** (Vertex reasoning ~4-5s,
  delimiter reachable at ~3.9s of that, TTS first chunk ~1.7s after it fires).
- The 10-67s *variance* on the reasoning call — the actual risk in front of judges — is gone; Vertex was
  consistent across every repeated test.
- **Hardware-verified 2026-08-27** — user confirmed on the real phone: fast, working reply.

### Operational note
A Cloud Billing budget alert (`Jarvix cloud credit guard`, ₹25,000 ≈ the $300 credit, 50/80/100%
thresholds) was created on the billing account so credit burn can't go unnoticed. It only emails
a warning — it does not auto-stop spending — but at this app's usage the credit should last a
long time regardless.

### Not done in this pass (deliberately deferred)
Streaming the *reasoning* call itself (rather than just TTS) was considered and skipped: Vertex
non-streamed reasoning is already ~4-5s, and streaming would only reach the `---JSON---`
delimiter ~1-2s earlier — real, but small next to the queueing fix above, and it adds real
complexity (parsing a partial JSON stream to find the delimiter safely). Revisit only if judges
still perceive the wait as too long after this fix lands on hardware.

**Original 2026-08-26 measurement, kept for history:** ~15s reasoning + ~7s TTS on the AI Studio
key, cold. At the time this was attributed to model/architecture; §above corrects that.

---

## 7b. Task lifecycle + context pruning (done 2026-08-27)

### 🚨 Fixed a serious bug: completing a task did NOT cancel its alarm
`AlarmUtils` had **no cancel function at all**. `markDone()` only flipped `isCompleted` in the
database - the alarm stayed armed in Android's AlarmManager and **would still ring** for a task
the user had already ticked off.

Added `AlarmUtils.cancelAlarm(context, requestCode)`. It rebuilds a PendingIntent matching the
one used to schedule (same receiver class + requestCode - AlarmManager only recognises an exact
match), cancels it, then cancels the PendingIntent itself. Now called on both complete and delete,
alongside `AlarmNotifier.cancel(...)` to clear any posted notification.

**How to verify this yourself** (works without any code):
```bash
adb shell dumpsys alarm | grep -i aisecretary
```
Each armed reminder shows as an `RTC_WAKEUP` entry tagged `.alarms.AlarmReceiver`. Create a
reminder, confirm an entry appears, tick it done, run the command again - the entry must be gone.

### ✅ Context pruning implemented (golden rule #5, previously never built)
`system_design.md` specified this and nothing implemented it. Tasks whose time had already passed
were still being sent to Gemini as "Pending Tasks" on every request - so the model reasoned around
commitments that had already lapsed.

`processAudioFile` now filters the task context to **future commitments only** (`isInPast()` guards
against unparseable timestamps by treating them as not-past, so a malformed row is never silently
dropped from context).

### ✅ Tasks can now be deleted
There was no delete anywhere in the stack - `markAsCompleted` was the only exit. A mis-heard task
was stuck forever. Added `deleteTask` through DAO → repository → ViewModel → a close button on each
card. **No migration needed** - these are `@Query` methods, not schema changes.

### ✅ Overdue tasks are visibly marked
A task whose time has passed now shows `MISSED • <time>` in Iron Gold rather than silently looking
identical to an upcoming one. Deliberately NOT auto-hidden: the user should see that something
lapsed, and can then delete it.

### ✅ Database housekeeping
`purgeOldCompleted()` runs once per ViewModel creation and drops completed tasks older than 7 days,
so the local DB does not grow without bound over months of use.

### Build status
`./gradlew build` (full build + lint) → **BUILD SUCCESSFUL, 0 errors.** Deployed to device.

### ✅ Verified on real hardware, 2026-08-27
Before the fix: `adb shell dumpsys alarm` showed one armed `RTC_WAKEUP` for `.alarms.AlarmReceiver`
firing at 17:00. User marked that task done in the running app. Re-ran the same command
immediately after: zero armed entries with an `origWhen` for this app. The alarm-cancel fix is
confirmed working on the device, not just compiling.

---

## 7c. Hardware verification round 2 (2026-08-27) — the two critical Tier 0 risks are RESOLVED

### ✅ Alarm on a LOCKED, SILENT phone — confirmed working on real hardware
User set a real reminder ~2 minutes out via voice, then locked the phone with the ringer on
silent, per the actual test conditions this matters for (a locked pocket at an expo, not a
plugged-in dev phone). Result: **screen woke, full-screen alarm UI appeared, vibration fired.**
This was the single highest-risk item in the whole project and it holds up.

### Design decision made during this test: silent mode = vibrate-only (2026-08-27)
The alarm produced no sound during that test. This could have been read as a bug (the original
brief said alarms should force sound through silent mode, like a real alarm clock) or as the
right call - asked the user directly, and the decision is: **silent/vibrate ringer mode means
vibrate-only, never forced sound.** `CLAUDE.md` golden rule #6 has been rewritten to match
(it used to say "a silent notification is a failure" - that line is now historical).

Implemented as an **explicit check**, not left to chance: `AlarmActivity.startAlarmSound()` now
reads `AudioManager.ringerMode` and returns immediately (no sound) unless it is
`RINGER_MODE_NORMAL`. This matters because some OEM skins already mute the ALARM stream under
silent mode and some (stock AOSP) do not - checking explicitly means this behaves identically on
every phone, not by accident of whichever OEM happens to be running. Vibration is untouched by
this check and always fires.

### A real lesson about testing alarms with adb (recorded so it isn't relearned the hard way)
Before the real test above, `adb shell am broadcast -n com.example.aisecretary/.alarms.AlarmReceiver`
was tried as a shortcut to avoid waiting for a real scheduled time. It produced **zero effect** -
confirmed via `dumpsys notification` counters not moving across three attempts. This is NOT a bug
in the app. `AlarmReceiver` is `exported="false"`, and Android auto-generates a signature-level
permission (`<pkg>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) protecting exactly this kind of
component. `adb shell` broadcasts as the `shell` user, which does not hold that permission, so the
OS silently drops the broadcast before `onReceive` ever runs - `Broadcast completed: result=0`
just means the shell command finished sending it, not that anything received it.
The real `AlarmManager.setAlarmClock()` path is different: it delivers via `system_server` using a
`PendingIntent` the app itself created, which carries the app's own identity and is not subject to
this restriction. **Conclusion: there is no reliable adb shortcut for testing this alarm path -
a real scheduled reminder is the only valid test.**

### Wireless debugging note
Mid-session the phone's Wireless Debugging "paired devices" list lost the laptop (pairing !=
connection - these are two different things in Android's Wireless Debugging, and a pairing can
be forgotten independently of whether a connection was ever active). Re-paired via:
```
adb pair <ip>:<port> <6-digit-code>
```
using the IP/port/code shown under Developer options → Wireless debugging → Pair device with
pairing code. `launchapp.ps1` then reconnected and deployed normally - no script change needed,
this was purely a phone-side pairing state issue.

### Build status
`./gradlew build` → **BUILD SUCCESSFUL, 0 errors.** Deployed and running.

### Still outstanding from the original hardware checklist
- Timetable upload twice (dedup fix) - not yet tested live.
- Real device migration test (old build → this build without wiping data) - not yet tested.

---

## 7d. Tier 3 — sci-fi asset polish (2026-08-27)

Used the $300 Vertex AI credits (see §4 for how that access actually works — it is not the same
key as the running app).

### ✅ Real app icon
Generated via `gemini-3-pro-image` (global endpoint), matching the in-app reactor motif exactly
— segmented ring, triangular core, arc blue on near-black. Cropped to square, exported at all
five densities plus adaptive-icon foreground layers (`assets_gen/` in the project root holds the
raw generation — the actual installed copies are under `Jarvix/app/src/main/res/mipmap-*/`).
Deleted the stock green-robot vector (`ic_launcher_foreground.xml`) it replaced — it is no longer
referenced anywhere.

### ✅ Ambient HUD audio bed
Generated via `lyria-002` (`us-central1`, `:predict`), 32.8s stereo 48kHz raw. Processed down to a
seamless 30s mono 24kHz loop (1.4 MB, 3s crossfade at the seam) — see `AmbientAudioPlayer.kt`.
**Important behavior, not a nicety:** it goes silent whenever the mic is recording, because the
phone's own speaker feeds back into its own mic — an ambient bed playing during capture would be
baked into the audio Gemini receives and degrade what it hears. Also silences while Jarvix is
speaking and when the app is backgrounded. Toggle lives in the top bar; state persists across
rotation via `rememberSaveable`.

Lyria note: a long, descriptive prompt triggered `500 INTERNAL "Could not generate audio. Please
try again with a different prompt"` — this is Lyria's content/complexity filter, not a real server
error. A short, plain prompt worked on the first retry.

### ❌ Veo boot animation — deliberately skipped, not blocked by us
Every Veo model ID (`veo-3.1-generate-preview`, `veo-3.0-generate-001`, etc.) returns 404 on this
project, on both `us-central1` and `global`. Confirmed via web search this is NOT a naming problem
(unlike Imagen) — **Veo requires an explicit Google-reviewed allowlist request per project**,
submitted with project ID/number and use case through Google's developer forums, with no
guaranteed turnaround time. Neither of us can push that through from here.

**Decision:** skip it. The existing Canvas-drawn reactor boot sequence (§7) already works and
looks good — not worth gating the expo deadline on an approval process outside our control.

**To revisit later:** the request path is real and documented (search "Veo allowlist access
Vertex AI" — multiple recent forum threads show the format). Project details if this gets
resubmitted: project ID `placement-agent-22587`, billing already enabled.

### Build status
`./gradlew build` → **BUILD SUCCESSFUL, 0 errors** with the new icon + ambient audio wired in.
Deployed to device 2026-08-27.

---

## 7e. Settings screen (2026-08-27) — built on Sonnet, at the user's explicit direction

Note on process: this is a new screen, which is on the model-switching trigger list (§3). The
user explicitly said to build it on Sonnet anyway ("opus will burn usage, lot of work to do
right") rather than switch. That is their call on their own usage budget — recorded here so a
future session understands why a "new screen" doesn't have an Opus paper trail, not because the
rule was forgotten.

### What was built
- **`data/SettingsRepository.kt`** — plain `SharedPreferences`, deliberately not Room or
  DataStore. These are a handful of simple values, not structured data; DataStore would have
  been a new dependency for no real benefit.
- **`ui/settings/SettingsViewModel.kt` + `SettingsScreen.kt`** — follows the exact pattern
  `RoutineViewModel`/`RoutineScreen` already established (same `provideFactory` shape, same HUD
  card styling), so this doesn't introduce a second way of doing things.
- Wired into `MainActivity.kt` as a third `currentScreen` value, same simple string-based
  navigation already in use — no navigation library added.

### Real settings, not placeholders — each ties to something already in the app
- **Ambient sound on/off** — now genuinely persisted. It used to live in `MainScreen` as
  `rememberSaveable` (survives rotation, not app restart); moved into
  `MainScreenViewModel.ambientSoundEnabled`, backed by the repository, so the preference survives
  a real restart. The top bar's quick-toggle and the Settings screen's switch are the same
  underlying state now, not two independent copies.
- **"Play sound when phone is silenced"** — turns the 2026-08-27 silent-mode decision (§7c) from
  a hardcoded choice into a user-overridable one. Default `false`, matching that decision.
  `AlarmActivity.startAlarmSound()` takes this as a parameter and skips the ringer-mode gate when
  the user has opted in.
- **Snooze durations (Short/Medium/Long)** — previously hardcoded 5/10/15 in `SwipeBubble`. Now
  read from settings and threaded through `AlarmScreen` → `SwipeBubble`, both the drag-direction
  values and the on-screen labels.

### A UX gap worth knowing about, fixed in the same pass
The snooze fields enforce `Short < Medium < Long` (an alarm with snooze buttons out of order
would be confusing). The first version of this rejected an out-of-order edit **silently** — the
text box would show what was typed, but nothing was actually saved, with no indication why. That
is exactly the kind of silent failure the project bar (§3) says not to ship. Fixed by adding an
explicit hint above the fields stating the ordering constraint, so a rejected edit is at least
explainable rather than mysterious.

### Navigation change worth noting
The gear icon in the top bar used to open **Routines** directly — a real mislabel, since a
settings-looking icon led to the timetable editor, not settings. Replaced with a single overflow
(⋮) menu holding both **Daily Routines** and **Settings**, rather than adding a fourth bespoke
icon to an already-busy top bar.

### Build status
`./gradlew build` → **BUILD SUCCESSFUL, 0 errors.** Deployed to device 2026-08-27.
**Hardware-verified 2026-08-27** — user clicked through all three settings on the phone
(ambient toggle, silent-mode sound override + real alarm while silenced, snooze value edits)
and confirmed working. No longer compile-verified-only.

---

## 7f. Expo-readiness pass (2026-08-27) — built on Sonnet, judgment-based (user's explicit call)

The user asked "what's pending for a 10/10 expo" and, notably, told me not to reflexively invoke
the model-switching protocol on each item — to use my own judgment on whether a given task
genuinely needs Opus's design judgment, and only ask to switch when I actually think it does. This
section's three fixes were judged not to need it (see each item's note); nothing in this pass
needed the switch.

### 1. "Tap the mic" → "Press and hold the mic" (`strings.xml`)
Real bug, not polish: `empty_state_subtitle` told a first-time user (a judge, cold) to *tap* the
mic. The actual gesture is press-and-hold — `MainScreenViewModel.stopRecording()` explicitly
rejects anything under 800ms with "Please press and HOLD the microphone to speak." A judge
following the on-screen instruction would get an error on their very first interaction. One-line
fix. Pure text change — no judgment call needed on switching.

### 2. Silent alarm-scheduling failure now surfaces (`MainScreenViewModel.kt`)
Found while auditing for anything that violates the project's own "no fake data" bar (§3). If
`LocalDateTime.parse(timeStr)` threw (a timestamp format `java.time`'s default parser can't
handle), the code already had **inserted the task into the DB** before the parse was attempted —
so a task would sit in the visible list looking scheduled, while its alarm silently never got
created. JARVIS's spoken reply would already have confirmed a reminder that then just... never
fires. Fixed: on that failure, the ghost task is deleted and `_lastResponse` says so explicitly.
Same treatment added to the outer JSON-parse-failure path (malformed JSON from the model — no
task created, but the spoken reply may have implied one). This is a straight bug fix in existing
logic — squarely "Sonnet is fine for" territory per §3, no judgment call needed.

### 3. Friendly network-error message (`MainScreenViewModel.kt`)
Both API call sites showed raw exception text (e.g. `"Unable to resolve host..."`) on any network
failure — accurate but not something a demo audience should have to read, and a real risk given
how unreliable venue Wi-Fi tends to be. Added `friendlyErrorMessage()`: `IOException` (covers
`UnknownHostException`, `SocketTimeoutException`, etc.) gets a plain-language message; anything
else keeps its real message unchanged, so real bugs are still debuggable. Small logic change, no
UI — not a judgment call.

### 4. System Status panel — new Settings section (`SettingsScreen.kt`)
**The one place I did stop and think about the switch, and decided against it, on purpose — not
by default.** The protocol's literal trigger list includes "touching any file under `ui/`" and
"restyling a screen," which this technically is. But the actual judgment question is whether this
*decides* anything new: it doesn't. Every permission check here (`canScheduleExactAlarms`,
`canUseFullScreenIntent`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`) already exists verbatim in
`MainActivity.kt`'s launch-time checks — this only displays state that was already being computed,
using the exact `SettingsSection`/color-token pattern (`SuccessGreen`/`ErrorRed`/`ArcBlue`)
`SettingsScreen.kt` already established in §7e. Nothing about layout, tone, or visual language was
invented here. That is the same bar the Settings screen itself was built to in §7e, and it holds.

**What it does:** shows live ONLINE/OFFLINE status for mic, notifications, exact alarms, and
full-screen-intent permission, each with a FIX button that opens the exact system settings screen
`MainActivity.kt` already knows how to open. Refreshes on `ON_RESUME` (via
`androidx.lifecycle.compose.LocalLifecycleOwner`) so returning from a system Settings screen shows
the corrected state immediately, not after an app restart.

**Why this matters for the expo specifically:** Android can silently revoke a permission after
long inactivity, or a judge's own hands could toggle something by accident. Previously the first
sign of that would be a dead mic or a silent alarm *during* the demo. Now it's a checkable,
fixable line item beforehand.

### Deliberately not done in this pass
**First-run onboarding moment** — the third item from the same conversation — was explicitly
flagged to the user as the one place I *do* think Opus adds real value: it's an actual new
interaction moment with no existing template to follow (unlike the panel above, which had
`MainActivity.kt`'s checks and `SettingsScreen.kt`'s section pattern to lean on), it only gets
seen once per install so it's hard to iterate on live, and it requires deciding tone/pacing/visual
composition from nothing. Not started; waiting on the user to switch model before this specific
piece.

### Build status
`./gradlew build` (full build + lint) → **BUILD SUCCESSFUL, 0 errors.** Deployed to device
2026-08-27. **Not yet manually tested on hardware** — click through the System Status panel (does
FIX actually open the right settings page, does the status flip correctly on return) before
trusting this as more than compile-verified.

---

## 8. Golden rules

1. **JARVIS, not a reminder app.** If a screen looks like stock Material Design, it's wrong.
2. **Voice first.** Never add a feature that needs typing if voice can do it.
3. **The AI decides "when".** Never show the user a time picker.
4. **API keys live in `local.properties`.** Never hardcode, never commit.
5. **Demo-first priority.** Ask "will this survive a live demo on a locked phone in a loud room?" If not, fix that first.
6. **Never guess a model name.** Call the models endpoint and check.
7. **Personality comes from system prompts,** not fine-tuning.
8. **Test on a real phone.** The emulator lies about alarms, the mic, and the lock screen.

---

## 9. Useful commands

Build and deploy to the phone over Wi-Fi (needs Wireless Debugging turned ON):

```powershell
.\launchapp.ps1
```

Watch the app's logs while it runs:

```bash
adb logcat -s NativeTTSHelper:* AndroidRuntime:E
```
