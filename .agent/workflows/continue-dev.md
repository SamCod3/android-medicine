---
description: Resume development on MediTrack Android app
---

# Continue MediTrack Development

## Project Context
MediTrack is an Android app for scanning Spanish medication barcodes and managing treatments.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, CameraX, ML Kit, Room, Retrofit, Koin

**API:** CIMA AEMPS (Spanish medication database)

**Current Branch:** `feature/ai-integration`

## ✅ Recently Completed (Dec 2024)

### AI Summary System
- **Contextual Refinement Menu:** "Opciones" button with section-specific modes
  - Dosage: Focus dosage, For child, For elderly
  - Side Effects: Serious only, All effects
  - Interactions: Alcohol, Pregnancy
  - General: Regenerate, More detail, Simpler
- **Glassmorphism Progress Indicator:** Animated gradient bar with bounce effect
- **Markdown Cleanup:** `cleanMarkdown()` post-processing removes residual formatting
- **Improved Prompts:** Explicit instructions to use ONLY provided content

### Data Layer
- **Section Summary Cache:** `SectionSummaryCacheDao` for caching AI summaries
- **Backup System:** Full JSON export/import with de-duplication

## 🔴 NEXT: AI Reformatting

### Proposal: Use Gemini Nano for Content Reformatting
Current `AILeafletParser` uses two-pass heuristic approach.
Proposal: Let AI reformat content with simple markers, then parse to ContentBlocks.

**Benefits:**
- Better detection of non-standard lists
- Handles malformed HTML better
- Uses same chunking strategy as summaries

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
| **Cache DAO** | `data/local/dao/SectionSummaryCacheDao.kt` |

## Recent Commits
- `feat(ai): add contextual refinement menu and glassmorphism progress indicator`
