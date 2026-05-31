# Session Handover
_Generated: 2026-05-31T06:38:00Z_
_Branch: main_
_Trigger: user request (`/handover` via code review) | Context at compact: 100% of pro window_
_Compact count this project: 0_

---

## 🎯 Active Task
**What we're building/fixing:**
DermaTrack AI — native Android (Kotlin + Compose) skin-biomarker tracking app. Last branch of work integrated scientifically validated clinical features from the PRD: standardized capture, IGA-style severity proxy, baseline-to-follow-up deltas, expanded face mesh + clinical reticle, and a deterministic biomarker analyzer with image-derived heuristic fallback. ML model slots (TFLite + MediaPipe Face Landmarker) are scaffolded but not yet populated.

**Phase:** Post-PRD-integration code review, before model-asset population.
**Next action:** Run `/code-review` on the diff `1ba3f97..bf19fbc` (everything after the initial foundation), focused on `MainViewModel.kt`, `BiomarkerAnalyzer.kt`, `FaceDetectionFrameAnalyzer.kt`, and `DermaTrackAppRoot.kt`.

---

## ✅ Completed This Session
- [x] CameraX `ImageCapture` writes JPEG to private app storage before scan record
- [x] `MainViewModel.recordCapturedScan(...)` decodes saved frame, routes through `BiomarkerAnalyzer.analyzeCapturedFrame(...)`
- [x] `BiomarkerAnalyzer` image-derived heuristic (records `ImageDerivedHeuristic` source); decode-fail falls back to `DeterministicFallback`
- [x] Model asset slots documented at `app/src/main/assets/models/README.md`; `.tflite`/`.task` marked `noCompress`
- [x] Expanded mesh coverage, clinical reticle, stage-aware guidance, build errors resolved
- [x] PRD clinical features integrated (IGA proxy, erythema/melanin/pore metrics, longitudinal deltas)
- [x] Gradle wrapper committed, `./gradlew :app:assembleDebug` passes

---

## 🔄 In Progress (Exact Resume Point)
**Branch:** `main`
**Last commit:** `bf19fbc Integrate scientifically validated clinical features from PRD`
**Next immediate action:** Code review of the post-foundation diff and address findings inline; then begin populating real model assets (MediaPipe Face Landmarker `.task` + TFLite biomarker models).

---

## 📋 Remaining Work
1. Code review on current diff — focus on `MainViewModel`, `BiomarkerAnalyzer`, `FaceDetectionFrameAnalyzer`, `DermaTrackAppRoot`.
2. Add MediaPipe Face Landmarker task asset.
3. Add TFLite models: erythema, melanin distribution, pore/texture density, acne lesion classification.
4. Replace `estimateFromCaptureQualityFallback` with real image-frame inference.
5. Validate against Fitzpatrick IV–VI labeled datasets before presenting biomarkers as clinically meaningful.
6. Restrained agentic ingredient-pivot logic after ≥21d longitudinal evidence (no coach persona).

---

## 🏗 Architecture Decisions Made
| Decision | Rationale | Date |
|----------|-----------|------|
| Room DB local-only; no network for biometric data | Privacy / data minimization | 2026-05 |
| Capture path: CameraX `ImageCapture` → private app storage → analyzer | Deterministic input vs. live preview frames | 2026-05 |
| Analyzer fallback chain: ImageDerivedHeuristic → DeterministicFallback | Build degrades safely until real models land | 2026-05 |
| `.tflite` / `.task` marked `noCompress` in Gradle | TFLite/MediaPipe require uncompressed assets | 2026-05 |
| Clinical reporting UX over agentic coach persona | PRD direction; trust before delight | 2026-05 |

---

## 🔧 Commands to Resume
```bash
# On any machine after git pull:
git pull origin main
bash scripts/session_sync.sh --load  # (script lives in context-engineering-kit, not this repo)

# Android build:
./gradlew :app:assembleDebug

# Python backend syntax check:
python3 -m py_compile backend/app/*.py

# In Claude Code:
# /context-health     — verify hooks
# /handover           — refresh this file
# /token-status       — check context usage
# /code-review        — review current diff
```

---

## 📁 Files Modified This Session (since 1ba3f97)
| File | Status |
|------|--------|
| README.md | modified |
| app/build.gradle.kts | modified |
| app/src/main/AndroidManifest.xml | modified |
| app/src/main/assets/models/README.md | added |
| app/src/main/java/com/dermatrack/ai/MainViewModel.kt | modified (+111) |
| app/src/main/java/com/dermatrack/ai/analysis/BiomarkerAnalyzer.kt | added (+165) |
| app/src/main/java/com/dermatrack/ai/analysis/MediaPipeFaceLandmarker.kt | added |
| app/src/main/java/com/dermatrack/ai/capture/FaceDetectionFrameAnalyzer.kt | added |
| app/src/main/java/com/dermatrack/ai/capture/FaceTrackingState.kt | added |
| app/src/main/java/com/dermatrack/ai/data/AppContainer.kt | modified |
| app/src/main/java/com/dermatrack/ai/data/VaultRepository.kt | modified |
| app/src/main/java/com/dermatrack/ai/ui/DermaTrackAppRoot.kt | modified (+746) |
| gradle.properties | modified |
| gradle/libs.versions.toml | modified |
| gradle/wrapper/* | added |
| gradlew, gradlew.bat | added |
| session_handover.md | modified |
| settings.gradle.kts | modified |

---

## 🌿 Git Context
```
Branch  : main
Commit  : bf19fbc Integrate scientifically validated clinical features from PRD
Status  : dirty (.claude/session/* + worktree pointers only — no source changes)
```

Recent commits:
```
bf19fbc Integrate scientifically validated clinical features from PRD
7821d06 Refine UI: expand mesh coverage, add clinical reticle, and fix stage-aware guidance
ead49ce Resolve build errors and implement facial alignment architecture
1ba3f97 Initial DermaTrack AI foundation
```

---

## ⚠️ Critical Rules
- Never commit secrets or API keys.
- Room DB is local-only; **no network calls for biometric data**.
- TFLite + MediaPipe slots are scaffolded but empty — **do not fabricate model outputs**; only present heuristic source labels.
- Validate any clinical-looking metric against Fitzpatrick IV–VI labels before user-facing claims.
- Run `/handover` before switching devices.

---

## 🧬 Bioinformatics Context (if applicable)
- Not applicable — this is a mobile clinical-imaging project, not a genomics pipeline.

---
_Auto-updated by `pre-compact.sh` hook and `/handover` skill._
_Read this at the start of every session. Update with `/handover`._
