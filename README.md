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

*   **Platform**: Android (Min SDK 26, Target SDK 34)
*   **Framework**: Jetpack Compose (100% Kotlin-first declarative UI)
*   **Architecture Pattern**: Model-View-Intent (MVI) utilizing `StateFlow` and structured tick-loops inside a central `ViewModel` as the single source of truth.
*   **Audio Implementation**: `SoundPool` engine integrated with a procedural WAV file generation system at startup. This prevents dynamic `AudioTrack` recreation, eliminates ashmem pinning warnings, and reduces playback latency.
*   **Gesture Handling**: Full-screen immersive display (`WindowCompat`) with back-gesture interception to prevent accidental app exits.
