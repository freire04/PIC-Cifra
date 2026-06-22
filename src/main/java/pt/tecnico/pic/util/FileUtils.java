package pt.tecnico.pic.util;

import java.io.File;
import java.util.Locale;

public final class FileUtils {

    private FileUtils() {}

    public static String suggestEncryptedFileName(File inputFile) {
        if (inputFile == null) return "";

        return inputFile.getName() + ".enc";
    }

    public static String suggestDecryptedFileName(File inputFile) {
        if (inputFile == null) return "";

        String name = inputFile.getName();
        if (name.toLowerCase(Locale.ROOT).endsWith(".enc")) {
            return name.substring(0, name.length() - 4);
        }

        return name + ".dec";
    }

    public static File getDefaultDirectory() {
        String home = System.getProperty("user.home");
        File downloads = new File(home, "Downloads");
        return downloads.exists() ? downloads : new File(home);
    }

    public static String formatSize(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";

        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.2f KB", kb);
        }

        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.2f MB", mb);
        }

        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, "%.2f GB", gb);
    }
}