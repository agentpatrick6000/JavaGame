#!/usr/bin/env python3
"""
House Builder Agent for VoxelGame.
Connects via WebSocket, navigates to flat ground, and builds a house.
"""

import asyncio
import json
import math
import sys
import time

try:
    import websockets
except ImportError:
    print("Install websockets: pip3 install websockets")
    sys.exit(1)

URI = "ws://localhost:25566"

# ---- Camera math ----
# Camera convention (from Camera.java):
#   front.x = cos(pitch) * cos(yaw)
#   front.y = sin(pitch)
#   front.z = cos(pitch) * sin(yaw)
# Initial yaw = -90 (looking along -Z), pitch = 0 (horizontal)
# Positive pitch = look UP, negative pitch = look DOWN
# action_look sends DELTAS (added to current yaw/pitch)

def calc_look_delta(cx, cy, cz, cur_yaw, cur_pitch, tx, ty, tz):
    """Calculate yaw/pitch deltas to look from camera (cx,cy,cz) at target (tx,ty,tz).
    
    cur_yaw, cur_pitch: current camera angles in degrees.
    Returns (delta_yaw, delta_pitch).
    """
    dx = tx - cx
    dy = ty - cy
    dz = tz - cz
    
    horizontal_dist = math.sqrt(dx * dx + dz * dz)
    
    if horizontal_dist < 0.001:
        # Target directly above/below
        target_yaw = cur_yaw  # keep current
        target_pitch = 89.0 if dy > 0 else -89.0
    else:
        target_yaw = math.degrees(math.atan2(dz, dx))
        target_pitch = math.degrees(math.atan2(dy, horizontal_dist))
    
    # Clamp pitch
    target_pitch = max(-89.0, min(89.0, target_pitch))
    
    # Delta
    delta_yaw = target_yaw - cur_yaw
    delta_pitch = target_pitch - cur_pitch
    
    # Normalize yaw delta to -180..180
    while delta_yaw > 180: delta_yaw -= 360
    while delta_yaw < -180: delta_yaw += 360
    
    return delta_yaw, delta_pitch


