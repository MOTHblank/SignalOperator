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

## 2024-05-18 - Retro Thematic States in Jetpack Compose
**Learning:** Default Android ripples clash heavily with retro aesthetics (like terminals or oscilloscopes). Using `MutableInteractionSource` to manage `focus`, `hover`, and `press` states, and applying a soft transparent background (e.g. `color.copy(alpha = 0.2f)`) instead of standard Android indications creates a more authentic, cohesive UX while remaining fully accessible to keyboard and switch control users.
**Action:** Replace default `clickable` ripples with `MutableInteractionSource` background state highlights on custom components requiring retro styling. Ensure `role = Role.Button` and `contentDescription` are provided for these components.

## 2024-07-12 - Proper semantics for full-row toggles in Jetpack Compose
**Learning:** When creating settings menu options with a text label and a visual toggle indicator (like an "Enabled/Disabled" button), placing a clickable `Button` inside a `Row` forces users to tap a small touch target. This also causes screen readers to announce a generic button rather than a toggleable switch state. Wrapping the entire row in `Modifier.toggleable(role = Role.Switch)` expands the touch target to the full width of the item and provides proper binary state semantics for accessibility services.
**Action:** Replace nested `Button` elements inside settings rows with non-interactive UI representations (like `Surface` or `Box`), and apply `Modifier.toggleable` to the parent container with `Role.Switch` for improved touch UX and a11y.

## 2024-07-13 - Dynamic Accessibility for Compose Overlay Intercepts
**Learning:** In full-screen Jetpack Compose overlays where tapping anywhere progresses the state (e.g., skipping typing animations vs. advancing dialog), standard `clickable` modifiers lack screen reader context. Users reliant on TalkBack will just hear "Double tap to activate" without knowing what the action does.
**Action:** Use `.semantics { contentDescription = if (state) "Action A" else "Action B" }` alongside `role = Role.Button` on overlay containers to provide clear, state-aware context to screen reader users.

## 2024-07-25 - Contextual Disabled States for Retro UI Elements
**Learning:** In immersive retro-styled applications, simply preventing action when criteria (like stability in an oscilloscope simulation) aren't met can feel like an unresponsive bug if the UI lacks visual disabled feedback. However, default Android disabled styling often breaks the custom monochromatic CRT aesthetic.
**Action:** When adding `enabled = false` to Jetpack Compose elements in custom retro views, ensure custom colors for `disabledContainerColor` and `disabledContentColor` are explicitly defined (e.g. `color.copy(alpha = 0.2f)` and `color.copy(alpha = 0.5f)`). This maintains visual coherence while clearly communicating interactive boundaries to the user.

## 2024-07-28 - Explicit Semantics for ASCII Art and Dynamic Text
**Learning:** Decorative text elements like ASCII art logos and rapidly updating thematic text (e.g., blinking cursor characters) create severe accessibility issues. Screen readers will read every individual character of ASCII art (slashes, underscores) and will constantly re-announce UI elements that rapidly update (like a blinking `█` cursor), resulting in unnavigable screen reader spam.
**Action:** Always use `Modifier.clearAndSetSemantics { contentDescription = "..." }` on ASCII art and rapidly blinking thematic text components to hide the raw text representation and provide a clean, static, and meaningful description to accessibility services.

## 2024-07-28 - Explicit Semantics for Typewriter Text Animations
**Learning:** Animated "typewriter" text sequences that append characters sequentially create severe accessibility issues. Screen readers will attempt to read each fragment as it updates, resulting in an unnavigable stutter of repeated incomplete sentences.
**Action:** Always use `Modifier.clearAndSetSemantics { contentDescription = fullText }` on `Text` components containing typewriter animations to hide the dynamically updating raw text representation and provide the complete static text to accessibility services immediately.

## 2024-10-24 - Overriding Semantics in Interactive ASCII Components
**Learning:** In custom interactive components that include decorative dynamic ASCII art (like "▮" or ">"), using the standard `semantics` modifier merges the custom content description with the text contents of the component. When focus or hover states change, screen readers will often read out these decorative characters alongside the custom description.
**Action:** Use `Modifier.clearAndSetSemantics` instead of `semantics` in custom interactive components to completely override the default semantics and prevent screen readers from reading out decorative characters when focus/hover states change.

## 2024-10-25 - Using clearAndSetSemantics Appropriately
**Learning:** In Jetpack Compose, use `Modifier.clearAndSetSemantics` (instead of `.semantics`) on interactive components containing decorative text/ASCII art to prevent screen readers from reading the decorative elements. However, avoid applying it to large parent containers (like overlays) as it removes all descendant semantics, rendering informative children text inaccessible.
**Action:** Use `Modifier.clearAndSetSemantics` strategically on individual components like `SectorMapNode` to hide decorative ASCII, but avoid it on broader overlays like `DialogueOverlay` where child text is critical.
