# Movement & Aiming Guide for VoxelGame Agent API

> **Generated:** 2025-07-13  
> **Method:** Empirical testing via `nav_test.py` and `aim_test.py`  
> **Game version:** AgentServer WebSocket on port 25566

---

## TL;DR — Quick Reference

| Mechanic | Key Number | Notes |
|----------|-----------|-------|
| **Walk speed** | **~3.3 blocks/s** | Effective speed (not the 4.3 constant) |
| **Sprint speed** | **~3.4 blocks/s** | Barely faster — likely a bug or friction issue |
| **Look precision** | **< 0.02° error** | Single-shot, no correction needed |
| **Rotation speed** | **Instant** | Even 360° applies in 1 tick |
| **Stopping behavior** | **Instant stop** | Zero coast/overshoot when move expires |
| **Correction loop** | **1 iteration** | Converges on first shot |
| **Duration → Distance** | **~0.75 × WALK_SPEED × seconds** | See calibration table below |
| **Jump height** | **~1.19 blocks** | Peak at tick 6 (~300ms), lands at tick 12 |
| **Eye height** | **1.62 blocks** above ground | `ground_y = floor(eye_y - 1.62)` |

---

## 1. Looking (action_look)

### How It Works

`action_look` sends **delta** yaw and pitch values that are added to the current camera angles.

```json
{"type": "action_look", "yaw": 45.0, "pitch": -10.0}
```

- **Yaw delta:** Added to current yaw (positive = turn right in world space)
- **Pitch delta:** Added to current pitch (positive = look up, negative = look down)
- **Application:** Instant. Applied within the same game frame.
- **No interpolation, no speed limit, no animation delay.**

### Precision: Perfect

| Metric | Value |
|--------|-------|
| Yaw error after single action | **0.000°** |
| Pitch error after single action | **0.000°** |
| Max residual from look-at calc | **< 0.02°** |

action_look applies the EXACT delta you send. There is no rounding, no smoothing, no momentum. This means:

- **You do NOT need a correction loop for looking.** One shot is sufficient.
- The house_builder.py `max_attempts=5` correction loop is overkill.

### Rotation Speed: Unlimited

Tested deltas from 10° to 360° — all apply instantly within 1 tick (50ms):

```
Δ 10° → applied in 1 tick ✅
Δ 45° → applied in 1 tick ✅
Δ 90° → applied in 1 tick ✅
Δ180° → applied in 1 tick ✅
Δ270° → applied in 1 tick ✅
Δ360° → applied in 1 tick ✅
```

**You can spin 360° in a single action.** No need to break large rotations into smaller steps.

### Yaw Accumulation Warning

⚠️ **Yaw is NOT normalized to -180°..180°.** It accumulates indefinitely:

```
Initial yaw: -90.00°
After +45°: -45.00°
After +45°: 0.00°
After +45°: 45.00°
... eventually: 1338.69° (after many rotations)
```

This doesn't affect gameplay (cos/sin handle any angle), but your delta calculations must normalize to -180°..180°:

```python
delta_yaw = target_yaw - current_yaw
while delta_yaw > 180: delta_yaw -= 360
while delta_yaw < -180: delta_yaw += 360
```

### Camera Math (from Camera.java)

```
front.x = cos(pitch) * cos(yaw)    ← yaw in radians
front.y = sin(pitch)
front.z = cos(pitch) * sin(yaw)
```

**To look at target (tx, ty, tz) from camera (cx, cy, cz):**

```python
import math

dx = tx - cx
dy = ty - cy
dz = tz - cz
horiz_dist = math.sqrt(dx*dx + dz*dz)

target_yaw = math.degrees(math.atan2(dz, dx))
target_pitch = math.degrees(math.atan2(dy, horiz_dist))
target_pitch = max(-89.0, min(89.0, target_pitch))

delta_yaw = target_yaw - current_yaw
delta_pitch = target_pitch - current_pitch
# Normalize delta_yaw to -180..180
while delta_yaw > 180: delta_yaw -= 360
while delta_yaw < -180: delta_yaw += 360
```

### Best Practice: look_at in 1 Shot

