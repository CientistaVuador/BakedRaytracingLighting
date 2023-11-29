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
package cientistavuador.bakedlighting.natives;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author Cien
 */
public class NativesExtractor {
    
    public static void extractLinux() {
        try {
            extract("natives_linux.zip", "linux");
        } catch (IOException ex) {
            System.out.println("Failed to extract linux natives:");
            ex.printStackTrace(System.out);
        }
    }
    
    public static void extractMacOS() {
        try {
            extract("natives_macos.zip", "macos");
        } catch (IOException ex) {
            System.out.println("Failed to extract macos natives:");
            ex.printStackTrace(System.out);
        }
    }
    
    private static void extract(String zipFile, String osName) throws IOException {
        if (Files.isRegularFile(Path.of("natives", osName+".extracted"))) {
            System.out.println("Natives for "+osName+" already extracted.");
            return;
        }
        
        System.out.println("Extracting "+osName+" natives...");
        
        InputStream zipFileInput = NativesExtractor.class.getResourceAsStream(zipFile);
        if (zipFileInput == null) {
            System.out.println("Natives zip file: '"+zipFile+"' not found.");
            return;
        }
        
        Path nativesFolder = Path.of("natives");
        if (Files.notExists(nativesFolder)) {
            Files.createDirectory(nativesFolder);
        }
        
        try (ZipInputStream zipInput = new ZipInputStream(zipFileInput, StandardCharsets.UTF_8)) {
            byte[] buffer = new byte[8192];
            int bufferLength = 0;
            
            ZipEntry e;
            while ((e = zipInput.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                Files.deleteIfExists(Path.of("natives", e.getName()));
                try (FileOutputStream out = new FileOutputStream(Path.of("natives", e.getName()).toFile())) {
                    while ((bufferLength = zipInput.read(buffer)) != -1) {
                        out.write(buffer, 0, bufferLength);
                    }
                }
                System.out.println("Extracted "+e.getName());
            }
        }
        
        Path finishedFile = Path.of("natives", osName+".extracted");
        if (Files.notExists(finishedFile)) {
            Files.createFile(finishedFile);
        }
        
        System.out.println("Finished extracting natives.");
    }
    
    private NativesExtractor() {
        
    }
    
}
