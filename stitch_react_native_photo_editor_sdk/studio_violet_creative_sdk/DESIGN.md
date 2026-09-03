---
name: Studio Violet Creative SDK
colors:
  surface: '#131316'
  surface-dim: '#131316'
  surface-bright: '#39393c'
  surface-container-lowest: '#0e0e11'
  surface-container-low: '#1b1b1e'
  surface-container: '#1f1f22'
  surface-container-high: '#2a2a2d'
  surface-container-highest: '#353438'
  on-surface: '#e4e1e6'
  on-surface-variant: '#ccc3d8'
  inverse-surface: '#e4e1e6'
  inverse-on-surface: '#303033'
  outline: '#958da1'
  outline-variant: '#4a4455'
  surface-tint: '#d2bbff'
  primary: '#d2bbff'
  on-primary: '#3f008e'
  primary-container: '#7c3aed'
  on-primary-container: '#ede0ff'
  inverse-primary: '#732ee4'
  secondary: '#c0c1ff'
  on-secondary: '#1000a9'
  secondary-container: '#3131c0'
  on-secondary-container: '#b0b2ff'
  tertiary: '#d0bcff'
  on-tertiary: '#3c0091'
  tertiary-container: '#7645e0'
  on-tertiary-container: '#ece1ff'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#eaddff'
  primary-fixed-dim: '#d2bbff'
  on-primary-fixed: '#25005a'
  on-primary-fixed-variant: '#5a00c6'
  secondary-fixed: '#e1e0ff'
  secondary-fixed-dim: '#c0c1ff'
  on-secondary-fixed: '#07006c'
  on-secondary-fixed-variant: '#2f2ebe'
  tertiary-fixed: '#e9ddff'
  tertiary-fixed-dim: '#d0bcff'
  on-tertiary-fixed: '#23005c'
  on-tertiary-fixed-variant: '#5516be'
  background: '#131316'
  on-background: '#e4e1e6'
  surface-variant: '#353438'
typography:
  headline-lg:
    fontFamily: plusJakartaSans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: plusJakartaSans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: plusJakartaSans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: hankenGrotesk
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
    letterSpacing: 0em
  body-md:
    fontFamily: hankenGrotesk
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
    letterSpacing: 0em
  label-md:
    fontFamily: hankenGrotesk
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: hankenGrotesk
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.03em
  numeric-pill:
    fontFamily: hankenGrotesk
    fontSize: 12px
    fontWeight: '700'
    lineHeight: 14px
    letterSpacing: -0.01em
  caption:
    fontFamily: hankenGrotesk
    fontSize: 10px
    fontWeight: '500'
    lineHeight: 12px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  space-2xs: 0.125rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.25rem
  space-2xl: 1.5rem
  space-3xl: 2rem
  touch-target-min: 2.75rem
  bottom-sheet-inset: 1rem
  gutter-mobile: 1rem
---

## Brand & Style

This design system delivers a tactile, studio-grade creative workspace calibrated for high-precision mobile photo editing. Tailored specifically for touch-first portrait interfaces on iOS and Android, the aesthetic balances dark minimalism with focused luminescent accents, establishing an environment where photographic media remains the undisputed focal point.

### Visual Aesthetic & Movement
The design language synthesizes dark utility styling with translucent glassmorphic surfaces:
- **Studio Neutrality:** Deep zinc and charcoal tiers recede into the backdrop, minimizing eye fatigue and color contamination during color correction, masking, and grading.
- **Electric Violet Precision:** High-chroma violet accents serve purely functional feedback loops—signaling active tool states, non-destructive adjustment ranges, crop guidelines, and keyframe selections.
- **Tactile Instrument Feel:** Every control behaves like calibrated hardware. Sliders provide micro-haptic tension, bottom sheets snap with fluid inertia, and action targets maintain immediate touch responsiveness.
- **Physical Clutter Elimination:** Transient controls collapse gracefully when interacting directly with the image canvas, returning maximum screen real estate to the creator's viewport.

## Colors

The palette operates in strict dark mode to isolate the viewport and preserve accurate perceptual color values of edited photographs.

