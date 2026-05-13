# Keep MediaPipe and TensorFlow Lite task classes reachable in release builds.
-keep class com.google.mediapipe.** { *; }
-keep class org.tensorflow.** { *; }
