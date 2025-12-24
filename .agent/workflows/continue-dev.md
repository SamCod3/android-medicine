---
description: Resume development on MediTrack Android app
---

# Continue MediTrack Development

## Project Context
MediTrack is an Android app for scanning Spanish medication barcodes and managing treatments.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, CameraX, ML Kit, Room, Retrofit, Koin

**API:** CIMA AEMPS (Spanish medication database)

**Current Branch:** `feature/ai-integration`

## ✅ Recently Completed (Dec 24, 2024)

### Mi Agenda - In-Row Swipe Confirmation 🚀
- **Pattern:** "In-Row Transformation" (No dialogs).
- **Behavior:** Swiping transforms the card into a confirmation row (Solid Red for Delete, Blue for Toggle).
- **Design:**
  - **Height:** 72dp (increased for better touch targets).
  - **Padding:** 16dp start padding for visual breathing room.
  - **Background:** Opaque (matches container) to prevent visual overlap glitches.
  - **Safety:** Large explicit confirmation buttons with 32dp separation.
- **Tech:** `SwipeToDismissBox` + `AnimatedContent` state transition.

### Other Improvements
- **Accordion:** Collapsible time sections with 3D effect.
- **AI Summary:** Full-screen bottom sheet with dynamic WebView.

## 🔴 PENDING: Next Steps

- **User Testing:** Verify swipe interaction in real-world usage over a few days.
- **Next Feature:** (To be defined by user).

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
