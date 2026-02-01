package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Minimal bitmap font renderer using a procedurally generated 8×8 pixel font.
 * Supports printable ASCII (32–126). Renders text as textured quads.
 */
public class BitmapFont {

    private static final int CHAR_W = 8;
    private static final int CHAR_H = 8;
    private static final int CHARS_PER_ROW = 16;
    private static final int FIRST_CHAR = 32;
    private static final int LAST_CHAR = 126;
    private static final int NUM_CHARS = LAST_CHAR - FIRST_CHAR + 1; // 95
    private static final int ROWS = (NUM_CHARS + CHARS_PER_ROW - 1) / CHARS_PER_ROW; // 6
    private static final int TEX_W = CHARS_PER_ROW * CHAR_W; // 128
    private static final int TEX_H = ROWS * CHAR_H;          // 48

    /** Max characters per drawText call. */
    private static final int MAX_CHARS = 512;

    private int textureId;
    private int vao, vbo;
    private Shader fontShader;

    // 4 vertices per char, each vertex: x, y, u, v = 4 floats
    // 6 vertices per quad (2 triangles)
    private final float[] vertexData = new float[MAX_CHARS * 6 * 4];

    public void init() {
        fontShader = new Shader("shaders/font.vert", "shaders/font.frag");
        generateTexture();
        createBuffers();
    }

    /**
     * Generate the font atlas texture procedurally.
     */
    private void generateTexture() {
        ByteBuffer pixels = MemoryUtil.memAlloc(TEX_W * TEX_H);

        for (int c = FIRST_CHAR; c <= LAST_CHAR; c++) {
            int idx = c - FIRST_CHAR;
            int col = idx % CHARS_PER_ROW;
            int row = idx / CHARS_PER_ROW;
            long glyph = getGlyph(c);

            for (int py = 0; py < CHAR_H; py++) {
                // MSB = row 0 (top), LSB = row 7 (bottom)
                int bits = (int) ((glyph >> ((7 - py) * 8)) & 0xFF);
                for (int px = 0; px < CHAR_W; px++) {
                    int tx = col * CHAR_W + px;
                    int ty = row * CHAR_H + py;
                    boolean on = ((bits >> (7 - px)) & 1) == 1;
                    pixels.put(ty * TEX_W + tx, on ? (byte) 0xFF : (byte) 0x00);
                }
            }
        }

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        pixels.position(0);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, TEX_W, TEX_H, 0, GL_RED, GL_UNSIGNED_BYTE, pixels);
        glBindTexture(GL_TEXTURE_2D, 0);

