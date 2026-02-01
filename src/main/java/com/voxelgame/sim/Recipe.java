package com.voxelgame.sim;

public class Recipe {

    private final int width, height;
    private final int[] pattern;
    private final int outputId;
    private final int outputCount;
    private final boolean shapeless;

    public Recipe(int width, int height, int[] pattern, int outputId, int outputCount) {
        this.width = width;
        this.height = height;
        this.pattern = pattern;
        this.outputId = outputId;
        this.outputCount = outputCount;
        this.shapeless = false;
    }

    private Recipe(int width, int height, int[] pattern, int outputId, int outputCount, boolean shapeless) {
        this.width = width;
        this.height = height;
        this.pattern = pattern;
        this.outputId = outputId;
        this.outputCount = outputCount;
        this.shapeless = shapeless;
    }

    public static Recipe shapeless(int[] ingredients, int outputId, int outputCount) {
        return new Recipe(0, 0, ingredients, outputId, outputCount, true);
    }

    public int getOutputId() { return outputId; }
    public int getOutputCount() { return outputCount; }
    public boolean isShapeless() { return shapeless; }

    public boolean matches(int[] grid) {
        if (grid == null || grid.length != 4) return false;
        if (shapeless) return matchesShapeless(grid);
        return matchesShaped(grid);
    }

    private boolean matchesShapeless(int[] grid) {
        int[] patternCounts = new int[256];
        int patternNonEmpty = 0;
        for (int id : pattern) {
            if (id > 0) { patternCounts[id]++; patternNonEmpty++; }
        }
        int[] gridCounts = new int[256];
        int gridNonEmpty = 0;
        for (int id : grid) {
            if (id > 0) { gridCounts[id]++; gridNonEmpty++; }
        }
        if (gridNonEmpty != patternNonEmpty) return false;
        for (int i = 0; i < 256; i++) {
            if (gridCounts[i] != patternCounts[i]) return false;
        }
        return true;
    }

    private boolean matchesShaped(int[] grid) {
        int maxOffCol = 2 - width;
        int maxOffRow = 2 - height;
        for (int offRow = 0; offRow <= maxOffRow; offRow++) {
            for (int offCol = 0; offCol <= maxOffCol; offCol++) {
                if (tryOffset(grid, offCol, offRow, false)) return true;
                if (width > 1 && tryOffset(grid, offCol, offRow, true)) return true;
            }
        }
        return false;
    }

    private boolean tryOffset(int[] grid, int offCol, int offRow, boolean mirror) {
        for (int gRow = 0; gRow < 2; gRow++) {
            for (int gCol = 0; gCol < 2; gCol++) {
                int pCol = gCol - offCol;
                int pRow = gRow - offRow;
                int gridVal = grid[gRow * 2 + gCol];
                if (pCol >= 0 && pCol < width && pRow >= 0 && pRow < height) {
                    int patCol = mirror ? (width - 1 - pCol) : pCol;
                    int expected = pattern[pRow * width + patCol];
                    if (gridVal != expected) return false;
                } else {
                    if (gridVal != 0) return false;
                }
            }
        }
        return true;
    }
}
