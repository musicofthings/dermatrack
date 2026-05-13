# DermaTrack AI

Privacy-first Android app for longitudinal skin health biomarker tracking, calibrated around clinical reporting for Fitzpatrick IV-VI skin tones.

## What is built

- Native Kotlin and Jetpack Compose Android project.
- Local-only Room database for numerical biomarker records and product inventory.
- Private app vault path for raw scan image slots.
- Markdown-compatible metadata export under private app storage.
- CameraX capture screen with ghost overlay, alignment gate, and light-meter gate.
- Clinical report dashboard with biomarker bars and melanin trend chart.
- Inventory logger for product-to-result correlation.
- Agentic regimen decision logic for longitudinal deltas and ingredient pivots.
- Interfaces for MediaPipe face landmarking, TFLite biomarker inference, and Amazon India PA-API.

## Privacy posture

Raw biometric images are intended to remain in Android internal app storage. The Room database stores biomarker numbers and capture metadata only. Android backup is disabled in the manifest and data extraction rules.

## Current model status

The app includes model contracts and a deterministic fallback estimator so the screens and data flow can be exercised before the real TFLite and MediaPipe assets are added. The fallback is not a clinical model.

Expected production model path:

1. Add MediaPipe Face Landmarker task asset for facial alignment.
2. Add TFLite models for erythema, melanin distribution, texture density, and acne lesion classification.
3. Replace `BiomarkerAnalyzer.estimateFromCaptureQualityFallback` with image-frame inference.
4. Validate against Fitzpatrick IV-VI labeled datasets before presenting any biomarker as clinically meaningful.

## Build

This repository is scaffolded as a standard Android Gradle project. On a machine with Android Studio or Gradle wrapper installed:

```bash
./gradlew :app:assembleDebug
```

This workspace did not contain Gradle or a populated Android SDK cache when the project was created, so local compilation was not available in this session.

## Product direction

The interaction model intentionally leans toward clinical reporting. The agentic coach appears as restrained decision logic after enough longitudinal evidence exists, not as a motivational chat surface.
