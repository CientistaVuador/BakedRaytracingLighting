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
package cientistavuador.bakedlighting;

import cientistavuador.bakedlighting.sound.SoundSystem;
import cientistavuador.bakedlighting.geometry.Geometries;
import cientistavuador.bakedlighting.sound.Sounds;
import cientistavuador.bakedlighting.text.GLFonts;
import cientistavuador.bakedlighting.texture.Textures;
import cientistavuador.bakedlighting.ubo.UBOBindingPoints;
import cientistavuador.bakedlighting.util.ALSourceUtil;
import cientistavuador.bakedlighting.util.Aab;
import cientistavuador.bakedlighting.util.Cursors;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;
import org.lwjgl.glfw.GLFWFramebufferSizeCallbackI;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GLDebugMessageCallback;
import static org.lwjgl.opengl.KHRDebug.*;
import static org.lwjgl.stb.STBImage.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Main class
 *
 * @author Cien
 */
public class Main {

    public static final boolean USE_MSAA = false;
    public static final boolean DEBUG_ENABLED = true;
    public static final boolean SPIKE_LAG_WARNINGS = false;
    public static final int MIN_UNIFORM_BUFFER_BINDINGS = UBOBindingPoints.MIN_NUMBER_OF_UBO_BINDING_POINTS;

    static {
        org.lwjgl.system.Configuration.LIBRARY_PATH.set("natives");
    }

    public static class OpenGLErrorException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final int error;

        public OpenGLErrorException(int error) {
            super("OpenGL Error " + error);
            this.error = error;
        }

