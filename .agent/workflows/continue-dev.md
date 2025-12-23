---
description: Resume development on MediTrack Android app
---

# Continue MediTrack Development

## Project Context
MediTrack is an Android app for scanning Spanish medication barcodes and managing treatments.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, CameraX, ML Kit, Room, Retrofit, Koin

**API:** CIMA AEMPS (Spanish medication database)

**Current Branch:** `feature/ai-integration`

## ✅ Recently Completed (Dec 23, 2024)

### AI Summary Section BottomSheet Improvements
- **Full-screen BottomSheet:** `skipPartiallyExpanded = true`
- **Accordion behavior:** Summary hidden when showing WebView
- **WebView with dynamic height:** Uses `weight(1f)` for full available space
- **WebView LayoutParams:** MATCH_PARENT for proper sizing
- **Font size:** Increased to 17px for better readability

### Mi Agenda Accordion Redesign
- **Collapsible sections by time slot:** Headers like "07:50 (2)" are clickable
- **Auto-expand next slot:** Calculates which time ≥ current time to expand
- **CollapsibleTimeHeader:** 16dp corners, count badge in blue pill, chevron on right
- **Blue border:** Highlighted + expanded sections have blue border
- **12dp spacing:** Between accordion sections
- **CompactReminderCard:** Truncated medication names, compact layout

## 🔴 PENDING: Improve CompactReminderCard

The medication rows inside expanded accordion sections need refinement:
- Match mockup design more closely
- Consider removing visible trash icon (swipe gesture instead?)
- Better visual hierarchy between medication name and dose
- Cards should feel as polished as the headers

Reference mockup: See `agenda_collapsible_*.png` in brain folder

## Quick Start

// turbo-all

1. Check current git status and branch
```bash
git status && git log --oneline -3
```

2. Build to verify everything works
```bash
./gradlew assembleDebug
```

3. Install and test
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.samcod3.meditrack/.MainActivity
```

## Key Files

| Area | File |
|------|------|
| **AI Summaries** | `domain/usecase/SectionSummaryUseCase.kt` |
| **Refinement Modes** | `domain/model/RefinementMode.kt` |
| **AI Services** | `ai/GeminiNanoService.kt`, `ai/HybridAIService.kt` |
| **Leaflet Parser** | `ai/AILeafletParser.kt` |
| **Leaflet UI** | `ui/screens/leaflet/LeafletScreen.kt` |
| **Leaflet VM** | `ui/screens/leaflet/LeafletViewModel.kt` |
| **Section WebView** | `ui/components/SectionWebView.kt` |
| **Mi Agenda** | `ui/screens/allreminders/AllRemindersScreen.kt` |
| **Cache DAO** | `data/local/dao/SectionSummaryCacheDao.kt` |

## Recent Work
- Section BottomSheet: Accordion + WebView full-screen
- Mi Agenda: Collapsible time sections with auto-expand
