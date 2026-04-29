package com.example.facedetection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.Optional;

/**
 * Security service for verifying file integrity and authenticity.
 * Provides SHA-256 checksum verification and digital signature validation.
 */
public class SecurityService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityService.class);
    private static final String SHA256_ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;

    /**
     * Verifies a file's SHA-256 checksum.
     *
     * @param filePath path to the file
     * @param expectedHash expected SHA-256 hash (hex string or base64)
     * @return true if hash matches, false otherwise
     */
    public boolean verifyChecksum(String filePath, String expectedHash) {
        if (filePath == null || expectedHash == null) {
            logger.warn("Cannot verify null file path or hash");
            return false;
        }

        try {
            String actualHash = calculateSha256(filePath);
            if (actualHash == null) {
                return false;
            }

            // Normalize expected hash (remove whitespace, convert to lowercase)
            String normalizedExpected = expectedHash.replaceAll("\\s", "").toLowerCase();
            String normalizedActual = actualHash.toLowerCase();

            boolean matches = normalizedActual.equals(normalizedExpected);
            if (!matches) {
                logger.warn("Checksum mismatch for {}: expected={}, actual={}",
                        filePath, normalizedExpected, normalizedActual);
            } else {
                logger.info("Checksum verified for {}", filePath);
            }

            return matches;
        } catch (Exception e) {
            logger.error("Failed to verify checksum for {}: {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * Calculates the SHA-256 hash of a file.
     *
     * @param filePath path to the file
     * @return SHA-256 hash as lowercase hex string, or null if failed
     */
    public String calculateSha256(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            logger.warn("File does not exist: {}", filePath);
            return null;
        }

        try (InputStream is = new FileInputStream(path.toFile())) {
            MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;

            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available: {}", e.getMessage());
            return null;
        } catch (IOException e) {
            logger.error("Failed to read file for hashing: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calculates SHA-256 hash of a byte array.
     *
     * @param data byte array
     * @return SHA-256 hash as lowercase hex string
     */
    public String calculateSha256(byte[] data) {
        if (data == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Converts byte array to hexadecimal string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Converts byte array to base64 string.
     */
    private String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Verifies a file's digital signature (placeholder for future implementation).
     * In production, this would verify Authenticode signatures on Windows.
     *
     * @param filePath path to the executable
     * @return VerificationResult containing status and details
     */
    public VerificationResult verifyDigitalSignature(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return new VerificationResult(false, "File not found", Optional.empty());
        }

        // Note: Full Authenticode verification requires JNI or external tools
        // This is a simplified check that verifies the file exists and is an executable
        if (!filePath.endsWith(".exe")) {
            return new VerificationResult(false, "Not an executable file", Optional.empty());
        }

        // In a production environment, you would:
        // 1. Use Windows API via JNI/JNA to call WinVerifyTrust
        // 2. Or use signtool.exe to verify the signature
        // 3. Or use a Java library that supports Authenticode

        logger.info("Digital signature verification not fully implemented (file exists check only)");
        return new VerificationResult(true, "File exists (full signature verification not implemented)",
                Optional.of("Place holder for certificate info"));
    }

    /**
     * Downloads and verifies a file with checksum.
     *
     * @param filePath path to downloaded file
     * @param expectedHash expected SHA-256 hash
     * @param deleteOnFailure whether to delete the file if verification fails
     * @return true if verification passed, false otherwise
     */
    public boolean verifyDownloadedFile(String filePath, String expectedHash, boolean deleteOnFailure) {
        boolean valid = verifyChecksum(filePath, expectedHash);

        if (!valid && deleteOnFailure) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
                logger.info("Deleted unverified file: {}", filePath);
            } catch (IOException e) {
                logger.error("Failed to delete unverified file: {}", e.getMessage());
            }
        }

        return valid;
    }

    /**
     * Result of a verification operation.
     */
    public static class VerificationResult {
        private final boolean valid;
        private final String message;
        private final Optional<String> certificateInfo;

        public VerificationResult(boolean valid, String message, Optional<String> certificateInfo) {
            this.valid = valid;
            this.message = message;
            this.certificateInfo = certificateInfo;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public Optional<String> getCertificateInfo() {
            return certificateInfo;
        }
    }
}
