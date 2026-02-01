# Movement & Aiming Guide for VoxelGame Agent API

> **Tested:** 2025-02-01  
> **API version:** 1.0  
> **Scripts:** `nav_test.py`, `aim_test.py`

---

## 1. Aiming (action_look)

### Key Finding: Aiming is Perfect

The `action_look` command applies **exact delta yaw/pitch** with **zero overshoot**. In all tests:

| Test | Yaw Error | Pitch Error | Correction Steps |
|------|-----------|-------------|------------------|
| 5 blocks above (pitch 89°) | 0.000° | 0.000° | 0 |
| 3 blocks below (pitch -72°) | 0.000° | 0.000° | 0 |
| 45° diagonal | 0.000° | 0.000° | 0 |
| 180° turn (behind player) | 0.000° | 0.000° | 0 |
| Ground block (complex angle) | 0.001° | 0.000° | 0 |
| Precision sweep (0°–180° offsets) | 0.000° | 0.000° | 0 |
| After 360° spin | 0.000° | 0.000° | 0 |

**Bottom line:** Single-shot aiming is sufficient. Correction loops are unnecessary but harmless as a safety net.

### Formula: Look at World Position

```python
import math

def calc_look_deltas(player_x, player_y, player_z, cur_yaw, cur_pitch,
                     target_x, target_y, target_z):
    """
    Calculate (delta_yaw, delta_pitch) to look from player eye at target.
    
    Game conventions:
      yaw  = atan2(dz, dx) in degrees
      pitch up = positive, pitch down = negative
      Pitch clamped to [-89, 89]
      action_look sends DELTAS added to current angles
    """
    dx = target_x - player_x
    dy = target_y - player_y
    dz = target_z - player_z
    
    h_dist = math.sqrt(dx*dx + dz*dz)
    
    if h_dist < 0.001:
        # Directly above/below — keep current yaw
        target_yaw = cur_yaw
        target_pitch = 89.0 if dy > 0 else -89.0
    else:
        target_yaw = math.degrees(math.atan2(dz, dx))
        target_pitch = math.degrees(math.atan2(dy, h_dist))
    
    target_pitch = max(-89.0, min(89.0, target_pitch))
    
    # Delta with yaw normalization to -180..180
    delta_yaw = target_yaw - cur_yaw
    while delta_yaw > 180:  delta_yaw -= 360
    while delta_yaw < -180: delta_yaw += 360
    
    delta_pitch = target_pitch - cur_pitch
    
    return delta_yaw, delta_pitch
```

### Usage

```python
dy, dp = calc_look_deltas(
    pose['x'], pose['y'], pose['z'],
    pose['yaw'], pose['pitch'],
    target_x, target_y, target_z
)
await ws.send(json.dumps({"type": "action_look", "yaw": dy, "pitch": dp}))
# Wait 2 ticks (~100ms) for state to settle
await recv_state()
await recv_state()
```

### Best Practices — Aiming

1. **Single-shot is enough.** No correction loop needed.
2. **Always normalize yaw delta** to -180..180 before sending.
3. **Wait 2 state ticks** after action_look before reading raycast.
4. **Pitch clamp to ±89°** — the game clamps internally but your math should too.
5. **Use block center coordinates** (e.g., `block_x + 0.5, block_y + 0.5`) for aiming at blocks.
6. **Yaw accumulates** in the game state (you may see 540° instead of 180°). This is fine — the delta math handles it.

---

## 2. Navigation (action_move)

### Key Finding: Navigation Difficulty Depends on Terrain

Navigation is the hard part. Unlike aiming, movement involves physics, collision, and terrain.

### Measured Walking Speed

| Condition | Speed | Notes |
|-----------|-------|-------|
| Walking (forward=1.0) | **~1.16 blocks/sec** | Measured on flat sand |
| Sprinting (toggle) | ~1.7-2.0 blocks/sec | Estimated from longer runs |

**Note:** These speeds are MUCH slower than the 4.3 blocks/sec suggested in the operator guide. Actual in-game physics may vary.

### Test Results

