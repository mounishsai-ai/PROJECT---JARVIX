# Jarvix - System Architecture & Design

## 1. High-Level Architecture Overview
The system is designed to be highly responsive, context-aware, and cost-effective (tailored for a student budget but enterprise-grade in design). It relies on a "Thick Client, Smart Brain" model where the Android app handles device-level execution, and Gemini handles all the complex reasoning.

### Key Components
1. **The Client (Android App):** The user's interaction point. Handles voice recording, playing alarms, and storing local state.
2. **The Brain (Gemini 1.5 Flash Multimodal):** Processes raw audio and current user context to make scheduling and contextual decisions.
3. **The Memory (Local/Cloud DB):** Stores the user's daily routine/calendar, active tasks, and temporary context.

---

## 2. Component Breakdown

### A. The Android App (Frontend & OS Integration)
* **Tech Stack:** Kotlin (Native Android). Native is preferred over cross-platform (Flutter/React Native) here because we need deep, low-level access to Android's `AlarmManager` and Audio APIs.
* **Responsibilities:**
    * **Audio Capture:** Records voice input using `AudioRecord`.
    * **Alarm Scheduling:** Uses `AlarmManager` (`setExactAndAllowWhileIdle`) to schedule background wake-ups.
    * **Intrusive Alerts:** Uses Full-Screen Intents and `MediaPlayer` (ALARM stream) to ring and vibrate the phone, bypassing silent modes (like a real alarm).
    * **Local Storage (Room Database):** Stores the user's routine and active reminders locally so the app doesn't need network access just to ring an alarm.

### B. The Backend (Serverless & Lightweight)
To keep costs near zero while maintaining security, we will use a serverless architecture.
* **Tech Stack:** Firebase (Cloud Functions, Firestore) or Supabase.
* **Responsibilities:**
    * Acts as a secure proxy between the Android app and the Gemini API (so API keys aren't hardcoded in the app).
    * Handles authentication (if needed).
    * Synchronizes routine/context across devices (optional, can start local-only).

### C. The Brain (Gemini Integration)
* **Model:** Gemini 1.5 Flash (via Google AI Studio / Vertex API). Chosen for its multimodal audio input, blazing speed, and generous free tier.
* **LLM Prompting & Reasoning Strategy (Balancing Speed & Accuracy):** 
  To prevent the LLM from hallucinating times (e.g. setting an alarm for 3 AM instead of 3 PM), we use a strict **Chain-of-Thought** and **Structured Output** approach:
    * **Absolute Context:** The app MUST send the exact current `ISO-8601` timestamp, the user's timezone, and the JSON routine with *every* request. The LLM never guesses the time.
    * **Structured JSON Schema:** Gemini is forced to reply in a strict JSON format. 
    * **Reasoning Field:** Before outputting the final `target_time`, the JSON schema requires a `reasoning` field. This forces the LLM to "think out loud" (e.g., `"User said after work. Routine says office ends at 17:30. Adding 15 minutes for travel. Target time is 17:45"`). This drastically reduces logical errors.
* **Workflow:**
    1. App sends **Raw Audio** + **Current Context JSON** (Absolute Time, Timezone, Daily Routine, Active Tasks) to Gemini.
    2. Gemini processes the audio and context in one step (usually under 1 second).
    3. Gemini evaluates the time math in the `reasoning` field, then outputs the final `target_time` in a **Structured JSON Response**, defining exactly what the app should do next.

---

## 3. Core Workflows

### Workflow 1: Ingesting a Task (e.g., "Remind me to buy groceries after work")
1. **Capture:** User speaks the command. App records audio.
2. **Contextualize:** App fetches today's routine from the local DB (e.g., "Work ends at 17:00 today").
3. **Request:** App sends `[Audio File] + [Routine Context] + [Current Time]` to Gemini.
4. **Reasoning:** Gemini determines that "after work" means 17:00. It also checks if the user has a conflicting task at 17:00.
5. **Response:** Gemini replies with JSON: 
   ```json
   {
     "action": "schedule_alarm",
     "title": "Buy Record",
     "time": "2026-08-10T16:05:00",
     "message": "Ma'am told you to buy the physics record by next class.",
     "context_management": { "add": ["buy_record_task"] }
   }
   ```
6. **Execution:** App receives JSON and registers the alarm in Android's `AlarmManager`.

### Workflow 2: Anti-Hallucination & Context Pruning
* To avoid the AI getting confused by old, completed tasks, Gemini will be instructed to act as a "Garbage Collector" for memory.
* On every interaction, Gemini reviews the active context list.
* If a task is completed or the time has passed, Gemini's JSON response will include commands to delete that context:
  ```json
  "context_management": { "remove": ["old_task_id_123"] }
  ```
* The Android app will then delete these entries from the local database.

---

## 4. Required Permissions (Android)
* `RECORD_AUDIO`: For voice input.
* `SCHEDULE_EXACT_ALARM`: For precise reminders.
* `USE_FULL_SCREEN_INTENT`: To wake up the phone screen like an incoming call.
* `POST_NOTIFICATIONS`: To show standard notifications.
* `VIBRATE`: For haptic feedback during alarms.
* `INTERNET`: To communicate with Gemini.
