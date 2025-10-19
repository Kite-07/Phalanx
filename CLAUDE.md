# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Phalanx** is a **full-featured SMS messaging app** for Android with privacy-first security features built on top.

### Core Functionality
Phalanx is first and foremost a complete SMS messaging app that:
- Sends and receives SMS messages with a modern, intuitive interface
- Displays conversations in threaded format with contact integration
- Handles multi-SIM devices with per-SIM preferences
- Provides standard messaging features (delete, search, notifications)

### Security Layer (Built On Top)
On top of the core messaging functionality, Phalanx analyzes messages **entirely on-device** to:
1. Reveal the **true destination** of shortened/obfuscated links
2. Produce an **explainable risk score** (Green/Amber/Red)
3. **Notify only on issues** (Amber/Red threats)
4. Provide **Trash/Restore functionality** (30-day soft delete)

**Core principle**: No cloud processing - all analysis happens locally. The app operates as the Default SMS app (full messaging + security features) or in Assist Mode (read-only security overlay via Notification Listener).

**Package name**: `com.kite.phalanx`

**Current Status**: Early development - basic SMS reading UI is implemented. Message sending, receiving, and the security analysis features are not yet built.

### Product Requirements Document

The complete PRD with detailed phased build order is located at:
`app\References for building (delete before publishing)\PRD - Phalanx.md`

**Phase 0 specification** (must be built first, before PRD phases) is located at:
`app\References for building (delete before publishing)\Phalanx - Phase 0.md`

**CRITICAL**: Always consult Phase 0 before implementing any features. Phase 0 defines the complete core messaging app that must be built before any security features from the PRD. After Phase 0 is complete, consult the PRD for implementing security features.

**Phase 0 defines:**
- Core messaging: Send/receive SMS & MMS, conversation threading, dual SIM support
- Core flows: Inbox → thread → compose → send; notification reply
- Features: Drafts, contact sync, search, notifications, spam blocking
- UI/UX: Material 3, bottom FAB, swipe gestures, bubble layout
- Data model: Thread, Message, Attachment, ContactCache, BlockedNumber, Settings

**PRD defines (to be implemented after Phase 0):**
- 8 development phases (Phase 0: Core Messaging → Phase 7: Freshness & Reliability)
- Domain model (Message, Link, Signals, Verdict, Trash, RuleOverride, etc.)
- Core risk detection rules (SHORTENER_EXPANDED, HOMOGLYPH_SUSPECT, USERINFO_IN_URL, etc.)
- Performance budgets (≤300ms per-message analysis, ≤600ms cold start, <1-2% daily battery)
- Tech stack requirements (Hilt, Room, OkHttp, ICU4J, etc.)

### Development Approach

Build features **incrementally** following the phased order in the PRD:
0. **Phase 0**: Core messaging app - send/receive SMS, conversation threading, contact integration, notifications
1. **Phase 1**: Security pipeline - link extractor, URL expander, domain profiler, risk engine
2. **Phase 2**: Security UI - decorators (chips, bottom sheets), notification manager for threats
3. **Phase 3**: Safety rails - trash vault, allow/block lists, security settings
4. **Phase 4**: Sender intelligence packs, first-run flow
5. **Phase 5**: Safe preview fetcher, audit logging
6. **Phase 6**: Language/grammar signals, optional ML classifier
7. **Phase 7**: Update service, cache hardening, battery optimization

**Phase 0 must be completed first** - the app must function as a reliable SMS messenger before adding security features on top. Do not implement features out of order or skip foundational phases.

## Build Commands

```bash
# Build the project
gradlew build

# Build debug APK
gradlew assembleDebug

# Build release APK
gradlew assembleRelease

# Run unit tests
gradlew test

# Run instrumented tests (requires connected device/emulator)
gradlew connectedAndroidTest

# Run a specific test class
gradlew test --tests com.kite.phalanx.ExampleUnitTest

# Clean build artifacts
gradlew clean
```

## Architecture

### Target Architecture (from PRD)

The app will follow **MVVM + Use Cases** pattern with:
- **Presentation Layer**: Jetpack Compose (Material3) + ViewModels
- **Domain Layer**: Use cases for link analysis, URL expansion, risk scoring
- **Data Layer**: Room (messages, verdicts, cache), Proto DataStore (config), in-memory LRU caches
- **DI**: Hilt for dependency injection
- **Concurrency**: Coroutines + Flow for reactive streams

### Current Architecture (Basic SMS Reader)

Currently implemented as a simple two-activity architecture:

1. **SmsListActivity** (app\src\main\java\com\kite\phalanx\SmsListActivity.kt:61) - Main launcher activity
   - Displays conversation list grouped by sender/contact
   - Shows latest message preview for each contact
   - Handles runtime permissions for READ_SMS and READ_CONTACTS
   - Refreshes message list on resume using lifecycle observers
   - Reads contact photos and displays them as avatars

