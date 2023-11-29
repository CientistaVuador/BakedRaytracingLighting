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

import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.openal.AL11.*;

/**
 *
 * @author Cien
 */
public class ALSourceUtil {
    
    private static interface Updateable {
        public boolean update();
    }
    
    private static class DeleteWhenStopped implements Updateable {

        private final int source;
        private final Runnable callback;
        
        public DeleteWhenStopped(int source, Runnable callback) {
            this.source = source;
            this.callback = callback;
        }
        
        @Override
        public boolean update() {
            if (alGetSourcei(this.source, AL_SOURCE_STATE) == AL_STOPPED) {
                alDeleteSources(this.source);
                if (this.callback != null) {
                    this.callback.run();
                }
                return true;
            }
            return false;
        }
    }
    
    private static final List<Updateable> sources = new ArrayList<>();
    
    public static void deleteWhenStopped(int source, Runnable afterDeleteCallback) {
        ALSourceUtil.sources.add(new DeleteWhenStopped(source, afterDeleteCallback));
    }
    
    public static void update() {
        Updateable[] array = ALSourceUtil.sources.toArray(Updateable[]::new);
        for (Updateable e:array) {
            if (e.update()) {
                ALSourceUtil.sources.remove(e);
            }
        }
    }
    
    private ALSourceUtil() {
        
    }
}
