#!/usr/bin/env python3
"""Position camera directly facing the house."""
import asyncio, json, math, websockets

async def main():
    ws = await websockets.connect("ws://localhost:25566", max_size=2**20)
    hello = json.loads(await ws.recv())
    
    async def send(a): await ws.send(json.dumps(a))
    async def recv():
        while True:
            m = json.loads(await ws.recv())
            if m.get("type") == "state": return m
    
    state = await recv()
    pose = state['pose']
    print(f"Start: ({pose['x']:.1f}, {pose['y']:.1f}, {pose['z']:.1f}) yaw={pose['yaw']:.1f}")
    
    # House at (422-426, 73-76, 186-190), center (424, 188)
    # Position: south of house (lower z), looking north
    # Move to (424, y, 183) and look north toward (424, 188)
    
    # First: move to viewing position (south of house)
    target_view_x, target_view_z = 424.5, 182.5
    
    for step in range(12):
        state = await recv()
        cx, cz = state['pose']['x'], state['pose']['z']
        dx, dz = target_view_x - cx, target_view_z - cz
        dist = math.sqrt(dx*dx + dz*dz)
        
        if dist < 1.5:
            print(f"Reached viewing spot! dist={dist:.1f}")
            break
        
        target_yaw = math.degrees(math.atan2(dz, dx))
        cur_yaw = state['pose']['yaw']
        dy = target_yaw - cur_yaw
        while dy > 180: dy -= 360
        while dy < -180: dy += 360
        
        await send({"type": "action_look", "yaw": dy, "pitch": -state['pose']['pitch']})
        for _ in range(2): await recv()
        
        move_ms = min(2000, int(dist * 250))
        await send({"type": "action_move", "forward": 1.0, "strafe": 0.0, "duration": move_ms})
        for _ in range(max(3, move_ms // 50)): await recv()
        print(f"  Step {step}: dist={dist:.1f}")
    
    # Now look directly at house center, slightly up to see walls
    state = await recv()
    cx, cy, cz = state['pose']['x'], state['pose']['y'], state['pose']['z']
    
    # Look at house center at wall mid-height
    house_target_x = 424.5
    house_target_y = cy + 1.5  # slightly above to see full structure
    house_target_z = 188.5
    
    dx = house_target_x - cx
    dy_look = house_target_y - cy
    dz = house_target_z - cz
    
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
    for _ in range(5): await recv()
    
    state = await recv()
    ray = state.get('raycast', {})
    sim = state.get('simscreen', [])
    solid = sum(1 for row in sim for cell in row if cell[0] == 2)
    foliage = sum(1 for row in sim for cell in row if cell[0] == 5)
    sky = sum(1 for row in sim for cell in row if cell[0] == 0)
    
    print(f"\nCamera: ({state['pose']['x']:.1f}, {state['pose']['y']:.1f}, {state['pose']['z']:.1f})")
    print(f"Yaw: {state['pose']['yaw']:.1f} Pitch: {state['pose']['pitch']:.1f}")
    print(f"Raycast: type={ray.get('hit_type')} id={ray.get('hit_id')} face={ray.get('hit_normal')} dist={ray.get('hit_dist')}")
    print(f"SimScreen: SOLID={solid} FOLIAGE={foliage} SKY={sky}")
    print("\n>>> TAKE SCREENSHOT NOW <<<")
    
    # Hold for 20 seconds so screenshot can be taken
    for _ in range(400):
        await recv()
    
    print("Done holding. Closing.")
    await ws.close()

asyncio.run(main())
