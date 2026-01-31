package com.voxelgame;

import com.voxelgame.core.GameLoop;

/**
 * Entry point for VoxelGame.
 * <p>
 * Usage:
 *   java -jar voxelgame.jar                          # normal mode
 *   java -jar voxelgame.jar --automation              # automation mode (socket server on :25565)
 *   java -jar voxelgame.jar --automation --script demo-script.txt  # run script + socket
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("VoxelGame starting...");

        boolean automationMode = false;
        String scriptPath = null;

        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--automation" -> automationMode = true;
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
                    System.out.println("  --automation        Enable automation mode (socket server on localhost:25565)");
                    System.out.println("  --script <file>     Run automation script (implies --automation)");
                    System.out.println("  --help, -h          Show this help message");
                    System.out.println();
                    System.out.println("Automation Protocol:");
                    System.out.println("  key:<name>:press    Press and release a key");
                    System.out.println("  key:<name>:down     Hold a key down");
                    System.out.println("  key:<name>:up       Release a key");
                    System.out.println("  mouse:move:<dx>:<dy> Inject mouse movement");
                    System.out.println("  mouse:click:left    Left mouse click");
                    System.out.println("  mouse:click:right   Right mouse click");
                    System.out.println("  sleep:<ms>          Wait milliseconds");
                    System.out.println("  quit                Request shutdown");
                    System.out.println();
                    System.out.println("Key names: W,A,S,D,F,SPACE,SHIFT,CTRL,ESCAPE,F3,1-9,UP,DOWN,LEFT,RIGHT");
                    System.exit(0);
                }
                default -> System.err.println("Unknown argument: " + args[i]);
            }
        }

        // --script implies --automation
        if (scriptPath != null) {
            automationMode = true;
        }

        if (automationMode) {
            System.out.println("[Automation] Mode enabled" +
                (scriptPath != null ? " (script: " + scriptPath + ")" : " (socket only)"));
        }

        GameLoop loop = new GameLoop();
        loop.setAutomationMode(automationMode);
        loop.setScriptPath(scriptPath);
        loop.run();
    }
}