| Test | Distance | Final Error | Time | Corrections | Notes |
|------|----------|-------------|------|-------------|-------|
| 10 blocks N (flat) | 10.0 | 0.57 | ~5s | 6 | Clean run |
| 20 blocks E (terrain) | 20.0 | 9.22 | 30+ | 30 (max) | Stuck on hills |
| 30 blocks NE (terrain) | 30.0 | ~26 | timed out | 20+ | Oscillating path |
| 3 blocks E (precision) | 3.0 | 0.25 | ~1s | 2 | Very clean |

### Rotation Precision

| Direction | Target Yaw | Actual Yaw | Error | Steps |
|-----------|-----------|------------|-------|-------|
| North (-Z) | -90° | -90.00° | 0.00° | 1 |
| East (+X) | 0° | 0.00° | 0.00° | 1 |
| South (+Z) | 90° | 90.00° | 0.00° | 1 |
| West (-X) | 180° | 180.00° | 0.00° | 1 |

Rotation is always perfect. Navigation problems are 100% due to terrain.

### Navigation Strategy: Short-Burst Correction Loop

```python
async def navigate_to(ws, pose, target_x, target_z, tolerance=1.0, max_steps=30):
    """
    Navigate to (target_x, target_z) using short bursts + corrections.
    """
    prev_x, prev_z = pose['x'], pose['z']
    stuck_count = 0
    
    for step in range(max_steps):
        cx, cz = pose['x'], pose['z']
        remaining = distance(cx, cz, target_x, target_z)
        
        if remaining <= tolerance:
            return True  # Arrived!
        
        # 1) Face target
        target_yaw = math.degrees(math.atan2(target_z - cz, target_x - cx))
        delta_yaw = normalize_angle(target_yaw - pose['yaw'])
        await send({"type": "action_look", "yaw": delta_yaw, "pitch": -pose['pitch']})
        await recv_state(); await recv_state()
        
        # 2) Walk — duration based on remaining distance
        if remaining > 10:   walk_ms = 1500
        elif remaining > 5:  walk_ms = 1000
        elif remaining > 2:  walk_ms = 500
        else:                walk_ms = 250
        
        await send({"type": "action_move", "forward": 1.0, "strafe": 0.0,
                     "duration": walk_ms})
        
        # Wait for completion
        for _ in range(int(walk_ms / 50) + 3):
            await recv_state()
        
        # 3) Check if stuck
        moved = distance(prev_x, prev_z, pose['x'], pose['z'])
        if moved < 0.3:
            stuck_count += 1
            if stuck_count >= 2:
                # Jump + forward to clear obstacle
                await send({"type": "action_jump"})
                await recv_state()
                await send({"type": "action_move", "forward": 1.0,
                            "strafe": 0.0, "duration": 500})
                for _ in range(15): await recv_state()
                stuck_count = 0
        else:
            stuck_count = 0
        
        prev_x, prev_z = pose['x'], pose['z']
    
    return False  # Didn't arrive within max_steps
```

### Best Practices — Navigation

1. **Short bursts only.** Never send `duration > 1500ms`. Shorter = more corrections = better accuracy.
2. **Re-face every step.** Always recalculate direction before each move burst.
3. **Level pitch to 0** before walking. Walking while looking up/down doesn't help.
4. **Detect stuck conditions.** If `moved < 0.3 blocks` after a walk, you're stuck.
5. **Jump when stuck.** Send `action_jump` followed immediately by forward movement.
6. **Strafe when stuck repeatedly.** If jumping doesn't help, try `strafe: 1.0` with `forward: 0.5` to go around the obstacle.
7. **Don't use sprint toggle repeatedly.** `action_sprint toggle=true` is a TOGGLE — calling it twice turns sprint OFF. Set it once and leave it.
8. **Flat terrain is easy.** 3–10 blocks on flat ground works with 1–6 corrections and sub-block error.
9. **Hilly terrain is HARD.** 20+ blocks across varied terrain may require 30+ corrections and still fail. Consider fly mode for long-distance travel.
10. **Tolerance of 1.0 block** is practical for most tasks. 0.5 is achievable on flat terrain.