2. **SmsDetailActivity** (app\src\main\java\com\kite\phalanx\SmsDetailActivity.kt:39) - Detail view
   - Shows full message thread with a specific sender
   - Displays messages in chat bubble format (user messages aligned right, received messages aligned left)
   - Groups timestamps intelligently (shows time only when messages are >1 minute apart)

**Note**: These activities will be refactored to MVVM pattern and enhanced with security analysis features as development progresses.

### Data Model

**SmsMessage** (app\src\main\java\com\kite\phalanx\SmsMessage.kt:6) - Core data class representing an SMS message
- `sender`: Phone number or contact identifier
- `body`: Message text content
- `timestamp`: Message date/time in milliseconds
- `isSentByUser`: Boolean indicating message direction (sent vs received)
- `contactPhotoUri`: Optional URI for contact's photo

### SMS Reading Logic

**SmsListActivity approach**:
- Queries `Telephony.Sms.CONTENT_URI` for all messages
- Uses `LinkedHashMap<String, SmsMessage>` to deduplicate and keep only the latest message per sender
- Sorts by timestamp descending (newest first)
- Loads contact photos asynchronously on background thread

**SmsDetailActivity approach**:
- Queries messages filtered by sender address
- Sorts chronologically ascending (oldest first)
- Builds `MessageUiModel` list to determine which messages should show timestamps

### Permissions

The app requires two runtime permissions (declared in AndroidManifest.xml:5-6):
- `READ_SMS` - Required to read SMS messages from device
- `READ_CONTACTS` - Optional, for displaying contact photos

Permission handling in SmsListActivity:
- Uses `ActivityResultContracts.RequestMultiplePermissions()` for modern permission flow
- Requests permissions on initial launch via `LaunchedEffect`
- Refreshes data automatically when permissions granted

### UI/Compose

Built entirely with Jetpack Compose Material3:
- Theme files in `app\src\main\java\com\kite\phalanx\ui\theme\`
- Uses `LazyColumn` for efficient list rendering
- Contact avatars loaded asynchronously with `produceState`
- Message bubbles use `RoundedCornerShape(16.dp)` with tonal elevation

## Key Implementation Details

### Message Type Detection
The `isUserMessage()` helper (app\src\main\java\com\kite\phalanx\SmsMessage.kt:14) determines message direction by checking against:
- `MESSAGE_TYPE_SENT`
- `MESSAGE_TYPE_OUTBOX`
- `MESSAGE_TYPE_FAILED`
- `MESSAGE_TYPE_QUEUED`

### Contact Photo Loading
Asynchronous contact photo loading uses:
1. ContentResolver query against `ContactsContract.PhoneLookup.CONTENT_FILTER_URI`
2. Background thread execution with `withContext(Dispatchers.IO)`
3. BitmapFactory to decode stream into ImageBitmap
4. Fallback to launcher icon if photo unavailable

### Timestamp Grouping
Messages within the same minute bucket (`timestamp / 60_000L`) are grouped together, with only the last message in each group showing a timestamp.

## Build Configuration

### Current
- **Min SDK**: 28 (Android 9.0)
- **Target SDK**: 36
- **Compile SDK**: 36
- **Kotlin version**: 2.0.21
- **AGP version**: 8.13.0
- **Compose BOM**: 2024.06.00

### Required Dependencies (per PRD - to be added)
- **Hilt**: Dependency injection
- **Room**: Local database for messages, verdicts, signals, cache
- **Proto DataStore**: Type-safe configuration storage
- **OkHttp**: Network operations (URL expansion with strict timeouts, max 4 redirects)
- **Jsoup**: HTML parsing (no network mode)
- **ICU4J**: Unicode normalization and homoglyph detection (UTS-39)
- **WorkManager**: Background tasks (trash auto-purge, pack updates)
- **Coil**: Image loading (favicons only)
- **JUnit5, MockK, Turbine**: Testing framework

### Performance Budgets
- Per-message analysis: ≤300ms P50
- Cold start: ≤600ms
- URL expansion timeout: ≤1500ms
- Daily background battery: <1-2%
- Crash-free sessions: ≥99.5% in beta

## Testing

- Unit tests: `app\src\test\java\com\kite\phalanx\ExampleUnitTest.kt`
- Instrumented tests: `app\src\androidTest\java\com\kite\phalanx\ExampleInstrumentedTest.kt`

### Testing Requirements (per PRD)
- Unit tests for: link extractor (≥98% recall), PSL domain parser, URL expander, risk engine rules
- UI tests for: chips, notifications, trash vault, bottom sheets
- Performance tests to validate budgets
