# Project Context Memory — Jarvix

This document owns the **vision and the design language** for Jarvix — what we're building and how it should look and feel.

> **Read `agent.md` first.** It owns how the AI agent should work, the model-switching rules, and the *verified* current state of the build. If this file and `agent.md` disagree about what is actually built, **`agent.md` wins.**

---

## 1. The Ultimate Vision — What We Are Building

**Jarvix is J.A.R.V.I.S. for everyone.**

The long-term objective is to build an AI assistant that mirrors the experience of Tony Stark interacting with J.A.R.V.I.S. — a proactive, intelligent, voice-driven AI that manages your life in the background without you having to think about it. But instead of being only for a billionaire genius, it is for every college student, office worker, or parent who is overwhelmed by cognitive load.

### The North Star Experience
> The user says: *"Jarvix, remind me to buy the physics record before tomorrow's class"*
> Jarvix silently cross-references the user's timetable, determines that class is at 10:00 AM, triggers an alarm at 9:30 AM when the user is walking to campus, and dismisses itself the moment it's acknowledged.

**Every design decision, every UI screen, every piece of logic must serve this core experience.**

---

## 2. Design Language — The JARVIS HUD

The UI must feel like a **holographic heads-up display (HUD)** from Iron Man's suit. NOT a generic mobile app.

| Token | Color | Usage |
|---|---|---|
| `DeepNavy` | `#010409` | Background — void black like the visor |
| `ArcBlue` | `#00C8FF` | Primary — arc reactor electric blue glow |
| `AquaAccent` | `#00FFD1` | Secondary — holographic data stream cyan |
| `IronGold` | `#FFC200` | Warnings & highlights — Iron suit gold trim |
| `TextPrimary` | `#E8F4FD` | Body text — cool blue-white, not pure white |

**Vibe:** Every card, button, and line should feel like a holographic projection, not a flat mobile app.

---

## 3. Project Name & Identity

- **App Name:** Jarvix
- **Tagline:** *"Co-piloting your life."*
- **Vibe:** Premium, powerful, intelligent — like JARVIS, not like a basic reminder app.

---

## 4. Core Features (Non-Negotiable)

1. **Voice-First Input:** Most interaction is through voice. The mic FAB is the primary control.
2. **Routine-Aware Reminders:** AI cross-references daily schedule (classes, work) to determine the right reminder time.
3. **Conflict Resolution:** If a task conflicts with a scheduled event, Jarvix suggests the best alternative.
4. **Intrusive Alarms:** Uses AlarmManager + Full-Screen Intents to wake the device like a real alarm. Flashes the whole screen black and displays the reminder text with snooze options (5m, 10m, 15m).
5. **Auto-Context Pruning:** Completed/expired tasks are removed from AI context to prevent hallucinations.
6. **No Manual Scheduling:** The user describes the task in natural language. Jarvix determines "when" automatically.
7. **Voice Output (TTS):** Jarvix responds with voice, not just text display.
8. **Quick Activation Shortcut:** Integrates as a digital assistant so users can long-press the power button or use shortcuts to quickly launch the app and start recording.

---

## 5. Technical Architecture (Direct Mobile)

- **Platform:** Android Native (Kotlin + Jetpack Compose)
- **AI Engine:** Raw REST calls to the Gemini API (the SDK dependency was removed). Current model: `gemini-3.6-flash`. See `agent.md` §4 for the verified model list.
- **Backend:** NONE. 100% Serverless. Direct API calls from the Android app to Google.
- **Database:** Room DB (Local, fast, privacy-first)
- **Architecture Pattern:** Thick Client AI Direct

### Data Flow
```
User Voice → Android records M4A → 
Android passes audio + context string directly to Gemini 3.6 Flash REST API → 
Gemini JSON response parsed natively (extracts spoken response and task data) →
Spoken text is sent to Gemini 3.1 Flash TTS Preview REST API to generate premium voice (Charon) →
Android plays returned L16 audio and saves task to Room DB → 
Android schedules AlarmManager → AlarmActivity wakes device
```

*Note on Latency:* Because we are using two sequential REST API calls (one for reasoning, one for TTS), there is currently some latency. Future improvements could include using the Gemini Bidirectional (WebSockets) API for real-time text and audio streaming to improve speed.

---

## 6. Current Build Status

> ⚠️ For the authoritative, code-verified build status see **`agent.md` §5**. Summary below.

- **Phase 1:** ✅ COMPLETE — Foundation (Permissions, Room DB, AlarmManager, JARVIS HUD UI)
- **Phase 2:** ✅ COMPLETE — The Brain (Gemini wired up in MainScreenViewModel via raw REST, System Prompts defined)
- **Phase 3:** ✅ COMPLETE — Routine screen built (`ui/routine/`). Settings screen still missing.
- **Bonus (undocumented):** ✅ Timetable photo upload — photograph a timetable, Gemini reads it and fills the routine DB.
- **⚠️ Not actually built despite claims below:** Gemini premium TTS (still stock Android voice), full-screen-intent notification, context pruning.

### Key files built
- `JarvixApplication.kt` — initializes Room + Repository at startup
- `MainScreenViewModel.kt` — holds the `GenerativeModel` with the massive JARVIS system prompt
- `AudioRecorderHelper.kt` — M4A recording lifecycle
- `build.gradle.kts` — Securely loads `GEMINI_API_KEY` from `local.properties` via BuildConfig
- `Color.kt` — JARVIS HUD theme (void black, arc blue, holographic cyan, iron gold)

---

## 7. Next Roadmap

1. **Full-Screen Alarm UI:** Create the AlarmActivity (black screen, reminder text, 5/10/15 min snooze options).
2. **Voice Output (TTS):** Integrate Android `TextToSpeech` to read Jarvix's responses aloud.
3. **Quick Activation Shortcut:** Configure `ACTION_ASSIST` so Jarvix can be launched via long-pressing the power button.
4. **Daily Routine Screen:** UI for the user to input their weekly timetable (college schedule, work hours).
5. **Real Context Fetch:** Wire `MainScreenViewModel` to fetch real `DailyRoutine` from DB instead of the mock string.
6. **Settings Screen:** User preferences (ringtone, snooze duration, etc.).
7. **Physical Device Testing:** APK testing on a real Android device for mic, permissions, and alarm wake.

---

## 8. Golden Rules for Future Development

> These rules apply to **every** new feature, screen, or piece of logic added to Jarvix.

1. **JARVIS, not a reminder app.** Every UI element must feel premium and HUD-like.
2. **Voice first.** Never add a feature that requires typing if it can be done with voice.
3. **The AI handles the "when".** Never ask the user to pick a time manually.
4. **100% Local / Free.** No paid backends. API keys belong in `local.properties`.
5. **No hallucinations.** Always prune completed/expired context before sending to Gemini.
6. **Intrusive alarms, respecting silent mode.** Reminders must be capable of waking the device — full screen + vibration, always. Sound only plays when the ringer is in normal mode; if the user has silenced or vibrate-muted their phone, that is a deliberate choice and the alarm respects it with vibration only, never forcing sound through silent mode. *(Decided 2026-08-27 — earlier drafts of this rule said a silent notification is a failure; that was changed after real-device testing.)*
7. **System Prompts over Fine-Tuning.** Shape JARVIS's personality using robust System Instructions, not model training.
