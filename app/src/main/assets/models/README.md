# On-device model assets

Place production-validated assets here before enabling model-backed biomarker output:

- `face_landmarker.task` for MediaPipe Face Landmarker alignment.
- `erythema.tflite` for erythema index inference.
- `melanin_distribution.tflite` for melanin distribution inference.
- `pore_texture_density.tflite` for texture density inference.
- `acne_lesion_classifier.tflite` for lesion count and class inference.

The app currently saves each captured JPEG into private app storage and routes its bytes through `BiomarkerAnalyzer.analyzeCapturedFrame`. Until these assets are added and validated for Fitzpatrick IV-VI cohorts, successful image decodes record `ImageDerivedHeuristic` in the private markdown export. Decode failures record `DeterministicFallback`.
