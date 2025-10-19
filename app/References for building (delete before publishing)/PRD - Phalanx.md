# PRD — Phalanx

## 0) Product Context
Build a **full-featured SMS messaging app** for Android with privacy-first security features built on top. Core functionality includes sending and receiving SMS messages with a modern, intuitive interface. Security layer analyzes messages locally to (1) reveal the **true destination** of links, (2) produce an **explainable risk score** (Green/Amber/Red), and (3) **notify only on issues**, with **Trash/Restore (30 days)**. No cloud processing. Must work as **Default SMS app** (full messaging + security features) and **Assist Mode** (read-only security overlay via Notification Listener) if user declines default role.

## 1) Top-Level Requirements

### Core Messaging Features
- **Send/Receive SMS**: Full messaging capability with conversation threads
- **Modern UI**: Clean, intuitive Compose interface for reading and composing messages
- **Contact Integration**: Display contact names, photos, and metadata
- **Multi-SIM Support**: Handle multiple SIM cards with per-SIM preferences
- **Message Management**: Search, archive, delete, and organize conversations

### Security Features (on top of core messaging)
- Show final URL + **registered domain** under each SMS row.
- Flag risks: shorteners, punycode/homoglyph, IP links, `http://`, userinfo in URL, non‑standard ports, suspicious paths (`/login`, `/verify`, `/reset`, `/prize`, `/otp`), sender–message mismatch (via region packs), basic grammar/anomaly signals.
- Send notifications **only** for Amber/Red. Provide **Open Safely**, **Copy URL**, **Trash**, **Whitelist/Block**.
- **Trash Vault:** soft-delete for 30 days; **Restore** to original thread; auto‑purge.
- **Settings:** sensitivity slider, per‑SIM toggles, OTP pass‑through, region selection, feature flags.
- Everything runs **on‑device**; network only for URL expansion, optional preview, and signed pack/PSL updates.

## 2) Non-Goals (v1)
- In-app browsing; heavy grammar libraries; server-side scanning; uploading SMS content; background SMS sending.

## 3) Platform & Tech Constraints
- Kotlin, Jetpack Compose (Material3), MVVM/use-cases, Hilt DI, Coroutines/Flow.
- Min SDK 26+ (prefer 28), target latest.
- Storage: Room (SQLite) + Proto DataStore; in‑memory LRU caches.
- Network: OkHttp (strict timeouts, manual redirect follow ≤4, no JS). Parsing with Jsoup (no network) and ICU4J (UTS‑39). PSL bundled + updatable.
- Performance budgets: per‑message analysis ≤300ms P50; cold start ≤600ms; daily battery <1–2%.
- Permissions/roles: Default SMS role (full features) OR Notification Listener (Assist Mode); READ/RECEIVE SMS; POST_NOTIFICATIONS. No cleartext traffic by default.

## 4) Domain Model (concise)
- **Message(id, threadId, simSlot, timestamp, sender, body)**
- **Link(original, normalized, finalUrl, host, registeredDomain, scheme, port, path, params, flags)**
- **Signals(messageId, linkId, code, weight, meta)**
- **Verdict(messageId, level{GREEN|AMBER|RED}, score, reasons[List<Reason>])**
- **Reason(code, label, details)**
- **Trash(id, originalMessageRef, deletedAt, expiresAt)**
- **RuleOverride(type{ALLOW|BLOCK}, scope{sender|domain|pattern}, value, createdAt)**
- **SenderPack(region, version, entries[{pattern, brand, type}], signature)**
- **Config(sensitivity, region, featureFlags, perSimPrefs)**

## 5) Core Rules (initial set)
- `SHORTENER_EXPANDED` (source=expander)  
- `HOMOGLYPH_SUSPECT` (ICU UTS‑39 skeleton mismatch)  
- `PUNYCODE_DOMAIN` (host starts `xn--`)  
- `RAW_IP_HOST` (IPv4/6 literal)  
- `HTTP_SCHEME` (scheme `http`)  
- `USERINFO_IN_URL` (presence of `user:pass@host`) → **critical Red**  
- `NON_STANDARD_PORT` (not 80/443 unless allowlisted)  
- `SUSPICIOUS_PATH` (keywords set)  
- `SENDER_MISMATCH` (claimed intent vs sender pack)  
- `ZERO_WIDTH_CHARS`, `WEIRD_CAPS`, `EXCESSIVE_UNICODE` (minor bumps)

