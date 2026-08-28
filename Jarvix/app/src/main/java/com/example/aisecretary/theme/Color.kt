package com.example.aisecretary.theme

import androidx.compose.ui.graphics.Color

// ── JARVIS HUD Background Layers ─────────────────────────────────────────────
// Deep void black of the Iron Man suit's inner display
val DeepNavy    = Color(0xFF010409)   // Near-void black
val SurfaceNavy = Color(0xFF0D1117)   // Dark panel background
val CardNavy    = Color(0xFF0D1F2D)   // Holographic card surface
val CardNavyElevated = Color(0xFF112233) // Elevated card, subtle blue tint

// ── Arc Reactor Core Colors ────────────────────────────────────────────────
// The iconic glowing blue-white of the arc reactor
val ArcBlue      = Color(0xFF00C8FF)  // Arc reactor electric blue — primary brand
val ArcBlueLight = Color(0xFF7DE8FF)  // Bright arc glow (lighter)
val ArcBlueDim   = Color(0xFF005F7A)  // Dimmed arc, for borders & outlines

// ── Holographic Cyan Accent ────────────────────────────────────────────────
val AquaAccent    = Color(0xFF00FFD1)  // JARVIS interface teal/cyan
val AquaAccentDim = Color(0xFF00998A)  // Dimmer teal for secondary elements

// ── Iron Gold / Suit Accent ────────────────────────────────────────────────
// The warm gold of the Iron Man armor trim
val IronGold    = Color(0xFFFFC200)  // Suit gold — warnings, highlights
val IronGoldDim = Color(0xFF997500)  // Dimmed gold

// ── Text Colors ────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFE8F4FD)  // Cool white with blue tint
val TextSecondary = Color(0xFF6EA8CC)  // Secondary HUD text — muted blue
val TextMuted     = Color(0xFF2A4D63)  // Very dim, subtle UI labels

// ── Status Colors ──────────────────────────────────────────────────────────
val SuccessGreen = Color(0xFF00FF88)  // JARVIS "system online" green
val WarnAmber    = IronGold           // Iron gold doubles as warning
val ErrorRed     = Color(0xFFFF3B3B)  // Alert red — system critical

// ── Aliases for backward compat with MainScreen.kt ────────────────────────
// (Maps old violet names → new arc blue so no other file needs to change)
val ElectricViolet      = ArcBlue
val ElectricVioletLight = ArcBlueLight
