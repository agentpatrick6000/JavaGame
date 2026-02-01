#!/usr/bin/env python3
"""
Build a 3×3 Stone Wall — Enhanced API (v6 — Pillar Technique)

PROVEN results from v5:
  - Rows 1-2: 6/6 perfect (100%) via TOP face aiming from 1.5 blocks away
  - Row 3: 0/3 — player eye is below TOP face of row 2, ray hits WEST face

FIX for Row 3: PILLAR UP
  - Build temporary 2-block pillar next to wall
  - Jump onto it → eye rises above row 2 → can aim DOWN at TOP face
  - Place row 3 blocks
  - Break pillar to clean up

This uses:
  - hotbar_contents: verify stone
  - last_action_result: confirm every placement
  - jump_and_place_below: pillar technique from house_builder.py
"""

import asyncio
import json
import math
import sys
import time

try:
    import websockets
except ImportError:
    print("pip3 install websockets"); sys.exit(1)

URI = "ws://localhost:25566"
EYE_HEIGHT = 1.62


def norm_angle(a):
    while a > 180: a -= 360
    while a < -180: a += 360
    return a


def look_delta(cx, cy, cz, cyaw, cpitch, tx, ty, tz):
    dx, dy, dz = tx - cx, ty - cy, tz - cz
    h = math.sqrt(dx*dx + dz*dz)
    if h < 0.001:
        tyaw = cyaw
        tpitch = 89.0 if dy > 0 else -89.0
    else:
        tyaw = math.degrees(math.atan2(dz, dx))
        tpitch = math.degrees(math.atan2(dy, h))
    tpitch = max(-89.0, min(89.0, tpitch))
    return norm_angle(tyaw - cyaw), tpitch - cpitch


def dist_xz(x1, z1, x2, z2):
    return math.sqrt((x2-x1)**2 + (z2-z1)**2)