### Duration-to-Distance Table

Based on measured speed of ~1.16 blocks/sec:

| Duration (ms) | Expected Distance (blocks) |
|---------------|---------------------------|
| 250 | ~0.29 |
| 500 | ~0.58 |
| 1000 | ~1.16 |
| 1500 | ~1.74 |
| 2000 | ~2.32 |
| 3000 | ~3.49 |

---

## 3. Common Pitfalls

### Overshoot
- **Aiming:** Not a problem. action_look is exact.
- **Movement:** Overshoot happens with long durations. Use short bursts (≤ 1000ms) and correct between each.

### Collision / Getting Stuck
- **Symptoms:** `moved < 0.3` after walk command. Position doesn't change.
- **Causes:** Terrain elevation change, trees, block edges, water.
- **Fix:** Jump + forward, then strafe if still stuck. Consider flying over obstacles.
- **Prevention:** Use shorter walk durations so you detect collision earlier.

### Yaw Accumulation
- The game does NOT normalize yaw. You'll see values like 540°, 720°, -450°.
- Your code MUST normalize deltas to -180..180 before sending action_look.
- The `normalize_angle` helper handles this.

### Sprint Toggle Bug
- `action_sprint toggle=true` is a TOGGLE, not a SET.
- Calling it every step will alternate sprint on/off/on/off.
- Set sprint ONCE at the start of navigation, don't touch it during.

### Oscillating Path
- On hilly terrain, the player may walk AWAY from the target due to deflection by slopes.
- The remaining distance oscillates (increases/decreases) instead of monotonically decreasing.
- **Detection:** If remaining increases 3 steps in a row, you're oscillating.
- **Fix:** Try a completely different approach angle (strafe to a new position, then approach from there).

### Collision with Placed Blocks
- After building, the player may be surrounded by blocks they placed.
- Always leave a clear exit path when building.
- Jump on top of structures to get clear line-of-sight.

---

## 4. Fly Mode (Recommended for Long Distance)

For travel > 10 blocks over terrain, **fly mode is strongly recommended**:
- Eliminates all terrain collision issues
- Eliminates stuck conditions
- Makes navigation purely about direction + duration
- Currently no `action_fly_toggle` in the API — must be toggled via game controls

If available, fly mode reduces navigation to:
1. Face target (perfect, single-shot)
2. Move forward for `distance / speed` seconds
3. Done — no stuck detection, no jumping needed

---

## 5. Coordinate System Reference

```
          -Z (North)
           ↑
           |
-X (West) ←──→ +X (East)
           |
           ↓
          +Z (South)

+Y = Up
-Y = Down

Yaw conventions (atan2):
  East (+X)  → yaw = 0°
  North (-Z) → yaw = -90°
  West (-X)  → yaw = ±180°
  South (+Z) → yaw = 90°

Player eye height: ~1.62 blocks above feet
Ground block Y: floor(player_y - 1.62)
Block center: (block_x + 0.5, block_y + 0.5, block_z + 0.5)
Block top face: (block_x + 0.5, block_y + 1.0, block_z + 0.5)
```

---

## 6. Quick Reference

### Aiming Checklist
- [x] Calculate delta yaw/pitch with atan2
- [x] Normalize yaw delta to -180..180
- [x] Clamp pitch to ±89°
- [x] Send single action_look
- [x] Wait 2 ticks for settle
- [x] Read raycast for verification
- [x] No correction loop needed (0° error guaranteed)

### Navigation Checklist
- [x] Face target direction (single action_look)
- [x] Level pitch to 0°
- [x] Walk in short bursts (250–1500ms based on distance)
- [x] Check remaining distance after each burst
- [x] Detect stuck (moved < 0.3 blocks)
- [x] Jump when stuck, strafe when very stuck
- [x] Don't toggle sprint repeatedly
- [x] Expect 1.0 block tolerance (0.5 on flat terrain)
- [x] Budget ~1 correction per block of distance on rough terrain
