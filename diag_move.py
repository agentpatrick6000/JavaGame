#!/usr/bin/env python3
"""Minimal movement diagnostic - send ONE action_move and track position."""

import asyncio
import json
import sys
import math

try:
    import websockets
except ImportError:
    print("pip3 install websockets")
    sys.exit(1)

URI = "ws://localhost:25566"

async def main():
    print(f"Connecting to {URI}...")
    ws = await websockets.connect(URI, max_size=2**20)
    
    # Get handshake
    hello = json.loads(await ws.recv())
    print(f"Connected! v={hello.get('version')}")
    
    # Get initial state
    state = None
    for _ in range(5):
        msg = json.loads(await ws.recv())
        if msg.get("type") == "state":
            state = msg
            break
    
    pose = state["pose"]
    ui = state.get("ui_state", {})
    print(f"Start: ({pose['x']:.3f}, {pose['y']:.3f}, {pose['z']:.3f})")
    print(f"Yaw: {pose['yaw']:.2f}, Pitch: {pose['pitch']:.2f}")
    print(f"on_ground: {ui.get('on_ground')}, fly_mode: {ui.get('fly_mode')}")
    
    start_x, start_z = pose['x'], pose['z']
    
    # First: test action_look to make sure actions work at all
    print("\n--- Test 1: action_look (yaw +45) ---")
    await ws.send(json.dumps({"type": "action_look", "yaw": 45.0, "pitch": 0.0}))
    for _ in range(5):
        msg = json.loads(await ws.recv())
        if msg.get("type") == "state":
            pose = msg["pose"]
            print(f"  Yaw after look: {pose['yaw']:.2f} (expected change of +45)")
            break
    
    # Second: test movement
    print("\n--- Test 2: action_move forward=1.0 for 2000ms ---")
    start_x, start_z = pose['x'], pose['z']
    
    await ws.send(json.dumps({
        "type": "action_move",
        "forward": 1.0,
        "strafe": 0.0,
        "duration": 2000
    }))
    
    # Track position every tick for 3 seconds
    for i in range(60):
        msg = json.loads(await ws.recv())
        if msg.get("type") == "state":
            pose = msg["pose"]
            dist = math.sqrt((pose['x'] - start_x)**2 + (pose['z'] - start_z)**2)
            if i % 5 == 0 or i < 10:
                print(f"  tick {i:3d}: ({pose['x']:.3f}, {pose['y']:.3f}, {pose['z']:.3f}) dist={dist:.4f} on_ground={msg.get('ui_state',{}).get('on_ground')}")
    
    # Third: try action_jump
    print("\n--- Test 3: action_jump ---")
    start_y = pose['y']
    await ws.send(json.dumps({"type": "action_jump"}))
    for i in range(20):
        msg = json.loads(await ws.recv())
        if msg.get("type") == "state":
            pose = msg["pose"]
            dy = pose['y'] - start_y
            if i % 3 == 0:
                print(f"  tick {i:3d}: y={pose['y']:.3f} (dy={dy:+.3f}) on_ground={msg.get('ui_state',{}).get('on_ground')}")
    
    # Fourth: try fly mode toggle + move
    print("\n--- Test 4: toggle fly + move ---")
    # Check if there's a fly toggle action
    # Actually the API doesn't have fly toggle. Let's try a sprint move.
    print("  Sprinting forward...")
    await ws.send(json.dumps({"type": "action_sprint", "toggle": True}))
    start_x, start_z = pose['x'], pose['z']
    await ws.send(json.dumps({
        "type": "action_move",
        "forward": 1.0,
        "strafe": 0.0,
        "duration": 2000
    }))
    for i in range(60):
        msg = json.loads(await ws.recv())
        if msg.get("type") == "state":
            pose = msg["pose"]
            dist = math.sqrt((pose['x'] - start_x)**2 + (pose['z'] - start_z)**2)
            if i % 10 == 0:
                print(f"  tick {i:3d}: dist={dist:.4f}")
    
    await ws.send(json.dumps({"type": "action_sprint", "toggle": False}))
    
    await ws.close()
    print("\nDone!")

if __name__ == "__main__":
    asyncio.run(main())