```python
async def look_at(self, tx, ty, tz):
    """Look at target. Single shot — no correction loop needed."""
    dx = tx - self.pose['x']
    dy = ty - self.pose['y']
    dz = tz - self.pose['z']
    horiz = math.sqrt(dx*dx + dz*dz)
    
    target_yaw = math.degrees(math.atan2(dz, dx))
    target_pitch = math.degrees(math.atan2(dy, horiz)) if horiz > 0.001 else (89 if dy > 0 else -89)
    target_pitch = max(-89.0, min(89.0, target_pitch))
    
    dyaw = target_yaw - self.pose['yaw']
    dpitch = target_pitch - self.pose['pitch']
    while dyaw > 180: dyaw -= 360
    while dyaw < -180: dyaw += 360
    
    await self.send_action({"type": "action_look", "yaw": dyaw, "pitch": dpitch})
    await self.wait_ticks(2)  # let state update propagate
```

Only add a correction loop if you need sub-0.02° precision (you don't).

---

## 2. Movement (action_move)

### How It Works

```json
{"type": "action_move", "forward": 1.0, "strafe": 0.0, "duration": 1000}
```

- **forward:** -1.0 (backward) to 1.0 (forward) along camera's flat-front direction
- **strafe:** -1.0 (left) to 1.0 (right) along camera's flat-right direction
- **duration:** milliseconds the input is active
- **Physics:** Acceleration + friction model (not instant velocity)
- **Terrain collision:** Movement is blocked/redirected by solid blocks

### Effective Walk Speed: ~3.3 blocks/s

**Not 4.3 blocks/s** as the constant suggests. The acceleration/friction model results in a lower effective speed:

| Game Constant | Value | Notes |
|--------------|-------|-------|
| WALK_SPEED | 4.3 blocks/s | Theoretical max speed |
| SPRINT_MULTIPLIER | 1.5 | Should give 6.45 b/s sprinting |
| GROUND_ACCEL | 60.0 | Acceleration force |
| GROUND_FRICTION | 18.0 | Very high friction |
| **Measured walk** | **~3.3 blocks/s** | Actual steady-state |
| **Measured sprint** | **~3.4 blocks/s** | Barely faster (possible bug) |

### Duration → Distance Calibration Table

Measured empirically (walk, flat terrain, no obstacles):

| Duration (ms) | Expected (4.3 b/s) | **Actual** | Ratio | b/s |
|--------------|--------------------:|----------:|---------:|--------:|
| 100 | 0.430 | **0.226** | 0.526 | 2.26 |
| 200 | 0.860 | **0.546** | 0.635 | 2.73 |
| 300 | 1.290 | **1.007** | 0.781 | 3.36 |
| 500 | 2.150 | **1.545** | 0.719 | 3.09 |
| 750 | 3.225 | **2.380** | 0.738 | 3.17 |
| 1000 | 4.300 | **3.211** | 0.747 | 3.21 |
| 1500 | 6.450 | **4.875** | 0.756 | 3.25 |
| 2000 | 8.600 | **6.582** | 0.765 | 3.29 |

**Quick estimation formula:**

```
distance ≈ 3.3 × (duration_seconds)         for duration > 300ms
distance ≈ 2.5 × (duration_seconds)         for duration < 300ms (acceleration phase)
```

Or more precisely:
```
distance ≈ WALK_SPEED × duration_seconds × 0.75
```

### To walk N blocks:

```python
duration_ms = int(N / 3.3 * 1000)   # rough estimate
# Or more conservative (better for small distances):
duration_ms = int(N / 3.0 * 1000)   # leaves margin for acceleration
```

### Acceleration Phase

The first ~3-4 ticks (~150-200ms) have lower speed as the player accelerates:

```
Tick-by-tick distance from start (500ms move):
  tick 0: 0.051 blocks  (starting from zero)
  tick 1: 0.171 blocks  (accelerating)
  tick 2: 0.318 blocks  (accelerating)
  tick 3: 0.478 blocks  (near steady state)
  tick 4: 0.642 blocks  (steady state: ~0.165/tick)
  tick 5: 0.964 blocks
  ...
  tick 10: 1.531 blocks (move ends, instant stop)
  tick 11: 1.531 blocks (zero coast)
```

**Key observation:** ~0.165 blocks/tick at 20Hz = **3.3 blocks/s steady state**.

### Stopping Behavior: INSTANT

When the duration expires, the player stops **immediately**:

- **Zero coast distance** (0.000 blocks after command ends)
- **Zero deceleration ticks** (stops in the same tick)
- The high GROUND_FRICTION (18.0) kills all velocity within one frame

This means:
- ✅ No need to compensate for overshoot
- ✅ Duration accurately controls stopping point
- ✅ Fine-grained positioning is possible with short durations

### Sprint: Barely Effective (Possible Bug)

Sprint via `action_sprint` showed minimal speed improvement:

```
Walk:   3.27 blocks/s  (2000ms test)
Sprint: 3.39 blocks/s  (2000ms test)
```

Expected sprint = 6.45 b/s (1.5× walk). The measured 3.39 is only 3.6% faster than walk. **This may be a bug** in how agent sprint interacts with the physics model. Don't rely on sprint for speed.

### Direction Accuracy: Perfect (on flat terrain)

When moving on flat, obstacle-free terrain, direction accuracy is exact:

```
yaw=  0° → actual angle: 0.0°  ✅  (error: 0.0°)
yaw= 45° → actual angle: 45.0° ✅  (error: 0.0°)
yaw= 90° → actual angle: 90.0° ✅  (error: 0.0°)
```

⚠️ **Terrain collision redirects movement.** If the path has hills/walls, the player slides along them:

```
yaw=-90° → actual angle: 2.0°   ⚠️ (redirected by terrain)
yaw=180° → actual angle: -122°  ⚠️ (redirected by terrain)
```

**Always re-check position after moving.** Don't assume you arrived at the intended destination.

---

## 3. Navigation (move_to_position)

### Algorithm

```python
async def move_to_xz(self, tx, tz, tolerance=0.5, max_attempts=20):
    """Navigate to target XZ coordinates."""
    for attempt in range(max_attempts):
        cx, cz = self.pose['x'], self.pose['z']
        dist = math.sqrt((tx-cx)**2 + (tz-cz)**2)
        
        if dist < tolerance:
            return True  # arrived
        
        # 1. Look toward target (level pitch)
        self.look_at_xz(tx, tz)
        
        # 2. Walk forward (scale duration to remaining distance)
        move_ms = min(int(dist / 3.3 * 1000 * 0.7), 3000)
        move_ms = max(move_ms, 100)
        
        await self.move_forward(move_ms)
    
    return False  # didn't reach in max_attempts
```

### Key Parameters

| Parameter | Recommended | Notes |
|-----------|-------------|-------|
| **tolerance** | 0.5 blocks | Reliable convergence. Use 1.0 for coarse nav. |
| **max_attempts** | 15-20 | 10 blocks takes ~16 attempts on rough terrain |
| **duration scaling** | `dist / 3.3 * 0.7` | 0.7 factor prevents overshoot |
| **max single move** | 3000ms | Cap to prevent going too far off-course |
| **min single move** | 100ms | Below this, movement is negligible |

### Performance

Measured from test:

| Route | Distance | Attempts | Time | Notes |
|-------|----------|----------|------|-------|
| 10 blocks east | 10 bl | 16 | 20.7s | Rough terrain, many corrections |

**Navigation is slow.** ~2 blocks/second including look+move+settle overhead. For building tasks, minimize long-distance travel.

### Terrain Challenges

- **Hills:** Player can walk up 1-block steps automatically
- **Cliffs:** Movement blocked, player slides along face
- **Water:** Player sinks (no swim mechanics in API)
- **Jump:** Use `action_jump` to clear 1-block obstacles

---

## 4. Jumping

### Characteristics

```
Jump peak:    ~1.19 blocks above ground (at tick 6, ~300ms)
Total time:   ~600ms (12 ticks) from jump to landing
on_ground:    false during entire jump arc
```

### Jump-and-Place-Below (Pillar Technique)

From house_builder.py — works for building upward:

```python
async def pillar_up(self):
    """Jump and place block below feet at peak."""
    await self.send_action({"type": "action_jump"})
    await self.wait_ticks(4)     # reach near-peak
    
    # Look straight down
    await self.send_action({"type": "action_look", 
                           "yaw": 0, 
                           "pitch": -89.0 - self.pose['pitch']})
    await self.wait_ticks(1)
    
    # Place block
    await self.send_action({"type": "action_use"})
    await self.wait_ticks(6)     # land on placed block
```

---

## 5. Raycast (Crosshair Target)

### What It Tells You

```json
{
  "hit_type": "block",      // "block" | "entity" | "miss"
  "hit_class": "SOLID",     // CellClass enum
  "hit_id": "stone",        // Block name (string)
  "hit_dist": 1,            // Distance bucket (0-5)
  "hit_normal": "TOP"       // Face: TOP|BOTTOM|NORTH|SOUTH|EAST|WEST
}
```

### Distance Buckets

| Bucket | Range |
|--------|-------|
| 0 | 0-2m |
| 1 | 2-5m |
| 2 | 5-10m |
| 3 | 10-20m |
| 4 | 20-50m |
| 5 | 50+m |

### Max Reach

**8.0 blocks** — you can only place/break blocks within this range.

### Aiming at Block Faces

To place a block on TOP of block at (bx, by, bz):
1. Aim at `(bx + 0.5, by + 1.0, bz + 0.5)` — center of top face
2. Check `raycast.hit_normal == "TOP"`
3. If correct, `action_use` places on top

To place against NORTH face:
1. Aim at block's north face center
2. Check `raycast.hit_normal == "NORTH"`

### Face Normal → Placement Offset

When `action_use` is sent, the new block appears at `(hit_pos + normal)`:

| hit_normal | New block offset |
|------------|-----------------|
| TOP | (0, +1, 0) — above |
| BOTTOM | (0, -1, 0) — below |
| NORTH | (0, 0, -1) |
| SOUTH | (0, 0, +1) |
| EAST | (+1, 0, 0) |
| WEST | (-1, 0, 0) |

---

## 6. Common Patterns

### Place Block at Ground Level

```python
async def place_on_ground(self, bx, bz, ground_y):
    """Place block on top of ground at (bx, ground_y, bz)."""
    tx, ty, tz = bx + 0.5, ground_y + 1.0, bz + 0.5
    await self.look_at(tx, ty, tz)
    
    if self.raycast.get('hit_type') == 'block' and self.raycast.get('hit_normal') == 'TOP':
        await self.send_action({"type": "action_use"})
        await self.wait_ticks(2)
        return True
    return False
```

### Select Block Type

```python
HOTBAR = {0: "stone", 1: "cobblestone", 2: "dirt", 3: "grass", 
          4: "sand", 5: "log", 6: "leaves", 7: "gravel", 8: "water"}

async def select_block(self, block_name):
    slot = {v: k for k, v in HOTBAR.items()}.get(block_name)
    if slot is not None:
        await self.send_action({"type": "action_hotbar_select", "slot": slot})
        await self.wait_ticks(1)
```

### Break Block at Crosshair

```python
async def break_target(self):
    """Break whatever block the crosshair is on."""
    if self.raycast.get('hit_type') == 'block':
        await self.send_action({"type": "action_attack"})
        await self.wait_ticks(2)
        return True
    return False
```

---

## 7. Timing Reference

| Action | Settle Time | Notes |
|--------|-------------|-------|
| action_look | 2 ticks (100ms) | Applied instantly, wait for state update |
| action_move | duration + 5 ticks | No coast, wait for state to reflect |
| action_jump | 12 ticks (600ms) | Full arc until on_ground=true |
| action_use | 2 ticks (100ms) | Block placement, no confirmation |
| action_attack | 2 ticks (100ms) | Block break (instant) |
| action_hotbar_select | 1 tick (50ms) | Immediate |

### Tick Rate

- **State broadcast:** ~20Hz (every 50ms)
- **Game physics:** Runs at game FPS (may be higher than 20Hz)
- **WebSocket latency:** < 1ms (localhost)

---

## 8. Known Issues & Gotchas

1. **Sprint barely works for agents** — 3.4 b/s vs 3.3 b/s walk. Don't rely on it.
2. **Yaw accumulates** — Can reach 1000+ degrees. Always use delta normalization.
3. **No placement confirmation** — action_use is fire-and-forget. Verify by looking at the placed position and checking raycast.hit_id.
4. **Terrain collision redirects** — Moving toward a wall slides you along it. Always re-check position.
5. **Distance bucket is coarse** — Only 6 buckets, not exact distance. Use block ID for verification.
6. **SimScreen can't identify block types** — Only SOLID/WATER/FOLIAGE classes. Use raycast.hit_id for specific blocks.
7. **action_move direction uses current camera facing** — Always look first, then move.

---

## 9. Recommended Agent Architecture

```
loop:
    1. Observe: read pose + raycast + ui_state
    2. Decide: what to do next (look, move, interact)
    3. Act: send ONE action
    4. Wait: recv_state for 2-3 ticks
    5. Verify: check if action had expected effect
    6. Repeat
```

**Don't batch multiple actions** without waiting between them. The queue is FIFO but actions interact (e.g., look changes move direction).

**Prefer short, frequent corrections** over long blind moves. A series of 300ms moves with position checks between them is more reliable than one 3000ms move.

---

## Appendix: Test Scripts

- `nav_test.py` — Navigation tests (speed, stopping, waypoints)
- `aim_test.py` — Aiming tests (look precision, raycast accuracy)
- `diag_move.py` — Minimal movement diagnostic
- `test_agent_client.py` — Basic API connectivity test
- `house_builder.py` — Full building agent (reference implementation)
