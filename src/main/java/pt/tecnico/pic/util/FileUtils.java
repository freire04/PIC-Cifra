package pt.tecnico.pic.util;

import java.io.File;

public final class FileUtils {

    // Construtor privado para impedir instanciação
    private FileUtils() {}

    public static String suggestEncryptedFileName(File inputFile) {
        if (inputFile == null) return "";
        
        String name = inputFile.getName();
        int dotIndex = name.lastIndexOf('.');
        
        // Extrai o nome base (sem a extensão original)
        String baseName = (dotIndex == -1) ? name : name.substring(0, dotIndex);
        
        // Força a extensão a ser SEMPRE .enc
        return baseName + ".enc";
    }

    public static String suggestDecryptedFileName(File inputFile) {
        if (inputFile == null) return "";
        
        String name = inputFile.getName();
        // Se terminar em .enc, removemos apenas essa extensão
        if (name.endsWith(".enc")) {
            return name.substring(0, name.length() - 4);
        }
        
        // Fallback caso metam um ficheiro sem .enc
        return name + ".dec";
    }

    public static File getDefaultDirectory() {
        String home = System.getProperty("user.home");
        File downloads = new File(home, "Downloads");
        return downloads.exists() ? downloads : new File(home);
    }

    public static String formatSize(long bytes) {
        if (bytes < 0) return "0 B"; // Salvaguarda para bytes negativos inválidos
        if (bytes < 1024) return bytes + " B";
        
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.2f KB", kb);
        
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.2f MB", mb);
        
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }
}