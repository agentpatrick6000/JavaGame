# Agent WebSocket API Assessment

> **Date:** 2025-07-12  
> **Purpose:** Evaluate whether the current agent WebSocket API is sufficient for AI agents to perform building tasks in VoxelGame.  
> **Verdict:** ✅ **Agents CAN build with the current API**, but reliability is limited by missing feedback loops. See recommendations below.

---

## 1. Architecture Overview

| Component | File | Role |
|-----------|------|------|
| `AgentServer` | `agent/AgentServer.java` | WebSocket server on port 25566, manages connections, broadcasts state at ~20Hz |
| `Messages` | `agent/Messages.java` | JSON protocol: hello handshake, state messages, action parsing |
| `SimScreen` | `agent/SimScreen.java` | 64×36 raycasted grid representing agent's first-person POV |
| `ActionQueue` | `agent/ActionQueue.java` | Lock-free concurrent queue bridging WebSocket threads → game loop |
| `Controller` | `sim/Controller.java` | Drains agent actions each tick, applies them with same priority as keyboard |
| `GameLoop` | `core/GameLoop.java` | Integrates agent use/attack into block interaction (place/break) |

**Thread model:** WebSocket callbacks → `ActionQueue` (concurrent) → game loop drains per tick.  
**Broadcast throttle:** 50ms minimum interval (~20Hz).

---

## 2. Data Sent to Agents (Server → Agent)

### 2.1 Handshake (`hello` message, sent on connect)

| Field | Content |
|-------|---------|
| `version` | `"1.0"` |
| `capabilities` | simscreen dimensions (64×36), tick rate (20Hz), max reach (8.0), action list |
| `schema` | Full JSON schema for state messages and all action types with parameter docs |
| `operator_guide` | Detailed guide: camera math (yaw/pitch conventions, atan2 formulas), movement speeds, block placement mechanics (face normals → adjacent block), hotbar usage, survival stubs |
| `memory_contract` | STM/MTM/LTM memory framework with provenance tagging |

**Quality: Excellent.** The operator guide alone gives agents everything needed for camera math and placement logic.

### 2.2 State Tick (broadcast every ~50ms to all connected agents)

```json
{
  "type": "state",
  "tick": 1234,
  "pose": { "x": 10.5, "y": 72.62, "z": -30.2, "yaw": -45.0, "pitch": -15.0 },
  "raycast": {
    "hit_type": "block",
    "hit_class": "SOLID",
    "hit_id": "stone",
    "hit_dist": 1,
    "hit_normal": "TOP"
  },
  "ui_state": {
    "health": 20,
    "hotbar_selected": 0,
    "fly_mode": false,
    "on_ground": true
  },
  "sound_events": [],
  "simscreen": [/* 36 rows × 64 cols, each cell = [cls, depth, light] */]
}
```

#### Pose
- **Exact float coordinates** (x, y, z) — eye-level position
- **Exact yaw/pitch** in degrees
- **Quality: Excellent** — full precision, no quantization

#### Raycast (crosshair center)
- `hit_type`: `"block"` | `"entity"` | `"miss"`
- `hit_class`: CellClass enum (`SOLID`, `WATER`, `FOLIAGE`, etc.)
- **`hit_id`: block name string** (e.g., `"stone"`, `"dirt"`, `"grass"`, `"cobblestone"`)
- `hit_dist`: distance bucket 0-5 (0-2m, 2-5m, 5-10m, 10-20m, 20-50m, 50+m)
- `hit_normal`: face name (`TOP`, `BOTTOM`, `NORTH`, `SOUTH`, `EAST`, `WEST`)
- **Quality: Very good** — includes block identity AND face normal, critical for placement

#### SimScreen (64×36 grid)
- Each cell: `[cls_ordinal, depth_bucket, light_bucket]`
- `cls`: SKY(0), AIR(1), SOLID(2), WATER(3), LAVA(4), FOLIAGE(5), ITEM(6), ENTITY(7), UI(8), UNKNOWN(9)
- `depth`: 6 buckets (0-5)
- `light`: 4 buckets (0-3)
- Uses DDA voxel traversal (occlusion-correct)
- **Quality: Good for spatial awareness, but cls is too coarse for block identification** (see §4)

#### UI State
- `health`: stubbed at 20.0 (no damage system yet)
- `hotbar_selected`: 0-8
- `fly_mode`: boolean
- `on_ground`: boolean
- **Quality: Adequate** — missing hotbar contents (what block is in each slot)

#### Sound Events
- Empty stub `[]` — not yet implemented

---

## 3. Actions Available (Agent → Server)

