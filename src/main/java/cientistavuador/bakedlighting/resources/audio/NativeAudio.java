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
package cientistavuador.bakedlighting.resources.audio;

import java.nio.ShortBuffer;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.openal.AL11.*;

/**
 * must be manually freed
 * @author Cien
 */
public class NativeAudio {
    
    private final ShortBuffer data;
    private final int channels;
    private final int sampleRate;
    private final float duration;
    
    private boolean freed = false;
    private int audioBuffer = 0;
    
    protected NativeAudio(ShortBuffer data, int channels, int sampleRate) {
        this.data = data;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.duration = (data.capacity() / ((float)channels)) / sampleRate;
    }

    public ShortBuffer getData() {
        throwExceptionIfFreed();
        return data;
    }

    public int getChannels() {
        return channels;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public float getDuration() {
        return duration;
    }
    
    public boolean hasAudioBuffer() {
        if (this.freed) {
            return false;
        }
        return this.audioBuffer != 0;
    }
    
    public int getAudioBuffer() {
        throwExceptionIfFreed();
        if (this.audioBuffer == 0) {
            this.audioBuffer = alGenBuffers();
            if (this.channels == 1) {
                alBufferData(this.audioBuffer, AL_FORMAT_MONO16, this.data, this.sampleRate);
            } else {
                alBufferData(this.audioBuffer, AL_FORMAT_STEREO16, this.data, this.sampleRate);
            }
        }
        return this.audioBuffer;
    }
    
    public void deleteAudioBuffer() {
        if (this.audioBuffer != 0) {
            alDeleteBuffers(this.audioBuffer);
            this.audioBuffer = 0;
        }
    }

    private void throwExceptionIfFreed() {
        if (this.freed) {
            throw new RuntimeException("Image is already freed!");
        }
    }
    
    public void free() {
        throwExceptionIfFreed();
        deleteAudioBuffer();
        MemoryUtil.memFree(this.data);
        this.freed = true;
    }
}
