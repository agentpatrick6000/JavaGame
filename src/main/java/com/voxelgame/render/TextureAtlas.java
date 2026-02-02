package com.voxelgame.render;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Procedurally generated block texture atlas.
 * 16x16 pixel tiles arranged in a grid.
 * Colors matched to InfDev 611 palette.
 */
public class TextureAtlas {

    private static final int TILE_SIZE = 16;
    private static final int TILES_PER_ROW = 8;
    private static final int TILE_COUNT = 49; // 0-48 (includes fluids: 46=lava, 47=obsidian, 48=feather)
    private static final int ATLAS_WIDTH = TILES_PER_ROW * TILE_SIZE;  // 128
    private static final int ATLAS_HEIGHT = ((TILE_COUNT + TILES_PER_ROW - 1) / TILES_PER_ROW) * TILE_SIZE;

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
            case 22 -> generateChestTop(pixels, baseX, baseY);
            case 23 -> generateChestSide(pixels, baseX, baseY);
            case 24 -> generateRail(pixels, baseX, baseY);
            case 25 -> generateTNTTop(pixels, baseX, baseY);
            case 26 -> generateTNTSide(pixels, baseX, baseY);
            case 27 -> generateFurnaceTop(pixels, baseX, baseY);
            case 28 -> generateFurnaceSide(pixels, baseX, baseY);
            case 29 -> generateFurnaceFront(pixels, baseX, baseY);
            case 30 -> generateTorch(pixels, baseX, baseY);
            case 31 -> generateCoalItem(pixels, baseX, baseY);
            case 32 -> generateIronIngot(pixels, baseX, baseY);
            case 33 -> generateGlass(pixels, baseX, baseY);
            case 34 -> generateSolidColor(pixels, baseX, baseY, 200, 130, 80);  // cooked porkchop
            case 35 -> generateRedFlower(pixels, baseX, baseY);
            case 36 -> generateYellowFlower(pixels, baseX, baseY);
            case 37 -> generateDiamond(pixels, baseX, baseY);
            case 38 -> generateCharcoal(pixels, baseX, baseY);
            case 39 -> generateGoldIngot(pixels, baseX, baseY);
            case 40 -> generatePoweredRail(pixels, baseX, baseY);
            case 41 -> generateRedstoneItem(pixels, baseX, baseY);
            case 42 -> generateRedstoneWire(pixels, baseX, baseY);
            case 43 -> generateRedstoneTorch(pixels, baseX, baseY);
            case 44 -> generateRedstoneRepeater(pixels, baseX, baseY);
            case 45 -> generateRedstoneOre(pixels, baseX, baseY);
            case 46 -> generateLava(pixels, baseX, baseY);
            case 47 -> generateObsidian(pixels, baseX, baseY);
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

