#!/usr/bin/env python3
"""
Send proper key down/key up events via CGEvents.
This allows holding keys for a duration, which GLFW can detect via isKeyDown().

Usage:
  python3 keyhold.py <keycode> <duration_seconds>
  python3 keyhold.py <keycode> <duration_seconds> <keycode2> <duration2> ...
  python3 keyhold.py sequence  (predefined demo sequence)

Key codes (macOS virtual key codes):
  W=13, A=0, S=1, D=2, F=3, Space=49, Escape=53, Shift=56, Ctrl=59
"""

import sys
import time
import Quartz

KEY_CODES = {
    'w': 13, 'a': 0, 's': 1, 'd': 2,
    'f': 3, 'space': 49, 'escape': 53, 'esc': 53,
    'shift': 56, 'ctrl': 59, 'tab': 48,
    'e': 14, 'q': 12, 'r': 15,
    'f3': 99, 'f4': 118,
}

def key_down(keycode):
    event = Quartz.CGEventCreateKeyboardEvent(None, keycode, True)
    Quartz.CGEventPost(Quartz.kCGHIDEventTap, event)

def key_up(keycode):
    event = Quartz.CGEventCreateKeyboardEvent(None, keycode, False)
    Quartz.CGEventPost(Quartz.kCGHIDEventTap, event)

def hold_key(keycode, duration):
    """Hold a key down for the specified duration."""
    key_down(keycode)
    time.sleep(duration)
    key_up(keycode)

def press_key(keycode):
    """Quick press and release."""
    key_down(keycode)
    time.sleep(0.05)
    key_up(keycode)

def resolve_key(name):
    """Resolve key name to keycode."""
    name = name.lower().strip()
    if name in KEY_CODES:
        return KEY_CODES[name]
    try:
        return int(name)
    except ValueError:
        print(f"Unknown key: {name}")
        sys.exit(1)

def demo_sequence():
    """Run the water demo automation sequence."""
    print("Starting water demo sequence...")
    
    # Walk forward (walk mode, 3s)
    print("Phase 1: Walking forward...")
    hold_key(KEY_CODES['w'], 3.0)
    time.sleep(0.5)
    
    # Toggle fly mode (F key)
    print("Phase 2: Toggle fly mode ON")
    press_key(KEY_CODES['f'])
    time.sleep(0.5)
    
    # Fly up with space
    print("Phase 3: Flying up...")
    hold_key(KEY_CODES['space'], 3.0)
    time.sleep(0.3)
    
    # Fly forward
    print("Phase 4: Flying forward towards water...")
    hold_key(KEY_CODES['w'], 6.0)
    time.sleep(0.3)
    
    # Fly more forward + some strafing
    print("Phase 5: Strafing to show terrain...")
    hold_key(KEY_CODES['a'], 2.0)
    time.sleep(0.2)
    hold_key(KEY_CODES['d'], 4.0)
    time.sleep(0.2)
    hold_key(KEY_CODES['a'], 2.0)
    time.sleep(0.3)
    
    # Continue forward
    print("Phase 6: More forward flight...")
    hold_key(KEY_CODES['w'], 5.0)
    time.sleep(0.3)
    
    # Toggle fly mode OFF (back to walk)
    print("Phase 7: Toggle fly mode OFF (walk mode)")
    press_key(KEY_CODES['f'])
    time.sleep(1.0)
    
    # Walk forward (should fall/walk on terrain)
    print("Phase 8: Walking forward on terrain...")
    hold_key(KEY_CODES['w'], 5.0)
    time.sleep(0.3)
    
    # Strafe around
    print("Phase 9: Looking around...")
    hold_key(KEY_CODES['a'], 2.0)
    time.sleep(0.2)
    hold_key(KEY_CODES['w'], 3.0)
    time.sleep(0.2)
    hold_key(KEY_CODES['d'], 3.0)
    time.sleep(0.2)
    hold_key(KEY_CODES['w'], 3.0)
    time.sleep(0.3)
    
    # Fly mode again for aerial view
    print("Phase 10: Fly mode for aerial view")
    press_key(KEY_CODES['f'])
    time.sleep(0.5)
    hold_key(KEY_CODES['space'], 4.0)
    time.sleep(0.2)
    hold_key(KEY_CODES['w'], 6.0)
    time.sleep(0.2)
    
    # Descend with ctrl
    print("Phase 11: Descending towards water...")
    hold_key(KEY_CODES['ctrl'], 3.0)
    time.sleep(0.3)
    hold_key(KEY_CODES['w'], 4.0)
    time.sleep(0.3)
    
    # Back to walk mode
    print("Phase 12: Back to walk mode, interact with water")
    press_key(KEY_CODES['f'])
    time.sleep(0.5)
    hold_key(KEY_CODES['w'], 5.0)
    time.sleep(0.2)
    hold_key(KEY_CODES['a'], 2.0)
    time.sleep(0.2)
    hold_key(KEY_CODES['d'], 4.0)
    time.sleep(0.2)
    hold_key(KEY_CODES['w'], 3.0)
    
    print("Demo sequence complete!")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    
    if sys.argv[1] == 'sequence':
        demo_sequence()
    elif sys.argv[1] == 'press':
        # Quick press
        keycode = resolve_key(sys.argv[2])
        press_key(keycode)
    else:
        # Hold key(s)
        args = sys.argv[1:]
        i = 0
        while i < len(args):
            keycode = resolve_key(args[i])
            duration = float(args[i + 1]) if i + 1 < len(args) else 0.1
            print(f"Holding key {args[i]} (code {keycode}) for {duration}s")
            hold_key(keycode, duration)
            time.sleep(0.1)
            i += 2
