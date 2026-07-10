# 🎨 PALETTE'S JOURNAL - CRITICAL UX & ACCESSIBILITY LEARNINGS

This journal is a curated repository of critical UX and accessibility learnings discovered during the development of **Signal Operator**.

*This is a living document. Only add entries for critical, non-obvious UX/a11y insights and learnings.*

## 2026-06-22 - Unified Interactive States for Retro Terminal Buttons
**Learning:** Hardcoded state variables like `isHovered` in custom Compose menu items remain dead unless explicitly wired to gesture listeners. Reusable retro components that require hover, touch press, and keyboard navigation focus are most robustly styled by collecting states from a `MutableInteractionSource`. This guarantees accessibility compliance (TalkBack/keyboard navigation) and provides instantaneous tactical feedback upon touch.
**Action:** Always prefer `MutableInteractionSource` with `collectIsHoveredAsState()`, `collectIsFocusedAsState()`, and `collectIsPressedAsState()` for interactive custom-styled components in Jetpack Compose instead of relying on manually managed state variables.

## 2026-06-22 - Interactive Signposting for Retro Log Lines
**Learning:** Flat text entries in retro terminals (like logging outputs) that support expandable tap actions often suffer from low discovery rates. Using subtle interaction triggers to render a soft thematic phosphor background glow (e.g., `alpha = 0.15f`) and appending a blinking-style cursor block (`▮`) at the row's trailing edge offers highly intuitive, context-appropriate signposting of clickability without introducing modern buttons that break immersion.
**Action:** Use soft high-contrast background highlights and retro indicators (`▮`, `>`) to signal interactive list rows inside terminals.

## 2026-07-04 - Dynamic Accessibility Labels for Icon Toggle Buttons
**Learning:** Static labels for icon-only buttons that toggle UI states (like opening/closing a map) fail to describe the outcome of interacting with the button to screen readers. For example, reading just \"Map\" does not communicate whether the action will display the map or dismiss it. Dynamically updating the content description based on the button's current active state bridges this context gap.
**Action:** Use conditional logic to supply contextually accurate content descriptions (e.g., `if (isOpen) \"Close\" else \"Open\"`) to all icon-only toggle buttons.

## 2026-07-06 - Semantic Roles for Custom Compose Clickables
**Learning:** Custom interactive components in Jetpack Compose (such as clickable `Row`s acting as list items or menu options) do not inherently announce themselves as buttons to screen readers, leaving users unaware they can interact with them.
**Action:** Always append the `semantics { role = Role.Button }` modifier (and appropriate `contentDescription`s when needed) to `.clickable` modifiers on non-standard button elements to ensure proper screen reader identification. Alternatively, you can use the `role` parameter on the `clickable` modifier natively.

## 2026-07-07 - Adding Interactive Map Node Accessibility
**Learning:** Location nodes that communicate their status purely through text color (`Color.Red` for corrupted, etc.) are invisible to screen readers and difficult for users with impaired vision or color-blindness.
**Action:** Extract map node UI into distinct components, provide a semantic `Role.Button` combined with `contentDescription` explaining the status textually (e.g., "Status: Corrupted"), and handle `MutableInteractionSource` to create visible active states when the item is hovered or focused via accessibility tools.
## 2024-05-18 - Jetpack Compose Focusable Clickable Redundancy
**Learning:** In Jetpack Compose, the `Modifier.clickable()` inherently makes the composable focusable. Adding an explicit `.focusable()` modifier when `.clickable()` is already present is redundant and can cause issues like double-focusing for keyboard or screen reader users.
**Action:** Remove redundant `.focusable()` modifiers from elements that are already using `.clickable()`. Ensure correct semantic roles (e.g. `role = Role.Button`) are applied to clickable elements instead of arbitrary text components to ensure correct screen reader announcements.