class HouseBuilder:
    def __init__(self):
        self.ws = None
        self.state = None
        self.tick = 0
        self.pose = {}
        self.raycast = {}
        self.ui_state = {}
        self.blocks_placed = 0
        self.failed_placements = 0
        
    async def connect(self):
        print(f"Connecting to {URI}...")
        self.ws = await websockets.connect(URI, max_size=2**20)
        
        # Receive handshake
        hello = json.loads(await self.ws.recv())
        print(f"Connected! Version: {hello.get('version')}")
        print(f"Actions: {list(hello.get('capabilities', {}).get('actions', []))}")
        
        # Get first state
        await self.recv_state()
        print(f"Initial pos: ({self.pose['x']:.1f}, {self.pose['y']:.1f}, {self.pose['z']:.1f})")
        print(f"Initial yaw: {self.pose['yaw']:.1f}, pitch: {self.pose['pitch']:.1f}")
        print(f"On ground: {self.ui_state.get('on_ground')}, fly: {self.ui_state.get('fly_mode')}")
        print(f"Hotbar slot: {self.ui_state.get('hotbar_selected')}")
        
    async def disconnect(self):
        if self.ws:
            await self.ws.close()
            print("Disconnected.")
    
    async def recv_state(self):
        """Receive next state tick."""
        while True:
            msg = json.loads(await self.ws.recv())
            if msg.get("type") == "state":
                self.state = msg
                self.tick = msg["tick"]
                self.pose = msg["pose"]
                self.raycast = msg.get("raycast", {})
                self.ui_state = msg.get("ui_state", {})
                return msg
    
    async def send_action(self, action):
        """Send an action and wait a moment."""
        await self.ws.send(json.dumps(action))
    
    async def look_at(self, tx, ty, tz, settle_ticks=3):
        """Look at a specific world position. Returns after settling."""
        cx = self.pose['x']
        cy = self.pose['y']
        cz = self.pose['z']
        cur_yaw = self.pose['yaw']
        cur_pitch = self.pose['pitch']
        
        dy, dp = calc_look_delta(cx, cy, cz, cur_yaw, cur_pitch, tx, ty, tz)
        
        await self.send_action({"type": "action_look", "yaw": dy, "pitch": dp})
        
        # Wait for state to settle
        for _ in range(settle_ticks):
            await self.recv_state()
        
        return self.raycast
    
    async def look_at_precise(self, tx, ty, tz, max_attempts=5):
        """Look at target with correction loop for precision."""
        for attempt in range(max_attempts):
            cx = self.pose['x']
            cy = self.pose['y']
            cz = self.pose['z']
            cur_yaw = self.pose['yaw']
            cur_pitch = self.pose['pitch']
            
            dy, dp = calc_look_delta(cx, cy, cz, cur_yaw, cur_pitch, tx, ty, tz)
            
            if abs(dy) < 0.5 and abs(dp) < 0.5:
                return self.raycast  # Close enough
            
            await self.send_action({"type": "action_look", "yaw": dy, "pitch": dp})
            await self.recv_state()
            await self.recv_state()
        
        return self.raycast
    
    async def select_slot(self, slot):
        """Select a hotbar slot."""
        await self.send_action({"type": "action_hotbar_select", "slot": slot})
        await self.recv_state()
        print(f"  Selected hotbar slot {slot}")
    
    async def place_block(self):
        """Place a block (right-click)."""
        await self.send_action({"type": "action_use"})
        await self.recv_state()
        await self.recv_state()
        self.blocks_placed += 1
    
    async def break_block(self):
        """Break a block (left-click)."""
        await self.send_action({"type": "action_attack"})
        await self.recv_state()
        await self.recv_state()
    
    async def move(self, forward, strafe, duration_ms):
        """Move player."""
        await self.send_action({
            "type": "action_move",
            "forward": forward,
            "strafe": strafe,
            "duration": duration_ms
        })
        # Wait for move to complete
        wait_ticks = max(2, int(duration_ms / 50))
        for _ in range(wait_ticks):
            await self.recv_state()
    
    async def jump(self):
        """Jump."""
        await self.send_action({"type": "action_jump"})
        await self.recv_state()
        await self.recv_state()
    
    async def jump_and_place_below(self):
        """Pillar up: jump and place block below feet at jump peak."""
        # Jump
        await self.send_action({"type": "action_jump"})
        # Wait a bit for jump to reach peak
        for _ in range(4):
            await self.recv_state()
        
        # Look straight down
        cur_pitch = self.pose['pitch']
        await self.send_action({"type": "action_look", "yaw": 0, "pitch": -89.0 - cur_pitch})
        await self.recv_state()
        
        # Place block
        await self.send_action({"type": "action_use"})
        for _ in range(6):
            await self.recv_state()
        
        self.blocks_placed += 1
    
    def get_ground_y(self):
        """Estimate ground block Y from current position.
        Player eye = ground_top + 1.62, so ground_block_y = floor(eye_y - 1.62)
        """
        return int(math.floor(self.pose['y'] - 1.62))
    
    async def find_flat_ground(self):
        """Walk forward a bit looking for flat ground. Returns when on ground."""
        print("Finding flat ground...")
        
        # First, look forward
        cur_pitch = self.pose['pitch']
        await self.send_action({"type": "action_look", "yaw": 0, "pitch": -cur_pitch})
        await self.recv_state()
        
        # Walk forward briefly to find open area
        await self.move(1.0, 0, 1500)
        
        # Wait for on_ground
        for _ in range(20):
            await self.recv_state()
            if self.ui_state.get('on_ground', False):
                break
        
        ground_y = self.get_ground_y()
        print(f"On ground at Y={ground_y}, pos=({self.pose['x']:.1f}, {self.pose['y']:.1f}, {self.pose['z']:.1f})")
        return ground_y
    
    async def place_block_at_ground_top(self, bx, bz, ground_y):
        """Place a block on top of the ground block at (bx, ground_y, bz).
        Target = center of ground block's top face.
        """
        target_x = bx + 0.5
        target_y = ground_y + 1.0  # top face of ground block
        target_z = bz + 0.5
        
        # Look at the target
        ray = await self.look_at_precise(target_x, target_y, target_z)
        
        # Check raycast
        if ray.get('hit_type') == 'block' and ray.get('hit_normal') == 'TOP':
            await self.place_block()
            return True
        elif ray.get('hit_type') == 'block':
            # Hit a block but wrong face - try anyway, placement might work
            await self.place_block()
            return True
        else:
            self.failed_placements += 1
            return False
    
    async def place_block_on_top_of(self, bx, by, bz):
        """Place a block on TOP of existing block at (bx, by, bz).
        Must be looking at the block from above (eye level > block top).
        """
        target_x = bx + 0.5
        target_y = by + 1.0  # top face
        target_z = bz + 0.5
        
        ray = await self.look_at_precise(target_x, target_y, target_z)
        
        if ray.get('hit_type') == 'block':
            await self.place_block()
            return True
        else:
            self.failed_placements += 1
            return False
    
    async def move_to_position(self, tx, tz, tolerance=1.5):
        """Move toward a target XZ position. Rough positioning."""
        for attempt in range(15):
            cx = self.pose['x']
            cz = self.pose['z']
            dx = tx - cx
            dz = tz - cz
            dist = math.sqrt(dx * dx + dz * dz)
            
            if dist < tolerance:
                return True
            
            # Look toward target
            cur_yaw = self.pose['yaw']
            target_yaw = math.degrees(math.atan2(dz, dx))
            dy = target_yaw - cur_yaw
            while dy > 180: dy -= 360
            while dy < -180: dy += 360
            
            # Level the pitch first
            await self.send_action({"type": "action_look", "yaw": dy, "pitch": -self.pose['pitch']})
            await self.recv_state()
            
            # Move forward
            move_time = min(int(dist * 250), 2000)
            await self.move(1.0, 0, move_time)
            
        return False
    
    async def build_house(self):
        """Main building sequence."""
        print("\n=== PHASE 1: Setup ===")
        await self.connect()
        
        # Select STONE (slot 0)
        await self.select_slot(0)
        
        print("\n=== PHASE 2: Find flat ground ===")
        ground_y = self.get_ground_y()
        
        # Wait for on_ground
        for _ in range(10):
            await self.recv_state()
            if self.ui_state.get('on_ground', False):
                ground_y = self.get_ground_y()
                break
        
        player_x = self.pose['x']
        player_z = self.pose['z']
        
        # Building center (offset a few blocks in front of player)
        # Use atan2 from yaw to get forward direction
        yaw_rad = math.radians(self.pose['yaw'])
        center_x = int(player_x + math.cos(yaw_rad) * 4)
        center_z = int(player_z + math.sin(yaw_rad) * 4)
        
        print(f"Player at ({player_x:.1f}, {self.pose['y']:.1f}, {player_z:.1f})")
        print(f"Ground Y: {ground_y}")
        print(f"Building center: ({center_x}, {center_z})")
        
        # 5×5 building area
        min_x = center_x - 2
        max_x = center_x + 2
        min_z = center_z - 2
        max_z = center_z + 2
        
        print(f"Building area: ({min_x},{min_z}) to ({max_x},{max_z})")
        
        # ---- Build Layer 1: Stand on ground, place on ground tops ----
        print("\n=== PHASE 3a: Wall Layer 1 (on ground) ===")
        
        # Move to center of building area
        await self.move_to_position(center_x + 0.5, center_z + 0.5)
        
        layer1_positions = []
        for x in range(min_x, max_x + 1):
            for z in range(min_z, max_z + 1):
                if x == min_x or x == max_x or z == min_z or z == max_z:
                    layer1_positions.append((x, z))
        
        print(f"Placing {len(layer1_positions)} blocks for layer 1...")
        placed = 0
        for bx, bz in layer1_positions:
            ok = await self.place_block_at_ground_top(bx, bz, ground_y)
            if ok:
                placed += 1
                if placed % 4 == 0:
                    print(f"  Layer 1: {placed}/{len(layer1_positions)} blocks placed")
        print(f"  Layer 1 complete: {placed}/{len(layer1_positions)} placed")
        
        # ---- Build Layer 2: Jump onto layer 1, place on layer 1 tops ----
        print("\n=== PHASE 3b: Wall Layer 2 ===")
        
        # Walk to a wall block and jump on it
        wall_x = min_x
        wall_z = center_z
        await self.move_to_position(wall_x + 0.5, wall_z + 0.5, tolerance=1.0)
        
        # Jump onto the wall block
        await self.jump()
        await self.jump()
        for _ in range(10):
            await self.recv_state()
        
        # Now place layer 2 blocks
        layer2_y = ground_y + 1  # layer 1 blocks are here, place ON TOP of them
        placed = 0
        for bx, bz in layer1_positions:
            ok = await self.place_block_on_top_of(bx, layer2_y, bz)
            if ok:
                placed += 1
                if placed % 4 == 0:
                    print(f"  Layer 2: {placed}/{len(layer1_positions)} blocks placed")
        print(f"  Layer 2 complete: {placed}/{len(layer1_positions)} placed")
        
        # ---- Build Layer 3 ----
        print("\n=== PHASE 3c: Wall Layer 3 ===")
        
        # Pillar up once more
        await self.jump_and_place_below()
        for _ in range(5):
            await self.recv_state()
        
        layer3_y = ground_y + 2
        placed = 0
        for bx, bz in layer1_positions:
            ok = await self.place_block_on_top_of(bx, layer3_y, bz)
            if ok:
                placed += 1
                if placed % 4 == 0:
                    print(f"  Layer 3: {placed}/{len(layer1_positions)} blocks placed")
        print(f"  Layer 3 complete: {placed}/{len(layer1_positions)} placed")
        
        # ---- Build Roof (perimeter at least) ----
        print("\n=== PHASE 3d: Roof ===")
        
        # Pillar up once more
        await self.jump_and_place_below()
        for _ in range(5):
            await self.recv_state()
        
        roof_y = ground_y + 3
        roof_positions = []
        for x in range(min_x, max_x + 1):
            for z in range(min_z, max_z + 1):
                roof_positions.append((x, z))
        
        placed = 0
        for bx, bz in roof_positions:
            # For perimeter blocks, place on top of walls
            if bx == min_x or bx == max_x or bz == min_z or bz == max_z:
                ok = await self.place_block_on_top_of(bx, roof_y, bz)
            else:
                # Interior: try to place next to existing roof blocks
                ok = await self.place_block_on_top_of(bx, roof_y, bz)
            if ok:
                placed += 1
                if placed % 5 == 0:
                    print(f"  Roof: {placed}/{len(roof_positions)} blocks placed")
        print(f"  Roof complete: {placed}/{len(roof_positions)} placed")
        
        # ---- Summary ----
        print(f"\n=== BUILD COMPLETE ===")
        print(f"Total blocks placed: {self.blocks_placed}")
        print(f"Failed placements: {self.failed_placements}")
        print(f"Final position: ({self.pose['x']:.1f}, {self.pose['y']:.1f}, {self.pose['z']:.1f})")
        
        # Walk back to get a view of the house
        print("\nBacking up for a view...")
        # Turn around (180 degrees)
        await self.send_action({"type": "action_look", "yaw": 180, "pitch": 0})
        await self.recv_state()
        # Walk backward/away
        await self.move(1.0, 0, 3000)
        # Turn back to face house
        await self.send_action({"type": "action_look", "yaw": 180, "pitch": -10})
        for _ in range(20):
            await self.recv_state()
        
        print("House should be visible in game window!")
        
        await self.disconnect()


async def main():
    builder = HouseBuilder()
    try:
        await builder.build_house()
    except Exception as e:
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()
        try:
            await builder.disconnect()
        except:
            pass

if __name__ == "__main__":
    asyncio.run(main())