    /** Clamp integer to [0, 255] */
    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void generateAir(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++)
                setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
    }

    // InfDev 611 Stone: #7f7f7f (127, 127, 127) — medium gray
    private void generateStone(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 31) - 16;
                int v = 127 + noise;
                setPixel(buf, bx + x, by + y, clamp(v), clamp(v), clamp(v), 255);
            }
    }

    // InfDev 611 Cobblestone: #8a8a8a (138, 138, 138) — light gray with brick pattern
    private void generateCobblestone(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x + 37, y + 13) & 63) - 32;
                boolean dark = ((x / 4 + y / 4) & 1) == 0;
                int base = 138 + noise;
                if (dark) base -= 20;
                setPixel(buf, bx + x, by + y, clamp(base), clamp(base), clamp(base), 255);
            }
    }

    // InfDev 611 Dirt: #96684f (150, 104, 79) — warm brown
    private void generateDirt(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                setPixel(buf, bx + x, by + y,
                    clamp(150 + noise), clamp(104 + noise), clamp(79 + noise), 255);
            }
    }

    // InfDev 611 Grass Top: #7cbd6b (124, 189, 107) — bright green
    private void generateGrassTop(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                setPixel(buf, bx + x, by + y,
                    clamp(124 + noise), clamp(189 + noise), clamp(107 + noise), 255);
            }
    }

    // Grass Side: green top strip (#7cbd6b) over dirt (#96684f)
    private void generateGrassSide(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                if (y < 4) {
                    // Green top strip
                    setPixel(buf, bx + x, by + y,
                        clamp(124 + noise), clamp(189 + noise), clamp(107 + noise), 255);
                } else {
                    // Dirt below
                    setPixel(buf, bx + x, by + y,
                        clamp(150 + noise), clamp(104 + noise), clamp(79 + noise), 255);
                }
            }
    }

    // InfDev 611 Sand: #dbd3a0 (219, 211, 160) — tan/beige
    private void generateSand(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                setPixel(buf, bx + x, by + y,
                    clamp(219 + noise), clamp(211 + noise), clamp(160 + noise), 255);
            }
    }

    // InfDev 611 Gravel: #857b7b (133, 123, 123) — brownish gray
    private void generateGravel(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int v = 133 + (hash(x * 3, y * 7) & 31) - 16;
                if ((hash(x + 5, y + 3) & 7) == 0) v -= 25;
                setPixel(buf, bx + x, by + y, clamp(v), clamp(v - 10), clamp(v - 10), 255);
            }
    }

    // InfDev 611 Log End: rings in oak brown (#9f8150 base)
    private void generateLogEnd(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int cx = x - 8, cy = y - 8;
                double dist = Math.sqrt(cx * cx + cy * cy);
                int ring = (int)(dist * 1.5) & 1;
                int base = ring == 0 ? 170 : 140;
                int noise = (hash(x, y) & 7) - 4;
                setPixel(buf, bx + x, by + y,
                    clamp(base + noise),
                    clamp((int)(base * 0.65) + noise),
                    clamp((int)(base * 0.38) + noise), 255);
            }
    }

    // InfDev 611 Log Bark: #9f8150 (159, 129, 80) — oak brown
    private void generateLogBark(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                int stripe = (y & 3) == 0 ? -12 : 0;
                setPixel(buf, bx + x, by + y,
                    clamp(159 + noise + stripe),
                    clamp(129 + noise + stripe),
                    clamp(80 + noise + stripe), 255);
            }
    }

    // InfDev 611 Leaves: #6ba05c (107, 160, 92) — forest green
    private void generateLeaves(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 31) - 16;
                boolean hole = (hash(x + 11, y + 7) & 7) == 0;
                if (hole) {
                    setPixel(buf, bx + x, by + y,
                        clamp(70 + noise), clamp(120 + noise), clamp(55 + noise), 180);
                } else {
                    setPixel(buf, bx + x, by + y,
                        clamp(107 + noise), clamp(160 + noise), clamp(92 + noise), 230);
                }
            }
    }

    // InfDev 611 Water: #3f76e4 (63, 118, 228) — classic blue
    private void generateWater(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                setPixel(buf, bx + x, by + y,
                    clamp(63 + noise), clamp(118 + noise), clamp(228 + noise), 150);
            }
    }

    // Ore textures use InfDev stone base (#7f7f7f)
    private void generateOre(ByteBuffer buf, int bx, int by, int oreR, int oreG, int oreB) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int stoneNoise = (hash(x, y) & 31) - 16;
                boolean isOreSpot = (hash(x * 17 + 3, y * 13 + 7) & 7) < 2;
                if (isOreSpot) {
                    setPixel(buf, bx + x, by + y, oreR, oreG, oreB, 255);
                } else {
                    int v = 127 + stoneNoise;
                    setPixel(buf, bx + x, by + y, clamp(v), clamp(v), clamp(v), 255);
                }
            }
    }

    private void generateSolidColor(ByteBuffer buf, int bx, int by, int r, int g, int b) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                setPixel(buf, bx + x, by + y,
                    clamp(r + noise), clamp(g + noise), clamp(b + noise), 255);
            }
    }

    private void generateBedrock(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int v = 40 + (hash(x, y) & 31);
                setPixel(buf, bx + x, by + y, v, v, v, 255);
            }
    }

    // InfDev 611 Planks: #bc9862 (188, 152, 98) — warm oak planks
    private void generatePlanks(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                int stripe = (y % 4 == 0) ? -20 : 0;
                int grain = ((x + y / 3) & 3) < 1 ? -8 : 0;
                setPixel(buf, bx + x, by + y,
                    clamp(188 + noise + stripe + grain),
                    clamp(152 + noise + stripe + grain),
                    clamp(98 + noise + stripe + grain), 255);
            }
    }

    private void generateCraftingTableTop(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean gridLine = (x == 7 || x == 8 || y == 7 || y == 8);
                if (gridLine) {
                    setPixel(buf, bx + x, by + y,
                        clamp(100 + noise), clamp(75 + noise), clamp(45 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y,
                        clamp(178 + noise), clamp(140 + noise), clamp(85 + noise), 255);
                }
            }
    }

    private void generateChestTop(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean border = x == 0 || x == 15 || y == 0 || y == 15;
                boolean latch = (x >= 6 && x <= 9 && y >= 0 && y <= 2);
                if (latch) {
                    setPixel(buf, bx + x, by + y,
                        clamp(80 + noise), clamp(70 + noise), clamp(50 + noise), 255);
                } else if (border) {
                    setPixel(buf, bx + x, by + y,
                        clamp(100 + noise), clamp(75 + noise), clamp(40 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y,
                        clamp(165 + noise), clamp(125 + noise), clamp(65 + noise), 255);
                }
            }
    }

    private void generateChestSide(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean border = x == 0 || x == 15 || y == 0 || y == 15;
                boolean latch = (x >= 6 && x <= 9 && y >= 5 && y <= 9);
                if (latch) {
                    setPixel(buf, bx + x, by + y,
                        clamp(80 + noise), clamp(70 + noise), clamp(50 + noise), 255);
                } else if (border) {
                    setPixel(buf, bx + x, by + y,
                        clamp(100 + noise), clamp(75 + noise), clamp(40 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y,
                        clamp(165 + noise), clamp(125 + noise), clamp(65 + noise), 255);
                }
            }
    }

    private void generateRail(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean rail = (x == 3 || x == 12);
                boolean tie = (y % 4 < 2) && (x >= 2 && x <= 13);
                if (rail) {
                    setPixel(buf, bx + x, by + y,
                        clamp(140 + noise), clamp(130 + noise), clamp(120 + noise), 255);
                } else if (tie) {
                    setPixel(buf, bx + x, by + y,
                        clamp(120 + noise), clamp(85 + noise), clamp(50 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    private void generateTNTTop(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean fuse = (x >= 6 && x <= 9 && y >= 6 && y <= 9);
                if (fuse) {
                    setPixel(buf, bx + x, by + y,
                        clamp(60 + noise), clamp(60 + noise), clamp(60 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y,
                        clamp(200 + noise), clamp(50 + noise), clamp(30 + noise), 255);
                }
            }
    }

    private void generateTNTSide(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean stripe = (y >= 4 && y <= 11);
                if (stripe) {
                    setPixel(buf, bx + x, by + y,
                        clamp(200 + noise), clamp(50 + noise), clamp(30 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y,
                        clamp(160 + noise), clamp(140 + noise), clamp(120 + noise), 255);
                }
            }
    }

    // Furnace top uses stone color (#7f7f7f)
    private void generateFurnaceTop(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                int v = 127 + noise;
                setPixel(buf, bx + x, by + y, clamp(v), clamp(v), clamp(v), 255);
            }
    }

    private void generateFurnaceSide(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean border = x == 0 || x == 15 || y == 0 || y == 15;
                if (border) {
                    int v = 100 + noise;
                    setPixel(buf, bx + x, by + y, clamp(v), clamp(v), clamp(v), 255);
                } else {
                    int v = 127 + noise;
                    setPixel(buf, bx + x, by + y, clamp(v), clamp(v), clamp(v), 255);
                }
            }
    }

    private void generateFurnaceFront(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean mouth = (x >= 4 && x <= 11 && y >= 6 && y <= 13);
                boolean border = x == 0 || x == 15 || y == 0 || y == 15;
                if (mouth) {
                    setPixel(buf, bx + x, by + y,
                        clamp(40 + noise), clamp(35 + noise), clamp(30 + noise), 255);
                } else if (border) {
                    int v = 100 + noise;
                    setPixel(buf, bx + x, by + y, clamp(v), clamp(v), clamp(v), 255);
                } else {
                    int v = 127 + noise;
                    setPixel(buf, bx + x, by + y, clamp(v), clamp(v), clamp(v), 255);
                }
            }
    }

    private void generateTorch(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 3) - 2;
                boolean stick = (x >= 6 && x <= 9 && y >= 4 && y <= 15);
                boolean flame = (x >= 5 && x <= 10 && y >= 0 && y <= 4);
                boolean flameCenter = (x >= 7 && x <= 8 && y >= 1 && y <= 3);
                if (flameCenter) {
                    setPixel(buf, bx + x, by + y, 255, 255, 180, 240);
                } else if (flame && ((x + y) % 2 == 0)) {
                    setPixel(buf, bx + x, by + y, 255, clamp(180 + noise * 10), 50, 200);
                } else if (stick) {
                    setPixel(buf, bx + x, by + y,
                        clamp(120 + noise), clamp(85 + noise), clamp(50 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    private void generateCoalItem(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                boolean shape = (x >= 3 && x <= 12 && y >= 3 && y <= 12) &&
                                !((x == 3 || x == 12) && (y == 3 || y == 12));
                if (shape) {
                    int v = 35 + noise;
                    setPixel(buf, bx + x, by + y, clamp(v), clamp(v), clamp(v), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    private void generateIronIngot(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean shape = (x >= 2 && x <= 13 && y >= 5 && y <= 12);
                boolean top = (x >= 4 && x <= 11 && y >= 3 && y <= 5);
                if (shape || top) {
                    setPixel(buf, bx + x, by + y,
                        clamp(200 + noise), clamp(195 + noise), clamp(190 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    private void generateGlass(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                boolean border = x == 0 || x == 15 || y == 0 || y == 15;
                boolean frame = (x == 1 || x == 14 || y == 1 || y == 14);
                int noise = (hash(x, y) & 7) - 4;
                if (border) {
                    setPixel(buf, bx + x, by + y,
                        clamp(180 + noise), clamp(200 + noise), clamp(210 + noise), 255);
                } else if (frame) {
                    setPixel(buf, bx + x, by + y,
                        clamp(200 + noise), clamp(220 + noise), clamp(230 + noise), 200);
                } else {
                    setPixel(buf, bx + x, by + y,
                        clamp(210 + noise), clamp(230 + noise), clamp(240 + noise), 80);
                }
            }
    }

    private void generateRedFlower(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 3) - 2;
                boolean stem = (x >= 7 && x <= 8 && y >= 10 && y <= 15);
                boolean petal = Math.sqrt((x - 7.5) * (x - 7.5) + (y - 6) * (y - 6)) < 4.5;
                boolean center = Math.sqrt((x - 7.5) * (x - 7.5) + (y - 6) * (y - 6)) < 1.5;
                if (center) {
                    setPixel(buf, bx + x, by + y, 255, clamp(200 + noise * 10), 50, 255);
                } else if (petal) {
                    setPixel(buf, bx + x, by + y, clamp(220 + noise * 5), clamp(30 + noise * 5), 30, 255);
                } else if (stem) {
                    setPixel(buf, bx + x, by + y,
                        clamp(40 + noise), clamp(120 + noise), clamp(20 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    private void generateYellowFlower(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 3) - 2;
                boolean stem = (x >= 7 && x <= 8 && y >= 10 && y <= 15);
                boolean petal = Math.sqrt((x - 7.5) * (x - 7.5) + (y - 6) * (y - 6)) < 4;
                boolean center = Math.sqrt((x - 7.5) * (x - 7.5) + (y - 6) * (y - 6)) < 1.5;
                if (center) {
                    setPixel(buf, bx + x, by + y, clamp(180 + noise * 5), clamp(120 + noise * 5), 30, 255);
                } else if (petal) {
                    setPixel(buf, bx + x, by + y, 255, clamp(230 + noise * 5), clamp(50 + noise * 5), 255);
                } else if (stem) {
                    setPixel(buf, bx + x, by + y,
                        clamp(40 + noise), clamp(120 + noise), clamp(20 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    private void generateDiamond(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                int cx = Math.abs(x - 7);
                int cy = Math.abs(y - 7);
                boolean shape = (cx + cy) <= 6 && cy <= 5;
                if (shape) {
                    int bright = (cx + cy < 4) ? 20 : 0;
                    setPixel(buf, bx + x, by + y,
                        clamp(130 + noise + bright), clamp(230 + noise + bright), 255, 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    private void generateCharcoal(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                boolean shape = (x >= 3 && x <= 12 && y >= 3 && y <= 12) &&
                                !((x == 3 || x == 12) && (y == 3 || y == 12));
                if (shape) {
                    setPixel(buf, bx + x, by + y,
                        clamp(55 + noise), clamp(40 + noise), clamp(25 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
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
                    setPixel(buf, bx + x, by + y,
                        clamp(130 + noise), clamp(100 + noise), clamp(60 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y,
                        clamp(168 + noise), clamp(130 + noise), clamp(78 + noise), 255);
                }
            }
    }

    // Gold Ingot: warm yellow-gold ingot shape
    private void generateGoldIngot(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean shape = (x >= 2 && x <= 13 && y >= 5 && y <= 12);
                boolean top = (x >= 4 && x <= 11 && y >= 3 && y <= 5);
                if (shape || top) {
                    setPixel(buf, bx + x, by + y,
                        clamp(255 + noise), clamp(200 + noise), clamp(50 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    // Powered Rail: golden rails with redstone-tinted ties
    private void generatePoweredRail(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean rail = (x == 3 || x == 12);
                boolean tie = (y % 4 < 2) && (x >= 2 && x <= 13);
                boolean center = (x >= 7 && x <= 8) && (y % 4 < 2);
                if (rail) {
                    // Golden rails
                    setPixel(buf, bx + x, by + y,
                        clamp(255 + noise), clamp(200 + noise), clamp(50 + noise), 255);
                } else if (center) {
                    // Redstone center strip
                    setPixel(buf, bx + x, by + y,
                        clamp(180 + noise), clamp(20 + noise), clamp(20 + noise), 255);
                } else if (tie) {
                    // Darker wooden ties
                    setPixel(buf, bx + x, by + y,
                        clamp(100 + noise), clamp(70 + noise), clamp(40 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    // Redstone item: small red dust pile
    private void generateRedstoneItem(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 15) - 8;
                double cx = x - 7.5, cy = y - 7.5;
                boolean shape = (cx * cx + cy * cy) < 25;
                boolean bright = (cx * cx + cy * cy) < 9;
                if (bright) {
                    setPixel(buf, bx + x, by + y,
                        clamp(220 + noise), clamp(30 + noise), clamp(20 + noise), 255);
                } else if (shape) {
                    setPixel(buf, bx + x, by + y,
                        clamp(160 + noise), clamp(15 + noise), clamp(15 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    // Redstone Wire: cross-shaped red dust pattern on ground
    private void generateRedstoneWire(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean hLine = (y >= 6 && y <= 9) && (x >= 1 && x <= 14);
                boolean vLine = (x >= 6 && x <= 9) && (y >= 1 && y <= 14);
                boolean center = (x >= 5 && x <= 10 && y >= 5 && y <= 10);
                if (center) {
                    setPixel(buf, bx + x, by + y,
                        clamp(200 + noise), clamp(20 + noise), clamp(15 + noise), 230);
                } else if (hLine || vLine) {
                    setPixel(buf, bx + x, by + y,
                        clamp(160 + noise), clamp(10 + noise), clamp(10 + noise), 200);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    // Redstone Torch: like regular torch but red flame
    private void generateRedstoneTorch(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 3) - 2;
                boolean stick = (x >= 6 && x <= 9 && y >= 4 && y <= 15);
                boolean flame = (x >= 5 && x <= 10 && y >= 0 && y <= 4);
                boolean flameCenter = (x >= 7 && x <= 8 && y >= 1 && y <= 3);
                if (flameCenter) {
                    setPixel(buf, bx + x, by + y, 255, clamp(80 + noise * 10), 50, 240);
                } else if (flame && ((x + y) % 2 == 0)) {
                    setPixel(buf, bx + x, by + y, clamp(200 + noise * 10), 20, 20, 200);
                } else if (stick) {
                    setPixel(buf, bx + x, by + y,
                        clamp(120 + noise), clamp(85 + noise), clamp(50 + noise), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    // Redstone Repeater: stone slab with two redstone torches and arrow
    private void generateRedstoneRepeater(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x, y) & 7) - 4;
                boolean base = (x >= 1 && x <= 14 && y >= 1 && y <= 14);
                boolean torch1 = (x >= 3 && x <= 5 && y >= 6 && y <= 9);
                boolean torch2 = (x >= 10 && x <= 12 && y >= 6 && y <= 9);
                boolean arrow = (x == 7 || x == 8) && (y >= 3 && y <= 12);
                if (torch1 || torch2) {
                    setPixel(buf, bx + x, by + y,
                        clamp(200 + noise), clamp(20 + noise), clamp(15 + noise), 255);
                } else if (arrow) {
                    setPixel(buf, bx + x, by + y,
                        clamp(160 + noise), clamp(10 + noise), clamp(10 + noise), 255);
                } else if (base) {
                    int v = 140 + noise;
                    setPixel(buf, bx + x, by + y, clamp(v), clamp(v), clamp(v), 255);
                } else {
                    setPixel(buf, bx + x, by + y, 0, 0, 0, 0);
                }
            }
    }

    // Redstone Ore: stone base with red ore spots
    private void generateRedstoneOre(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int stoneNoise = (hash(x, y) & 31) - 16;
                boolean isOreSpot = (hash(x * 17 + 3, y * 13 + 7) & 7) < 2;
                if (isOreSpot) {
                    setPixel(buf, bx + x, by + y, 200, 30, 20, 255);
                } else {
                    int v = 127 + stoneNoise;
                    setPixel(buf, bx + x, by + y, clamp(v), clamp(v), clamp(v), 255);
                }
            }
    }

    /**
     * Lava texture — glowing orange-red with darker veins (Infdev 611 style).
     */
    private void generateLava(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise1 = (hash(x * 7 + 3, y * 11 + 5) & 63) - 32;
                int noise2 = (hash(x * 13 + y * 7, y * 3 + x * 11) & 31) - 16;
                boolean isDarkVein = (hash(x * 23 + 11, y * 19 + 7) & 15) < 3;
                boolean isBrightSpot = (hash(x * 31 + 2, y * 29 + 13) & 15) < 2;

                int r, g, b;
                if (isDarkVein) {
                    // Dark orange/brown veins
                    r = clamp(160 + noise2);
                    g = clamp(60 + noise2 / 2);
                    b = clamp(10);
                } else if (isBrightSpot) {
                    // Bright yellow-white hot spots
                    r = clamp(255);
                    g = clamp(220 + noise2);
                    b = clamp(80 + noise1 / 2);
                } else {
                    // Base orange-red
                    r = clamp(220 + noise1 / 2);
                    g = clamp(100 + noise1 / 3);
                    b = clamp(20 + noise2 / 4);
                }
                setPixel(buf, bx + x, by + y, r, g, b, 255);
            }
    }

    /**
     * Obsidian texture — dark purple-black with subtle purple highlights.
     */
    private void generateObsidian(ByteBuffer buf, int bx, int by) {
        for (int y = 0; y < TILE_SIZE; y++)
            for (int x = 0; x < TILE_SIZE; x++) {
                int noise = (hash(x * 11 + 7, y * 13 + 3) & 31) - 16;
                boolean hasHighlight = (hash(x * 37 + y * 23, y * 17 + x * 11) & 15) < 2;

                int r, g, b;
                if (hasHighlight) {
                    // Subtle purple highlight
                    r = clamp(40 + noise);
                    g = clamp(15 + noise / 2);
                    b = clamp(55 + noise);
                } else {
                    // Very dark base (almost black with purple tint)
                    r = clamp(20 + noise / 2);
                    g = clamp(12 + noise / 3);
                    b = clamp(30 + noise / 2);
                }
                setPixel(buf, bx + x, by + y, r, g, b, 255);
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
