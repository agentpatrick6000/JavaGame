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
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("VoxelGame starting...");

        boolean automationMode = false;
        boolean agentServerMode = false;
        boolean autoTestMode = false;
        boolean directMode = false;
        boolean createMode = false;
        String directWorldName = null;
        String scriptPath = null;

        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--automation" -> automationMode = true;
                case "--agent-server" -> agentServerMode = true;
                case "--auto-test" -> autoTestMode = true;
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
                    System.out.println("  --world <name>      Skip main menu, load world directly (default: 'default')");
                    System.out.println("  --direct [name]     Alias for --world");
                    System.out.println("  --automation        Enable automation mode (socket server on localhost:25565)");
                    System.out.println("  --agent-server      Enable AI agent interface (WebSocket on localhost:25566)");
                    System.out.println("  --script <file>     Run automation script (implies --automation)");
                    System.out.println("  --auto-test         Automated screenshot test sequence");
                    System.out.println("  --help, -h          Show this help message");
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
        if (automationMode || agentServerMode || autoTestMode) {
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
        loop.setScriptPath(scriptPath);

        // Direct/create mode skips the menu
        if (createMode) {
            loop.setCreateNewWorld(directWorldName);
        } else if (directMode) {
            loop.setDirectWorld(directWorldName);
        }

        loop.run();
    }
}
