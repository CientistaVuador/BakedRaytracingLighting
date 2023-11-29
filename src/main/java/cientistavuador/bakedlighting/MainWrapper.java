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
import java.awt.Toolkit;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.openal.ALC11.*;

/**
 *
 * @author Cien
 */
public class MainWrapper {

    static {
        System.out.println("  /$$$$$$  /$$        /$$$$$$  /$$");
        System.out.println(" /$$__  $$| $$       /$$__  $$| $$");
        System.out.println("| $$  \\ $$| $$      | $$  \\ $$| $$");
        System.out.println("| $$  | $$| $$      | $$$$$$$$| $$");
        System.out.println("| $$  | $$| $$      | $$__  $$|__/");
        System.out.println("| $$  | $$| $$      | $$  | $$    ");
        System.out.println("|  $$$$$$/| $$$$$$$$| $$  | $$ /$$");
        System.out.println(" \\______/ |________/|__/  |__/|__/");
        
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            Logger.getLogger(MainWrapper.class.getName()).log(Level.WARNING, "Native Look and Feel not supported.", ex);
        }

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
            //GLPool.destroy();
            //glfwTerminate();

            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            PrintStream messageStream = new PrintStream(byteArray);
            e.printStackTrace(messageStream);
            messageStream.flush();
            
            Toolkit.getDefaultToolkit().beep();
            String message = new String(byteArray.toByteArray(), StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(
                    null,
                    message,
                    "Game has crashed!",
                    JOptionPane.ERROR_MESSAGE
            );
            throw e;
        }
        alcMakeContextCurrent(0);
        alcDestroyContext(SoundSystem.CONTEXT);
        alcCloseDevice(SoundSystem.DEVICE);
        glfwTerminate();
        System.exit(0);
    }

}
