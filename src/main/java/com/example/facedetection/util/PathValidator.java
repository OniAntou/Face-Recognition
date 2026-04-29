package com.example.facedetection.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for validating file paths to prevent path traversal attacks.
 * Ensures all file operations stay within allowed directories.
 */
public final class PathValidator {

    private static final Logger logger = LoggerFactory.getLogger(PathValidator.class);

    // Allowed file extensions for different operations
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "pbm", "pgm", "ppm", "tiff"
    ));

    private static final Set<String> ALLOWED_MODEL_EXTENSIONS = new HashSet<>(Arrays.asList(
            "onnx", "caffemodel", "prototxt", "xml"
    ));

    private static final Set<String> FORBIDDEN_PATHS = new HashSet<>(Arrays.asList(
            "..", "~", ".", "/", "\\", "\u0000"
    ));

    private PathValidator() {
        // Utility class
    }

    /**
     * Validates that a path does not contain path traversal sequences.
     *
     * @param path the path to validate
     * @return true if the path is safe, false otherwise
     */
    public static boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        // Check for null bytes
        if (path.contains("\u0000")) {
            logger.warn("Path contains null byte: {}", path);
            return false;
        }

        // Normalize the path
        String normalized = path.replace('/', File.separatorChar)
                                  .replace('\\', File.separatorChar);

        // Check for traversal attempts
        String[] parts = normalized.split(File.separator.equals("\\") ? "\\\\" : File.separator);
        for (String part : parts) {
            if (part.equals("..") || part.equals("...")) {
                logger.warn("Path traversal attempt detected: {}", path);
                return false;
            }
        }

        // Check for absolute paths that might be dangerous
        if (normalized.startsWith(File.separator) ||
            (normalized.length() > 1 && normalized.charAt(1) == ':')) {
            logger.debug("Absolute path detected: {}", path);
            // Absolute paths are allowed but logged for audit
        }

        return true;
    }

    /**
     * Validates that a file has an allowed extension.
     *
     * @param path the file path
     * @param allowedExtensions set of allowed extensions
     * @return true if the extension is allowed, false otherwise
     */
    public static boolean hasAllowedExtension(String path, Set<String> allowedExtensions) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1 || lastDot == path.length() - 1) {
            return false;
        }

        String extension = path.substring(lastDot + 1).toLowerCase();
        return allowedExtensions.contains(extension);
    }

    /**
     * Validates an image file path.
     *
     * @param path the image file path
     * @return true if valid image path, false otherwise
     */
    public static boolean isValidImagePath(String path) {
        return isValidPath(path) && hasAllowedExtension(path, ALLOWED_IMAGE_EXTENSIONS);
    }

    /**
     * Validates a model file path.
     *
     * @param path the model file path
     * @return true if valid model path, false otherwise
     */
    public static boolean isValidModelPath(String path) {
        return isValidPath(path) && hasAllowedExtension(path, ALLOWED_MODEL_EXTENSIONS);
    }

    /**
     * Sanitizes a filename by removing or replacing dangerous characters.
     *
     * @param filename the filename to sanitize
     * @return sanitized filename
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unnamed";
        }

        // Remove path separators and other dangerous characters
        String sanitized = filename
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\.{2,}", ".")
                .trim();

        // Limit length
        if (sanitized.length() > 255) {
            int lastDot = sanitized.lastIndexOf('.');
            if (lastDot > 0) {
                String ext = sanitized.substring(lastDot);
                sanitized = sanitized.substring(0, 255 - ext.length()) + ext;
            } else {
                sanitized = sanitized.substring(0, 255);
            }
        }

        // Ensure not empty after sanitization
        if (sanitized.isEmpty() || sanitized.equals(".")) {
            sanitized = "unnamed";
        }

        return sanitized;
    }

    /**
     * Resolves a path against a base directory and validates it stays within bounds.
     *
     * @param baseDir the base directory
     * @param userPath the user-provided relative path
     * @return resolved path if valid, null otherwise
     */
    public static Path resolveSafePath(String baseDir, String userPath) {
        if (baseDir == null || userPath == null) {
            return null;
        }

        if (!isValidPath(userPath)) {
            return null;
        }

        try {
            Path base = Paths.get(baseDir).toAbsolutePath().normalize();
            Path resolved = base.resolve(userPath).normalize();

            // Ensure the resolved path is within the base directory
            if (!resolved.startsWith(base)) {
                logger.warn("Path escapes base directory: {} -> {}", userPath, resolved);
                return null;
            }

            return resolved;
        } catch (Exception e) {
            logger.warn("Failed to resolve path: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validates that a path points to an existing file within allowed directories.
     *
     * @param path the path to check
     * @param allowedBaseDirs array of allowed base directories
     * @return true if file exists and is within allowed directory
     */
    public static boolean isFileInAllowedDirectory(String path, String... allowedBaseDirs) {
        if (path == null || allowedBaseDirs == null || allowedBaseDirs.length == 0) {
            return false;
        }

        File file = new File(path);
        if (!file.exists()) {
            return false;
        }

        try {
            String canonicalPath = file.getCanonicalPath();

            for (String baseDir : allowedBaseDirs) {
                File baseFile = new File(baseDir);
                String canonicalBase = baseFile.getCanonicalPath();

                if (canonicalPath.startsWith(canonicalBase)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to check file path: {}", e.getMessage());
        }

        return false;
    }
}
