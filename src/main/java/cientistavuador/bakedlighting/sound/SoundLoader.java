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

import cientistavuador.bakedlighting.resources.audio.AudioResources;
import cientistavuador.bakedlighting.resources.audio.NativeAudio;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 *
 * @author Cien
 */
public class SoundLoader {
    
    public static final boolean DEBUG_OUTPUT = true;
    
    public static NativeAudio[] load(String[] audioNames) {
        List<Future<NativeAudio>> tasks = new ArrayList<>();
        
        for (int i = 0; i < audioNames.length; i++) {
            String audioName = audioNames[i];
            final int finalIndex = i;
            tasks.add(CompletableFuture.supplyAsync(() -> {
                if (DEBUG_OUTPUT) {
                    System.out.println("Loading '"+audioName+"', index "+finalIndex);
                }
                NativeAudio audio = AudioResources.load(audioName);
                if (DEBUG_OUTPUT){
                    System.out.println("Finished loading '"+audioName+"', index "+finalIndex);
                }
                return audio;
            }));
        }
        
        NativeAudio[] audioOutput = new NativeAudio[audioNames.length];
        
        int index = 0;
        RuntimeException exception = null;
        for (Future<NativeAudio> futureAudio:tasks) {
            try {
                audioOutput[index] = futureAudio.get();
            } catch (InterruptedException | ExecutionException ex) {
                exception = new RuntimeException(ex);
            }
            index++;
        }
        
        if (exception != null) {
            for (int i = 0; i < audioOutput.length; i++) {
                if (audioOutput[i] != null) {
                    audioOutput[i].free();
                }
            }
            throw exception;
        }
        
        NativeAudio[] output = new NativeAudio[audioNames.length];
        
        for (int i = 0; i < audioOutput.length; i++) {
            NativeAudio audio = audioOutput[i];
            if (DEBUG_OUTPUT) {
                System.out.println("Creating buffer for "+audioNames[i]+", index:"+i);
            }
            audio.getAudioBuffer();
            if (DEBUG_OUTPUT) {
                System.out.println("Finished creating buffer for "+audioNames[i]+", index:"+i+", buffer id:"+audio.getAudioBuffer()+", sampleRate:"+audio.getSampleRate()+", duration:"+audio.getDuration()+", channels:"+audio.getChannels());
            }
            output[i] = audio;
        }
        
        return output;
    }
    
    private SoundLoader() {
        
    }
}
