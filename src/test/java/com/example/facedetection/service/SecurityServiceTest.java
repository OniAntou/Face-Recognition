package com.example.facedetection.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityServiceTest {

    @Test
    void verifiesOnlyAValidSha256Value() throws Exception {
        Path file = Files.createTempFile("security-test-", ".bin");
        Files.writeString(file, "face recognition update");
        try {
            SecurityService service = new SecurityService(path ->
                    new SecurityService.VerificationResult(true, "test signature", Optional.empty()));
            String checksum = service.calculateSha256(file.toString());

            assertTrue(service.verifyChecksum(file.toString(), checksum));
            assertTrue(service.verifyChecksum(file.toString(), "  " + checksum.toUpperCase() + "\n"));
            assertFalse(service.verifyChecksum(file.toString(), "not-a-checksum"));
            assertFalse(service.verifyChecksum(file.toString(), "0".repeat(64)));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void updaterRejectsInvalidSignatureEvenWhenChecksumMatches() throws Exception {
        Path file = Files.createTempFile("FaceRecognition_Setup_", ".exe");
        Files.write(file, new byte[10 * 1024 * 1024]);
        try {
            SecurityService service = new SecurityService(path ->
                    SecurityService.VerificationResult.invalid("unsigned test file"));
            UpdateService updates = new UpdateService(
                    com.example.facedetection.config.AppConfig.getInstance(), service, true);
            String checksum = service.calculateSha256(file.toString());

            assertFalse(updates.verifyDownloadedFile(file.toString(), checksum));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void updaterAcceptsOnlyChecksumAndSignatureTogether() throws Exception {
        Path file = Files.createTempFile("FaceRecognition_Setup_", ".exe");
        Files.write(file, new byte[10 * 1024 * 1024]);
        try {
            SecurityService service = new SecurityService(path ->
                    new SecurityService.VerificationResult(true, "signed test file", Optional.empty()));
            UpdateService updates = new UpdateService(
                    com.example.facedetection.config.AppConfig.getInstance(), service, true);
            String checksum = service.calculateSha256(file.toString());

            assertTrue(updates.verifyDownloadedFile(file.toString(), checksum));
            assertFalse(updates.verifyDownloadedFile(file.toString(), "0".repeat(64)));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void checksumOnlyModeAcceptsUnsignedExecutableWhenExplicitlyConfigured() throws Exception {
        Path file = Files.createTempFile("FaceRecognition_Setup_", ".exe");
        Files.write(file, new byte[10 * 1024 * 1024]);
        try {
            SecurityService service = new SecurityService(path ->
                    SecurityService.VerificationResult.invalid("unsigned test file"));
            UpdateService updates = new UpdateService(
                    com.example.facedetection.config.AppConfig.getInstance(), service);
            String checksum = service.calculateSha256(file.toString());

            assertTrue(updates.verifyDownloadedFile(file.toString(), checksum));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void realWindowsVerifierDoesNotAcceptAnUnsignedExecutable() throws Exception {
        Path file = Files.createTempFile("unsigned-update-", ".exe");
        Files.writeString(file, "not a signed executable");
        try {
            SecurityService.VerificationResult result = new SecurityService()
                    .verifyDigitalSignature(file.toString());
            assertFalse(result.isValid(), result.getMessage());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
