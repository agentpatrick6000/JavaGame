package com.voxelgame.render;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Procedurally generated block texture atlas.
 * 16x16 pixel tiles arranged in a grid.
 */
public class TextureAtlas {

    private static final int TILE_SIZE = 16;
    private static final int TILES_PER_ROW = 8;
    private static final int TILE_COUNT = 22; // 0-21 (includes crafted blocks)
    private static final int ATLAS_WIDTH = TILES_PER_ROW * TILE_SIZE;  // 128
    private static final int ATLAS_HEIGHT = ((TILE_COUNT + TILES_PER_ROW - 1) / TILES_PER_ROW) * TILE_SIZE; // 48

    private int textureId;

    public void init() {
        ByteBuffer pixels = MemoryUtil.memAlloc(ATLAS_WIDTH * ATLAS_HEIGHT * 4);
        generateAtlas(pixels);

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, ATLAS_WIDTH, ATLAS_HEIGHT,
            0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        MemoryUtil.memFree(pixels);
        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("TextureAtlas created: " + ATLAS_WIDTH + "x" + ATLAS_HEIGHT +
            " (" + TILE_COUNT + " tiles)");
    }

    private void generateAtlas(ByteBuffer pixels) {
        // Clear to transparent
        for (int i = 0; i < ATLAS_WIDTH * ATLAS_HEIGHT * 4; i++) {
            pixels.put(i, (byte) 0);
        }

        for (int tileIdx = 0; tileIdx < TILE_COUNT; tileIdx++) {
            int tileCol = tileIdx % TILES_PER_ROW;
            int tileRow = tileIdx / TILES_PER_ROW;
            int baseX = tileCol * TILE_SIZE;
            int baseY = tileRow * TILE_SIZE;
            generateTile(pixels, baseX, baseY, tileIdx);
        }
    }

    private void generateTile(ByteBuffer pixels, int baseX, int baseY, int tileIdx) {
        switch (tileIdx) {
            case 0 -> generateAir(pixels, baseX, baseY);
            case 1 -> generateStone(pixels, baseX, baseY);
            case 2 -> generateCobblestone(pixels, baseX, baseY);
            case 3 -> generateDirt(pixels, baseX, baseY);
            case 4 -> generateGrassTop(pixels, baseX, baseY);
            case 5 -> generateGrassSide(pixels, baseX, baseY);
            case 6 -> generateSand(pixels, baseX, baseY);
            case 7 -> generateGravel(pixels, baseX, baseY);
            case 8 -> generateLogEnd(pixels, baseX, baseY);
            case 9 -> generateLogBark(pixels, baseX, baseY);
            case 10 -> generateLeaves(pixels, baseX, baseY);
            case 11 -> generateWater(pixels, baseX, baseY);
            case 12 -> generateOre(pixels, baseX, baseY, 50, 50, 50);    // coal ore
            case 13 -> generateOre(pixels, baseX, baseY, 180, 140, 100); // iron ore
            case 14 -> generateOre(pixels, baseX, baseY, 255, 215, 0);   // gold ore
            case 15 -> generateOre(pixels, baseX, baseY, 100, 220, 255); // diamond ore
            case 16 -> generateBedrock(pixels, baseX, baseY);
            case 17 -> generateSolidColor(pixels, baseX, baseY, 242, 140, 128); // raw porkchop
            case 18 -> generateSolidColor(pixels, baseX, baseY, 140, 102, 64);  // rotten flesh
            case 19 -> generatePlanks(pixels, baseX, baseY);
            case 20 -> generateCraftingTableTop(pixels, baseX, baseY);
            case 21 -> generateCraftingTableSide(pixels, baseX, baseY);
        }
    }

    private void setPixel(ByteBuffer buf, int x, int y, int r, int g, int b, int a) {
        if (x < 0 || x >= ATLAS_WIDTH || y < 0 || y >= ATLAS_HEIGHT) return;
        int idx = (y * ATLAS_WIDTH + x) * 4;
        buf.put(idx, (byte) r);
        buf.put(idx + 1, (byte) g);
        buf.put(idx + 2, (byte) b);
        buf.put(idx + 3, (byte) a);
    }