| Action | Parameters | Effect |
|--------|-----------|--------|
| `action_look` | `yaw: float, pitch: float` | Delta rotation applied to camera |
| `action_move` | `forward: float(-1..1), strafe: float(-1..1), duration: int(ms)` | Timed movement with physics |
| `action_jump` | *(none)* | Jump if on_ground and not flying |
| `action_crouch` | `toggle: bool` | Toggle crouch state |
| `action_sprint` | `toggle: bool` | Toggle sprint (1.5× speed) |
| `action_use` | *(none)* | Right-click: place block from hotbar at raycast target + normal offset |
| `action_attack` | *(none)* | Left-click: break block at raycast target (instant, set to AIR) |
| `action_hotbar_select` | `slot: int(0-8)` | Select hotbar slot |

### Block Breaking (action_attack)
- Instant break — no mining time, no tool requirements
- Sets target block to AIR
- Updates lighting and rebuilds chunk mesh
- **No response/confirmation sent back**

### Block Placement (action_use)
- Places `player.getSelectedBlock()` at `(hit.x + hit.nx, hit.y + hit.ny, hit.z + hit.nz)`
- i.e., adjacent block in the direction of the face normal
- Updates lighting and rebuilds chunk mesh
- **No response/confirmation sent back**
- **No collision check** — can place inside player's own position

### Hotbar Contents (fixed, from Player.java)

| Slot | Block ID | Block Name |
|------|----------|------------|
| 0 | 1 | stone |
| 1 | 2 | cobblestone |
| 2 | 3 | dirt |
| 3 | 4 | grass |
| 4 | 5 | sand |
| 5 | 7 | log |
| 6 | 8 | leaves |
| 7 | 6 | gravel |
| 8 | 9 | water |

**Note:** Hotbar contents are NOT transmitted in state messages. Agent must hardcode these.

---

## 4. Gap Analysis for Building Tasks

### 4.1 Can agents determine WHAT block type is at a position?

**✅ YES — for the block at crosshair center** via `raycast.hit_id` (returns block name like `"stone"`, `"dirt"`).

**❌ NO — for arbitrary positions** without physically looking at them. There is no `query_block(x, y, z)` endpoint.

**⚠️ PARTIALLY — via SimScreen** which covers the entire FOV but only classifies blocks as `SOLID`/`WATER`/`FOLIAGE`, NOT by specific type. An agent can see "there's a solid block 5m away at screen position (32, 20)" but cannot tell if it's stone or dirt.

**Impact on building:** Agent can verify placement by looking at a placed block and checking `raycast.hit_id`, but must do this one block at a time. Cannot efficiently scan a build area.

### 4.2 Can agents verify successful placement?

**⚠️ INDIRECTLY** — there is no placement confirmation. The agent must:
1. Send `action_use`
2. Wait 1-2 ticks
3. Look at where the block should be
4. Check if `raycast.hit_id` matches expected block

**Problems:**
- Fire-and-forget actions with no success/failure response
- Raycast may not hit the placed block if camera drifted
- No "block changed" event stream
- The `house_builder.py` client tracks `failed_placements` but has no reliable way to verify

### 4.3 Is spatial awareness sufficient?

**✅ YES for basic building:**
- Exact float coordinates → agent knows precisely where it is
- Raycast face normals → agent knows exactly where a placed block will appear
- `on_ground` state → knows when it's landed after jumps
- Camera math is well-documented in operator guide

**⚠️ LIMITED for complex tasks:**
- SimScreen depth uses 6 coarse buckets, not exact distances
- No way to query neighboring blocks without looking at each one
- No mini-map, no chunk data access
- Agent must build a mental model from sequential observations

### 4.4 Missing: Hotbar Contents in State

The state message includes `hotbar_selected` (which slot) but NOT what block is in each slot. Agent must hardcode the fixed hotbar layout. If the game ever adds inventory management, this becomes a critical gap.

### 4.5 Missing: Action Feedback

Every action is fire-and-forget. There is no:
- `action_ack` with success/failure status
- `block_placed` event with coordinates
- `block_broken` event with coordinates  
- Error responses for invalid actions

---

## 5. Block Registry (for reference)

15 block types (from `Blocks.java`):

| ID | Name | Solid | Transparent | Hotbar Slot |
|----|------|-------|-------------|-------------|
| 0 | air | ❌ | ✅ | — |
| 1 | stone | ✅ | ❌ | 0 |
| 2 | cobblestone | ✅ | ❌ | 1 |
| 3 | dirt | ✅ | ❌ | 2 |
| 4 | grass | ✅ | ❌ | 3 |
| 5 | sand | ✅ | ❌ | 4 |
| 6 | gravel | ✅ | ❌ | 7 |
| 7 | log | ✅ | ❌ | 5 |
| 8 | leaves | ✅ | ✅ | 6 |
| 9 | water | ❌ | ✅ | 8 |
| 10 | coal_ore | ✅ | ❌ | — |
| 11 | iron_ore | ✅ | ❌ | — |
| 12 | gold_ore | ✅ | ❌ | — |
| 13 | diamond_ore | ✅ | ❌ | — |
| 14 | bedrock | ✅ | ❌ | — |

