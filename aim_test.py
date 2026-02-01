#!/usr/bin/env python3
"""
Aiming Test — Look at specific blocks, measure raycast accuracy.

Tests:
1. Single-shot look-at precision (one delta, how close?)
2. Correction loop convergence (how many iterations to hit target?)
3. Various distances (near/far blocks)
4. Various angles (above/below/behind)
5. Look-at-precise algorithm: best practices

Outputs JSON results + human-readable summary.
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


def calc_look_delta(cx, cy, cz, cur_yaw, cur_pitch, tx, ty, tz):
    """Calculate yaw/pitch deltas to look from camera at target."""
    dx = tx - cx
    dy = ty - cy
    dz = tz - cz
    horizontal_dist = math.sqrt(dx * dx + dz * dz)
    
    if horizontal_dist < 0.001:
        target_yaw = cur_yaw
        target_pitch = 89.0 if dy > 0 else -89.0
    else:
        target_yaw = math.degrees(math.atan2(dz, dx))
        target_pitch = math.degrees(math.atan2(dy, horizontal_dist))
    
    target_pitch = max(-89.0, min(89.0, target_pitch))
    delta_yaw = target_yaw - cur_yaw
    delta_pitch = target_pitch - cur_pitch
    while delta_yaw > 180: delta_yaw -= 360
    while delta_yaw < -180: delta_yaw += 360
    return delta_yaw, delta_pitch


class AimTester:
    def __init__(self):
        self.ws = None
        self.pose = {}
        self.raycast = {}
        self.ui_state = {}
        self.tick = 0
        self.results = []
    
    async def connect(self):
        print(f"Connecting to {URI}...")
        self.ws = await websockets.connect(URI, max_size=2**20)
        hello = json.loads(await self.ws.recv())
        print(f"Connected! Version: {hello.get('version')}")
        await self.recv_state()
        print(f"Position: ({self.pose['x']:.2f}, {self.pose['y']:.2f}, {self.pose['z']:.2f})")
        print(f"Yaw: {self.pose['yaw']:.2f}, Pitch: {self.pose['pitch']:.2f}")
        print(f"Raycast: {self.raycast.get('hit_type')}/{self.raycast.get('hit_id')}")
    
    async def disconnect(self):
        if self.ws:
            await self.ws.close()
    
    async def recv_state(self):
        while True:
            msg = json.loads(await self.ws.recv())
            if msg.get("type") == "state":
                self.tick = msg["tick"]
                self.pose = msg["pose"]
                self.raycast = msg.get("raycast", {})
                self.ui_state = msg.get("ui_state", {})
                return msg
    
    async def send_action(self, action):
        await self.ws.send(json.dumps(action))
    
    async def wait_ticks(self, n):
        for _ in range(n):
            await self.recv_state()
    
    async def set_look(self, target_yaw, target_pitch=0):
        """Set absolute look direction."""
        dy = target_yaw - self.pose['yaw']
        dp = target_pitch - self.pose['pitch']
        while dy > 180: dy -= 360
        while dy < -180: dy += 360
        await self.send_action({"type": "action_look", "yaw": dy, "pitch": dp})
        await self.wait_ticks(2)
    
    def get_ground_y(self):
        """Player eye = ground_top + 1.62, so ground = floor(eye_y - 1.62)"""
        return int(math.floor(self.pose['y'] - 1.62))
    
    # ========================
    # TEST 1: Single-shot look-at accuracy
    # ========================
    async def test_single_shot_lookat(self):
        print("\n" + "="*60)
        print("TEST 1: Single-shot look-at accuracy")
        print("="*60)
        print("(One action_look, then check if raycast hits the target block)")
        
        results = []
        ground_y = self.get_ground_y()
        cx, cy, cz = self.pose['x'], self.pose['y'], self.pose['z']
        
        # Look at the ground at various offsets
        # Target = center of ground block top face at various XZ offsets
        offsets = [
            (1, 0, "1 block east"),
            (3, 0, "3 blocks east"),
            (5, 0, "5 blocks east"),
            (2, 2, "diagonal 2,2"),
            (0, 3, "3 blocks south"),
            (-2, 0, "2 blocks west"),
            (0, -3, "3 blocks north"),
            (5, 5, "diagonal 5,5"),
        ]
        
        for dx, dz, label in offsets:
            # Target block top face center
            bx = int(cx) + dx
            bz = int(cz) + dz
            tx = bx + 0.5
            ty = ground_y + 1.0  # top face
            tz = bz + 0.5
            
            target_dist = math.sqrt((tx - cx)**2 + (ty - cy)**2 + (tz - cz)**2)
            
            # Calculate and send look delta
            dyaw, dpitch = calc_look_delta(cx, cy, cz, self.pose['yaw'], self.pose['pitch'], tx, ty, tz)
            
            await self.send_action({"type": "action_look", "yaw": dyaw, "pitch": dpitch})
            await self.wait_ticks(3)
            
            # Check raycast
            hit = self.raycast
            
            # After the look, recalculate what we're actually pointing at
            actual_yaw = self.pose['yaw']
            actual_pitch = self.pose['pitch']
            
            # What was the residual error?
            dyaw2, dpitch2 = calc_look_delta(
                self.pose['x'], self.pose['y'], self.pose['z'],
                actual_yaw, actual_pitch,
                tx, ty, tz
            )
            residual = math.sqrt(dyaw2**2 + dpitch2**2)
            
            result = {
                "label": label,
                "target_block": (bx, ground_y, bz),
                "target_dist": round(target_dist, 2),
                "hit_type": hit.get("hit_type"),
                "hit_id": hit.get("hit_id"),
                "hit_normal": hit.get("hit_normal"),
                "sent_delta": (round(dyaw, 3), round(dpitch, 3)),
                "residual_error": round(residual, 3),
                "success": hit.get("hit_type") == "block" and hit.get("hit_normal") == "TOP",
            }
            results.append(result)
            
            status = "✅" if result["success"] else "⚠️"
            print(f"  {status} {label}: hit={hit.get('hit_id','miss')} face={hit.get('hit_normal','?')} "
                  f"dist={target_dist:.1f} residual={residual:.3f}°")
        
        self.results.append({"test": "single_shot_lookat", "results": results})
        return results
    
    # ========================
    # TEST 2: Correction loop convergence
    # ========================
    async def test_correction_convergence(self):
        print("\n" + "="*60)
        print("TEST 2: Correction loop convergence")
        print("="*60)
        print("(Multiple iterations to converge on target)")
        
        results = []
        ground_y = self.get_ground_y()
        cx, cy, cz = self.pose['x'], self.pose['y'], self.pose['z']
        
        targets = [
            (int(cx) + 3, ground_y, int(cz) + 0, "3bl east ground"),
            (int(cx) + 5, ground_y, int(cz) + 5, "diagonal 7bl"),
            (int(cx) + 1, ground_y + 2, int(cz) + 0, "1bl east, 2 above"),
            (int(cx) + 0, ground_y, int(cz) + 7, "7bl south ground"),
        ]
        
        for bx, by, bz, label in targets:
            # First, look somewhere random
            await self.set_look(45, 10)
            
            tx, ty, tz = bx + 0.5, by + 1.0, bz + 0.5
            iterations = []
            
            for attempt in range(10):
                cx_now = self.pose['x']
                cy_now = self.pose['y']
                cz_now = self.pose['z']
                
                dyaw, dpitch = calc_look_delta(
                    cx_now, cy_now, cz_now,
                    self.pose['yaw'], self.pose['pitch'],
                    tx, ty, tz
                )
                
                residual = math.sqrt(dyaw**2 + dpitch**2)
                
                if residual < 0.5:
                    # Close enough before sending
                    iterations.append({
                        "attempt": attempt,
                        "residual_before": round(residual, 4),
                        "delta_sent": (0, 0),
                        "converged": True,
                        "hit_type": self.raycast.get("hit_type"),
                        "hit_id": self.raycast.get("hit_id"),
                        "hit_normal": self.raycast.get("hit_normal"),
                    })
                    break
                
                await self.send_action({"type": "action_look", "yaw": dyaw, "pitch": dpitch})
                await self.wait_ticks(2)
                
                # Measure residual after
                dyaw2, dpitch2 = calc_look_delta(
                    self.pose['x'], self.pose['y'], self.pose['z'],
                    self.pose['yaw'], self.pose['pitch'],
                    tx, ty, tz
                )
                residual_after = math.sqrt(dyaw2**2 + dpitch2**2)
                
                iterations.append({
                    "attempt": attempt,
                    "residual_before": round(residual, 4),
                    "delta_sent": (round(dyaw, 3), round(dpitch, 3)),
                    "residual_after": round(residual_after, 4),
                    "converged": residual_after < 0.5,
                    "hit_type": self.raycast.get("hit_type"),
                    "hit_id": self.raycast.get("hit_id"),
                    "hit_normal": self.raycast.get("hit_normal"),
                })
                
                if residual_after < 0.5:
                    break
            
            converged = any(i.get("converged") for i in iterations)
            num_iters = len(iterations)
            final_residual = iterations[-1].get("residual_after", iterations[-1].get("residual_before", 99))
            
            result = {
                "label": label,
                "converged": converged,
                "iterations": num_iters,
                "final_residual": round(final_residual, 4),
                "final_hit": iterations[-1].get("hit_id"),
                "iteration_details": iterations,
            }
            results.append(result)
            
            status = "✅" if converged else "❌"
            print(f"\n  {status} {label}: converged in {num_iters} iter, residual={final_residual:.4f}°")
            for it in iterations:
                r_before = it.get("residual_before", "?")
                r_after = it.get("residual_after", "n/a")
                print(f"    iter {it['attempt']}: {r_before:.2f}° → {r_after if isinstance(r_after, str) else f'{r_after:.4f}°'} "
                      f"[hit={it.get('hit_id','?')} {it.get('hit_normal','?')}]")
        
        self.results.append({"test": "correction_convergence", "results": results})
        return results
    
    # ========================
    # TEST 3: Large rotation speed (how fast can we turn?)
    # ========================
    async def test_rotation_speed(self):
        print("\n" + "="*60)
        print("TEST 3: Rotation speed / large angle handling")
        print("="*60)
        
        results = []
        
        # Test various delta magnitudes
        for delta_yaw in [10, 45, 90, 135, 180, 270, 360]:
            start_yaw = self.pose['yaw']
            
            await self.send_action({"type": "action_look", "yaw": float(delta_yaw), "pitch": 0})
            await self.wait_ticks(1)
            
            after_1tick_yaw = self.pose['yaw']
            change_1tick = after_1tick_yaw - start_yaw
            
            await self.wait_ticks(2)
            after_settle_yaw = self.pose['yaw']
            change_settle = after_settle_yaw - start_yaw
            
            result = {
                "delta_yaw": delta_yaw,
                "change_1tick": round(change_1tick, 3),
                "change_settled": round(change_settle, 3),
                "applied_instantly": abs(change_1tick - delta_yaw) < 0.5,
            }
            results.append(result)
            print(f"  delta={delta_yaw:4d}°: after 1 tick={change_1tick:.1f}°, settled={change_settle:.1f}° "
                  f"{'✅ instant' if result['applied_instantly'] else '⚠️ delayed'}")
        
        # Test pitch extremes
        print("  --- Pitch extremes ---")
        for delta_pitch in [-89, -45, 0, 45, 89]:
            await self.set_look(0, 0)  # reset
            
            await self.send_action({"type": "action_look", "yaw": 0, "pitch": float(delta_pitch)})
            await self.wait_ticks(2)
            
            actual = self.pose['pitch']
            result = {
                "delta_pitch": delta_pitch,
                "actual_pitch": round(actual, 3),
                "error": round(abs(actual - delta_pitch), 3),
            }
            results.append(result)
            print(f"  pitch delta={delta_pitch:4d}°: actual={actual:.1f}° (err={result['error']:.3f}°)")
        
        self.results.append({"test": "rotation_speed", "results": results})
        return results
    
    # ========================
    # TEST 4: Raycast hit accuracy at various ranges
    # ========================
    async def test_raycast_ranges(self):
        print("\n" + "="*60)
        print("TEST 4: Raycast accuracy at various ranges")
        print("="*60)
        
        results = []
        
        # Look straight down - should hit ground
        await self.set_look(0, -89)
        hit = self.raycast
        result = {
            "direction": "straight down (-89°)",
            "hit_type": hit.get("hit_type"),
            "hit_id": hit.get("hit_id"),
            "hit_normal": hit.get("hit_normal"),
            "hit_dist": hit.get("hit_dist"),
        }
        results.append(result)
        print(f"  Down (-89°): {hit.get('hit_type')}/{hit.get('hit_id')} normal={hit.get('hit_normal')} dist_bucket={hit.get('hit_dist')}")
        
        # Look at horizon - different directions
        for angle in [0, 45, 90, 135, 180]:
            await self.set_look(angle, -10)  # Slight downward to hit terrain
            hit = self.raycast
            result = {
                "direction": f"yaw={angle} pitch=-10",
                "hit_type": hit.get("hit_type"),
                "hit_id": hit.get("hit_id"),
                "hit_normal": hit.get("hit_normal"),
                "hit_dist": hit.get("hit_dist"),
            }
            results.append(result)
            print(f"  yaw={angle:3d}° p=-10°: {hit.get('hit_type')}/{hit.get('hit_id')} "
                  f"normal={hit.get('hit_normal')} dist={hit.get('hit_dist')}")
        
        # Look at sky
        await self.set_look(0, 89)
        hit = self.raycast
        result = {
            "direction": "straight up (89°)",
            "hit_type": hit.get("hit_type"),
            "hit_id": hit.get("hit_id"),
        }
        results.append(result)
        print(f"  Up (89°): {hit.get('hit_type')}/{hit.get('hit_id')}")
        
        self.results.append({"test": "raycast_ranges", "results": results})
        return results
    
    # ========================
    # TEST 5: look_at_precise benchmark (the algorithm from house_builder.py)
    # ========================
    async def test_lookat_precise_benchmark(self):
        print("\n" + "="*60)
        print("TEST 5: look_at_precise algorithm benchmark")
        print("="*60)
        
        results = []
        ground_y = self.get_ground_y()
        cx, cy, cz = self.pose['x'], self.pose['y'], self.pose['z']
        
        # Various targets at different angles/distances
        targets = [
            (int(cx) + 2, ground_y + 1.0, int(cz), "near ground"),
            (int(cx) + 5, ground_y + 1.0, int(cz) + 3, "mid diagonal"),
            (int(cx) + 7, ground_y + 1.0, int(cz) + 7, "far diagonal"),
            (int(cx), ground_y + 1.0, int(cz) + 1, "1bl south"),
            (int(cx) - 3, ground_y + 1.0, int(cz) - 3, "behind diagonal"),
        ]
        
        for tx, ty, tz, label in targets:
            # Start from a random angle
            await self.set_look(123, -20)
            
            start_time = time.time()
            converged = False
            attempts = 0
            
            for attempt in range(8):
                attempts += 1
                cx_now, cy_now, cz_now = self.pose['x'], self.pose['y'], self.pose['z']
                
                dyaw, dpitch = calc_look_delta(
                    cx_now, cy_now, cz_now,
                    self.pose['yaw'], self.pose['pitch'],
                    tx + 0.5, ty, tz + 0.5
                )
                
                if abs(dyaw) < 0.5 and abs(dpitch) < 0.5:
                    converged = True
                    break
                
                await self.send_action({"type": "action_look", "yaw": dyaw, "pitch": dpitch})
                await self.wait_ticks(2)
            
            elapsed_ms = (time.time() - start_time) * 1000
            
            # Check final raycast
            hit = self.raycast
            
            result = {
                "label": label,
                "target": (tx, ty, tz),
                "converged": converged,
                "attempts": attempts,
                "elapsed_ms": round(elapsed_ms),
                "final_hit": hit.get("hit_id"),
                "final_normal": hit.get("hit_normal"),
            }
            results.append(result)
            status = "✅" if converged else "⚠️"
            print(f"  {status} {label}: {attempts} attempts, {elapsed_ms:.0f}ms, "
                  f"hit={hit.get('hit_id','miss')} {hit.get('hit_normal','')}")
        
        self.results.append({"test": "lookat_precise_benchmark", "results": results})
        return results
    
    async def run_all(self):
        await self.connect()
        
        try:
            await self.test_rotation_speed()
            await self.test_single_shot_lookat()
            await self.test_correction_convergence()
            await self.test_raycast_ranges()
            await self.test_lookat_precise_benchmark()
            
            # Write JSON results
            out_path = "/Users/machiagent/Desktop/Code/MyProjects/JavaGame/aim_test_results.json"
            with open(out_path, "w") as f:
                json.dump(self.results, f, indent=2)
            print(f"\n\nResults written to {out_path}")
            
            # Summary
            print("\n" + "="*60)
            print("AIMING TEST SUMMARY")
            print("="*60)
            for suite in self.results:
                test_name = suite['test']
                print(f"\n  {test_name}:")
                for r in suite['results']:
                    if test_name == "rotation_speed":
                        if 'delta_yaw' in r:
                            inst = '✅' if r.get('applied_instantly') else '⚠️'
                            print(f"    {inst} yaw Δ{r['delta_yaw']}°: {r['change_1tick']:.1f}° after 1 tick")
                    elif test_name == "single_shot_lookat":
                        s = '✅' if r['success'] else '⚠️'
                        print(f"    {s} {r['label']}: residual={r['residual_error']:.3f}°")
                    elif test_name == "correction_convergence":
                        s = '✅' if r['converged'] else '❌'
                        print(f"    {s} {r['label']}: {r['iterations']} iters, final={r['final_residual']:.4f}°")
                    elif test_name == "lookat_precise_benchmark":
                        s = '✅' if r['converged'] else '⚠️'
                        print(f"    {s} {r['label']}: {r['attempts']} attempts, {r['elapsed_ms']}ms")
        
        finally:
            await self.disconnect()


async def main():
    tester = AimTester()
    try:
        await tester.run_all()
    except Exception as e:
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()
        await tester.disconnect()

if __name__ == "__main__":
    asyncio.run(main())
