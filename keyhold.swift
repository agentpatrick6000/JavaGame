#!/usr/bin/env swift
import Foundation
import CoreGraphics

// Key codes (macOS virtual key codes)
let keyCodes: [String: CGKeyCode] = [
    "w": 13, "a": 0, "s": 1, "d": 2,
    "f": 3, "space": 49, "escape": 53, "esc": 53,
    "shift": 56, "ctrl": 59, "tab": 48,
    "e": 14, "q": 12, "r": 15,
    "f3": 99, "f4": 118,
]

func keyDown(_ code: CGKeyCode) {
    let event = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: true)
    event?.post(tap: .cghidEventTap)
}

func keyUp(_ code: CGKeyCode) {
    let event = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: false)
    event?.post(tap: .cghidEventTap)
}

func holdKey(_ code: CGKeyCode, duration: Double) {
    keyDown(code)
    Thread.sleep(forTimeInterval: duration)
    keyUp(code)
}

func pressKey(_ code: CGKeyCode) {
    keyDown(code)
    Thread.sleep(forTimeInterval: 0.05)
    keyUp(code)
}

func resolveKey(_ name: String) -> CGKeyCode {
    let lower = name.lowercased().trimmingCharacters(in: .whitespaces)
    if let code = keyCodes[lower] {
        return code
    }
    if let num = UInt16(lower) {
        return CGKeyCode(num)
    }
    print("Unknown key: \(name)")
    exit(1)
}

func demoSequence() {
    print("Starting water demo sequence...")
    
    // Walk forward (walk mode, 3s)
    print("Phase 1: Walking forward...")
    holdKey(keyCodes["w"]!, duration: 3.0)
    Thread.sleep(forTimeInterval: 0.5)
    
    // Toggle fly mode (F key)
    print("Phase 2: Toggle fly mode ON")
    pressKey(keyCodes["f"]!)
    Thread.sleep(forTimeInterval: 0.5)
    
    // Fly up with space
    print("Phase 3: Flying up...")
    holdKey(keyCodes["space"]!, duration: 3.0)
    Thread.sleep(forTimeInterval: 0.3)
    
    // Fly forward
    print("Phase 4: Flying forward towards water...")
    holdKey(keyCodes["w"]!, duration: 6.0)
    Thread.sleep(forTimeInterval: 0.3)
    
    // Fly more + strafing
    print("Phase 5: Strafing to show terrain...")
    holdKey(keyCodes["a"]!, duration: 2.0)
    Thread.sleep(forTimeInterval: 0.2)
    holdKey(keyCodes["d"]!, duration: 4.0)
    Thread.sleep(forTimeInterval: 0.2)
    holdKey(keyCodes["a"]!, duration: 2.0)
    Thread.sleep(forTimeInterval: 0.3)
    
    // Continue forward
    print("Phase 6: More forward flight...")
    holdKey(keyCodes["w"]!, duration: 5.0)
    Thread.sleep(forTimeInterval: 0.3)
    
    // Toggle fly mode OFF
    print("Phase 7: Toggle fly mode OFF (walk mode)")
    pressKey(keyCodes["f"]!)
    Thread.sleep(forTimeInterval: 1.0)
    
    // Walk forward
    print("Phase 8: Walking forward on terrain...")
    holdKey(keyCodes["w"]!, duration: 5.0)
    Thread.sleep(forTimeInterval: 0.3)
    
    // Strafe around
    print("Phase 9: Looking around...")
    holdKey(keyCodes["a"]!, duration: 2.0)
    Thread.sleep(forTimeInterval: 0.2)
    holdKey(keyCodes["w"]!, duration: 3.0)
    Thread.sleep(forTimeInterval: 0.2)
    holdKey(keyCodes["d"]!, duration: 3.0)
    Thread.sleep(forTimeInterval: 0.2)
    holdKey(keyCodes["w"]!, duration: 3.0)
    Thread.sleep(forTimeInterval: 0.3)
    
    // Fly mode again for aerial view
    print("Phase 10: Fly mode for aerial view")
    pressKey(keyCodes["f"]!)
    Thread.sleep(forTimeInterval: 0.5)
    holdKey(keyCodes["space"]!, duration: 4.0)
    Thread.sleep(forTimeInterval: 0.2)
    holdKey(keyCodes["w"]!, duration: 6.0)
    Thread.sleep(forTimeInterval: 0.2)
    
    // Descend with ctrl
    print("Phase 11: Descending towards water...")
    holdKey(keyCodes["ctrl"]!, duration: 3.0)
    Thread.sleep(forTimeInterval: 0.3)
    holdKey(keyCodes["w"]!, duration: 4.0)
    Thread.sleep(forTimeInterval: 0.3)
    
    // Back to walk mode
    print("Phase 12: Back to walk mode")
    pressKey(keyCodes["f"]!)
    Thread.sleep(forTimeInterval: 0.5)
    holdKey(keyCodes["w"]!, duration: 5.0)
    Thread.sleep(forTimeInterval: 0.2)
    holdKey(keyCodes["a"]!, duration: 2.0)
    Thread.sleep(forTimeInterval: 0.2)
    holdKey(keyCodes["d"]!, duration: 4.0)
    Thread.sleep(forTimeInterval: 0.2)
    holdKey(keyCodes["w"]!, duration: 3.0)
    
    print("Demo sequence complete!")
}

// Main
let args = CommandLine.arguments
if args.count < 2 {
    print("Usage: swift keyhold.swift <key> <duration> | sequence")
    exit(0)
}

if args[1] == "sequence" {
    demoSequence()
} else if args[1] == "press" {
    let code = resolveKey(args[2])
    pressKey(code)
} else {
    var i = 1
    while i < args.count {
        let code = resolveKey(args[i])
        let duration = i + 1 < args.count ? Double(args[i + 1]) ?? 0.1 : 0.1
        print("Holding key \(args[i]) (code \(code)) for \(duration)s")
        holdKey(code, duration: duration)
        Thread.sleep(forTimeInterval: 0.1)
        i += 2
    }
}