### Primary Accents & Active States
- **Electric Violet (`#7C3AED`):** The primary interactive seed. Used for active state indicators, primary CTA buttons, selection rings, scrub heads, and focused tool icons.
- **Vivid Indigo (`#6366F1`):** Secondary accent. Applied to multi-point gradient vectors, selective adjustment pins, and auxiliary action highlights.
- **Luminous Violet (`#8B5CF6`):** Tertiary highlight. Employed for active slider progress fills, hover/pressed feedback glows, and active toggle track fills.

### Canvas & Surface Hierarchy
- **Canvas Viewport (`#09090B`):** Near-black workspace base that cradles the photo canvas, offering stark contrast without pure `#000000` clipping.
- **Primary Editor Surface (`#121214`):** Background for bottom control sheets, tool drawers, and persistent toolbars.
- **Elevated Panels & Tiles (`#18181B`):** Surface tone for tool cards, preset chips, and floating segmented toggles.
- **Interactive Neutral Hover/Active (`#27272A`):** Hairline borders, dividers, unselected tool pills, and inactive slider troughs.
- **Border Muted (`#3F3F46`):** Highlighted stroke for selected cards and active input focus perimeters.

### Typography & Content Grayscale
- **High Contrast (`#FFFFFF`):** Primary text, key icons, crop anchors, and critical numerical readouts.
- **Muted Label (`#A1A1AA`):** Secondary text, unselected filter labels, and inactive parameter markers.
- **Subtle Metadata (`#71717A`):** Disabled actions, placeholder labels, and non-interactive grid dividers.

## Typography

Typography balances structural clarity with compact data density. Because mobile editing panels house multiple parameters in restricted vertical height, type sizing is optimized for legibility at small scale.

### Font Pairing Logic
- **Headlines (`plusJakartaSans`):** Modern geometric cuts provide clean, human authority for tool headers, modal titles, and action confirmations without appearing bulky.
- **Body & Instrumentation (`hankenGrotesk`):** Highly legible grotesque typography with open counters, uniform stroke weights, and distinct numerals—crucial for floating parameter pills, degree readouts, and scale percentages.

### Numerical Readouts
All adjustments (e.g., Exposure `+0.45`, Temperature `5400K`, Hue `-12°`) use tabular figure spacing within `numeric-pill` to prevent interface jitter during continuous slider scrubbing.

## Layout & Spacing

The layout model is optimized for single-thumb mobile interaction within portrait orientation (9:16 and modern 19.5:9 display aspects).

### Touch Geometry & Safe Zones
- **Minimum Interactive Target:** `touch-target-min` (44x44px / 2.75rem) enforced on every icon button, swatch selector, undo/redo node, and slider thumb, even when visual icons render smaller (e.g., 20x20px).
- **Portrait Stacking:**
  - **Top Navigation (Safe Area Top + 48px):** Dismiss, Canvas Settings, Undo/Redo history stack, and Export CTA.
  - **Viewport Canvas:** Dynamic flex bounds with minimum 16px lateral margins, ensuring full image inspection without overlay clipping.
  - **Contextual Inspector Area (48px):** Active parameter readout, numerical pills, and zero-reset actions.
  - **Bottom Dock / Sheet (160px – 280px):** Categorized sub-tool panels, horizontal filter scrollers, and fine-grain slider modules.

### Rhythm & Density
Internal tool groups maintain tight 8px (`space-sm`) gaps between closely related controls (e.g., Brightness, Contrast, Saturation chips), while contextual modules separate at 16px (`space-lg`) to avoid mis-taps during gesture manipulation.

## Elevation & Depth

Depth in this design system is engineered through layered surface translucency and refined hairline boundaries rather than heavy drop shadows, keeping rendering overhead minimal on native mobile GPUs.

### Surface Tiers
- **Tier 0 (Workspace Base):** `#09090B` solid canvas, strictly non-reflective.
- **Tier 1 (Persistent Chromes):** `#121214` at 92% opacity with 20px background blur (`rgba(18, 18, 20, 0.92)`). Keeps context visible under top and bottom chrome.
- **Tier 2 (Floating Trays & Modals):** `#18181B` at 88% opacity, backed by a 24px backdrop blur and a continuous hairline stroke (`#27272A`).
- **Tier 3 (Popovers, Tooltips & Sliders Pills):** `#27272A` elevated with a focused directional glow (`0px 8px 24px rgba(0, 0, 0, 0.5)`) and an accent-tinted ambient border (`rgba(124, 58, 237, 0.25)`).

