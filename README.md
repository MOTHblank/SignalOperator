# Signal Operator

Signal Operator is a retro terminal-styled interactive simulator for Android, built using modern native development practices. Players act as a terminal operator, scanning radio frequencies, calibrating signals on an oscilloscope, and decoding cipher telemetry.

## Key Features

*   **Analog Radio Dial Tuning**: A dial-scanning interface (88–108 MHz) with drift mechanics, proximity noise filtering, and a locked signal indicator LED.
*   **Oscilloscope Visualizer**: A custom canvas-based waveform visualizer that simulates a cathode-ray tube (CRT) display. Players align live synthesized sine waves with reference target waveforms using Gain and Filter controls.
*   **Telemetry Decoder Panel**: A touch-driven terminal dashboard used to solve multi-stage tactical decryption puzzles, including:
    *   Boolean logic gates
    *   Coordinate grids
    *   NATO phonetic translation
    *   Chronological timeline reconstruction
    *   Vigenère shift ciphers
*   **Procedural Audio Engine**: High-fidelity analog DSP synthesizer incorporating custom soft-tube saturation, overdrive, bandpass filtering, ring modulation, and mechanical keyboard acoustics.
*   **Retro Visual Styling**: Clean, scanline-shadowed amber monochromatic CRT aesthetics built with high-performance Jetpack Compose Canvas operations.

## Architecture & Technology Stack

*   **Platform**: Android (Min SDK 26, Target SDK 36)
*   **Framework**: Jetpack Compose (100% Kotlin-first declarative UI)
*   **Architecture**: Persistent campaign state (`GameState`) and transient tuner state (`SignalRuntimeState`) are exposed with `StateFlow`. Pure domain components own campaign reduction, hotspot planning, signal generation, and router topology; `MainViewModel` orchestrates lifecycle, persistence, audio, and UI intents.
*   **Audio Implementation**: `SoundPool` handles generated UI effects, while a streaming `AudioTrack` synthesizes radio static/drone layers and a reusable voice track applies radio DSP to Android TTS output.
*   **Gesture Handling**: Full-screen immersive display (`WindowCompat`) with back-gesture interception to prevent accidental app exits.
