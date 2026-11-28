English | [简体中文](README_zh.md)

# Seamless Gradient Glass

A client-side Fabric mod for Minecraft Java Edition `26.x` and `1.21.x`. Going beyond connected glass textures, it smoothly blends colors across neighboring glass blocks and panes while interacting with beacons and beacon beams.

---

## Features

### Seamless Connected Glass & Color Gradients

- **Unified Transparent Glass Walls**: Adjacent clear glass, 16-color stained glass, and tinted glass automatically merge into a single continuous wall. Inner borders between connected blocks are removed, leaving only the outer perimeter frame while preserving the diagonal glass glare.
- **Smooth Color Transitions**: Neighboring stained glass blocks of different colors no longer show harsh tile seams—colors blend smoothly both horizontally and vertically. Even high-contrast pairs such as red and blue fade naturally without sharp edges or corners, while solid-color regions retain their clean, transparent look.
- **Live Updates**: Placing or breaking glass immediately updates the surrounding gradient effect, and existing builds in your worlds work automatically without modification.

### Smooth Transitions for Clear Glass & Mixed Patterns

- **Clear-to-Stained Borders**: Clear glass itself keeps its vanilla or resource-pack appearance rather than turning solid colored. Where it meets stained glass, a subtle colored mist extends roughly `1.5` blocks on both sides of the border and fades out smoothly, with rounded corners and continuous outer frames and highlights across the seam.
- **Multi-Pattern Resource Packs**: When using a resource pack that gives different glass colors distinct patterns (or when stained glass connects with tinted glass), both colors and textures cross-fade across the border while keeping overall opacity balanced.

### Tinted Glass & Beacon Casing Integration

- **Tinted Glass Blending**: Tinted glass participates in color gradients with its dark grayish-purple tint when placed next to other full glass blocks, while keeping its light-blocking behavior.
- **Beacon Glass Casing Blending**: A beacon's outer glass casing connects and blends colors with adjacent clear glass, stained glass, tinted glass, and other beacons, while keeping its inner crystal and obsidian base unchanged.

### Glass Panes & Moving Pistons

- **Glass Pane Gradients**: Clear panes and all 16 stained glass pane colors support horizontal and vertical color gradients across posts, straight walls, corners, T-junctions, and crosses. Vertically stacked panes show only exposed thin edges. Panes connected to or stacked with full glass blocks share the same color blending and clear-border transitions.
- **Moving Piston Rendering (`26.x` & `1.21.11`)**: On `26.x` and `1.21.11`, glass blocks and panes pushed by pistons connect and blend with glass around their destination mid-motion for a seamless arrival (other blocks keep vanilla rendering). On `1.21.1` and `1.21.8`, piston movement keeps the vanilla appearance and immediately refreshes the gradient within a 2-block radius once settled.

### Beacon Beam Gradients & Tinting

- **Smooth Segment-by-Segment Beam Transitions**: When an active beacon beam passes through a stained glass block of a new color, it fades smoothly from the previous color to the new color over the height of one block instead of snapping abruptly. Stacking multiple colors creates a continuous rainbow beam across both the inner core and outer glow.
- **Beam-to-Glass Dyeing**: A colored beacon beam passing upward through clear glass blocks or clear glass panes tints those clear blocks with the beam's current color and blends them into neighboring glass.

### Resource Pack & Mod Compatibility

- **CTM & Standard Resource Packs**: Supports CTM connected textures (including common 5-tile compact and 47-tile rules), keeping your pack's patterns while adding color gradients. Standard `16×16` resource packs also have inner borders between connected glass removed automatically. Colors or panes without dedicated rules fall back to their corresponding defaults.
- **Continuity**: Compatible alongside Continuity (enable its built-in `Default` and `Glass Pane Culling Fix` packs). This mod handles clear glass, stained glass, and panes while Continuity continues rendering connected textures for other blocks.
- **QuickCraft**: Compatible with QuickCraft's Litematica isometric previews on both `26.x` and `1.21.x`.
- **Sodium Extra**: Beacon beam gradients render properly alongside Sodium Extra `0.7.0`.

---

## Requirements & Configuration

### Environment Requirements

| Branch | Game Versions | Java Version | Fabric Loader | Required Dependency |
| --- | --- | --- | --- | --- |
| `26.x` | `26.1.2` / `26.2` / `26.3` | Java 25 | `>=0.19.5` | Matching Fabric API |
| `1.21.x` | `1.21.1` / `1.21.8` / `1.21.11` | Java 21 | `>=0.16.10` | Matching Fabric API |

- **Optional Dependencies (both branches)**: Installing both [Mod Menu](https://modrinth.com/mod/modmenu) and [YetAnotherConfigLib](https://modrinth.com/mod/yacl) (YACL) enables the in-game configuration screen. Without them, the mod still runs normally and can be configured directly in `config/gradientglass.json`.

### Config Options

Saving changes in the config GUI immediately re-renders loaded chunks:

| Option | Default | Description |
| --- | --- | --- |
| Clear Transition Width | `1.5` | Width in blocks of the transition on each side of the border between stained and connected clear glass (`0.25–2`). |
| Color Blur | `On` | Mixes each glass block's color with neighboring glass within one block. |
| Stained Pattern Layers | `2` | Maximum number of neighboring texture layers overlaid per face where different stained patterns meet (`0–4`, `0` disables; each extra layer roughly doubles faces in transition zones). |
| Block Subdivisions | `4` | Pieces per direction a full block face is split into where color varies (`1–8`). |
| Pane Rows / Segments | `4` / `2` | Subdivisions along pane height and each arm's length where color varies (`1–8`). |
| Beacon Beam Gradient | `On` | Smoothly fades beacon beam color transitions over one block of height. |
| Beacon Beam Dye | `On` | Clear glass and panes inside a colored beacon beam take the beam's color. |
| Weak Channel Threshold | `0.2` | Channels whose brightest value falls below this ratio use luminance pattern instead (reloads resources on change). |
| Visible Pixel Alpha | `32` | Pixels below this alpha are ignored when sampling brightest channel values (reloads resources on change). |

---

## Branches & Maintenance (For Developers)

### Branch Strategy

This repository uses versioned generation branches instead of a generic `main` branch, with Stonecutter managing patch versions inside each branch:

| Branch | Game Versions | Maintenance Status |
| --- | --- | --- |
| `26.x` (default) | `26.1.2` / `26.2` / `26.3` (Stonecutter VCS base: `26.3`) | **Primary development branch** — all new features and refactors land here first. |
| `1.21.x` | `1.21.1` / `1.21.8` / `1.21.11` (Stonecutter VCS base: `1.21.8`) | **Active compatibility branch** — receives backports from `26.x`. |
| `sodium-0.5` | `1.19.4` / `1.20.1` / `1.20.4` | **Archived** — legacy Sodium 0.5 branch, no longer maintained. |

### Differences Between `26.x` and `1.21.x`

- **Piston-moving glass rendering** is supported on `26.x` and `1.21.11`; `1.21.1` and `1.21.8` keep vanilla movement visuals and refresh the 2-block neighborhood gradient once placed.
