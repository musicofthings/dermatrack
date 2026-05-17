# DermaTrack AI

Privacy-first Android app for longitudinal skin health biomarker tracking, calibrated around clinical reporting for Fitzpatrick IV-VI skin tones.

## What is built

- Native Kotlin and Jetpack Compose Android project.
- Local-only Room database for numerical biomarker records and product inventory.
- Private app vault path for raw scan image slots.
- Camera capture now writes scan JPEGs into that private vault before analysis.
- Markdown-compatible metadata export under private app storage.
- CameraX capture screen with standardized capture guidance, ML Kit face tracking, alignment gate, and light-meter gate.
- Clinical report dashboard with biomarker bars and melanin trend chart.
- Baseline-to-latest progress metrics for lesion count, erythema, and pigmentation.
- IGA-style acne severity proxy derived from lesion count for trend tracking, not diagnosis.
- Inventory logger for product-to-result correlation.
- Agentic regimen decision logic for longitudinal deltas and ingredient pivots.
- Interfaces for MediaPipe face landmarking, TFLite biomarker inference, and Amazon India PA-API.

## Privacy posture

Raw biometric images are intended to remain in Android internal app storage. The Room database stores biomarker numbers and capture metadata only. Android backup is disabled in the manifest and data extraction rules.

## Current model status

The app includes model contracts plus an image-derived heuristic so the screens and private JPEG data flow can be exercised before the real TFLite and MediaPipe assets are added. Captured JPEG bytes flow through `BiomarkerAnalyzer.analyzeCapturedFrame`, and successful decodes record `ImageDerivedHeuristic` in the private markdown export. If a frame cannot be decoded, the analyzer records `DeterministicFallback`. Neither path is a clinical model.

Expected production model path:

1. Add MediaPipe Face Landmarker task asset for facial alignment.
2. Add TFLite models for erythema, melanin distribution, texture density, and acne lesion classification.
3. Replace `BiomarkerAnalyzer.estimateFromCaptureQualityFallback` with image-frame inference.
4. Validate against Fitzpatrick IV-VI labeled datasets before presenting any biomarker as clinically meaningful.

Expected on-device model filenames are documented in `app/src/main/assets/models/README.md`.

## Evidence-aligned MVP

The product should prioritize validated measurement and longitudinal tracking features before adding broader coaching:

1. Guided standardized capture with consistent distance, frontal baseline, neutral expression, even lighting, and plain background.
2. Acne lesion detection/counting with inflammatory and non-inflammatory separation.
3. Acne severity trend scoring aligned to IGA/GEA-style outputs.
4. Erythema/redness quantification from controlled color analysis or model inference.
5. Pigmentation and spot/evenness metrics with tone-aware validation for Fitzpatrick IV-VI.
6. Time-series deltas against baseline, shown as progress metrics rather than diagnostic claims.

## Build

This repository is scaffolded as a standard Android Gradle project. On a machine with Android Studio or Gradle wrapper installed:

```bash
./gradlew :app:assembleDebug
```

Debug assembly now passes in this workspace.

## Product direction

The interaction model intentionally leans toward clinical reporting. The agentic coach appears as restrained decision logic after enough longitudinal evidence exists, not as a motivational chat surface.
