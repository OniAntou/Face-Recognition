package com.example.facedetection.service;

import com.example.facedetection.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Verifies update integrity and authenticity.
 *
 * <p>Checksum verification is platform independent. Authenticode verification
 * is delegated to Windows PowerShell and fails closed on other platforms or
 * when the verifier cannot produce a valid result.</p>
 */
public class SecurityService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityService.class);
    private static final String SHA256_ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;
    private static final long SIGNATURE_TIMEOUT_SECONDS = 10;

    private final String trustedSignerSubject;
    private final String trustedSignerThumbprint;
    private final DigitalSignatureVerifier signatureVerifier;

    public SecurityService() {
        this(AppConfig.getInstance(), null);
    }

    public SecurityService(AppConfig config) {
        this(config, null);
    }

    /**
     * Constructor for deterministic tests and callers that provide a platform
     * signature verifier.
     */
    public SecurityService(DigitalSignatureVerifier signatureVerifier) {
        this(AppConfig.getInstance(), signatureVerifier);
    }

    public SecurityService(AppConfig config, DigitalSignatureVerifier signatureVerifier) {
        this.trustedSignerSubject = config.updateTrustedSignerSubject == null
                ? "" : config.updateTrustedSignerSubject.trim();
        this.trustedSignerThumbprint = normalizeThumbprint(config.updateTrustedSignerThumbprint);
        this.signatureVerifier = signatureVerifier != null
                ? signatureVerifier : this::verifyWithPowerShell;
    }

    /**
     * Verifies a file's SHA-256 checksum. Only a normalized 64-character
     * hexadecimal SHA-256 value is accepted.
     */
    public boolean verifyChecksum(String filePath, String expectedHash) {
        if (filePath == null || expectedHash == null) {
            logger.warn("Cannot verify null file path or hash");
            return false;
        }

        String normalizedExpected = expectedHash.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        if (!normalizedExpected.matches("[0-9a-f]{64}")) {
            logger.warn("Rejected malformed SHA-256 checksum for {}", filePath);
            return false;
        }

        String actualHash = calculateSha256(filePath);
        if (actualHash == null) {
            return false;
        }

        boolean matches = MessageDigest.isEqual(
                normalizedExpected.getBytes(StandardCharsets.US_ASCII),
                actualHash.getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            logger.warn("Checksum mismatch for {}: expected={}, actual={}",
                    filePath, normalizedExpected, actualHash);
        } else {
            logger.info("Checksum verified for {}", filePath);
        }
        return matches;
    }

    /** Calculates a file's lowercase SHA-256 hash, or {@code null} on failure. */
    public String calculateSha256(String filePath) {
        if (filePath == null) {
            return null;
        }

        final Path path;
        try {
            path = Path.of(filePath);
        } catch (InvalidPathException e) {
            logger.warn("Invalid file path for hashing: {}", e.getMessage());
            return null;
        }

        if (!Files.isRegularFile(path)) {
            logger.warn("File does not exist: {}", filePath);
            return null;
        }

        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            return null;
        } catch (IOException e) {
            logger.error("Failed to read file for hashing: {}", e.getMessage());
            return null;
        }
    }

    /** Calculates SHA-256 for an in-memory payload. */
    public String calculateSha256(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(SHA256_ALGORITHM).digest(data));
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            return null;
        }
    }

    /**
     * Verifies a downloaded executable's Authenticode signature.
     */
    public VerificationResult verifyDigitalSignature(String filePath) {
        try {
            if (filePath == null || !Files.isRegularFile(Path.of(filePath))) {
                return VerificationResult.invalid("File not found");
            }
        } catch (InvalidPathException e) {
            return VerificationResult.invalid("Invalid file path");
        }
        if (!filePath.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            return VerificationResult.invalid("Not an executable file");
        }
        return signatureVerifier.verify(filePath);
    }

    /**
     * Verifies a file with checksum and optionally deletes it on failure.
     */
    public boolean verifyDownloadedFile(String filePath, String expectedHash, boolean deleteOnFailure) {
        boolean valid = verifyChecksum(filePath, expectedHash);
        if (!valid && deleteOnFailure && filePath != null) {
            try {
                Files.deleteIfExists(Path.of(filePath));
                logger.info("Deleted unverified file: {}", filePath);
            } catch (IOException | InvalidPathException e) {
                logger.error("Failed to delete unverified file: {}", e.getMessage());
            }
        }
        return valid;
    }

    private VerificationResult verifyWithPowerShell(String filePath) {
        if (!isWindows()) {
            return VerificationResult.invalid("Authenticode verification is only available on Windows");
        }

        String script = "$s = Get-AuthenticodeSignature -LiteralPath $env:FACE_UPDATE_PATH; "
                + "if ($null -eq $s) { Write-Output 'STATUS=Missing'; exit 1 }; "
                + "if ($s.Status -ne 'Valid') { Write-Output ('STATUS=' + $s.Status); exit 1 }; "
                + "$subject = if ($null -ne $s.SignerCertificate) {$s.SignerCertificate.Subject} else {''}; "
                + "$thumb = if ($null -ne $s.SignerCertificate) {$s.SignerCertificate.Thumbprint} else {''}; "
                + "Write-Output 'STATUS=Valid'; Write-Output ('SUBJECT=' + $subject); "
                + "Write-Output ('THUMBPRINT=' + $thumb)";

        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
            builder.redirectErrorStream(true);
            builder.environment().put("FACE_UPDATE_PATH", filePath);

            Process process = builder.start();
            boolean finished = process.waitFor(SIGNATURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return VerificationResult.invalid("Authenticode verifier timed out");
            }

            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            String status = outputValue(output, "STATUS");
            if (process.exitValue() != 0 || !"Valid".equalsIgnoreCase(status)) {
                return VerificationResult.invalid("Authenticode status: "
                        + (status.isBlank() ? "unknown" : status));
            }

            String subject = outputValue(output, "SUBJECT");
            String thumbprint = normalizeThumbprint(outputValue(output, "THUMBPRINT"));
            if (!trustedSignerSubject.isBlank()
                    && !subject.toLowerCase(Locale.ROOT).contains(trustedSignerSubject.toLowerCase(Locale.ROOT))) {
                return VerificationResult.invalid("Signer subject is not trusted: " + subject);
            }
            if (!trustedSignerThumbprint.isBlank()
                    && !trustedSignerThumbprint.equalsIgnoreCase(thumbprint)) {
                return VerificationResult.invalid("Signer thumbprint is not trusted");
            }

            return new VerificationResult(true, "Authenticode signature is valid",
                    Optional.of("Subject=" + subject + "; Thumbprint=" + thumbprint));
        } catch (IOException e) {
            return VerificationResult.invalid("Could not start Authenticode verifier: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VerificationResult.invalid("Authenticode verification interrupted");
        }
    }

    private static String outputValue(String output, String key) {
        for (String line : output.split("\\R")) {
            if (line.startsWith(key + "=")) {
                return line.substring(key.length() + 1).trim();
            }
        }
        return "";
    }

    private static String normalizeThumbprint(String value) {
        return value == null ? "" : value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @FunctionalInterface
    public interface DigitalSignatureVerifier {
        VerificationResult verify(String filePath);
    }

    /** Result of checksum/signature verification. */
    public static final class VerificationResult {
        private final boolean valid;
        private final String message;
        private final Optional<String> certificateInfo;

        public VerificationResult(boolean valid, String message, Optional<String> certificateInfo) {
            this.valid = valid;
            this.message = message;
            this.certificateInfo = certificateInfo == null ? Optional.empty() : certificateInfo;
        }

        public static VerificationResult invalid(String message) {
            return new VerificationResult(false, message, Optional.empty());
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
