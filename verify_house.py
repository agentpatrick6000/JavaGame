#!/usr/bin/env python3
"""Verify the house exists and showcase it by moving the camera around."""

import asyncio
import json
import math
import websockets

URI = "ws://localhost:25566"

async def main():
    print("Connecting...")
    ws = await websockets.connect(URI, max_size=2**20)
    
    # Handshake
    hello = json.loads(await ws.recv())
    print(f"Connected! v{hello.get('version')}")
    
    # Get initial state
    state = json.loads(await ws.recv())
    pose = state['pose']
    print(f"Position: ({pose['x']:.1f}, {pose['y']:.1f}, {pose['z']:.1f})")
    print(f"Yaw: {pose['yaw']:.1f}, Pitch: {pose['pitch']:.1f}")
    
    # House was built at center (424, 188), area (422,186)-(426,190), ground_y=72
    house_cx, house_cz = 424, 188
    
    async def send(action):
        await ws.send(json.dumps(action))
    
    async def recv():
        while True:
            msg = json.loads(await ws.recv())
            if msg.get("type") == "state":
                return msg
    
    async def move_toward(tx, tz, duration=2000):
        """Look at target and move toward it."""
        state = await recv()
        cx, cz = state['pose']['x'], state['pose']['z']
        dx, dz = tx - cx, tz - cz
        
        target_yaw = math.degrees(math.atan2(dz, dx))
        cur_yaw = state['pose']['yaw']
        dy = target_yaw - cur_yaw
        while dy > 180: dy -= 360
        while dy < -180: dy += 360
        
        # Level pitch and turn
        await send({"type": "action_look", "yaw": dy, "pitch": -state['pose']['pitch']})
        await recv()
        
        # Move
        await send({"type": "action_move", "forward": 1.0, "strafe": 0.0, "duration": duration})
        for _ in range(max(2, duration // 50)):
            await recv()
    
    # Move toward house location
    print(f"\nMoving toward house at ({house_cx}, {house_cz})...")
    for i in range(5):
        state = await recv()
        cx, cz = state['pose']['x'], state['pose']['z']
        dist = math.sqrt((house_cx - cx)**2 + (house_cz - cz)**2)
        print(f"  Distance to house: {dist:.1f} blocks")
        if dist < 8:
            break
        await move_toward(house_cx, house_cz, min(2000, int(dist * 200)))
    
    # Look around to check for SOLID blocks in simscreen
    print("\nScanning for house blocks...")
    
    # Look toward house center at ground+2 level
    state = await recv()
    pose = state['pose']
    
    # Analyze simscreen
    sim = state.get('simscreen', [])
    solid_count = 0
    total_count = 0
    for row in sim:
        for cell in row:
            total_count += 1
            if cell[0] == 2:  # SOLID
                solid_count += 1
    
    print(f"SimScreen: {solid_count}/{total_count} SOLID cells")
    print(f"Raycast: {state.get('raycast', {})}")
    
    # Look in different directions to find the house
    for angle in [0, 90, 180, 270]:
        cur_yaw = state['pose']['yaw']
        delta = angle - (cur_yaw % 360)
        while delta > 180: delta -= 360
        while delta < -180: delta += 360
        
        await send({"type": "action_look", "yaw": delta, "pitch": -10})
        for _ in range(3):
            state = await recv()
        
        ray = state.get('raycast', {})
        sim = state.get('simscreen', [])
        solid = sum(1 for row in sim for cell in row if cell[0] == 2)
        close_solid = sum(1 for row in sim for cell in row if cell[0] == 2 and cell[1] <= 2)
        
        print(f"  Direction {angle}°: ray={ray.get('hit_type','?')}:{ray.get('hit_id','?')} dist={ray.get('hit_dist','?')} | SOLID={solid} close_solid={close_solid}")
    
    # Now let's build the house if it doesn't exist (re-run building)
    # First check if there are blocks nearby
    state = await recv()
    ray = state.get('raycast', {})
    
    print(f"\nFinal state:")
    print(f"  Position: ({state['pose']['x']:.1f}, {state['pose']['y']:.1f}, {state['pose']['z']:.1f})")
    print(f"  Raycast: {ray}")
    
    await ws.close()
    print("Done.")

asyncio.run(main())