        public int getError() {
            return error;
        }
    }

    public static class GLFWErrorException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public GLFWErrorException(String error) {
            super(error);
        }
    }

    public static boolean THROW_GL_GLFW_ERRORS = true;

    public static void checkGLError() {
        int error = glGetError();
        if (error != 0) {
            OpenGLErrorException err = new OpenGLErrorException(error);
            if (THROW_GL_GLFW_ERRORS) {
                throw err;
            } else {
                err.printStackTrace(System.err);
            }
        }
    }

    public static String WINDOW_TITLE = "CienCraft - FPS: 60";
    public static int WIDTH = 800;
    public static int HEIGHT = 600;
    public static double TPF = 1 / 60d;
    public static int FPS = 60;
    public static long WINDOW_POINTER = NULL;
    public static long FRAME = 0;
    public static boolean FULLSCREEN = false;
    public static double ONE_SECOND_COUNTER = 0.0;
    public static double ONE_MINUTE_COUNTER = 0.0;
    public static int NUMBER_OF_DRAWCALLS = 0;
    public static int NUMBER_OF_VERTICES = 0;
    public static float MOUSE_X = 0f;
    public static float MOUSE_Y = 0f;
    public static boolean EXIT_SIGNAL = false;
    public static Aab MOUSE_AAB = new Aab() {
        @Override
        public void getMin(Vector3f min) {
            min.set(Main.MOUSE_X, Main.MOUSE_Y, 0f);
        }

        @Override
        public void getMax(Vector3f max) {
            getMin(max);
        }
    };
    public static final ConcurrentLinkedQueue<Runnable> MAIN_TASKS = new ConcurrentLinkedQueue<>();
    public static final Vector3f DEFAULT_CLEAR_COLOR = new Vector3f(0.2f, 0.4f, 0.6f);
    public static final String WINDOW_ICON = "cientistavuador/bakedlighting/resources/image/window_icon.png";
    private static final int[] savedWindowStatus = new int[4];
    private static GLDebugMessageCallback DEBUG_CALLBACK = null;
    
    
    private static String debugSource(int source) {
        return switch (source) {
            case GL_DEBUG_SOURCE_API ->
                "API";
            case GL_DEBUG_SOURCE_WINDOW_SYSTEM ->
                "WINDOW SYSTEM";
            case GL_DEBUG_SOURCE_SHADER_COMPILER ->
                "SHADER COMPILER";
            case GL_DEBUG_SOURCE_THIRD_PARTY ->
                "THIRD PARTY";
            case GL_DEBUG_SOURCE_APPLICATION ->
                "APPLICATION";
            case GL_DEBUG_SOURCE_OTHER ->
                "OTHER";
            default ->
                "UNKNOWN";
        };
    }

    private static String debugType(int type) {
        return switch (type) {
            case GL_DEBUG_TYPE_ERROR ->
                "ERROR";
            case GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR ->
                "DEPRECATED BEHAVIOR";
            case GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR ->
                "UNDEFINED BEHAVIOR";
            case GL_DEBUG_TYPE_PORTABILITY ->
                "PORTABILITY";
            case GL_DEBUG_TYPE_PERFORMANCE ->
                "PERFORMANCE";
            case GL_DEBUG_TYPE_OTHER ->
                "OTHER";
            case GL_DEBUG_TYPE_MARKER ->
                "MARKER";
            default ->
                "UNKNOWN";
        };
    }

    private static String debugSeverity(int severity) {
        return switch (severity) {
            case GL_DEBUG_SEVERITY_HIGH ->
                "HIGH";
            case GL_DEBUG_SEVERITY_MEDIUM ->
                "MEDIUM";
            case GL_DEBUG_SEVERITY_LOW ->
                "LOW";
            default ->
                "UNKNOWN";
        };
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        glfwSetErrorCallback((error, description) -> {
            GLFWErrorException exception = new GLFWErrorException("GLFW Error " + error + ": " + memASCIISafe(description));
            if (THROW_GL_GLFW_ERRORS) {
                throw exception;
            } else {
                exception.printStackTrace(System.err);
            }
        });

        if (!glfwInit()) {
            throw new IllegalStateException("Could not initialize GLFW!");
        }

        if (USE_MSAA) {
            glfwWindowHint(GLFW_SAMPLES, 16); //MSAA 16x
        }
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);

        WINDOW_POINTER = glfwCreateWindow(Main.WIDTH, Main.HEIGHT, Main.WINDOW_TITLE, NULL, NULL);
        if (WINDOW_POINTER == NULL) {
            throw new IllegalStateException("Could not create a OpenGL 3.3 Context Window! Update your drivers or buy a new GPU.");
        }
        glfwMakeContextCurrent(WINDOW_POINTER);

        glfwSwapInterval(0);

        loadWindowIcon:
        {
            System.out.println("Loading window icon...");

            if (WINDOW_ICON == null) {
                System.out.println("Window icon is null.");
                break loadWindowIcon;
            }

            URL iconUrl = ClassLoader.getSystemResource(WINDOW_ICON);
            if (iconUrl == null) {
                System.out.println("Warning: Window icon '" + WINDOW_ICON + "' not found.");
                break loadWindowIcon;
            }

            ByteBuffer iconData = null;

            try {
                URLConnection connection = iconUrl.openConnection();
                connection.connect();

                iconData = MemoryUtil.memAlloc(connection.getContentLength());

                try (InputStream iconStream = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = iconStream.read(buffer)) != -1) {
                        iconData.put(buffer, 0, read);
                    }
                }

                iconData.rewind();
            } catch (IOException ex) {
                System.out.println("Warning: Failed to read window icon '" + WINDOW_ICON + "'");
                ex.printStackTrace(System.out);
                if (iconData != null) {
                    MemoryUtil.memFree(iconData);
                }
                break loadWindowIcon;
            }

            ByteBuffer iconRawData;
            int[] width = {0};
            int[] height = {0};
            try {
                iconRawData = stbi_load_from_memory(iconData, width, height, new int[1], 4);
            } finally {
                MemoryUtil.memFree(iconData);
            }

            if (iconRawData == null) {
                System.out.println("Warning: Window icon '" + WINDOW_ICON + "' not supported; " + stbi_failure_reason());
                break loadWindowIcon;
            }

            try {
                GLFWImage.Buffer iconImage = GLFWImage.calloc(1);
                try {
                    iconImage
                            .width(width[0])
                            .height(height[0])
                            .pixels(iconRawData);

                    glfwSetWindowIcon(WINDOW_POINTER, iconImage);
                    System.out.println("Finished loading window icon; width=" + width[0] + ", height=" + height[0] + ", path='" + WINDOW_ICON + "'");
                } finally {
                    MemoryUtil.memFree(iconImage);
                }
            } finally {
                MemoryUtil.memFree(iconRawData);
            }
        }

        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(WINDOW_POINTER, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }

        GL.createCapabilities();

        if (DEBUG_ENABLED) {
            debug:
            {
                if (!GL.getCapabilities().GL_KHR_debug) {
                    System.err.println("[GL-DEBUG] Debug was enabled but KHR_debug is not supported.");
                    break debug;
                }

                glEnable(GL_DEBUG_OUTPUT);

                DEBUG_CALLBACK = new GLDebugMessageCallback() {
                    @Override
                    public void invoke(int source, int type, int id, int severity, int length, long message, long userParam) {
                        if (severity == GL_DEBUG_SEVERITY_NOTIFICATION) {
                            return;
                        }

                        String msg = memASCII(message, length);

                        PrintStream out = System.out;
                        if (severity == GL_DEBUG_SEVERITY_HIGH) {
                            out = System.err;
                        }

                        out.println("[GL-DEBUG]");
                        out.println("    Severity: " + debugSeverity(severity));
                        out.println("    Source: " + debugSource(source));
                        out.println("    Type: " + debugType(type));
                        out.println("    ID: " + id);
                        out.println("    Message: " + msg);
                    }
                };
                glDebugMessageCallback(DEBUG_CALLBACK, NULL);
            }
        }

        if (USE_MSAA) {
            glEnable(GL_MULTISAMPLE);
        }
        glEnable(GL_STENCIL_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(DEFAULT_CLEAR_COLOR.x(), DEFAULT_CLEAR_COLOR.y(), DEFAULT_CLEAR_COLOR.z(), 1.0f);
        glEnable(GL_DEPTH_TEST);
        glClearDepth(1f);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glClearStencil(0);
        glCullFace(GL_BACK);
        glLineWidth(1f);
        int maxUBOBindings = glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS);
        if (maxUBOBindings < MIN_UNIFORM_BUFFER_BINDINGS) {
            throw new IllegalStateException("Max UBO Bindings too small! Update your drivers or buy a new GPU.");
        }
        int maxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE);
        if (maxTextureSize < 8192) {
            throw new IllegalStateException("Max texture size must be 8192 or more! Update your drivers or buy a new GPU.");
        }

        Main.checkGLError();

        GLFonts.init(); //static initialize
        Textures.init(); //static initialize
        Geometries.init(); //static initialize
        SoundSystem.init(); //static initialize
        Sounds.init(); //static initialize
        Cursors.init(); //static initialize
        Game.get(); //static initialize

        Main.checkGLError();

        GLFWFramebufferSizeCallbackI frameBufferSizecb = (window, width, height) -> {
            glViewport(0, 0, width, height);
            Main.WIDTH = width;
            Main.HEIGHT = height;
            Game.get().windowSizeChanged(width, height);
            Main.checkGLError();
        };
        frameBufferSizecb.invoke(WINDOW_POINTER, Main.WIDTH, Main.HEIGHT);
        glfwSetFramebufferSizeCallback(WINDOW_POINTER, frameBufferSizecb);

        glfwSetCursorPosCallback(WINDOW_POINTER, (window, x, y) -> {
            Game.get().mouseCursorMoved(x, y);
        });

        glfwSetKeyCallback(WINDOW_POINTER, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_F11 && action == GLFW_PRESS) {
                Main.FULLSCREEN = glfwGetWindowMonitor(window) != NULL;
                if (!Main.FULLSCREEN) {
                    goFullscreen:
                    {
                        long primary = glfwGetPrimaryMonitor();
                        if (primary == NULL) {
                            System.out.println("Warning: No Monitors Found.");
                            break goFullscreen;
                        }
                        GLFWVidMode mode = glfwGetVideoMode(primary);
                        if (mode == null) {
                            System.out.println("Warning: Video Mode Query Failed for " + glfwGetMonitorName(primary));
                            break goFullscreen;
                        }
                        int[] windowX = {0};
                        int[] windowY = {0};
                        int[] windowWidth = {0};
                        int[] windowHeight = {0};
                        
                        glfwGetWindowPos(window, windowX, windowY);
                        glfwGetWindowSize(window, windowWidth, windowHeight);
                        
                        Main.savedWindowStatus[0] = windowX[0];
                        Main.savedWindowStatus[1] = windowY[0];
                        Main.savedWindowStatus[2] = windowWidth[0];
                        Main.savedWindowStatus[3] = windowHeight[0];
                        
                        glfwSetWindowMonitor(window, primary, 0, 0, mode.width(), mode.height(), mode.refreshRate());
                        Main.FULLSCREEN = true;
                    }
                } else {
                    glfwSetWindowMonitor(window, NULL, Main.savedWindowStatus[0], Main.savedWindowStatus[1], Main.savedWindowStatus[2], Main.savedWindowStatus[3], GLFW_DONT_CARE);
                    Main.FULLSCREEN = false;
                }
            }
            Game.get().keyCallback(window, key, scancode, action, mods);
        });

        glfwSetMouseButtonCallback(WINDOW_POINTER, (window, button, action, mods) -> {
            Game.get().mouseCallback(window, button, action, mods);
        });

        Game.get().start();

        Main.checkGLError();

        int frames = 0;
        long nextFpsUpdate = System.currentTimeMillis() + 1000;
        long nextTitleUpdate = System.currentTimeMillis() + 100;
        long timeFrameBegin = System.nanoTime();

        while (!glfwWindowShouldClose(WINDOW_POINTER)) {
            Main.TPF = (System.nanoTime() - timeFrameBegin) / 1E9d;
            timeFrameBegin = System.nanoTime();

            Main.NUMBER_OF_DRAWCALLS = 0;
            Main.NUMBER_OF_VERTICES = 0;
            Main.WINDOW_TITLE = "Baked Raytracing Lighting - FPS: " + Main.FPS;

            if (SPIKE_LAG_WARNINGS) {
                int tpfFps = (int) (1.0 / Main.TPF);
                if (tpfFps < 60 && ((Main.FPS - tpfFps) > 30)) {
                    System.out.println("[Spike Lag Warning] From " + Main.FPS + " FPS to " + tpfFps + " FPS; current frame TPF: " + String.format("%.3f", Main.TPF) + "s");
                }
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                DoubleBuffer mouseX = stack.mallocDouble(1);
                DoubleBuffer mouseY = stack.mallocDouble(1);
                glfwGetCursorPos(Main.WINDOW_POINTER, mouseX, mouseY);
                double mX = mouseX.get();
                double mY = mouseY.get();
                mY = Main.HEIGHT - mY;
                mX /= Main.WIDTH;
                mY /= Main.HEIGHT;
                mX = (mX * 2.0) - 1.0;
                mY = (mY * 2.0) - 1.0;

                Main.MOUSE_X = (float) mX;
                Main.MOUSE_Y = (float) mY;
            }

            glfwPollEvents();
            glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

            Runnable r;
            while ((r = MAIN_TASKS.poll()) != null) {
                r.run();
            }

            ALSourceUtil.update();

            Game.get().loop();

            glFlush();

            Main.checkGLError();

            glfwSwapBuffers(WINDOW_POINTER);

            frames++;
            if (System.currentTimeMillis() >= nextFpsUpdate) {
                Main.FPS = frames;
                frames = 0;
                nextFpsUpdate = System.currentTimeMillis() + 1000;
            }

            if (System.currentTimeMillis() >= nextTitleUpdate) {
                nextTitleUpdate = System.currentTimeMillis() + 100;
                glfwSetWindowTitle(WINDOW_POINTER, Main.WINDOW_TITLE);
            }

            Main.ONE_SECOND_COUNTER += Main.TPF;
            Main.ONE_MINUTE_COUNTER += Main.TPF;

            if (Main.ONE_SECOND_COUNTER > 1.0) {
                Main.ONE_SECOND_COUNTER = 0.0;
            }
            if (Main.ONE_MINUTE_COUNTER > 60.0) {
                Main.ONE_MINUTE_COUNTER = 0.0;
            }

            Main.FRAME++;

            if (Main.EXIT_SIGNAL) {
                glfwMakeContextCurrent(0);
                glfwDestroyWindow(Main.WINDOW_POINTER);
                break;
            }
        }
        if (DEBUG_CALLBACK != null) {
            DEBUG_CALLBACK.free();
        }
    }

}
