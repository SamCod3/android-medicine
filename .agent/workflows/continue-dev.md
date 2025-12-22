---
description: Resume development on MediTrack Android app
---

# Continue MediTrack Development

## Project Context
MediTrack is an Android app for scanning Spanish medication barcodes and managing treatments.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, CameraX, ML Kit, Room, Retrofit, Koin

**API:** CIMA AEMPS (Spanish medication database)

**Current Branch:** `feature/ai-integration`

## ✅ AI Integration & Import/Export COMPLETE
- **Search:** Gemini Nano OCR & Smart Filters (Integrated)
- **Leaflet:** AI Summaries & Structured Parsing (Integrated)
- **Data Management:** 
    - Full JSON Backup & Restore (Implemented)
    - "Smart Save" de-duplication logic (Implemented)
    - PDF Import removed (Simplified UI)

## 🔴 NEXT TASKS: Refinement & Testing

### High Priority
1. **Medication Details Screen** - Apply AI to:
   - Summarize key drug information
   - Extract warnings/contraindications
   - Provide quick patient-friendly explanations

2. **Prospectus/Leaflet Screen** - Refinement:
   - Answer user questions about the medication
   - Highlight important warnings
   
### Implementation Notes
- Reuse `HybridAIService` for AI access
- Leverage `BackupUseCase` for any data migration needs

## Quick Start

// turbo-all

1. Check current git status and branch
```bash
git status && git log --oneline -5
```

2. Build to verify everything works
```bash
./gradlew assembleDebug
```

3. Review task.md for pending items

## Key Files

| Area | File |
|------|------|
| **AI Services** | `ai/GeminiNanoService.kt`, `ai/HybridAIService.kt` |
| **AI Status** | `ai/AIStatusChecker.kt` |
| **Tool Actions** | `ai/AIService.kt` (ToolAction sealed class) |
| Scanner | `ui/screens/scanner/ScannerScreen.kt` |
| Search | `ui/screens/search/SearchScreen.kt` |
| **Details** | `ui/screens/details/MedicationDetailScreen.kt` |
| **Leaflet** | `ui/screens/leaflet/LeafletScreen.kt` |
| API | `data/remote/api/CimaApiService.kt` |

## Common Tasks

### Add AI to a new screen
1. Inject `AIService` via Koin in `AppModule.kt`
2. Call `aiService.processXxx()` methods
3. Handle `ToolAction` results with `when` statement

### Test on device
```bash
./gradlew installDebug && adb shell am start -n com.samcod3.meditrack/.MainActivity
```
