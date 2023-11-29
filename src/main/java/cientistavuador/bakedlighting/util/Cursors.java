/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package cientistavuador.bakedlighting.util;

import cientistavuador.bakedlighting.Main;
import static org.lwjgl.glfw.GLFW.*;

/**
 *
 * @author Cien
 */
public class Cursors {

    public static final boolean DEBUG_ENABLED = true;

    public static final long CROSSHAIR;
    public static final long HAND;
    public static final long ARROW;

    static {
        if (DEBUG_ENABLED) {
            System.out.println("Initializing cursors...");
        }
        CROSSHAIR = glfwCreateStandardCursor(GLFW_CROSSHAIR_CURSOR);
        HAND = glfwCreateStandardCursor(GLFW_HAND_CURSOR);
        ARROW = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        if (DEBUG_ENABLED) {
            System.out.println("Cursors initialized.");
        }
    }

    public static void init() {

    }

    public static enum StandardCursor {
        CROSSHAIR(Cursors.CROSSHAIR, 2),
        HAND(Cursors.HAND, 1),
        ARROW_DEFAULT_NONE(Cursors.ARROW, 0);

        private final long cursorAddress;
        private final int priority;

        private StandardCursor(long cursorAddress, int priority) {
            this.cursorAddress = cursorAddress;
            this.priority = priority;
        }

        public long address() {
            return this.cursorAddress;
        }

        public int priority() {
            return this.priority;
        }
    }
    
    private static StandardCursor currentCursor = StandardCursor.ARROW_DEFAULT_NONE;
    private static StandardCursor nextCursor = StandardCursor.ARROW_DEFAULT_NONE;

    public static void setCursor(StandardCursor cursor) {
        if (cursor == null) {
            cursor = StandardCursor.ARROW_DEFAULT_NONE;
        }
        if (cursor.priority() > nextCursor.priority()) {
            nextCursor = cursor;
        }
    }
    
    public static void updateCursor() {
        if (!currentCursor.equals(nextCursor)) {
            currentCursor = nextCursor;
            glfwSetCursor(Main.WINDOW_POINTER, currentCursor.address());
        }
        nextCursor = StandardCursor.ARROW_DEFAULT_NONE;
    }

    private Cursors() {

    }
}
