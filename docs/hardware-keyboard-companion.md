# Hardware Keyboard Companion

1337 Board can show a compact two-row companion while a local USB, dock, or Bluetooth keyboard is connected to the Android device. The first row provides special, modifier, navigation, and arrow keys; the second keeps F1-F12 visible. The companion replaces the normal full-height touch keyboard while the system is in hardware-keyboard mode. On Android builds that permit candidates-only IME windows, that surface remains available as a fallback.

## Setup

1. Open **1337 Board Settings**.
2. Open **Hardware Keyboard Companion**.
3. Select **Automatic** to show the strip when Android detects a physical alphabetic keyboard. Use **Always** only for testing or devices whose keyboard attachment is not reported correctly.
4. Select an **Agent PBX**, **Terminal**, or **Minimal navigation** preset.
5. Adjust each companion row between 44dp and 72dp. The default two-row surface is 112dp tall.

Ctrl, Alt, and Shift use the same sticky-state behavior and active-key highlighting as the main keyboard when combined with other touch keys. Use the physical keyboard's modifier keys for combinations with its letter and number keys.

## Agent PBX Preset

The first row includes Esc, Tab, modifiers, Home, End, Page Up, Page Down, and arrows. The second row includes F1-F12, including Agent PBX's F8-F10 alert, editor-fullscreen, and workspace actions.

## Detection And Limitations

- Automatic mode watches Android input-device attachment and configuration changes. Virtual keyboards and 1337 Board's outbound Bluetooth HID targets are not treated as local hardware keyboards.
- If **Show virtual keyboard while physical keyboard is connected** is enabled in Android settings, the full touch keyboard takes precedence over the companion strip.
- OxygenOS may reject candidates-only IME windows while its hardware keyboard setting is off. 1337 Board handles this by requesting a compact input view containing only the companion rows.
- The OnePlus Pad3 cover keyboard is validated with `Automatic` attach/detach detection, the two-row Agent PBX layout, and touch-key dispatch.

Touch reliability counters are available from Diagnostics. Enable **Touch diagnostics overlay** only while investigating input behavior; counters contain timing and hit classifications, not typed text.