class Builder:
    def __init__(self):
        self.ws = None
        self.pose = {}
        self.ray = {}
        self.ui = {}
        self.hotbar = []
        self.result = None
        self.tick = 0
        self.ok = 0
        self.fail = 0
        self.retry = 0
        self.log = []

    async def connect(self):
        self.ws = await websockets.connect(URI, max_size=2**20)
        json.loads(await self.ws.recv())  # hello
        await self.recv()
        p = self.pose
        print(f"Connected: ({p['x']:.1f}, {p['y']:.1f}, {p['z']:.1f})")

    async def close(self):
        if self.ws: await self.ws.close()

    async def recv(self):
        while True:
            m = json.loads(await self.ws.recv())
            if m.get("type") == "state":
                self.tick = m["tick"]
                self.pose = m["pose"]
                self.ray = m.get("raycast", {})
                self.ui = m.get("ui_state", {})
                self.hotbar = m.get("hotbar_contents", [])
                r = m.get("last_action_result")
                if r is not None: self.result = r
                return m

    async def cmd(self, action):
        await self.ws.send(json.dumps(action))

    async def wait(self, n):
        for _ in range(n): await self.recv()

    def ground(self):
        return round(self.pose['y'] - EYE_HEIGHT)

    async def aim(self, tx, ty, tz, s=3):
        dy, dp = look_delta(self.pose['x'], self.pose['y'], self.pose['z'],
                            self.pose['yaw'], self.pose['pitch'], tx, ty, tz)
        await self.cmd({"type": "action_look", "yaw": dy, "pitch": dp})
        await self.wait(s)
        return self.ray

    async def place(self):
        self.result = None
        await self.cmd({"type": "action_use"})
        for _ in range(10):
            await self.recv()
            if self.result is not None: return self.result
        return self.result

    async def attack(self):
        self.result = None
        await self.cmd({"type": "action_attack"})
        for _ in range(6):
            await self.recv()
            if self.result is not None: return self.result
        return self.result

    async def walk(self, tx, tz, tol=0.7, mx=25):
        px, pz = self.pose['x'], self.pose['z']
        stuck = 0
        for _ in range(mx):
            cx, cz = self.pose['x'], self.pose['z']
            d = dist_xz(cx, cz, tx, tz)
            if d <= tol: return True
            yaw = math.degrees(math.atan2(tz-cz, tx-cx))
            await self.cmd({"type": "action_look", "yaw": norm_angle(yaw-self.pose['yaw']),
                           "pitch": -self.pose['pitch']})
            await self.wait(2)
            ms = max(200, min(int(d*600), 1500))
            await self.cmd({"type": "action_move", "forward": 1.0, "strafe": 0.0, "duration": ms})
            await self.wait(max(3, int(ms/50)+3))
            mv = dist_xz(px, pz, self.pose['x'], self.pose['z'])
            if mv < 0.2:
                stuck += 1
                if stuck >= 2:
                    await self.cmd({"type": "action_jump"})
                    await self.wait(3)
                    await self.cmd({"type": "action_move", "forward": 1.0, "strafe": 0.5, "duration": 800})
                    await self.wait(20)
                    stuck = 0
            else: stuck = 0
            px, pz = self.pose['x'], self.pose['z']
        return False

    async def place_on_top(self, bx, by, bz):
        """Place at (bx,by,bz) by aiming at TOP face of (bx,by-1,bz).
        Only succeeds if ray hits a block with TOP/valid face and result pos matches."""
        aim_x, aim_y, aim_z = bx + 0.5, (by-1) + 1.0, bz + 0.5
        ray = await self.aim(aim_x, aim_y, aim_z)
        hit = ray.get('hit_type', 'miss')
        normal = ray.get('hit_normal', 'NONE')
        hit_id = ray.get('hit_id', '?')

        tag = f"[{hit}/{hit_id}/{normal}]"
        if hit != 'block':
            print(f"    {tag} miss")
            self.retry += 1
            return False

        # Only place if face is TOP (placing on anything else gives wrong position)
        if normal != 'TOP':
            print(f"    {tag} wrong face (need TOP)")
            self.retry += 1
            return False

        result = await self.place()
        if result and result.get('success'):
            pos = result.get('pos', [])
            match = (len(pos) == 3 and pos[0] == bx and pos[1] == by and pos[2] == bz)
            if match:
                print(f"    {tag} → ✅ {pos} ✓")
                self.ok += 1
                self.log.append({"t": [bx,by,bz], "ok": True, "at": pos})
                return True
            else:
                print(f"    {tag} → ⚠ {pos} (wrong)")
                self.retry += 1
                return False
        print(f"    {tag} → no result")
        self.retry += 1
        return False

    async def jump_place_below(self):
        """Jump, look down at peak, place block below feet."""
        await self.cmd({"type": "action_jump"})
        await self.wait(4)  # reach peak
        # Look straight down
        dp = -89.0 - self.pose['pitch']
        await self.cmd({"type": "action_look", "yaw": 0, "pitch": dp})
        await self.wait(1)
        # Place
        result = await self.place()
        await self.wait(6)  # land
        return result

    async def pillar_up(self, n=2):
        """Build n blocks under feet by jumping and placing down."""
        print(f"    Pillaring up {n} blocks...")
        for i in range(n):
            result = await self.jump_place_below()
            if result and result.get('success'):
                print(f"    Pillar {i+1}: ✅ at {result.get('pos')}")
            else:
                print(f"    Pillar {i+1}: ⚠ {result}")
        return True

    async def build(self):
        print("=" * 50)
        print("  3×3 STONE WALL BUILD (v6)")
        print("=" * 50)

        # Hotbar
        slot = next((i for i, n in enumerate(self.hotbar) if n == "stone"), None)
        if slot is None:
            print("❌ No stone!"); return False
        print(f"Stone slot={slot}")
        await self.cmd({"type": "action_hotbar_select", "slot": slot})
        await self.wait(2)

        # Plan
        gy = self.ground()
        px, pz = self.pose['x'], self.pose['z']
        wx = int(px) + 3  # wall 3 blocks east
        z0 = int(pz) - 1

        print(f"Player=({px:.1f},{self.pose['y']:.1f},{pz:.1f}) gy={gy}")
        print(f"Wall: x={wx}, z={z0}..{z0+2}, y={gy+1}..{gy+3}")

        t0 = time.time()

        # === ROWS 1-2: Stand 1.5 blocks west of wall ===
        print(f"\n--- Rows 1-2 ---")
        await self.walk(wx - 1.5, z0 + 1.0, tol=0.5)
        print(f"  At ({self.pose['x']:.2f}, {self.pose['z']:.2f})")

        for row in range(2):
            by = gy + 1 + row
            print(f"  Row {row+1} (Y={by}):")
            for col in range(3):
                bz = z0 + col
                print(f"  ({wx},{by},{bz}):", end="")
                await self.place_on_top(wx, by, bz)

        # === ROW 3: Pillar up for height ===
        print(f"\n--- Row 3 (pillar technique) ---")
        # Walk to position right next to the wall
        pillar_x = wx - 1  # 1 block west of wall
        pillar_z = z0 + 1  # middle column
        await self.walk(pillar_x + 0.5, pillar_z + 0.5, tol=0.5)
        print(f"  At ({self.pose['x']:.2f}, {self.pose['y']:.2f}, {self.pose['z']:.2f})")

        # Pillar up 2 blocks
        await self.pillar_up(2)
        print(f"  Elevated: ({self.pose['x']:.2f}, {self.pose['y']:.2f}, {self.pose['z']:.2f})")
        print(f"  Eye height vs wall: eye={self.pose['y']:.1f}, wall_top={gy+3}")

        by = gy + 3
        print(f"  Row 3 (Y={by}):")
        for col in range(3):
            bz = z0 + col
            print(f"  ({wx},{by},{bz}):", end="")
            ok = await self.place_on_top(wx, by, bz)
            if not ok:
                # Fallback: try side face of adjacent row 3 blocks
                for nbz, desc in [(bz - 1, "south-of-north"), (bz + 1, "north-of-south")]:
                    # Check if neighbor at (wx, by, nbz) exists
                    r = await self.aim(wx + 0.5, by + 0.5, nbz + 0.5)
                    if r.get('hit_type') == 'block' and r.get('hit_id') == 'stone':
                        if nbz < bz:
                            # Neighbor is to north, aim at its SOUTH face
                            aim_z = nbz + 1.0
                        else:
                            # Neighbor is to south, aim at its NORTH face
                            aim_z = nbz + 0.0
                        r2 = await self.aim(wx + 0.5, by + 0.5, aim_z)
                        if r2.get('hit_type') == 'block':
                            result = await self.place()
                            if result and result.get('success'):
                                pos = result.get('pos', [])
                                match = (len(pos) == 3 and pos[0] == wx and pos[1] == by and pos[2] == bz)
                                if match:
                                    print(f"    [{desc}] → ✅ {pos} ✓")
                                    self.ok += 1
                                    self.log.append({"t": [wx,by,bz], "ok": True, "at": pos})
                                    break

        build_time = time.time() - t0

        # Clean up pillar
        print(f"\n--- Cleanup pillar ---")
        # Look down and break the pillar blocks
        for _ in range(3):
            dp = -89.0 - self.pose['pitch']
            await self.cmd({"type": "action_look", "yaw": 0, "pitch": dp})
            await self.wait(2)
            if self.ray.get('hit_type') == 'block' and self.ray.get('hit_id') == 'stone':
                r = await self.attack()
                if r and r.get('success'):
                    print(f"  Broke pillar: {r.get('pos')}")
                    await self.wait(5)  # fall

        # Verify
        print(f"\n--- Verify ---")
        await self.walk(wx - 4.0, z0 + 1.0, tol=1.0)
        verified = 0
        for row in range(3):
            by = gy + 1 + row
            cells = ""
            for col in range(3):
                bz = z0 + col
                r = await self.aim(wx + 0.5, by + 0.5, bz + 0.5, s=2)
                is_stone = r.get('hit_id') == 'stone'
                if is_stone: verified += 1
                cells += "█" if is_stone else "·"
            print(f"  Y={by}: [{cells}]")

        # Summary
        correct = sum(1 for e in self.log if e.get('ok'))
        total = 9
        print(f"\n{'='*50}")
        print(f"  Time:     {build_time:.1f}s")
        print(f"  Placed:   {correct}/{total}")
        print(f"  Verified: {verified}/{total}")
        print(f"  Fail:{self.fail} Retry:{self.retry}")
        print(f"  Rate:     {correct/total*100:.0f}%")
        for e in self.log:
            i = "✅" if e['ok'] else "❌"
            print(f"    {i} {e['t']} → {e.get('at','?')}")
        print("=" * 50)
        return correct > 0


async def main():
    b = Builder()
    try:
        await b.connect()
        ok = await b.build()
        return ok
    except Exception as e:
        print(f"❌ {e}")
        import traceback; traceback.print_exc()
        return False
    finally:
        await b.close()

if __name__ == "__main__":
    sys.exit(0 if asyncio.run(main()) else 1)