### Hairline Borders
Structural separation relies on precise 1px (or physical 0.5pt on retina screens) borders using `#27272A`. This prevents visual mudiness between adjacent dark sheets and controls.

## Shapes

The shape architecture relies on balanced, rounded corners (`roundedness: 2`) that bridge functional hardware precision with modern mobile OS conventions.

### Corner Radii Guidelines
- **Small Controls (`rounded-md` / 8px):** Numeric value pills, secondary menu dropdown items, and tool badges.
- **Standard Controls (`rounded-lg` / 12px–16px):** Filter thumbnail previews, segmented buttons, input text fields, and inspector tool cards.
- **Floating Panels & Sheets (`rounded-xl` / 24px top radiused):** Bottom sliding panels and context modal trays.
- **Circular Elements (Fully Rounded):** Primary slider thumbs, floating color-picker loupes, and icon-only round buttons (44px bounds).

## Components

### Buttons
- **Primary Export Button:** Solid `#7C3AED` fill, `#FFFFFF` text (`label-md`), 12px radius, 44px height, with a micro-glow shadow (`0 4px 12px rgba(124, 58, 237, 0.35)`). Active press drops scale to 0.97.
- **Tool Icon Buttons:** 44x44px touch container with a 22px centered outlined icon. Inactive: `#A1A1AA` icon on transparent background. Active: `#FFFFFF` icon on `#27272A` background with a bottom 2px `#7C3AED` indicator bar.
- **Ghost Action Buttons:** Transparent base, `#FFFFFF` text, subtle 1px border (`#27272A`), expanding to `#3F3F46` on press.

### Sliders & Fine-Tuning Controls
- **Precision Parameter Slider:** 
  - **Track:** 4px height, background `#27272A`, center-zero tick mark for bipolar settings (-100 to +100).
  - **Active Fill:** `#8B5CF6` bar from center tick or left origin to thumb position.
  - **Thumb:** 24x24px solid white disc with a subtle 2px ring (`#7C3AED`), elevated with `0 2px 8px rgba(0, 0, 0, 0.6)`.
  - **Floating Numeric Pill:** Renders 16px above the thumb during active drag. Background `#18181B` with 1px border (`#7C3AED`), containing signed numerical values (`numeric-pill` type). Snaps out of view on touch release.

### Segmented Controls & Tool Tabs
- **Segmented Bar:** Fixed 40px height, container background `#121214` with a 1px border (`#27272A`) and 12px radius.
- **Segment Item:** Smooth spring-animated sliding indicator tile (`#27272A`) that docks beneath the active tab with high-contrast `#FFFFFF` label text. Inactive tabs render `#A1A1AA`.

### Chips & Filter Presets
- **Preset Thumbnail Cards:** 64x80px vertical card. Top 64x64px photo preview with 8px radius, bottom label in `label-sm`.
- **Selected State:** 2px high-visibility `#7C3AED` outline around the thumbnail, text transitions from `#A1A1AA` to `#FFFFFF` bold.
- **Filter Tags:** 32px height, 16px radius pill, `#18181B` fill, 1px border `#27272A`. Selected: `#7C3AED` background with `#FFFFFF` label.

### Tactile Bottom Sheets
- **Draggable Drawer:** Anchored to screen bottom with a centered pill grab handle (36x4px, `#3F3F46`). Multi-detent snapping (collapsed at 120px for quick adjustments, expanded at 320px for advanced curves/HSL mixers).
- **Surface:** `#121214` with hairline border `#27272A` across the top edge.

### Canvas Overlays & Guides
- **Crop/Transform Bounding Box:** 1.5px solid `#FFFFFF` hairline with 12x12px solid corner brackets. Non-active grid uses 3x3 rule-of-thirds grid in `rgba(255, 255, 255, 0.2)`.
- **Rotation Wheel:** Horizontal curved vernier scale with alternating short and long ticks (`#7C3AED` center reference notch).