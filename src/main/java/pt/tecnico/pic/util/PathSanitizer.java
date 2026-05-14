package pt.tecnico.pic.util;

public final class PathSanitizer {

    private PathSanitizer() {
    }

    public static String toFileName(String path) {
        if (path == null) {
            return null;
        }

        String normalized = path.trim();
        if (normalized.isBlank()) {
            return null;
        }

        normalized = normalized.replace("\\", "/");

        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        int lastSlash = normalized.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;

        return fileName.isBlank() ? null : fileName;
    }
}
