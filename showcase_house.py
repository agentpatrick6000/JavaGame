#!/usr/bin/env python3
"""Move camera to showcase the built house."""

import asyncio
import json
import math
import websockets

URI = "ws://localhost:25566"

async def main():
    print("Connecting...")
    ws = await websockets.connect(URI, max_size=2**20)
    hello = json.loads(await ws.recv())
    print(f"Connected! v{hello.get('version')}")
    
    async def send(action):
        await ws.send(json.dumps(action))
    
    async def recv():
        while True:
            msg = json.loads(await ws.recv())
            if msg.get("type") == "state":
                return msg
    
    async def wait(n=2):
        for _ in range(n):
            await recv()
    
    state = await recv()
    pose = state['pose']
    print(f"Start: ({pose['x']:.1f}, {pose['y']:.1f}, {pose['z']:.1f}) yaw={pose['yaw']:.1f}")
    
    # House is at (422-426, 73-76, 186-190), center at (424, 188)
    # Position the camera southeast of the house, looking northwest toward it
    # Target viewing position: about (428, 74, 183) looking at house
    
    # Step 1: Move to a good viewing position
    # First walk to get clear of any blocks
    house_cx, house_cz = 424.0, 188.0
    view_x, view_z = 429.0, 183.0  # SE of house
    
    # Navigate to viewing position
    for step in range(8):
        state = await recv()
        cx, cz = state['pose']['x'], state['pose']['z']
        dx, dz = view_x - cx, view_z - cz
        dist = math.sqrt(dx*dx + dz*dz)
        
        if dist < 2.0:
            print(f"Reached viewing position! dist={dist:.1f}")
            break
        
        # Look toward target
        target_yaw = math.degrees(math.atan2(dz, dx))
        cur_yaw = state['pose']['yaw']
        dy = target_yaw - cur_yaw
        while dy > 180: dy -= 360
        while dy < -180: dy += 360
        
        await send({"type": "action_look", "yaw": dy, "pitch": -state['pose']['pitch']})
        await wait(2)
        
        move_ms = min(2000, int(dist * 250))
        await send({"type": "action_move", "forward": 1.0, "strafe": 0.0, "duration": move_ms})
        for _ in range(max(3, move_ms // 50)):
            await recv()
        
        print(f"  Step {step}: pos=({state['pose']['x']:.1f}, {state['pose']['z']:.1f}) dist={dist:.1f}")
    
    # Step 2: Look toward house center, slightly up
    state = await recv()
    cx, cy, cz = state['pose']['x'], state['pose']['y'], state['pose']['z']
    
    # Look at house center, at wall height
    target_x = house_cx + 0.5
    target_y = cy + 1.0  # slightly above eye level to see walls
    target_z = house_cz + 0.5
    
    dx = target_x - cx
    dy_look = target_y - cy
    dz = target_z - cz
    
    target_yaw = math.degrees(math.atan2(dz, dx))
    h_dist = math.sqrt(dx*dx + dz*dz)
    target_pitch = math.degrees(math.atan2(dy_look, h_dist))
    
    cur_yaw = state['pose']['yaw']
    cur_pitch = state['pose']['pitch']
    
    dyaw = target_yaw - cur_yaw
    while dyaw > 180: dyaw -= 360
    while dyaw < -180: dyaw += 360
    dpitch = target_pitch - cur_pitch
    
    await send({"type": "action_look", "yaw": dyaw, "pitch": dpitch})
    await wait(5)
    
    state = await recv()
    ray = state.get('raycast', {})
    print(f"\nViewing house:")
    print(f"  Camera: ({state['pose']['x']:.1f}, {state['pose']['y']:.1f}, {state['pose']['z']:.1f})")
    print(f"  Yaw: {state['pose']['yaw']:.1f}, Pitch: {state['pose']['pitch']:.1f}")
    print(f"  Raycast: {ray}")
    
    # Count what's in the simscreen
    sim = state.get('simscreen', [])
    counts = {}
    names = {0:'SKY', 1:'AIR', 2:'SOLID', 3:'WATER', 4:'LAVA', 5:'FOLIAGE'}
    for row in sim:
        for cell in row:
            n = names.get(cell[0], str(cell[0]))
            counts[n] = counts.get(n, 0) + 1
    print(f"  SimScreen: {counts}")
    
    # Step 3: Slowly pan around the house
    print("\nPanning around house for 10 seconds...")
    for i in range(200):  # 200 ticks at 20Hz = 10 seconds
        # Slow rotation
        await send({"type": "action_look", "yaw": 1.0, "pitch": 0.0})
        await recv()
    
    print("Showcase complete!")
    
    state = await recv()
    print(f"Final pos: ({state['pose']['x']:.1f}, {state['pose']['y']:.1f}, {state['pose']['z']:.1f})")
    
    await ws.close()
    print("Disconnected.")

asyncio.run(main())
