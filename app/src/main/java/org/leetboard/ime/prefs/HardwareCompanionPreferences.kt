package org.leetboard.ime.prefs

enum class HardwareCompanionMode {
    OFF,
    AUTOMATIC,
    ALWAYS,
}

enum class HardwareCompanionPreset {
    AGENT_PBX,
    TERMINAL,
    MINIMAL,
}

const val DEFAULT_HARDWARE_COMPANION_HEIGHT_DP = 56f
const val MIN_HARDWARE_COMPANION_HEIGHT_DP = 44f
const val MAX_HARDWARE_COMPANION_HEIGHT_DP = 72f
