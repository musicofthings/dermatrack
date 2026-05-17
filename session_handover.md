# Session Handover: DermaTrack AI

## Current State

The repository has been initialized, committed, and pushed to:

https://github.com/musicofthings/dermatrack.git

Initial commit:

```text
1ba3f97 Initial DermaTrack AI foundation
```

The working tree was clean after push.

## What Is Built

- Native Android project using Kotlin and Jetpack Compose.
- Room schema for local biomarker scan records and inventory items.
- Private app-storage vault path for raw scan image slots.
- Markdown-compatible scan metadata export.
- CameraX capture screen with:
  - front camera preview,
  - standardized capture guidance,
  - ML Kit face tracking overlay,
  - alignment reticle,
  - light-meter capture gate.
- Clinical report dashboard with:
  - biomarker bars,
  - melanin trend chart,
  - baseline-to-latest progress metrics,
  - IGA-style acne severity proxy for trend tracking,
  - clinical disclaimer,
  - scan history.
- Inventory logger for product-to-result correlation.
- Local regimen decision logic for biomarker deltas and ingredient pivots.
- FastAPI backend skeleton for non-biometric agentic orchestration and future Amazon India PA-API integration.

## Local Build Status

Android debug build now passes from this workspace:

```bash
./gradlew :app:assembleDebug
```

Python backend files were syntax-checked successfully with:

```bash
python3 -m py_compile backend/app/*.py
```

## Android Setup Steps

1. Install Android Studio.
2. Ensure these SDK components are installed:
   - Android SDK Platform 35
   - Android SDK Build-Tools
   - Android Emulator
   - Android SDK Platform-Tools
   - JDK 17 preferred
3. Open this folder in Android Studio:

```bash
/Users/theranosis_dx/projects/dermatrack_ai
```

4. Let Gradle sync.
5. Confirm these wrapper files exist:

```text
./gradlew
./gradlew.bat
gradle/wrapper/
```

6. Build the debug app:

```bash
./gradlew :app:assembleDebug
```

7. Run on an emulator or physical Android device.

## Recommended Test Pass

Use a physical Android device for best results because the app uses CameraX and `Sensor.TYPE_LIGHT`.

Test:

- Camera permission flow.
- Front camera preview appears.
- Ghost overlay renders over preview.
- Alignment reticle renders.
- Light gate updates when a light sensor exists.
- `Record Scan` creates a scan.
- Report screen shows biomarker bars and trend chart.
- Inventory entries save locally.
- App restart preserves scans and products through Room.

## Known Limitation

The current biomarker analyzer is a deterministic fallback, not a clinical model.

Follow-up work in this session added the image-frame path:

- CameraX `ImageCapture` now writes a real JPEG into private app storage before recording a scan.
- `MainViewModel.recordCapturedScan(...)` reads the saved private frame and routes it through `BiomarkerAnalyzer.analyzeCapturedFrame(...)`.
- The current analyzer uses a simple image-derived heuristic after a successful JPEG decode and records `ImageDerivedHeuristic` in private markdown metadata.
- Decode failures still fall back to `DeterministicFallback`.
- Expected model asset names are documented under `app/src/main/assets/models/README.md`.
- TFLite and MediaPipe task files are marked `noCompress` in Gradle resources.

Remaining fallback method:

```kotlin
BiomarkerAnalyzer.estimateFromCaptureQualityFallback(...)
```

Next engineering milestone:

1. Add MediaPipe Face Landmarker task asset.
2. Add TFLite models for:
   - erythema,
   - melanin distribution,
   - pore/texture density,
   - acne lesion classification.
3. Replace fallback inference with real image-frame inference.
4. Validate against Fitzpatrick IV-VI labeled datasets before presenting any biomarker as clinically meaningful.

## Product Direction

Keep the UX closer to clinical reporting than an agentic coach persona.

The agentic layer should appear as restrained decision logic after enough longitudinal evidence exists, especially for ingredient pivot suggestions such as stagnant melanin distribution after 21 days.

Prioritize the validated measurement MVP before expanding coaching:

1. Standardized capture controls.
2. Acne lesion detection/counting.
3. IGA/GEA-style severity trend scoring.
4. Erythema/redness quantification.
5. Pigmentation and spot/evenness metrics.
6. Baseline-to-follow-up deltas and visual trend reporting.
