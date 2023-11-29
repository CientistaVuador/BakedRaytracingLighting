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
package cientistavuador.bakedlighting.sound;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import static org.lwjgl.openal.ALC11.*;
import org.lwjgl.openal.ALCCapabilities;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 *
 * @author Cien
 */
public class SoundSystem {
    
    public static final boolean DEBUG_OUTPUT = true;
    public static final long DEVICE;
    public static final long CONTEXT;
    
    static {
        DEVICE = alcOpenDevice((ByteBuffer) null);
        if (DEVICE == NULL) {
            throw new RuntimeException("No Audio Device Found");
        }
        ALCCapabilities deviceCaps = ALC.createCapabilities(DEVICE);

        CONTEXT = alcCreateContext(DEVICE, (IntBuffer) null);
        alcMakeContextCurrent(CONTEXT);
        AL.createCapabilities(deviceCaps);
        
        if (DEBUG_OUTPUT) {
            System.out.println("OpenAL Initialized.");
            System.out.println(alcGetString(DEVICE, ALC_DEVICE_SPECIFIER)+" "+alcGetInteger(DEVICE, ALC_MAJOR_VERSION)+"."+alcGetInteger(DEVICE, ALC_MINOR_VERSION));
        }
    }
    
    public static void init() {
        
    }
    
    private SoundSystem() {
        
    }
    
}
