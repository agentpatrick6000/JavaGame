#!/usr/bin/env python3
"""
Navigation Test — Walk to coordinates.

Connects to VoxelGame agent API, gets current pose, navigates to target (x, z).
Measures: final position error, time taken, correction steps.
Documents: optimal rotation speed, movement duration tuning, correction strategy.

Test cases:
  1. 10 blocks north (-Z direction)
  2. 20 blocks east (+X direction)
  3. 30 blocks diagonal (NE: +X, -Z)
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

# Walking speed ≈ 4.3 blocks/sec, sprinting ≈ 6.5 blocks/sec
WALK_SPEED = 4.3


def normalize_angle(a):
    """Normalize angle to -180..180."""
    while a > 180: a -= 360
    while a < -180: a += 360
    return a


def calc_yaw_to_target(cx, cz, tx, tz):
    """Calculate the absolute yaw to face from (cx,cz) toward (tx,tz)."""
    dx = tx - cx
    dz = tz - cz
    return math.degrees(math.atan2(dz, dx))


def distance_xz(x1, z1, x2, z2):
    return math.sqrt((x2 - x1)**2 + (z2 - z1)**2)


class NavTester:
    def __init__(self):
        self.ws = None
        self.state = None
        self.pose = {}
        self.ui_state = {}
        self.results = []

    async def connect(self):
        self.ws = await websockets.connect(URI, max_size=2**20)
        hello = json.loads(await self.ws.recv())
        print(f"Connected. Version: {hello.get('version')}")
        await self.recv_state()
        self.print_pose("Initial")

    async def disconnect(self):
        if self.ws:
            await self.ws.close()

    async def recv_state(self):
        while True:
            msg = json.loads(await self.ws.recv())
            if msg.get("type") == "state":
                self.state = msg
                self.pose = msg["pose"]
                self.ui_state = msg.get("ui_state", {})
                return msg

    async def send(self, action):
        await self.ws.send(json.dumps(action))

    def print_pose(self, label=""):
        p = self.pose
        print(f"  [{label}] pos=({p['x']:.2f}, {p['y']:.2f}, {p['z']:.2f}) "
              f"yaw={p['yaw']:.2f} pitch={p['pitch']:.2f} "
              f"ground={self.ui_state.get('on_ground')}")

    # ----------------------------------------------------------------
    # ROTATION — turn to face target yaw
    # ----------------------------------------------------------------
    async def face_direction(self, target_yaw, max_steps=10, tolerance=1.0):
        """Rotate to face target_yaw. Returns (steps_taken, final_yaw_error)."""
        steps = 0
        for _ in range(max_steps):
            cur_yaw = self.pose['yaw']
            delta = normalize_angle(target_yaw - cur_yaw)
            if abs(delta) <= tolerance:
                break
            # Also level pitch to 0
            await self.send({"type": "action_look", "yaw": delta, "pitch": -self.pose['pitch']})
            await self.recv_state()
            await self.recv_state()  # settle
            steps += 1

        final_error = abs(normalize_angle(target_yaw - self.pose['yaw']))
        return steps, final_error

    # ----------------------------------------------------------------
    # MOVEMENT — walk toward target with correction loop
    # ----------------------------------------------------------------
    async def navigate_to(self, tx, tz, tolerance=1.0, max_corrections=30):
        """
        Navigate from current position to (tx, tz).
        Strategy: face target → walk short burst → correct → repeat.
        Detects stuck conditions and tries jumping.
        """
        start_x = self.pose['x']
        start_z = self.pose['z']
        target_dist = distance_xz(start_x, start_z, tx, tz)

        print(f"\n--- Navigate to ({tx:.1f}, {tz:.1f}), distance={target_dist:.1f} ---")
        self.print_pose("start")

        t0 = time.time()
        corrections = 0
        total_look_steps = 0
        stuck_count = 0
        jump_count = 0
        path_points = [(self.pose['x'], self.pose['z'])]
        prev_x, prev_z = self.pose['x'], self.pose['z']

        for i in range(max_corrections):
            cx, cz = self.pose['x'], self.pose['z']
            remaining = distance_xz(cx, cz, tx, tz)

            if remaining <= tolerance:
                print(f"  ✓ Arrived! remaining={remaining:.2f}")
                break

            # 1) Face target
            target_yaw = calc_yaw_to_target(cx, cz, tx, tz)
            look_steps, yaw_err = await self.face_direction(target_yaw, tolerance=2.0)
            total_look_steps += look_steps

            # 2) Walk — short bursts for better correction
            #    Use smaller durations for short distances
            if remaining > 10:
                walk_time_ms = 1500  # ~6.5 blocks
            elif remaining > 5:
                walk_time_ms = 1000  # ~4.3 blocks
            elif remaining > 2:
                walk_time_ms = 500   # ~2.1 blocks
            else:
                walk_time_ms = 250   # ~1 block

            await self.send({
                "type": "action_move",
                "forward": 1.0,
                "strafe": 0.0,
                "duration": walk_time_ms
            })

            # Wait for move to finish
            wait_ticks = max(3, int(walk_time_ms / 50) + 3)
            for _ in range(wait_ticks):
                await self.recv_state()

            path_points.append((self.pose['x'], self.pose['z']))
            corrections += 1

            # Check if we moved
            moved = distance_xz(prev_x, prev_z, self.pose['x'], self.pose['z'])
            new_remaining = distance_xz(tx, tz, self.pose['x'], self.pose['z'])

            if moved < 0.3:
                stuck_count += 1
                if stuck_count >= 2:
                    # Try jumping over obstacle
                    print(f"  ⚠ Stuck! Trying jump (attempt {jump_count+1})")
                    await self.send({"type": "action_jump"})
                    await self.recv_state()
                    # Move forward during jump
                    await self.send({
                        "type": "action_move",
                        "forward": 1.0, "strafe": 0.0, "duration": 500
                    })
                    for _ in range(15):
                        await self.recv_state()
                    jump_count += 1
                    stuck_count = 0

                    if jump_count >= 3:
                        # Try strafing around obstacle
                        print(f"  ⚠ Still stuck, trying strafe")
                        await self.send({
                            "type": "action_move",
                            "forward": 0.5, "strafe": 1.0, "duration": 800
                        })
                        for _ in range(20):
                            await self.recv_state()
                        jump_count = 0
            else:
                stuck_count = 0

            prev_x, prev_z = self.pose['x'], self.pose['z']

            print(f"  step {i+1}: walked {walk_time_ms}ms, moved={moved:.2f}, "
                  f"remaining={new_remaining:.2f}")

        elapsed = time.time() - t0
        final_x, final_z = self.pose['x'], self.pose['z']
        final_error = distance_xz(tx, tz, final_x, final_z)

        # Path efficiency
        path_length = 0
        for j in range(1, len(path_points)):
            path_length += distance_xz(
                path_points[j-1][0], path_points[j-1][1],
                path_points[j][0], path_points[j][1])
        efficiency = (target_dist / max(path_length, 0.01)) * 100

        self.print_pose("end")

        result = {
            "target": (tx, tz),
            "start": (start_x, start_z),
            "final": (final_x, final_z),
            "target_dist": target_dist,
            "final_error": final_error,
            "path_length": path_length,
            "efficiency_pct": efficiency,
            "elapsed_sec": elapsed,
            "corrections": corrections,
            "look_steps": total_look_steps,
            "jump_count": jump_count,
        }
        self.results.append(result)
        return result

    # ----------------------------------------------------------------
    # Tests
    # ----------------------------------------------------------------
    async def test_rotation_precision(self):
        """Test how precisely we can face N, E, S, W."""
        print("\n" + "="*60)
        print("TEST 0: Rotation precision (face N/E/S/W)")
        print("="*60)
        targets = {"North (-Z)": -90, "East (+X)": 0, "South (+Z)": 90, "West (-X)": 180}
        results = {}
        for name, target in targets.items():
            steps, err = await self.face_direction(target, tolerance=0.5)
            actual = self.pose['yaw']
            results[name] = {"target_yaw": target, "steps": steps, "error": err,
                            "actual_yaw": actual}
            print(f"  {name}: target={target}° actual={actual:.2f}° "
                  f"error={err:.2f}° steps={steps}")
        return results

    async def test_walk_speed_measurement(self):
        """Measure actual walking speed by walking straight for known duration."""
        print("\n" + "="*60)
        print("TEST 0b: Walking speed measurement")
        print("="*60)
        # Face east (+X)
        await self.face_direction(0, tolerance=1.0)
        
        before_x = self.pose['x']
        before_z = self.pose['z']
        
        # Walk 3 seconds
        duration = 3000
        await self.send({
            "type": "action_move",
            "forward": 1.0, "strafe": 0.0, "duration": duration
        })
        for _ in range(int(duration / 50) + 5):
            await self.recv_state()
        
        after_x = self.pose['x']
        after_z = self.pose['z']
        dist = distance_xz(before_x, before_z, after_x, after_z)
        speed = dist / (duration / 1000)
        
        print(f"  Walked {dist:.2f} blocks in {duration}ms = {speed:.2f} blocks/sec")
        print(f"  From ({before_x:.2f}, {before_z:.2f}) to ({after_x:.2f}, {after_z:.2f})")
        return {"distance": dist, "duration_ms": duration, "speed_bps": speed}

    async def test_north(self):
        """Walk 10 blocks north (negative Z direction)."""
        print("\n" + "="*60)
        print("TEST 1: Walk 10 blocks north (-Z)")
        print("="*60)
        cx, cz = self.pose['x'], self.pose['z']
        return await self.navigate_to(cx, cz - 10)

    async def test_east(self):
        """Walk 20 blocks east (positive X direction)."""
        print("\n" + "="*60)
        print("TEST 2: Walk 20 blocks east (+X)")
        print("="*60)
        cx, cz = self.pose['x'], self.pose['z']
        return await self.navigate_to(cx + 20, cz)

    async def test_diagonal(self):
        """Walk 30 blocks NE diagonal (+X, -Z)."""
        print("\n" + "="*60)
        print("TEST 3: Walk 30 blocks diagonal (NE)")
        print("="*60)
        cx, cz = self.pose['x'], self.pose['z']
        d = 30 / math.sqrt(2)
        return await self.navigate_to(cx + d, cz - d)

    async def test_short_walk(self):
        """Walk 3 blocks — test precision at short range."""
        print("\n" + "="*60)
        print("TEST 4: Walk 3 blocks east (precision)")
        print("="*60)
        cx, cz = self.pose['x'], self.pose['z']
        return await self.navigate_to(cx + 3, cz, tolerance=0.5)

    # ----------------------------------------------------------------
    # Main
    # ----------------------------------------------------------------
    async def run_all(self):
        await self.connect()

        rot_results = await self.test_rotation_precision()
        speed = await self.test_walk_speed_measurement()

        r1 = await self.test_north()
        r2 = await self.test_east()
        r3 = await self.test_diagonal()
        r4 = await self.test_short_walk()

        # Summary
        print("\n" + "="*60)
        print("NAVIGATION TEST SUMMARY")
        print("="*60)
        print(f"\nMeasured walking speed: {speed['speed_bps']:.2f} blocks/sec")
        print(f"\n{'Test':<25} {'Dist':>6} {'Error':>7} {'Time':>6} {'Steps':>6} "
              f"{'Eff%':>6} {'Jumps':>6}")
        print("-" * 68)
        labels = ["10 blocks N", "20 blocks E", "30 blocks NE", "3 blocks E (prec)"]
        for label, r in zip(labels, self.results):
            print(f"{label:<25} {r['target_dist']:>6.1f} {r['final_error']:>7.2f} "
                  f"{r['elapsed_sec']:>6.1f}s {r['corrections']:>5}  "
                  f"{r['efficiency_pct']:>5.1f}% {r['jump_count']:>5}")

        print(f"\nRotation precision:")
        for name, rr in rot_results.items():
            print(f"  {name}: error={rr['error']:.2f}° in {rr['steps']} step(s)")

        avg_error = sum(r['final_error'] for r in self.results) / len(self.results)
        avg_eff = sum(r['efficiency_pct'] for r in self.results) / len(self.results)
        print(f"\n--- Key findings ---")
        print(f"Average final error: {avg_error:.2f} blocks")
        print(f"Average path efficiency: {avg_eff:.1f}%")

        await self.disconnect()
        return self.results, rot_results, speed


async def main():
    tester = NavTester()
    try:
        results, rot, speed = await tester.run_all()
        with open("/tmp/nav_test_results.json", "w") as f:
            json.dump({
                "speed": speed,
                "navigation": [{k: v for k, v in r.items()} for r in results],
                "rotation": rot
            }, f, indent=2)
        print("\nResults saved to /tmp/nav_test_results.json")
    except ConnectionRefusedError:
        print("ERROR: Game not running. Start with: ./gradlew run --args='--agent-server'")
    except Exception as e:
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    asyncio.run(main())
