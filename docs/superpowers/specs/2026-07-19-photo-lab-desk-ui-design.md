# Photo Lab Desk UI Redesign

**Date:** 2026-07-19
**Status:** Implemented and verified
**Scope:** JavaFX presentation layer in `scene.fxml` and `styles.css`, with only the controller-facing IDs and labels needed to support the visual redesign.

## Intent

The current interface looks like an AI dashboard: dark purple surfaces, gradients, glow effects, all-caps labels, large rounded cards, and system language presented as product marketing. The redesign should feel like a calm desktop photo tool used by a person at a workstation.

The product remains a face-recognition application, but the interface should foreground the camera image and the user's immediate actions rather than advertise the detection engine.

## Goals

- Make the first impression warm, practical, and human-made.
- Give the two image areas the visual priority of a photo workstation.
- Keep camera start/stop and image upload obvious without adding new navigation.
- Reduce visual noise: no gradients, glow shadows, oversized rounded cards, or decorative AI language.
- Preserve all existing `fx:id` values, event handlers, update-service behavior, and responsive resizing behavior.
- Keep English UI copy, using sentence case instead of all caps.

## Non-goals

- No change to detection, tracking, camera, updater, or image-processing behavior.
- No new navigation system, settings screen, icon library, or external font dependency.
- No automatic GUI, camera, or installer launch as part of verification.

## Visual direction: Photo Lab Desk

The application is treated as a compact photo workstation: a warm desk surface surrounds two quiet image monitors, while controls read like practical tools instead of AI controls.

### Color tokens

| Token | Hex | Use |
|---|---|---|
| `desk` | `#F1EFE8` | Root background and quiet workspace canvas |
| `paper` | `#FBFAF7` | Panels, controls, and footer surfaces |
| `ink` | `#26302C` | Primary text and headings |
| `muted-ink` | `#6D746E` | Secondary labels and helper text |
| `sage` | `#5E7565` | Primary action, selected controls, active status |
| `clay` | `#B86E52` | Stop/error emphasis and restrained attention state |
| `monitor` | `#202624` | Image viewports, preserving contrast around photographs |
| `line` | `#D8D9D2` | Borders and separators |

The palette is intentionally quiet. Sage is the only persistent action color; clay appears only for stop/error states. No gradients or drop shadows are used for decoration.

### Typography

- App title: `Georgia`, 22 px, normal weight; this is the single editorial/photo-lab accent.
- Interface text: `Segoe UI`, 13 px, normal or semibold.
- Metadata and status: `Segoe UI`, 11-12 px, with restrained letter spacing where JavaFX supports it.
- Replace marketing copy such as `Advanced AI Vision & Real-time Analysis` with `Live camera and image review`.

### Signature detail

Each image viewport uses a thin monitor frame with a small, quiet status mark in its header. The result header names the human outcome (`Reviewed image`) rather than the internal pipeline (`AI Analysis Result`). This gives the app a recognizable photo-tool identity without adding decoration.

## Layout

The root remains a `BorderPane` at the existing 1280 x 800 preferred size.

```text
+--------------------------------------------------------------------------------+
| Face Recognition                         FPS: --        Ready                  |
| Live camera and image review                                                   |
+----------------------+---------------------------------------------------------+
| Camera                | Source image              | Reviewed image              |
|                       |                           |                            |
| Camera selector      |                           |                            |
| [Select device     v]|                           |                            |
| [Start Camera      ] |                           |                            |
| [Upload Image      ] |                           |                            |
|                       |                           |                            |
| Processing options   |                           |                            |
| [ ] Adaptive light  |                           |                            |
| [ ] Bright-light    |                           |                            |
| [ ] Gender labels   |                           |                            |
|                       |                           |                            |
| Engine: Idle         |                           |                            |
+----------------------+---------------------------------------------------------+
| System ready                                      Exposure: Ready   v1.2.1    |
+--------------------------------------------------------------------------------+
```

Implementation details:

- Keep the left control rail, but reduce its visual weight and width from the current heavy sidebar treatment.
- Keep the two image panels side by side when space allows. The source panel may still be hidden during live camera mode through the existing `UIManager` behavior.
- Use a single top header with title, subtitle, and FPS; remove the extra performance framing.
- Use a simple footer for status, exposure, and version.
- Give image viewports a dark background and modest radius so photographs remain readable against the light workspace.
- Use 12-16 px spacing and 1 px borders instead of 20 px cards and deep shadows.

## Copy and control treatment

- `CONTROL PANEL` becomes `Camera`.
- `Primary Source` becomes `Camera source`.
- `Select Device` becomes `Select device`.
- `Start Camera` / `Stop Camera` stay action-oriented.
- `Upload Image` becomes `Open image`.
- `AI PIPELINE SETTINGS` becomes `Processing options`.
- `Adaptive Exposure` becomes `Adaptive light`.
- `Bright-Light Mode` becomes `Bright-light correction`.
- `Gender Recognition` becomes `Show gender labels`.
- `SOURCE INPUT` becomes `Source image`.
- `AI ANALYSIS RESULT` becomes `Reviewed image`.
- `LIVE` becomes `Live`.
- `SYSTEM STATUS` becomes `Status`.
- Keep engine and exposure values dynamic; only change their surrounding labels and visual emphasis.

Checkboxes remain native-looking controls with a sage selected state. A selected checkbox uses a solid sage box with a light check glyph, while an unselected checkbox is an empty paper-colored box. Keyboard focus is shown around the box only instead of framing the label. Buttons use flat fills and a one-pixel border; the stop action uses clay without a gradient.

## Accessibility and behavior

- Preserve keyboard traversal order: camera selector, camera action, image action, then processing options.
- Maintain visible hover/focus states using border and fill changes, not glow effects; keep checkbox focus limited to the control box.
- Keep text contrast at or above normal desktop readability against the new light surfaces.
- Do not use color alone for active/error states; retain text labels and the existing status messages.
- Do not change controller IDs or event method names unless the current FXML requires it.

## Verification

- Run `mvn clean test` after the FXML/CSS changes to catch resource-loading and controller wiring regressions.
- Run `mvn clean verify` to verify filtered FXML and packaged resources.
- Confirm the target FXML contains the current project version after resource filtering.
- Review all project documents after implementation and update any stale UI description or screenshot claim.
- Do not launch the GUI, camera, or installer automatically.
