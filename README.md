# Jarvix

**A voice-first AI life copilot for Android — not a reminder app with a chatbot bolted on.**

You hold a button, say what you need, and Jarvix understands it in the context of your actual
week, schedules it as a real alarm that survives a locked and silenced phone, and speaks back
in a cinematic AI voice — all in about 6 seconds.

---

## Why not just use Gemini/ChatGPT directly?

A raw chat assistant is a text box. It doesn't know your Tuesday schedule, can't wake your phone
up, and forgets the moment you close the tab. Jarvix is the integration layer that turns a
general-purpose LLM into something that actually runs your day:

- **It knows your real routine.** "Remind me after college" means nothing to a chatbot with no
  memory of your schedule. Jarvix resolves it against your actual weekly timetable and turns it
  into an exact timestamp — no typing, no manual time picker.
- **It guarantees delivery, a chatbot can't.** Gemini can write you a reminder. It can't force a
  locked, silenced Android phone to wake up and show a full-screen alert. Jarvix uses Android's
  exact-alarm and full-screen-intent APIs to make sure the thing you asked for actually happens.
- **It's hands-free, start to finish.** Press and hold, speak, done — no screen interaction
  required to capture a task. A browser tab isn't built for that.
- **It's honest about its own state.** No task is ever shown as "scheduled" unless its alarm is
  genuinely registered with the OS; failures are surfaced, not swallowed. A live System Status
  panel shows whether the permissions it depends on (mic, exact alarms, full-screen intent) are
  actually still granted, before that silently breaks a reminder.

---

## What it actually does

- **Hold-to-talk voice capture** — no typing, ever. Real microphone amplitude drives the UI live,
  not a canned animation.
- **Context-aware task extraction** — a single Gemini call gets the user's audio, their weekly
  routine, and their pending tasks, and returns both a spoken reply and structured task data in
  one round trip.
- **Full-screen, lock-screen-breaking alarms** — built on `AlarmManager.setAlarmClock`, the same
  API real alarm-clock apps use, with a drag-to-snooze gesture and configurable snooze lengths.
- **A real cinematic voice**, not robotic text-to-speech — Gemini's streaming TTS, played back as
  the audio arrives so the reply starts in ~1.5 seconds instead of after the whole clip renders.
- **Photograph a paper timetable** and Gemini reads it into a live weekly schedule — no manual
  data entry.
- **A reactor, not a list.** The main screen is a Canvas-drawn arc-reactor core whose glow and
  motion reflect real app state — recording amplitude, network processing, TTS playback — with
  the nearest tasks orbiting it by urgency.
- **Configurable, not hardcoded.** Ambient sound, whether alarms force sound through silent mode,
  and snooze durations are all real user settings, not fixed constants.

---

## Tech stack

| Layer | Technology |
|---|---|
| App | Kotlin, Jetpack Compose, Material3 |
| Local storage | Room (SQLite), versioned migrations |
| Scheduling | Android `AlarmManager` (exact, lock-screen-safe alarms) |
| Reasoning | Google Gemini via Vertex AI |
| Voice | Gemini streaming text-to-speech, played through `AudioTrack` |
| Audio capture | Android `MediaRecorder`, live amplitude sampling |

## Architecture

```
Hold-to-talk audio
        │
        ▼
Gemini reasoning call ── + weekly routine context, pending tasks
        │
        ├── spoken reply ──► streamed TTS ──► AudioTrack playback
        │
        └── structured task JSON ──► Room DB ──► AlarmManager.setAlarmClock
                                                        │
                                                        ▼
                                        Full-screen alarm activity
                                        (survives locked + silenced phone)
```

---

## Running it yourself

This repo does **not** include API keys, by design — see [`agent.md`](agent.md) for why. To
build it:

1. Get a Google Gemini API key ([AI Studio](https://aistudio.google.com)) and, for full
   performance, a Google Cloud project with Vertex AI enabled.
2. Create `Jarvix/local.properties` with:
   ```
   sdk.dir=<your Android SDK path>
   GEMINI_API_KEY=<your key>
   VERTEX_API_KEY=<a Vertex Express authorization key, see agent.md §7a>
   VERTEX_PROJECT_ID=<your GCP project id>
   ```
3. `./gradlew assembleDebug` from the `Jarvix/` directory.

**We aren't distributing a public download build.** Any APK ships compiled secrets baked into it
— handing one out publicly means handing out the keys behind it. Reach out directly for a demo
build.

---

## Project docs

- [`agent.md`](agent.md) — full build history, verified facts, and what's actually tested on
  real hardware vs. compile-verified only.
- [`CLAUDE.md`](CLAUDE.md) — original vision and design language.
