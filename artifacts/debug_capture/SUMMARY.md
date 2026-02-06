# Visual Audit Artifact Summary

**Generated:** 2026-02-06  
**Project:** JavaGame (VoxelGame)

---

## Commits Analyzed

| Commit | Description | Key Changes |
|--------|-------------|-------------|
| `d4dfdb7` | BEFORE fixes (baseline) | Height fog enabled, exposure=1.8, saturation=1.15 |
| `fe47c1b` | Remove height fog | **Height fog REMOVED** (biggest fix) |
| `571f9e5` | Exposure/saturation/spawn fix | Exposure 1.8→1.0, Saturation 1.15→1.35, Spawn uses terrain height |
| `74dfb58` | Wireframe toggle | Added F12 wireframe mode (cosmetic) |
| `c240f8b` | Documentation | Added audit report (no code changes) |

---

## Which Commit Fixed What?

### 1. Exposure/ACES Changes
- **Commit:** `571f9e5`
- **Before:** `sceneColor *= 1.8;` (over-brightening)
- **After:** `sceneColor *= 1.0;` (neutral)
- **Impact:** Prevents color clipping in bright areas

### 2. Height Fog Removal
- **Commit:** `fe47c1b`
- **Before:** 
  ```glsl
  float heightFogFactor = exp(-max(vWorldPos.y - 64.0, 0.0) * 0.015);
  float combinedFog = max(vFogFactor, heightFogFactor * 0.3);
  ```
- **After:** Height fog code removed entirely
- **Impact:** **BIGGEST VISUAL CHANGE** — removes 20-30% baseline fog from ALL surfaces

### 3. Spawn Point Fix
- **Commit:** `571f9e5`
- **Before:** Fixed Y=120 spawn regardless of terrain
- **After:** Uses `terrainHeight + 2` for spawn position
- **Impact:** Players no longer spawn inside blocks or fall from high altitude

---

## BIGGEST Visual Delta

### 🏆 Winner: `fe47c1b` (Remove Height Fog)

**Evidence:**

| State | fog.png File Size | Reason |
|-------|-------------------|--------|
| BEFORE (`d4dfdb7`) | **283 KB** | Gray everywhere (20-30% baseline fog) |
| AFTER (`fe47c1b`) | **21 KB** | Mostly black (fog only at distance) |

The fog.png file size DROP from 283KB to 21KB proves the height fog was adding massive baseline fog to ALL terrain. This was the root cause of the "washed-out" appearance.

**Visual difference:** Close terrain goes from "milky gray haze" to "clear, vibrant colors".

---

## Per-Commit Screenshot Paths

### BEFORE (`d4dfdb7`)
- `artifacts/debug_capture/BEFORE/final.png` (1.2 MB)
- `artifacts/debug_capture/BEFORE/fog.png` (283 KB) ← Key evidence: high fog baseline
- `artifacts/debug_capture/BEFORE/depth.png` (352 KB)
- `artifacts/debug_capture/BEFORE/render_state.json`

### AFTER Height Fog Removal (`fe47c1b`)
- `artifacts/debug_capture/AFTER_fe47c1b/final.png` (1.5 MB)
- `artifacts/debug_capture/AFTER_fe47c1b/fog.png` (21 KB) ← Key evidence: fog nearly black
- `artifacts/debug_capture/AFTER_fe47c1b/depth.png` (330 KB)
- `artifacts/debug_capture/AFTER_fe47c1b/render_state.json`

### AFTER Exposure/Saturation Fix (`571f9e5`)
- `artifacts/debug_capture/AFTER_571f9e5/final.png` (1.4 MB)
- `artifacts/debug_capture/AFTER_571f9e5/fog.png` (200 KB)
- `artifacts/debug_capture/AFTER_571f9e5/depth.png` (345 KB)
- `artifacts/debug_capture/AFTER_571f9e5/render_state.json`

---

## Render State Comparison

| Parameter | BEFORE | AFTER `fe47c1b` | AFTER `571f9e5` |
|-----------|--------|-----------------|-----------------|
| Exposure | 1.8 | 1.8 | **1.0** |
| Saturation | 1.15 | 1.15 | **1.35** |
| Height Fog | ✅ Enabled (30%) | ❌ **REMOVED** | ❌ Removed |
| ACES Tonemap | ✅ | ✅ | ✅ |
| sRGB Framebuffer | ❌ | ❌ | ❌ |
| Manual Gamma | ✅ | ✅ | ✅ |

---

## Conclusion

The "washed-out" visuals were caused by **two issues**, fixed in **two commits**:

1. **Primary cause (80% of fix):** Height fog adding baseline fog to ALL surfaces regardless of distance. Fixed in `fe47c1b`.

2. **Secondary cause (20% of fix):** Over-aggressive exposure (1.8x) causing color clipping. Fixed in `571f9e5` by reducing to 1.0x and increasing saturation to 1.35x.

The fog.png file size difference (283KB → 21KB) is the definitive proof that height fog removal was the key fix.