Weights are deterministic; sensitivity slider shifts thresholds, not weights.

## 6) UX Contract (minimum)
- **Row chip** under each SMS: shows registered domain + color. Tap → **Explain** bottom sheet (top 1–3 reasons).  
- **Notifications**: Amber/Red only. Actions: Open Safely (external browser/custom tab), Copy URL, Trash (→ Trash Vault), Whitelist Sender/Domain.  
- **Settings**: Sensitivity, per‑SIM, region packs, feature flags, OTP pass‑through.  
- **Assist Mode**: overlay banner/chip; Trash action “hides” locally (no provider delete).

---

# PHASED BUILD ORDER

## Phase 0 — Core Messaging App
**Goal:** Build a fully-functional SMS messaging app with modern UI.

**Deliverables**
1. **Message Composer**: Text input with send functionality via SmsManager
2. **Conversation Threading**: Group messages by sender into threads with proper sorting
3. **Contact Integration**: Resolve phone numbers to contact names and photos
4. **Message Receiving**: BroadcastReceiver for incoming SMS with notification handling
5. **Multi-SIM Support**: Detect and use appropriate SIM slot for sending
6. **Basic Management**: Delete messages, mark as read, search conversations

**Acceptance**
- Can send and receive SMS messages reliably
- Messages appear in correct conversation threads
- Contact names/photos display correctly
- Notifications work for new incoming messages
- App can be set as default SMS app

**Dependencies:** Telephony APIs, Contacts Provider, NotificationManager

---

## Phase 1 — Core Signals v0
**Goal:** Create end‑to‑end pipeline producing verdicts from raw SMS text (security layer on top of messaging).

**Deliverables**
1. **Link Extractor**: robust URL detection; Unicode normalize; detect scheme‑less URLs.  
2. **URL Expander**: OkHttp `HEAD`/`GET` with manual redirects (≤4); cache final URL (Room+LRU).  
3. **Domain Profiler**: PSL registeredDomain, punycode decode, homoglyph check, IP/port/userinfo, suspicious paths, scheme check.  
4. **Risk Engine (rules‑only)**: weights → score → Green/Amber/Red; emit structured reasons.

**Acceptance**
- Extractor recall ≥98% on corpus; 0 false positives on plain text.  
- Expansion ≤1.5s on first, ≤50ms cached.  
- `USERINFO_IN_URL` → Red; `HTTP_SCHEME`/`RAW_IP_HOST` → ≥Amber.  
- Per‑message CPU ≤300ms P50 on mid device.

**Interfaces**
- `fun analyze(message: Message): Verdict`  
- `fun expand(url: String): ExpandedUrl`  
- `fun profile(url: ExpandedUrl): DomainProfile`

**Dependencies:** PSL bundle; ICU4J; Room/Cache.

---

## Phase 2 — Surfaces
**Goal:** Visualize verdicts and alert only on issues.

**Deliverables**
1. **UI Decorator**: Compose chip + bottom sheet (Explain‑Why).  
2. **Notifications Manager**: Amber/Red channels; actions Open Safely, Copy URL.

**Acceptance**
- No notification for Green.  
- Chip shows final domain + color; bottom sheet lists top reasons (mapped labels).

**Dependencies:** Phase 1 pipeline.

---

## Phase 3 — Safety Rails
**Goal:** User control + reversible cleanup.

**Deliverables**
1. **Trash Vault**: soft-delete, 30‑day retention, Restore, auto‑purge (WorkManager). Default SMS: real provider delete; Assist Mode: local hide.  
2. **Allow/Block Lists**: sender/domain/pattern with precedence rules.  
3. **Settings**: sensitivity slider, per‑SIM toggles, OTP pass‑through, feature flags.