---

## 6. Existing Agent Client (`house_builder.py`)

A working Python client exists that demonstrates:
- WebSocket connection and handshake parsing
- Camera math (atan2-based look-at with correction loops)
- Movement with timed actions
- Block placement via look → verify raycast → use
- Multi-layer building (walls, roof) with jump-and-pillar technique
- Position-based navigation (`move_to_position` with tolerance)

**Key observations from the client:**
- Uses `look_at_precise` with up to 5 correction attempts (compensating for no guaranteed look precision)
- Tracks `failed_placements` but has no real verification
- Places blocks somewhat blindly — checks raycast hit_type but not hit_id
- Building is layer-by-layer from ground up using jump/pillar technique

---

## 7. Recommendations

### Verdict: Agents CAN build with the current API

The `house_builder.py` already proves this. The API provides:
- ✅ Precise positioning (exact float coords)
- ✅ Block identity on crosshair (raycast.hit_id)
- ✅ Face normals for placement logic
- ✅ Complete action set (look, move, place, break, hotbar)
- ✅ Excellent operator guide documentation
- ✅ 20Hz tick rate (responsive feedback)

### But reliability is limited — suggested enhancements (priority-ordered):

#### P0: Action Responses (highest impact)
Add response messages for `action_use` and `action_attack`:
```json
{"type": "action_result", "action": "action_use", "success": true, 
 "block": "stone", "pos": {"x": 10, "y": 73, "z": -30}}
```
This alone would eliminate the need for look-verify loops and dramatically improve build reliability.

#### P1: Hotbar Contents in State
Add to `ui_state`:
```json
"hotbar": ["stone", "cobblestone", "dirt", "grass", "sand", "log", "leaves", "gravel", "water"]
```
Removes hardcoded dependency and enables future inventory changes.

#### P2: Block Query Endpoint (nice to have)
New action type:
```json
{"type": "query_block", "x": 10, "y": 73, "z": -30}
```
Response:
```json
{"type": "block_info", "x": 10, "y": 73, "z": -30, "block_id": 1, "block_name": "stone"}
```
Would enable agents to scan build areas and verify structures without physically looking at each block.

#### P3: SimScreen Block Identity (nice to have)
Change SimScreen cells from `[cls, depth, light]` to `[cls, depth, light, block_id]` or add a separate block identity layer. Would enable visual scanning of surroundings for specific block types.

#### P4: Block Change Events (nice to have)
Broadcast when blocks change in the agent's vicinity:
```json
{"type": "block_change", "x": 10, "y": 73, "z": -30, "old": "air", "new": "stone"}
```
Would enable agents to reactively track world state changes.

### What's NOT needed (current API handles well):
- ❌ Path planning API — agent has enough spatial data to navigate
- ❌ Auto-build commands — defeats the purpose of agentic players
- ❌ Coordinate system changes — current convention is clean and well-documented
- ❌ Higher tick rate — 20Hz is sufficient for building tasks

---

## 8. Summary Table

| Capability | Status | Notes |
|-----------|--------|-------|
| Connect & receive state | ✅ Working | WebSocket on :25566, JSON protocol |
| Know own position | ✅ Excellent | Exact float x/y/z + yaw/pitch |
| Know what's at crosshair | ✅ Good | Block name + face normal + distance |
| Know block types in FOV | ⚠️ Coarse | SimScreen: SOLID/WATER/FOLIAGE only, no specific types |
| Query arbitrary blocks | ❌ Missing | Must physically look at each block |
| Place blocks | ✅ Working | action_use, placement logic is sound |
| Verify placement | ⚠️ Indirect | Must look-and-check, no confirmation event |
| Break blocks | ✅ Working | action_attack, instant break |
| Navigate | ✅ Working | move + look + on_ground, physics handled server-side |
| Know hotbar contents | ⚠️ Hardcoded | Not in state messages, fixed layout in Player.java |
| Fly mode | ✅ Available | Can be toggled, simplifies building at height |
| Multi-material builds | ✅ Working | 9 block types in hotbar, switchable via action_hotbar_select |

**Bottom line:** Build an agent now with P0 (action responses) as a near-term enhancement goal. Everything else is quality-of-life.
