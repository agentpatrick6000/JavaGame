package com.voxelgame;

import com.voxelgame.core.GameLoop;

/**
 * Entry point for VoxelGame.
 * <p>
 * Usage:
 *   java -jar voxelgame.jar                          # start at main menu
 *   java -jar voxelgame.jar --direct [world]          # skip menu, load world directly
 *   java -jar voxelgame.jar --automation              # automation mode (direct to default world)
 *   java -jar voxelgame.jar --automation --script demo-script.txt
 *   java -jar voxelgame.jar --agent-server            # agent interface (direct to default world)
 *   java -jar voxelgame.jar --bench-world [BEFORE|AFTER]  # world streaming benchmark
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("VoxelGame starting...");

        boolean automationMode = false;
        boolean agentServerMode = false;
        boolean autoTestMode = false;
        boolean captureDebugViews = false;
        boolean captureSpawnValidation = false;
        boolean directMode = false;
        boolean createMode = false;
        boolean benchWorldMode = false;
        String benchWorldPhase = "BEFORE";
        String benchSeed = null;
        String benchOutDir = null;
        String directWorldName = null;
        String scriptPath = null;
        String captureOutputDir = null;
        String captureSeed = null;
        String captureProfile = null;  // --profile BEFORE/AFTER_FOG/AFTER_EXPOSURE

        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--automation" -> automationMode = true;
                case "--agent-server" -> agentServerMode = true;
                case "--auto-test" -> autoTestMode = true;
                case "--capture-debug-views" -> captureDebugViews = true;
                case "--capture-spawn-validation" -> captureSpawnValidation = true;
                case "--capture-output" -> {
                    if (i + 1 < args.length) {
                        captureOutputDir = args[++i];
                    }
                }
                case "--capture-seed" -> {
                    if (i + 1 < args.length) {
                        captureSeed = args[++i];
                    }
                }
                case "--profile" -> {
                    if (i + 1 < args.length) {
                        captureProfile = args[++i];
                    }
                }
                case "--bench-world" -> {
                    benchWorldMode = true;
                    directMode = true;
                    // Optional phase name: BEFORE or AFTER
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        benchWorldPhase = args[++i].toUpperCase();
                    }
                }
                case "--seed" -> {
                    if (i + 1 < args.length) {
                        benchSeed = args[++i];
                    }
                }
                case "--bench-out" -> {
                    if (i + 1 < args.length) {
                        benchOutDir = args[++i];
                    }
                }
                case "--bench-fix" -> {
                    if (i + 1 < args.length) {
                        com.voxelgame.bench.BenchFixes.parse(args[++i]);
                    }
                }
                case "--create" -> {
                    createMode = true;
                    directMode = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        directWorldName = args[++i];
                    }
                }
                case "--direct", "--world" -> {
                    directMode = true;
                    // Optional world name after --direct / --world
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        directWorldName = args[++i];
                    }
                }
                case "--script" -> {
                    if (i + 1 < args.length) {
                        scriptPath = args[++i];
                    } else {
                        System.err.println("--script requires a file path argument");
                        System.exit(1);
                    }
                }
                case "--help", "-h" -> {
                    System.out.println("VoxelGame - A voxel world engine");
                    System.out.println("Usage: java -jar voxelgame.jar [options]");
                    System.out.println();
                    System.out.println("Options:");
                    System.out.println("  --world <name>         Skip main menu, load world directly (default: 'default')");
                    System.out.println("  --direct [name]        Alias for --world");
                    System.out.println("  --create [name]        Create a new world with optional name");
                    System.out.println("  --automation           Enable automation mode (socket server on localhost:25565)");
                    System.out.println("  --agent-server         Enable AI agent interface (WebSocket on localhost:25566)");
                    System.out.println("  --script <file>        Run automation script (implies --automation)");
                    System.out.println("  --auto-test            Automated screenshot test sequence");
                    System.out.println("  --capture-debug-views  Capture debug view screenshots + render_state.json");
                    System.out.println("  --capture-spawn-validation  Capture spawn point validation report");
                    System.out.println("  --capture-output <dir> Output directory for captures");
                    System.out.println("  --capture-seed <seed>  Fixed seed for captures (default: 42)");
                    System.out.println("  --profile <name>       Capture profile: BEFORE, AFTER_FOG, AFTER_EXPOSURE");
                    System.out.println("                         (captures all profiles if not specified)");
                    System.out.println("  --bench-world          Run world streaming benchmark (60s flight test)");
                    System.out.println("  --help, -h             Show this help message");
                    System.exit(0);
                }
                default -> System.err.println("Unknown argument: " + args[i]);
            }
        }

        // --script implies --automation
        if (scriptPath != null) {
            automationMode = true;
        }

        // Automation and agent-server modes imply direct mode
        if (automationMode || agentServerMode || autoTestMode || captureDebugViews || captureSpawnValidation || benchWorldMode) {
            directMode = true;
        }

        if (automationMode) {
            System.out.println("[Automation] Mode enabled" +
                (scriptPath != null ? " (script: " + scriptPath + ")" : " (socket only)"));
        }

        if (agentServerMode) {
            System.out.println("[AgentServer] Mode enabled (WebSocket on localhost:25566)");
        }

        GameLoop loop = new GameLoop();
        loop.setAutomationMode(automationMode);
        loop.setAgentServerMode(agentServerMode);
        loop.setAutoTestMode(autoTestMode);
        loop.setCaptureDebugViews(captureDebugViews);
        loop.setCaptureSpawnValidation(captureSpawnValidation);
        loop.setScriptPath(scriptPath);
        
        // Set custom output directory if specified
        if (captureOutputDir != null) {
            loop.setDebugCaptureOutputDir(captureOutputDir);
            loop.setSpawnCaptureOutputDir(captureOutputDir);
        }
        
        // Set capture profile if specified
        if (captureProfile != null) {
            loop.setCaptureProfile(captureProfile);
        }
        
        // Set benchmark mode if requested
        if (benchWorldMode) {
            loop.setBenchWorld(true, benchWorldPhase, benchSeed, benchOutDir);
        }

        // Direct/create mode skips the menu
        // Capture modes create a new world with fixed seed
        if (captureDebugViews || captureSpawnValidation) {
            // Use fixed seed for reproducibility (default: 42)
            String seedStr = captureSeed != null ? captureSeed : "42";
            String worldName = "capture-" + seedStr;
            loop.setCaptureSeed(seedStr);
            loop.setCreateNewWorld(worldName);
        } else if (createMode) {
            loop.setCreateNewWorld(directWorldName);
        } else if (directMode) {
            loop.setDirectWorld(directWorldName);
        }

        loop.run();
    }
}
