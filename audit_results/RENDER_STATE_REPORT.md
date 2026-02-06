# JavaGame Visual Audit Report

## Date: 2026-02-06
## Commits: 571f9e5, 74dfb58

---

## Section A: Gamma / sRGB Audit

### Findings:
| Setting | Status |
|---------|--------|
| GL_FRAMEBUFFER_SRGB | ❌ NOT enabled |
| Albedo texture format | GL_RGBA (linear) |
| Manual gamma correction | ✅ composite.frag: `pow(color, 1/2.2)` |
| ACES tonemapping | ✅ Enabled |
| Exposure boost | Changed: 1.8 → 1.0 |
| Saturation boost | Changed: 1.15 → 1.35 |

### Toggle Added:
- **F9**: Cycles gamma mode (MANUAL_GAMMA / SRGB_FRAMEBUFFER)

### Root Cause:
The 1.8x exposure boost was over-brightening colors, causing them to clip and wash out.
Combined with ACES tonemapping compression, this resulted in desaturated, pale visuals.

### Fix:
- Reduced exposure to 1.0 (neutral)
- Increased saturation boost to 1.35 to counteract ACES desaturation

---

## Section B: Fog Double-Application Audit

### Findings:
| Location | Fog Applied? |
|----------|-------------|
| block.vert (vertex) | Distance fog factor calculation |
| block.frag (terrain) | ✅ `mix(litColor, uFogColor, vFogFactor)` |
| Water shader | Same as terrain (block.frag) |
| Sky shader | ❌ No fog |
| PostFX/composite | ❌ No fog |

### Conclusion:
**NO double fog application.** Fog is correctly applied once in the terrain shader.

### Toggle Added:
- **F10**: Cycles fog mode (WORLD_ONLY / POST_ONLY / OFF)

---

## Section C: Depth Space Validation

### Findings:
- Linear depth visualization (debug view 3) shows correct depth gradient
- Near objects: dark, Far objects: light
- Depth reconstruction uses `length(vViewPos)` (correct linear distance)
- No issues with depth space

---

## Section D: Chunk Render / Mesh Pipeline Check

### Render Order (verified):
1. Sky (fullscreen quad at max depth, depth write OFF)
2. Shadow pass (all cascades)
3. Opaque terrain (depth test ON, depth write ON, blend OFF)
4. Transparent terrain/water (depth test ON, depth write OFF, blend ON)

### State Management:
| State | Opaque Pass | Transparent Pass |
|-------|-------------|------------------|
| Depth Test | ON | ON |
| Depth Write | ON | OFF |
| Blending | OFF | ON (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) |
| Cull Face | OFF (for billboards) → ON | OFF → ON |

### Conclusion:
**All render states correct.** No state leaks causing wash-out.

### Toggle Added:
- **F12**: Wireframe mode

---

## Section E: OpenGL State Leak Detector

### Implementation:
Added `logGLState(checkpoint, sceneFBO)` method that logs:
- Current FBO binding
- Viewport dimensions
- Depth test enabled
- Depth mask enabled
- Blend enabled
- sRGB framebuffer enabled
- Clear color
- Active shader program

### Toggle Added:
- **F11**: GL State logging (prints once per second when enabled)

---

## Section F: Fixes Applied

### Issue 1: Washed-out Visuals
**Root Cause:** Over-aggressive exposure (1.8x) in PostFX composite shader

**Fix:**
- Reduced exposure multiplier from 1.8 to 1.0
- Increased saturation boost from 1.15 to 1.35

### Issue 2: Spawn Inside Blocks
**Root Cause:** SpawnPointFinder used fixed Y=120 regardless of terrain height

**Fix:**
- Now uses `context.getTerrainHeight(x, z)` to find actual terrain height
- Spawns player at `terrainHeight + 2` (2 blocks above ground)
- Fall damage reduced from 9.4 to 3.4 (minor drop, no longer spawning inside terrain)

---

## Final Render State

```json
{
  "near_plane": 0.1,
  "far_plane": 384.0,
  "srgb_framebuffer": false,
  "manual_gamma": true,
  "exposure_multiplier": 1.0,
  "saturation_boost": 1.35,
  "fog_applied_in": "terrain shader only (vFogFactor)",
  "postfx_effects": "SSAO + ACES tonemap + gamma correction",
  "render_order": ["sky", "shadow_pass", "opaque_terrain", "transparent_terrain"]
}
```

---

## Debug Key Summary

| Key | Function |
|-----|----------|
| F3 | Debug overlay toggle |
| F6 | Smooth lighting toggle |
| F7 | Debug view cycle (Normal → Albedo → Lighting → Depth → Fog) |
| F9 | Gamma mode toggle |
| F10 | Fog mode toggle |
| F11 | GL State logging toggle |
| F12 | Wireframe mode toggle |

---

## Before/After Comparison

### Before (exposure=1.8, saturation=1.15):
- Grass: Pale cyan/mint
- Dirt: Pinkish-gray
- Overall: Washed out, low contrast

### After (exposure=1.0, saturation=1.35):
- Grass: Vibrant green (slight cyan tint from sky ambient)
- Dirt: Brown/earthy
- Overall: Improved saturation and contrast
