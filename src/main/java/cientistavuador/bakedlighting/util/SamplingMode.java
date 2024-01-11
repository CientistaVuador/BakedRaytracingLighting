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

/**
 *
 * @author Cien
 */
public enum SamplingMode {
    SAMPLE_1(SamplingModeLoader.SAMPLES.get("1.png")),
    SAMPLE_3(SamplingModeLoader.SAMPLES.get("3.png")),
    SAMPLE_4(SamplingModeLoader.SAMPLES.get("4.png")),
    SAMPLE_5(SamplingModeLoader.SAMPLES.get("5.png")),
    SAMPLE_7(SamplingModeLoader.SAMPLES.get("7.png")),
    SAMPLE_8(SamplingModeLoader.SAMPLES.get("8.png")),
    SAMPLE_9(SamplingModeLoader.SAMPLES.get("9.png")),
    SAMPLE_11(SamplingModeLoader.SAMPLES.get("11.png")),
    SAMPLE_12(SamplingModeLoader.SAMPLES.get("12.png")),
    SAMPLE_13(SamplingModeLoader.SAMPLES.get("13.png")),
    SAMPLE_15(SamplingModeLoader.SAMPLES.get("15.png")),
    SAMPLE_16(SamplingModeLoader.SAMPLES.get("16.png")),
    ;
    
    private final float[] sampleLocations;

    private SamplingMode(float[] sampleLocations) {
        this.sampleLocations = sampleLocations;
    }

    public int numSamples() {
        return this.sampleLocations.length / 2;
    }

    public float sampleX(int sample) {
        return this.sampleLocations[(sample * 2) + 0];
    }

    public float sampleY(int sample) {
        return this.sampleLocations[(sample * 2) + 1];
    }
    
}
