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

import cientistavuador.bakedlighting.natives.NativesExtractor;
import cientistavuador.bakedlighting.sound.SoundSystem;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Toolkit;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.openal.ALC11.*;

/**
 *
 * @author Cien
 */
public class MainWrapper {

    static {
        Locale.setDefault(Locale.US);
        
        System.out.println("  /$$$$$$  /$$        /$$$$$$  /$$");
        System.out.println(" /$$__  $$| $$       /$$__  $$| $$");
        System.out.println("| $$  \\ $$| $$      | $$  \\ $$| $$");
        System.out.println("| $$  | $$| $$      | $$$$$$$$| $$");
        System.out.println("| $$  | $$| $$      | $$__  $$|__/");
        System.out.println("| $$  | $$| $$      | $$  | $$    ");
        System.out.println("|  $$$$$$/| $$$$$$$$| $$  | $$ /$$");
        System.out.println(" \\______/ |________/|__/  |__/|__/");
        
        FlatDarkLaf.setup();

        String osName = System.getProperty("os.name");
        System.out.println("Running on " + osName);

        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            NativesExtractor.extractLinux();
        } else if (osName.contains("mac")) {
            NativesExtractor.extractMacOS();
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            Main.main(args);
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            
            Toolkit.getDefaultToolkit().beep();
            
            JFrame dummyFrame = new JFrame("dummy frame");
            dummyFrame.setLocationRelativeTo(null);
            dummyFrame.setVisible(true);
            dummyFrame.toFront();
            dummyFrame.setVisible(false);
            
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            PrintStream messageStream = new PrintStream(byteArray);
            e.printStackTrace(messageStream);
            messageStream.flush();
            String message = new String(byteArray.toByteArray(), StandardCharsets.UTF_8);
            
            JOptionPane.showMessageDialog(
                    dummyFrame,
                    message,
                    "Game crashed!",
                    JOptionPane.ERROR_MESSAGE
            );
        }
        alcMakeContextCurrent(0);
        alcDestroyContext(SoundSystem.CONTEXT);
        alcCloseDevice(SoundSystem.DEVICE);
        glfwTerminate();
        System.exit(0);
    }

}