**Acceptance**
- Restore returns message to original thread (or un‑hides in Assist Mode).  
- Sensitivity changes verdicts without code change.  
- Allowlisted sender/domain forces Green unless **critical Red** rule is present.

**Dependencies:** Phase 2 UI surfaces; Telephony provider (if default).

---

## Phase 4 — Sender Intelligence + Privacy Guard
**Goal:** Sanity‑check message claims vs known senders; smooth first‑run.

**Deliverables**
1. **Sender Packs (IN v1)**: signed JSON (Ed25519) with carrier/bank/gov IDs + patterns.  
2. **First‑Run Flow**: privacy explainer; request Default SMS role; fallback to Assist Mode.

**Acceptance**
- “Missed call” intent from non‑carrier ID triggers `SENDER_MISMATCH`.  
- Invalid signature → discard pack; app functions with last good pack.

**Dependencies:** Phase 3 Settings for region; Phase 7 updater later for refresh.

---

## Phase 5 — Clarity Add‑ons
**Goal:** Safer context for links; transparent explanations.

**Deliverables**
1. **Safe Preview Fetcher**: GET cap 20–50KB, no JS, parse `<title>`, favicon bytes only, block `data:` redirects.  
2. **Audit & Explain‑Why**: persist rule IDs (not message text) for reproducible UI; show human labels.

**Acceptance**
- No remote scripts/images executed during preview.  
- Every Amber/Red shows ≥1 concrete reason.

**Dependencies:** Phase 1–2.

---

## Phase 6 — Language Signals (Rules + Optional ML)
**Goal:** Add lightweight language cues; optional on‑device classification.

**Deliverables**
1. **Grammar/Anomaly Rules**: zero‑width, weird caps, doubled spaces, excessive unicode.  
2. **Intent Classifier (TFLite, optional)**: OTP / delivery / bank / promo / missed‑call / phishing‑ish. Feature‑flagged.

**Acceptance**
- Classifier inference <10ms; model ≤2MB; memory <15MB peak.  
- Toggle in Settings; harmless when disabled.

**Dependencies:** Phase 1 outputs; Settings flags.

---

## Phase 7 — Freshness & Reliability
**Goal:** Keep packs/PSL fresh; ensure low battery and resilience.

**Deliverables**
1. **Update Service**: WorkManager fetch (metered only), verify Ed25519, atomic swap; offline fallback.  
2. **Cache/Storage Hardening**: LRU caps; cold‑start optimizations; battery audit.

**Acceptance**
- Airplane mode: app still works with cache; updates resume online.  
- 24h run: background battery <1–2%.

**Dependencies:** Sender Packs, PSL, Room/Cache.

---

## 7) Data & Storage (concise schemas)
- **Room tables:** `trash`, `overrides`, `packs`, `signals`, `verdicts`, `cache_expansions`, `config`.  
- **Indices:** `cache_expansions(finalUrl)`, `signals(messageId)`, `trash(expiresAt)`.  
- **DataStore (proto):** sensitivity, flags, region, per‑SIM prefs.

## 8) Feature Flags
- `ff_domain_age_check` (off by default)  
- `ff_safe_preview` (on)  
- `ff_intent_classifier_tflite` (off)  
- `ff_assist_mode_overlay` (on)

## 9) Performance & Quality Gates
- Analysis P50 ≤300ms; cold start ≤600ms; expansion timeout ≤1500ms; redirect depth ≤4.  
- Crash‑free sessions ≥99.5% in beta.  
- Unit tests for extractor/PSL/expander/risk; UI tests for chips/notifications/trash.

## 10) Build Dependencies (summary)
Kotlin, Compose, Hilt, Room, DataStore, OkHttp, Jsoup, ICU4J, WorkManager, Coil (favicons only), JUnit5, MockK, Turbine. No in‑app webview.

---

## 11) Success Criteria
- Users reliably see **true domains** and clear explanations.  
- Notifications occur **only** for issues.  
- Trash/Restore works predictably.  
- App remains private, fast, and battery‑light.

