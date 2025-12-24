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

### Swipe-Reveal Estilo Apple Mail 🍎
- **Component:** `PastReminderCard` en pestaña "Pasados"
- **Behavior:** Deslizar → revela [Tomado][Omitir] a la izquierda
- **Effects:**
  - Botones crecen progresivamente (0 → 80dp)
  - Iconos aparecen al 30%, texto al 60%
  - Pista visual si swipe incorrecta (peek + vibración)
- **Indicator:** 6 puntos (2x3) estilo iOS, color blanco
- **State:** key(reminder.id) evita persistencia al cambiar item

### Agenda con Pestañas
- Pestañas Pendientes/Pasados
- Filtrado por hora actual
- Acciones desde UI y notificaciones

### Sistema de Notificaciones
- ReminderAlarmScheduler integrado
- Botones "Tomar" y "Omitir" en notificación
- Deep link al perfil correcto

## 🔴 BUGS PENDIENTES

### Navegación desde Notificaciones
- Al pulsar notificación abre Agenda pero NO va a la pestaña correcta
- Debería abrir pestaña "Pasados" si el recordatorio ya pasó

## 📋 Mejoras Futuras

- Extraer `PastReminderCard` a componente reutilizable
- Animaciones de eliminación al marcar toma

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
| **Mi Agenda** | `ui/screens/allreminders/AllRemindersScreen.kt` |
| **Swipe-Reveal** | `PastReminderCard` (private fun en AllRemindersScreen) |
| **Notificaciones** | `notification/ReminderNotificationHelper.kt` |
| **Deep Link** | `MainActivity.kt`, `NavHost.kt` |

