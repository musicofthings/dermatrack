# CLAUDE.md — DermaTrack AI

Project-specific rules. The global rules in `~/CLAUDE.md` and `~/.claude/CLAUDE.md`
still apply on top of this; this file only adds DermaTrack-specific constraints.

---

## Active work context

Read `session_handover.md` first — it has the live task state, completed items,
and next milestone. This file is for rules that do not change between sessions.

---

## Stack

- Native Android, Kotlin + Jetpack Compose.
- Build: `./gradlew :app:assembleDebug` (single-module `:app`).
- Single quick-compile check: `./gradlew :app:compileDebugKotlin`.
- Min SDK 26, target SDK 35, JDK 17.
- Persistence: Room (local-only).
- Camera: CameraX `Preview` + `ImageAnalysis` + `ImageCapture`.
- Face tracking: ML Kit `FaceDetection` (live preview), MediaPipe Face Landmarker
  (intended for post-capture alignment scoring — model asset not yet shipped).
- Light sensor: `Sensor.TYPE_LIGHT` for capture gating.

## Non-negotiable rules

1. **Room DB is local-only. Never add network calls for biometric data.**
   Inventory + scans + images live entirely in the app's private storage. The
   FastAPI backend skeleton in `backend/` is for non-biometric orchestration
   only (e.g. future Amazon India PA-API product lookup).

2. **Never fabricate model outputs.** TFLite + MediaPipe model slots in
   `app/src/main/assets/models/` are currently empty. If a model isn't loaded,
   return `0f` / no-signal, or `BiomarkerAnalysisSource.DeterministicFallback`
   — never a hardcoded placeholder confidence value. The analyzer's source
   field (`OnDeviceModel` / `ImageDerivedHeuristic` / `DeterministicFallback`)
   is what the report screen and exported markdown surface to the user;
   misreporting it is a clinical-credibility bug.

3. **Validate against Fitzpatrick IV–VI before user-facing clinical claims.**
   The spot-detection thresholds in `BiomarkerAnalyzer.spotThresholdFor()` are
   tone-adapted; do not regress them to absolute cutoffs. Auto-detection of
   Fitzpatrick group is still TODO — when added, populate
   `detectedFitzpatrick` in `estimateFromImageHeuristic()`; do not switch on
   the input parameter.

4. **Alignment quality is reported separately, not folded into biomarker
   magnitude.** A misaligned capture must not be reported as more
   inflammation. `ScanEntity.alignmentScore` is the channel for capture
   quality; biomarker values themselves should reflect skin state.

5. **Image file ↔ scan row consistency.** If `scanRepository.insertScan` fails,
   delete the private JPEG. If the markdown export fails, log only — never
   delete the image or roll back the scan row. See
   `MainViewModel.recordCapturedScan` for the canonical pattern.

6. **Release native ML Kit / MediaPipe resources on teardown.** Any
   `FaceDetector` / `FaceLandmarker` instance must be `close()`d when its
   owning Composable / Analyzer is disposed. See `DisposableEffect` block in
   `DermaTrackAppRoot` `CaptureScreen`.

7. **Inflammatory/non-inflammatory acne split lives behind one constant.**
   `BiomarkerAnalyzer.INFLAMMATORY_RATIO`. Do not introduce path-specific
   ratios.

## Codex cruft policy

The user has explicitly flagged a wariness of codex/agent-generated cruft.
Prefer editing existing files over adding new ones. Do not add error handling
for scenarios that cannot happen. Do not add comments unless the *why* is
non-obvious. Do not leave commented-out code behind a refactor.

## Aggressive-upgrade preference

Library bumps go to latest stable, not conservative pinning. Delete dead code
on the way through; do not park it behind feature flags. (Source: user
memory.)

## Test pass

The capture/face/light-meter pipeline needs a physical Android device — the
emulator has no `Sensor.TYPE_LIGHT`. Minimum manual test before claiming a
capture-path change works:

- Camera permission flow.
- Front preview renders.
- ML Kit mesh + clinical reticle render over preview.
- Light gate updates when `Sensor.TYPE_LIGHT` is present.
- `Record Scan` writes JPEG, inserts ScanEntity, and exports markdown.
- App restart preserves scans and products (Room).

## Open milestones (highest first)

1. Drop MediaPipe Face Landmarker `.task` into `app/src/main/assets/models/`
   and wire real inference in `MediaPipeFaceLandmarker.estimateAlignment`.
2. Ship TFLite models: erythema, melanin distribution, pore/texture density,
   acne lesion classification. Replace
   `BiomarkerAnalyzer.estimateFromCaptureQualityFallback` with real inference.
3. Validate against Fitzpatrick IV–VI labeled datasets before any biomarker is
   surfaced as clinically meaningful.
4. Tone classifier → populate `detectedFitzpatrick` in
   `estimateFromImageHeuristic`.