        MemoryUtil.memFree(pixels);
    }

    private void createBuffers() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) MAX_CHARS * 6 * 4 * Float.BYTES, GL_DYNAMIC_DRAW);

        // aPos (location 0): vec2
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        // aTexCoord (location 1): vec2
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    /**
     * Draw a string at (x, y) in screen pixels. (0,0) = top-left.
     * Scale controls character size (1 = 8px, 2 = 16px, etc.).
     *
     * @param text  text to render
     * @param x     left edge in pixels
     * @param y     top edge in pixels (from top of screen)
     * @param scale character scale multiplier
     * @param screenW screen width
     * @param screenH screen height
     * @param r     red (0–1)
     * @param g     green (0–1)
     * @param b     blue (0–1)
     * @param a     alpha (0–1)
     */
    public void drawText(String text, float x, float y, float scale,
                         int screenW, int screenH,
                         float r, float g, float b, float a) {
        int charCount = Math.min(text.length(), MAX_CHARS);
        if (charCount == 0) return;

        float cw = CHAR_W * scale;
        float ch = CHAR_H * scale;
        float invTexW = 1.0f / TEX_W;
        float invTexH = 1.0f / TEX_H;

        int vi = 0;
        float curX = x;

        for (int i = 0; i < charCount; i++) {
            char c = text.charAt(i);
            if (c < FIRST_CHAR || c > LAST_CHAR) {
                curX += cw;
                continue;
            }

            int idx = c - FIRST_CHAR;
            int col = idx % CHARS_PER_ROW;
            int row = idx / CHARS_PER_ROW;

            float u0 = col * CHAR_W * invTexW;
            float v0 = row * CHAR_H * invTexH;
            float u1 = (col + 1) * CHAR_W * invTexW;
            float v1 = (row + 1) * CHAR_H * invTexH;

            // Convert top-left origin to OpenGL bottom-left
            float x0 = curX;
            float y0 = screenH - y - ch;
            float x1 = curX + cw;
            float y1 = screenH - y;

            // Triangle 1: top-left, bottom-left, bottom-right
            vertexData[vi++] = x0; vertexData[vi++] = y1; vertexData[vi++] = u0; vertexData[vi++] = v0;
            vertexData[vi++] = x0; vertexData[vi++] = y0; vertexData[vi++] = u0; vertexData[vi++] = v1;
            vertexData[vi++] = x1; vertexData[vi++] = y0; vertexData[vi++] = u1; vertexData[vi++] = v1;

            // Triangle 2: top-left, bottom-right, top-right
            vertexData[vi++] = x0; vertexData[vi++] = y1; vertexData[vi++] = u0; vertexData[vi++] = v0;
            vertexData[vi++] = x1; vertexData[vi++] = y0; vertexData[vi++] = u1; vertexData[vi++] = v1;
            vertexData[vi++] = x1; vertexData[vi++] = y1; vertexData[vi++] = u1; vertexData[vi++] = v0;

            curX += cw;
        }

        int vertexCount = vi / 4;

        // Upload and render
        fontShader.bind();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Matrix4f ortho = new Matrix4f().ortho(0, screenW, 0, screenH, -1, 1);
            FloatBuffer fb = stack.mallocFloat(16);
            ortho.get(fb);
            glUniformMatrix4fv(glGetUniformLocation(fontShader.getProgramId(), "uProjection"), false, fb);
        }

        fontShader.setInt("uFontAtlas", 0);
        fontShader.setVec4("uColor", r, g, b, a);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(vi);
            buf.put(vertexData, 0, vi).flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, buf);
        }

        glDrawArrays(GL_TRIANGLES, 0, vertexCount);

        glBindVertexArray(0);
        glBindTexture(GL_TEXTURE_2D, 0);
        fontShader.unbind();
    }

    /**
     * Convenience wrapper: draw a string at (x,y) from bottom-left origin.
     * Uses default scale of 2.0 and white color.
     */
    public void drawString(String text, float x, float y, int screenW, int screenH,
                           float r, float g, float b) {
        // Convert from bottom-left to top-left origin for drawText
        float topY = screenH - y - 8 * 2.0f; // char height * scale
        drawText(text, x, topY, 2.0f, screenW, screenH, r, g, b, 1.0f);
    }

    /**
     * Convenience wrapper: renderString at (x,y) from bottom-left origin.
     */
    public void renderString(String text, float x, float y, int screenW, int screenH,
                             float r, float g, float b) {
        drawString(text, x, y, screenW, screenH, r, g, b);
    }

    public void cleanup() {
        if (textureId != 0) glDeleteTextures(textureId);
        if (vbo != 0) glDeleteBuffers(vbo);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (fontShader != null) fontShader.cleanup();
    }

    // ============================================================
    // 8×8 pixel font data — each glyph is 64 bits (8 rows × 8 cols)
    // Row 0 is top, bit 7 of each byte is leftmost pixel.
    // ============================================================

    private static long getGlyph(int c) {
        return switch (c) {
            case ' ' -> 0x0000000000000000L;
            case '!' -> 0x1818181818001800L;
            case '"' -> 0x6C6C240000000000L;
            case '#' -> 0x6C6CFE6CFE6C6C00L;
            case '$' -> 0x107CFE7C3EFC1000L;
            case '%' -> 0x00C6CC183066C600L;
            case '&' -> 0x386C3876DCCC7600L;
            case '\'' -> 0x1818080000000000L;
            case '(' -> 0x0C18303030180C00L;
            case ')' -> 0x30180C0C0C183000L;
            case '*' -> 0x006C38FE386C0000L;
            case '+' -> 0x0018187E18180000L;
            case ',' -> 0x0000000000181830L;
            case '-' -> 0x000000FE00000000L;
            case '.' -> 0x0000000000181800L;
            case '/' -> 0x060C183060C08000L;
            case '0' -> 0x7CC6CEDEF6E67C00L;
            case '1' -> 0x1838181818187E00L;
            case '2' -> 0x7CC6060C3060FE00L;
            case '3' -> 0x7CC6063C06C67C00L;
            case '4' -> 0x0C1C3C6CFE0C0C00L;
            case '5' -> 0xFEC0FC0606C67C00L;
            case '6' -> 0x3C60C0FCC6C67C00L;
            case '7' -> 0xFE06060C18181800L;
            case '8' -> 0x7CC6C67CC6C67C00L;
            case '9' -> 0x7CC6C67E06067C00L;
            case ':' -> 0x0018180018180000L;
            case ';' -> 0x0018180018180800L;
            case '<' -> 0x0C18306030180C00L;
            case '=' -> 0x0000FE00FE000000L;
            case '>' -> 0x6030180C18306000L;
            case '?' -> 0x7CC6060C18001800L;
            case '@' -> 0x7CC6DEDEDCC07E00L;
            case 'A' -> 0x386CC6FEC6C6C600L;
            case 'B' -> 0xFCC6C6FCC6C6FC00L;
            case 'C' -> 0x3C66C0C0C0663C00L;
            case 'D' -> 0xF8CCC6C6C6CCF800L;
            case 'E' -> 0xFEC0C0FCC0C0FE00L;
            case 'F' -> 0xFEC0C0FCC0C0C000L;
            case 'G' -> 0x3C66C0CEC6663E00L;
            case 'H' -> 0xC6C6C6FEC6C6C600L;
            case 'I' -> 0x7E18181818187E00L;
            case 'J' -> 0x1E060606C6C67C00L;
            case 'K' -> 0xC6CCD8F0D8CCC600L;
            case 'L' -> 0xC0C0C0C0C0C0FE00L;
            case 'M' -> 0xC6EEFED6C6C6C600L;
            case 'N' -> 0xC6E6F6DECEC6C600L;
            case 'O' -> 0x7CC6C6C6C6C67C00L;
            case 'P' -> 0xFCC6C6FCC0C0C000L;
            case 'Q' -> 0x7CC6C6C6D6CC7A00L;
            case 'R' -> 0xFCC6C6FCD8CCC600L;
            case 'S' -> 0x7CC6C07C06C67C00L;
            case 'T' -> 0x7E18181818181800L;
            case 'U' -> 0xC6C6C6C6C6C67C00L;
            case 'V' -> 0xC6C6C6C66C381000L;
            case 'W' -> 0xC6C6C6D6FEEEC600L;
            case 'X' -> 0xC6C66C386CC6C600L;
            case 'Y' -> 0x6666663C18181800L;
            case 'Z' -> 0xFE060C183060FE00L;
            case '[' -> 0x3C30303030303C00L;
            case '\\' -> 0xC06030180C060200L;
            case ']' -> 0x3C0C0C0C0C0C3C00L;
            case '^' -> 0x10386CC600000000L;
            case '_' -> 0x00000000000000FEL;
            case '`' -> 0x1818100000000000L;
            case 'a' -> 0x00007C067EC67E00L;
            case 'b' -> 0xC0C0FCC6C6C6FC00L;
            case 'c' -> 0x00007CC6C0C67C00L;
            case 'd' -> 0x06067EC6C6C67E00L;
            case 'e' -> 0x00007CC6FEC07C00L;
            case 'f' -> 0x1C30FC3030303000L;
            case 'g' -> 0x00007EC6C67E067CL;
            case 'h' -> 0xC0C0FCC6C6C6C600L;
            case 'i' -> 0x1800381818183C00L;
            case 'j' -> 0x0600060606C6C67CL;
            case 'k' -> 0xC0C0CCD8F0D8CC00L;
            case 'l' -> 0x3818181818183C00L;
            case 'm' -> 0x0000CCFED6D6C600L;
            case 'n' -> 0x0000FCC6C6C6C600L;
            case 'o' -> 0x00007CC6C6C67C00L;
            case 'p' -> 0x0000FCC6C6FCC0C0L;
            case 'q' -> 0x00007EC6C67E0606L;
            case 'r' -> 0x0000DEC0C0C0C000L;
            case 's' -> 0x00007CC07C06FC00L;
            case 't' -> 0x30307C3030301800L;
            case 'u' -> 0x0000C6C6C6C67E00L;
            case 'v' -> 0x0000C6C66C381000L;
            case 'w' -> 0x0000C6D6D6FE6C00L;
            case 'x' -> 0x0000C66C386CC600L;
            case 'y' -> 0x0000C6C6C67E067CL;
            case 'z' -> 0x0000FE0C3860FE00L;
            case '{' -> 0x0E18186018180E00L;
            case '|' -> 0x1818181818181800L;
            case '}' -> 0x7018180618187000L;
            case '~' -> 0x76DC000000000000L;
            default  -> 0x0000000000000000L;
        };
    }
}
