package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import com.voxelgame.render.TextureAtlas;
import com.voxelgame.sim.Furnace;
import com.voxelgame.sim.SmeltingRecipe;
import com.voxelgame.sim.Inventory;
import com.voxelgame.world.Block;
import com.voxelgame.world.Blocks;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Furnace GUI screen. Shows input, fuel, and output slots with smelting progress.
 */
public class FurnaceScreen {

    private static final float SLOT_SIZE = 44.0f;
    private static final float PREVIEW_SIZE = 30.0f;
    private static final float BORDER = 2.0f;
    private static final float PANEL_W = 300;
    private static final float PANEL_H = 200;

    private BitmapFont font;
    private Shader uiShader;
    private Shader texShader;
    private int quadVao, quadVbo;
    private TextureAtlas atlas;
    private int sw, sh;
    private float mouseX, mouseY;

    private boolean open = false;
    private Furnace furnace;

    private int heldId = 0;
    private int heldCount = 0;

    // Slot screen positions (computed in render)
    private float inputSX, inputSY;
    private float fuelSX, fuelSY;
    private float outputSX, outputSY;
    private float invStartX, invStartY;

    public void init(BitmapFont font) {
        this.font = font;
        uiShader = new Shader("shaders/ui.vert", "shaders/ui.frag");
        texShader = new Shader("shaders/ui_tex.vert", "shaders/ui_tex.frag");

        float[] v = { 0,0, 1,0, 1,1,  0,0, 1,1, 0,1 };
        quadVao = glGenVertexArrays();
        quadVbo = glGenBuffers();
        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(v.length);
            fb.put(v).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    public void setAtlas(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    public void open(Furnace furnace) {
        this.furnace = furnace;
        this.open = true;
        this.heldId = 0;
        this.heldCount = 0;
    }

    public void close(Inventory inv) {
        if (heldId > 0 && heldCount > 0) {
            inv.addItem(heldId, heldCount);
            heldId = 0;
            heldCount = 0;
        }
        this.open = false;
        this.furnace = null;
    }

    public boolean isOpen() { return open; }
    public boolean isVisible() { return open; }
    public Furnace getFurnace() { return furnace; }

    public void updateMouse(double mx, double my, int h) {
        this.mouseX = (float) mx;
        this.mouseY = (float) (h - my); // flip Y
    }

    public void handleClick(Inventory inv, double mx, double my, int w, int h) {
        if (!open || furnace == null) return;

        float clickX = (float) mx;
        float clickY = (float) (h - my);

        // Check input slot
        if (hitSlot(clickX, clickY, inputSX, inputSY)) {
            clickFurnaceSlot(0);
            return;
        }
        // Check fuel slot
        if (hitSlot(clickX, clickY, fuelSX, fuelSY)) {
            clickFurnaceSlot(1);
            return;
        }
        // Check output slot
        if (hitSlot(clickX, clickY, outputSX, outputSY)) {
            clickFurnaceSlot(2);
            return;
        }

        // Check hotbar slots
        for (int i = 0; i < 9; i++) {
            float sx = invStartX + i * SLOT_SIZE;
            if (hitSlot(clickX, clickY, sx, invStartY)) {
                clickInventorySlot(inv, i);
                return;
            }
        }
    }

    private boolean hitSlot(float mx, float my, float sx, float sy) {
        return mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
    }

    private void clickFurnaceSlot(int slot) {
        int slotId = switch (slot) { case 0 -> furnace.getInputId(); case 1 -> furnace.getFuelId(); default -> furnace.getOutputId(); };
        int slotCount = switch (slot) { case 0 -> furnace.getInputCount(); case 1 -> furnace.getFuelCount(); default -> furnace.getOutputCount(); };

        if (heldId == 0) {
            if (slotId > 0 && slotCount > 0) {
                heldId = slotId;
                heldCount = slotCount;
                setSlot(slot, 0, 0);
            }
        } else {
            if (slot == 2) {
                // Output: take only
                if (slotId > 0 && heldId == slotId && heldCount + slotCount <= 64) {
                    heldCount += slotCount;
                    setSlot(2, 0, 0);
                } else if (slotId == 0) {
                    // Can't place into output
                }
                return;
            }

            if (slot == 1 && !Furnace.isFuel(heldId)) return;
            if (slot == 0 && !SmeltingRecipe.canSmelt(heldId)) return;

            if (slotId == 0) {
                setSlot(slot, heldId, heldCount);
                heldId = 0; heldCount = 0;
            } else if (slotId == heldId) {
                int space = 64 - slotCount;
                int toAdd = Math.min(space, heldCount);
                setSlot(slot, slotId, slotCount + toAdd);
                heldCount -= toAdd;
                if (heldCount <= 0) { heldId = 0; heldCount = 0; }
            } else {
                int tid = slotId, tc = slotCount;
                setSlot(slot, heldId, heldCount);
                heldId = tid; heldCount = tc;
            }
        }
    }

    private void clickInventorySlot(Inventory inv, int slot) {
        Inventory.ItemStack stack = inv.getSlot(slot);
        int sid = (stack != null && !stack.isEmpty()) ? stack.getBlockId() : 0;
        int sc = (stack != null && !stack.isEmpty()) ? stack.getCount() : 0;

        if (heldId == 0) {
            if (sid > 0) {
                heldId = sid; heldCount = sc;
                inv.setSlot(slot, null);
            }
        } else {
            if (sid == 0) {
                inv.setSlot(slot, new Inventory.ItemStack(heldId, heldCount));
                heldId = 0; heldCount = 0;
            } else if (sid == heldId && !stack.hasDurability()) {
                int space = 64 - sc;
                int toAdd = Math.min(space, heldCount);
                stack.setCount(sc + toAdd);
                heldCount -= toAdd;
                if (heldCount <= 0) { heldId = 0; heldCount = 0; }
            } else {
                inv.setSlot(slot, new Inventory.ItemStack(heldId, heldCount));
                heldId = sid; heldCount = sc;
            }
        }
    }

    private void setSlot(int slot, int id, int count) {
        switch (slot) {
            case 0 -> furnace.setInput(id, count);
            case 1 -> furnace.setFuel(id, count);
            case 2 -> furnace.setOutput(id, count);
        }
    }

    public void render(int w, int h, Inventory inv) {
        if (!open || furnace == null) return;

        sw = w; sh = h;

        uiShader.bind();
        glBindVertexArray(quadVao);

        float cx = w / 2.0f;
        float cy = h / 2.0f;

        // Dark overlay
        fillRect(0, 0, w, h, 0, 0, 0, 0.6f);

        // Panel background
        float px = cx - PANEL_W / 2;
        float py = cy - PANEL_H / 2;
        fillRect(px, py, PANEL_W, PANEL_H, 0.18f, 0.18f, 0.22f, 0.95f);
        strokeRect(px, py, PANEL_W, PANEL_H, 2, 0.4f, 0.4f, 0.5f, 1);

        // Input slot
        inputSX = cx - 90;
        inputSY = cy + 20;
        renderSlot(inputSX, inputSY, furnace.getInputId(), furnace.getInputCount());

        // Fuel slot
        fuelSX = cx - 90;
        fuelSY = cy - 40;
        renderSlot(fuelSX, fuelSY, furnace.getFuelId(), furnace.getFuelCount());

        // Output slot
        outputSX = cx + 50;
        outputSY = cy - 10;
        renderSlot(outputSX, outputSY, furnace.getOutputId(), furnace.getOutputCount());

        // Progress arrow (between input and output)
        float arrowX = cx - 30;
        float arrowY = cy - 2;
        fillRect(arrowX, arrowY, 60, 6, 0.3f, 0.3f, 0.35f, 1);
        float progress = furnace.getSmeltProgress();
        if (progress > 0) {
            fillRect(arrowX, arrowY, 60 * progress, 6, 1f, 0.6f, 0.1f, 1);
        }

        // Fuel fire indicator
        if (furnace.getCurrentFuelTotal() > 0 && furnace.getFuelRemaining() > 0) {
            float fuelFrac = furnace.getFuelRemaining() / furnace.getCurrentFuelTotal();
            float fireX = cx - 42;
            float fireY = fuelSY;
            float fireH = SLOT_SIZE * fuelFrac;
            fillRect(fireX, fuelSY + (SLOT_SIZE - fireH), 8, fireH, 1f, 0.4f, 0.05f, 1);
        }

        // Hotbar slots at bottom
        invStartX = cx - (9 * SLOT_SIZE) / 2;
        invStartY = cy - PANEL_H / 2 - SLOT_SIZE - 10;
        for (int i = 0; i < 9; i++) {
            int sid = inv.getHotbarBlockId(i);
            int sc = inv.getCount(i);
            renderSlot(invStartX + i * SLOT_SIZE, invStartY, sid, sc);
        }

        glBindVertexArray(0);
        uiShader.unbind();

        // Text labels
        font.drawString("Furnace", (int)(cx - 28), (int)(h - py - PANEL_H + 8), w, h, 1, 1, 1);
        font.drawString("Input", (int)(inputSX), (int)(h - inputSY - SLOT_SIZE - 2), w, h, 0.7f, 0.7f, 0.7f);
        font.drawString("Fuel", (int)(fuelSX), (int)(h - fuelSY - SLOT_SIZE - 2), w, h, 0.7f, 0.7f, 0.7f);
        font.drawString("Output", (int)(outputSX), (int)(h - outputSY - SLOT_SIZE - 2), w, h, 0.7f, 0.7f, 0.7f);

        if (furnace.isActive()) {
            font.drawString("Smelting...", (int)(cx - 35), (int)(h - cy + PANEL_H / 2 - 25), w, h, 1f, 0.8f, 0.3f);
        }

        // Slot counts
        renderSlotText(inputSX, inputSY, furnace.getInputId(), furnace.getInputCount(), w, h);
        renderSlotText(fuelSX, fuelSY, furnace.getFuelId(), furnace.getFuelCount(), w, h);
        renderSlotText(outputSX, outputSY, furnace.getOutputId(), furnace.getOutputCount(), w, h);
        for (int i = 0; i < 9; i++) {
            int sc = inv.getCount(i);
            int sid = inv.getHotbarBlockId(i);
            renderSlotText(invStartX + i * SLOT_SIZE, invStartY, sid, sc, w, h);
        }

        // Held item info
        if (heldId > 0 && heldCount > 0) {
            font.drawString(Blocks.get(heldId).name() + " x" + heldCount, 10, 10, w, h, 1, 1, 0.5f);
        }
    }

    private void renderSlot(float x, float y, int blockId, int count) {
        // Slot background
        fillRect(x, y, SLOT_SIZE, SLOT_SIZE, 0.12f, 0.12f, 0.14f, 1);
        fillRect(x + BORDER, y + BORDER, SLOT_SIZE - 2 * BORDER, SLOT_SIZE - 2 * BORDER,
                 0.22f, 0.22f, 0.26f, 1);

        if (blockId > 0 && count > 0) {
            float off = (SLOT_SIZE - PREVIEW_SIZE) / 2f;
            float px = x + off;
            float py = y + off;

            Block block = Blocks.get(blockId);
            int tileIndex = block.getTextureIndex(0);

            if (atlas != null && tileIndex >= 0) {
                float[] uv = atlas.getUV(tileIndex);

                texShader.bind();
                glBindVertexArray(quadVao);
                glActiveTexture(GL_TEXTURE0);
                atlas.bind(0);
                texShader.setInt("uTexture", 0);
                texShader.setVec4("uUVRect", uv[0], uv[3], uv[2], uv[1]);
                setProjectionTex(new Matrix4f().ortho(
                    -px / PREVIEW_SIZE, (sw - px) / PREVIEW_SIZE,
                    -py / PREVIEW_SIZE, (sh - py) / PREVIEW_SIZE,
                    -1, 1));
                glDrawArrays(GL_TRIANGLES, 0, 6);
                texShader.unbind();

                // Switch back to uiShader for subsequent rendering
                uiShader.bind();
                glBindVertexArray(quadVao);
            } else {
                // Fallback colored square (should rarely happen)
                fillRect(px, py, PREVIEW_SIZE, PREVIEW_SIZE, 0.5f, 0.5f, 0.5f, 1.0f);
            }
        }
    }

    private void renderSlotText(float sx, float sy, int blockId, int count, int w, int h) {
        if (blockId > 0 && count > 1) {
            font.drawString(String.valueOf(count),
                (int)(sx + SLOT_SIZE - 16), (int)(h - sy - 16), w, h, 1, 1, 1);
        }
    }

    private void fillRect(float x, float y, float w, float h,
                           float r, float g, float b, float a) {
        setProjection(new Matrix4f().ortho(
            -x / w, (sw - x) / w,
            -y / h, (sh - y) / h,
            -1, 1));
        uiShader.setVec4("uColor", r, g, b, a);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private void strokeRect(float x, float y, float w, float h, float bw,
                              float r, float g, float b, float a) {
        fillRect(x, y + h - bw, w, bw, r, g, b, a);
        fillRect(x, y, w, bw, r, g, b, a);
        fillRect(x, y, bw, h, r, g, b, a);
        fillRect(x + w - bw, y, bw, h, r, g, b, a);
    }

    private void setProjection(Matrix4f proj) {
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(16);
            proj.get(fb);
            glUniformMatrix4fv(
                glGetUniformLocation(uiShader.getProgramId(), "uProjection"),
                false, fb);
        }
    }

    private void setProjectionTex(Matrix4f proj) {
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(16);
            proj.get(fb);
            glUniformMatrix4fv(
                glGetUniformLocation(texShader.getProgramId(), "uProjection"),
                false, fb);
        }
    }

    public void cleanup() {
        if (quadVbo != 0) glDeleteBuffers(quadVbo);
        if (quadVao != 0) glDeleteVertexArrays(quadVao);
        if (uiShader != null) uiShader.cleanup();
        if (texShader != null) texShader.cleanup();
    }
}
