package com.voxelgame.core;

import com.voxelgame.platform.Input;
import com.voxelgame.platform.Window;
import com.voxelgame.render.GLInit;
import com.voxelgame.render.Renderer;
import com.voxelgame.sim.Controller;
import com.voxelgame.sim.Physics;
import com.voxelgame.sim.Player;
import com.voxelgame.world.Raycast;
import com.voxelgame.world.World;
import com.voxelgame.world.stream.ChunkManager;

import static org.lwjgl.opengl.GL33.*;

/**
 * Game loop integrating all subsystems.
 */
public class GameLoop {

    private Window window;
    private Time time;
    private Player player;
    private Controller controller;
    private Physics physics;
    private World world;
    private ChunkManager chunkManager;
    private Renderer renderer;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        window = new Window(1280, 720, "VoxelGame");
        GLInit.init();
        GLInit.setViewport(window.getWidth(), window.getHeight());

        time = new Time();
        time.init();

        player = new Player();
        controller = new Controller(player);
        physics = new Physics();

        Input.init(window.getHandle());
        Input.lockCursor();

        world = new World();
        physics.setWorld(world);
        renderer = new Renderer(world);
        renderer.init();

        chunkManager = new ChunkManager(world);
        chunkManager.init(renderer.getAtlas());

        // Initial chunk load
        chunkManager.update(player);

        System.out.println("VoxelGame initialized successfully!");
    }

    private void loop() {
        while (!window.shouldClose()) {
            time.update();
            float dt = time.getDeltaTime();

            window.pollEvents();

            if (window.wasResized()) {
                GLInit.setViewport(window.getWidth(), window.getHeight());
            }

            // ---- Update ----
            controller.update(dt);
            physics.step(player, dt);
            chunkManager.update(player);
            handleBlockInteraction();

            // ---- Render ----
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.render(player.getCamera(), window.getWidth(), window.getHeight());

            // ---- End frame ----
            Input.endFrame();
            window.swapBuffers();
        }
    }

    private void handleBlockInteraction() {
        if (!Input.isCursorLocked()) return;

        if (Input.isLeftMouseClicked()) {
            var hit = Raycast.cast(
                world, player.getCamera().getPosition(), player.getCamera().getFront(), 8.0f
            );
            if (hit != null) {
                world.setBlock(hit.x(), hit.y(), hit.z(), 0); // AIR
                chunkManager.rebuildMeshAt(hit.x(), hit.y(), hit.z());
            }
        }

        if (Input.isRightMouseClicked()) {
            var hit = Raycast.cast(
                world, player.getCamera().getPosition(), player.getCamera().getFront(), 8.0f
            );
            if (hit != null) {
                int px = hit.x() + hit.nx();
                int py = hit.y() + hit.ny();
                int pz = hit.z() + hit.nz();
                world.setBlock(px, py, pz, player.getSelectedBlock());
                chunkManager.rebuildMeshAt(px, py, pz);
            }
        }
    }

    private void cleanup() {
        chunkManager.shutdown();
        renderer.cleanup();
        window.destroy();
    }

    public Window getWindow() { return window; }
    public Time getTime() { return time; }
    public Player getPlayer() { return player; }
}
