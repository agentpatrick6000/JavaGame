#!/usr/bin/env python3
"""Quick demo - place a few blocks in a line"""
import asyncio
import json
import websockets

async def demo():
    async with websockets.connect("ws://localhost:25566") as ws:
        # Receive handshake
        hello = await ws.recv()
        print("Connected!")
        
        # Place 5 blocks in a line
        for i in range(5):
            # Look down
            await ws.send(json.dumps({"type":"action_look","dyaw":0,"dpitch":30}))
            await asyncio.sleep(0.5)
            
            # Place block
            await ws.send(json.dumps({"type":"action_use"}))
            await asyncio.sleep(0.3)
            
            # Move forward
            await ws.send(json.dumps({"type":"action_move","forward":1.0,"duration":500}))
            await asyncio.sleep(0.6)
        
        print("Demo complete - 5 blocks placed!")
        await asyncio.sleep(1)

asyncio.run(demo())