    private int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return h ^ (h >> 16);
    }

    private void generateAir(ByteBuffer buf, int bx, int by) {
        // Transparent
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++)
                setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
    }

    private void generateStone(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int v = 120 + (hash(x, y) & 31) - 16;
                setPixel(buf, bx + x, by + y, v, v, v, 255);
            }
    }

    private void generateCobblestone(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int v = 100 + (hash(x + 37, y + 13) & 63) - 32;
                boolean dark = ((x / 4 + y / 4) & 1) == 0;
                if (dark) v -= 20;
                setPixel(buf, bx + x, by + y, v, v, v, 255);
            }
    }

    private void generateDirt(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                setPixel(buf, bx + x, by + y, 134 + noise, 96 + noise, 67 + noise, 255);
            }
    }

    private void generateGrassTop(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                setPixel(buf, bx + x, by + y, 76 + noise, 153 + noise, 0, 255);
            }
    }

    private void generateGrassSide(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                if (y < 4) {
                    // Green top strip
                    setPixel(buf, bx + x, by + y, 76 + noise, 153 + noise, 0, 255);
                } else {
                    // Dirt below
                    setPixel(buf, bx + x, by + y, 134 + noise, 96 + noise, 67 + noise, 255);
                }
            }
    }

    private void generateSand(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                setPixel(buf, bx + x, by + y, 214 + noise, 207 + noise, 152 + noise, 255);
            }
    }

    private void generateGravel(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int v = 130 + (hash(x * 3, y * 7) & 31) - 16;
                if ((hash(x + 5, y + 3) & 7) == 0) v -= 30;
                setPixel(buf, bx + x, by + y, v, v - 5, v - 5, 255);
            }
    }

    private void generateLogEnd(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int cx = x - 8, cy = y - 8;
                double dist = Math.sqrt(cx * cx + cy * cy);
                int ring = (int)(dist * 1.5) & 1;
                int base = ring == 0 ? 160 : 130;
                int noise = (hash(x, y) & 7) - 4;
                setPixel(buf, bx + x, by + y, base + noise, (int)(base * 0.7) + noise, (int)(base * 0.4) + noise, 255);
            }
    }

    private void generateLogBark(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                int stripe = (y & 3) == 0 ? -15 : 0;
                setPixel(buf, bx + x, by + y, 100 + noise + stripe, 70 + noise + stripe, 40 + noise + stripe, 255);
            }
    }

    private void generateLeaves(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 31) - 16;
                boolean hole = (hash(x + 11, y + 7) & 7) == 0;
                if (hole) {
                    setPixel(buf, bx + x, by + y, 30 + noise, 100 + noise, 0, 180);
                } else {
                    setPixel(buf, bx + x, by + y, 50 + noise, 130 + noise, 10 + noise, 230);
                }
            }
    }

    private void generateWater(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                setPixel(buf, bx + x, by + y, 30 + noise, 80 + noise, 200 + noise, 150);
            }
    }

    private void generateOre(ByteBuffer buf, int bx, int by, int oreR, int oreG, int oreB) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int stoneNoise = (hash(x, y) & 31) - 16;
                boolean isOreSpot = (hash(x * 17 + 3, y * 13 + 7) & 7) < 2;
                if (isOreSpot) {
                    setPixel(buf, bx + x, by + y, oreR, oreG, oreB, 255);
                } else {
                    int v = 120 + stoneNoise;
                    setPixel(buf, bx + x, by + y, v, v, v, 255);
                }
            }
    }

    private void generateSolidColor(ByteBuffer buf, int bx, int by, int r, int g, int b) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                setPixel(buf, bx + x, by + y,
                    Math.max(0, Math.min(255, r + noise)),
                    Math.max(0, Math.min(255, g + noise)),
                    Math.max(0, Math.min(255, b + noise)), 255);
            }
    }

    private void generateBedrock(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int v = 40 + (hash(x, y) & 31);
                setPixel(buf, bx + x, by + y, v, v, v, 255);
            }
    }

    private void generatePlanks(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                int stripe = (y % 4 == 0) ? -20 : 0;
                int grain = ((x + y / 3) & 3) < 1 ? -8 : 0;
                setPixel(buf, bx + x, by + y,
                    180 + noise + stripe + grain,
                    140 + noise + stripe + grain,
                    76 + noise + stripe + grain, 255);
            }
    }

    private void generateCraftingTableTop(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean gridLine = (x == 7 || x == 8 || y == 7 || y == 8);
                if (gridLine) {
                    setPixel(buf, bx + x, by + y, 80 + noise, 60 + noise, 35 + noise, 255);
                } else {
                    setPixel(buf, bx + x, by + y, 170 + noise, 130 + noise, 70 + noise, 255);
                }
            }
    }

    private void generateCraftingTableSide(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean hasTool = (x >= 3 && x <= 5 && y >= 2 && y <= 10) ||
                                  (x >= 10 && x <= 12 && y >= 2 && y <= 10) ||
                                  (x >= 2 && x <= 6 && y >= 2 && y <= 4) ||
                                  (x >= 9 && x <= 13 && y >= 2 && y <= 4);
                if (hasTool) {
                    setPixel(buf, bx + x, by + y, 120 + noise, 90 + noise, 50 + noise, 255);
                } else {
                    setPixel(buf, bx + x, by + y, 160 + noise, 120 + noise, 65 + noise, 255);
                }
            }
    }

    /** Get UV coordinates for a tile index: [u0, v0, u1, v1] */
    public float[] getUV(int tileIndex) {
        int col = tileIndex % TILES_PER_ROW;
        int row = tileIndex / TILES_PER_ROW;
        float u0 = (float)(col * TILE_SIZE) / ATLAS_WIDTH;
        float v0 = (float)(row * TILE_SIZE) / ATLAS_HEIGHT;
        float u1 = (float)((col + 1) * TILE_SIZE) / ATLAS_WIDTH;
        float v1 = (float)((row + 1) * TILE_SIZE) / ATLAS_HEIGHT;
        return new float[]{u0, v0, u1, v1};
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
    }
}
