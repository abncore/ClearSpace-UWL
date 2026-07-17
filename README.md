# CLEARSPACE // FOCUS TERMINAL
> **Course Assignment Submission Document** // **MVVM Architecture & Local State Engine**

---

## ⚡ 1. EXECUTIVE SUMMARY

**ClearSpace** is a minimalist, gamified application terminal engineered to eliminate digital distractions and optimize deep work intervals. Built with a high-contrast, industrial aesthetic, the platform integrates programmatic focus boundaries with real-time feedback loops to incentivize user compliance. 

This report outlines the software engineering principles, structural design patterns, and persistent storage strategies executed to achieve a stable, localized runtime environment suitable for academic evaluation.

---

## 🛠️ 2. ARCHITECTURAL SYSTEM DESIGN

The application architecture utilizes the **Model-View-ViewModel (MVVM)** pattern to ensure strict separation of concerns, high maintainability, and predictable testing states. State transformations are completely decoupled from UI rendering, enforcing a strict unidirectional data flow.
Data Store Layer ] (ClearSpaceStore)
           ▲
           │  Persistent Data Read/Write
           ▼
   [ ViewModel Layer ]  (ClearSpaceViewModel)
           ▲
           │  Exposes Immutable State
           ▼
   [   View Layer    ]  (MainActivity / Compose UI)
   ### Architectural Breakdown

* **View Layer (`MainActivity.kt`)**  
  Renders the declarative user interface using Jetpack Compose. It reactively observes changes in the exposed state variables without directly mutating underlying data structures.
* **ViewModel Layer (`ClearSpaceViewModel.kt`)**  
  Encapsulates the core business logic, coordinates the countdown interval engines, processes user event inputs, and exposes observable runtime states via Jetpack Compose mutable state tracking.
* **Data Store Layer (`ClearSpaceStore.kt`)**  
  Manages disk-level key-value caching using an abstraction over the Android `SharedPreferences` system. It directly governs cross-session data preservation and absolute state flushes.

---

## 📱 3. CORE TECHNICAL IMPLEMENTATIONS

### 3.1 Persistent Onboarding & State-Driven Mandatory Tutorial
The system enforces a mandatory 6-step environment tutorial for all fresh installations or post-purge instances, preventing dashboard exposure until onboarding compliance is achieved. 

Upon boot, the `ViewModel` queries the storage interface. If profile registration coordinates are completely absent, the system locks dashboard rendering constraints and directs the application lifecycle to the onboarding layout. The tutorial layer marks its completion as a persistent state boundary only after explicit sequence exhaustion or user bypass.

![Onboarding Screen Setup](screenshots/onboarding_profile_setup.png)
*_Placeholder: Insert screenshot of the initial User Profile Creation input screen here_*

![Mandatory Tutorial Sequence](screenshots/tutorial_steps_flow.png)
*_Placeholder: Insert screenshot of the active 6-step interactive tutorial workflow here_*

### 3.2 Programmatic Zero-Data Initialization Engine (Wipe Instance)
To facilitate absolute privacy control, a specialized "Wipe Instance" routing function executes a four-phase purge chain to guarantee that no local data traces survive the runtime sequence:

1. **Synchronous Storage Purge:** Volatile data store tables are entirely cleared, forcing immediate disk operations to bypass background queue lag.
2. **Runtime Heap Cleansing:** All view model variables, state history collections, timer limits, and tracked progress markers are explicitly reset to initial default states in memory.
3. **OS Task Stack De-allocation:** The application leverages context package utilities to formulate a fresh launch configuration intent, while concurrently calling system-level affinity termination routines to discard the existing task graph.
4. **Process Hard-Exit:** The active JVM runtime wrapper is terminated programmatically, executing a cold boot cycle that forces the application container to restart into a fully reset initialization layout.

![Wipe Instance Activation](screenshots/wipe_instance_trigger.png)
*_Placeholder: Insert screenshot of the Profile Management screen highlighting the 'Wipe Instance' trigger action_*

---

## 📉 4. ENGINEERING CHALLENGES & RESOLUTIONS

### 4.1 Asynchronous Storage Write Race Conditions
* **Symptom:** Triggering the application initialization reset pipeline succeeded in clearing states temporarily, but reloading the app from a terminated state occasionally resurrected old profile configurations.
* **Root Cause:** The original data layer abstraction called the asynchronous storage execution command (`.apply()`), which queues write updates to a background thread. Because the process termination call executed immediately after, the host virtual machine thread was killed before the pending background disk IO operations finished flushing data to physical files.
* **Resolution:** The execution sequence was shifted to the synchronous persistence method (`.commit()`). This intentionally blocks downstream thread progression until the physical file write operation safely concludes, guaranteeing database state cleanliness before triggering the application restart logic.

```kotlin
// Architectural Shift to Synchronous Commits
fun clearAll() {
    // .commit() forces a synchronous block, preventing background race conditions
    prefs.edit().clear().commit() 
}
