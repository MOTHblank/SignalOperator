# Signal Operator

Signal Operator is a retro terminal-styled interactive simulator for Android, built using modern native development practices. Players act as a terminal operator, scanning radio frequencies, calibrating signals on an oscilloscope, and decoding cipher telemetry.

## Key Features

*   **Analog Radio Dial Tuning**: A dial-scanning interface (88–108 MHz) with drift mechanics, proximity noise filtering, persistent discovered-carrier memory, and a locked signal indicator LED.
*   **Oscilloscope Visualizer**: A custom canvas-based waveform visualizer that simulates a cathode-ray tube (CRT) display. Players align live synthesized sine waves with reference target waveforms using Gain and Filter controls.
*   **Telemetry Decoder Panel**: A touch-driven terminal dashboard used to solve multi-stage tactical decryption puzzles, including:
    *   Boolean logic gates
    *   Coordinate grids
    *   NATO phonetic translation
    *   Chronological timeline reconstruction
    *   Vigenère shift ciphers
*   **Procedural Audio Engine**: High-fidelity analog DSP synthesizer incorporating custom soft-tube saturation, overdrive, bandpass filtering, ring modulation, and mechanical keyboard acoustics.
*   **Strategic Sector Network**: Decoded mission signals alter named sites and agents. Security, threat, containment, exposure, ECHO trust, firewall breaches, reinforcement resources, and corrupted network links persist through the run and affect the ending.
*   **Consequential Signal Triage**: Mission, civilian, and kernel-telemetry signals have distinct roles. Ignoring mission traffic advances the incident but loses intelligence; dead drops provide strategic resources rather than campaign progress.
*   **Retro Visual Styling**: Clean, scanline-shadowed monochromatic CRT aesthetics built with Jetpack Compose Canvas and AGSL.

## Architecture & Technology Stack

*   **Platform**: Android (Min SDK 26, Target SDK 36)
*   **Framework**: Jetpack Compose (100% Kotlin-first declarative UI)
*   **Architecture**: Persistent campaign state (`GameState`) and transient tuner state (`SignalRuntimeState`) are exposed with `StateFlow`. Pure domain components own campaign reduction, hotspot planning, signal generation, and router topology; `MainViewModel` orchestrates lifecycle, persistence, audio, and UI intents.
*   **Audio Implementation**: `SoundPool` handles generated UI effects, while a streaming `AudioTrack` synthesizes radio static/drone layers and a reusable voice track applies radio DSP to Android TTS output.
*   **Gesture Handling**: Full-screen immersive display (`WindowCompat`) with back-gesture interception to prevent accidental app exits.
