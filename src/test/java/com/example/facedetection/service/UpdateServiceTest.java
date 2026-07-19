package com.example.facedetection.service;

import com.example.facedetection.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {

    @Test
    void parsesExecutableAndChecksumAssetsFromReleaseJson() {
        UpdateService service = new UpdateService(AppConfig.getInstance(),
                new SecurityService(path -> SecurityService.VerificationResult.invalid("not used")));
        String body = "{\"assets\":["
                + "{\"name\":\"FaceRecognition_Setup.exe\",\"browser_download_url\":"
                + "\"https://github.com/OniAntou/Face-Recognition/releases/download/v1.2.4/FaceRecognition_Setup.exe\"},"
                + "{\"name\":\"checksums.sha256\",\"browser_download_url\":"
                + "\"https://github.com/OniAntou/Face-Recognition/releases/download/v1.2.4/checksums.sha256\"}]}";

        assertEquals(2, service.parseAssets(body).size());
        assertTrue(UpdateService.isChecksumAsset("checksums.sha256"));
        assertTrue(UpdateService.isChecksumAsset("FaceRecognition_Setup.exe.sha256.txt"));
    }

    @Test
    void selectsTheExecutableHashFromAHashFile() {
        UpdateService service = new UpdateService(AppConfig.getInstance(),
                new SecurityService(path -> SecurityService.VerificationResult.invalid("not used")));
        String expected = "a".repeat(64);
        String content = "b".repeat(64) + " another.exe\n"
                + expected + " *FaceRecognition_Setup.exe\n";

        assertEquals(Optional.of(expected),
                service.parseChecksum(content, "FaceRecognition_Setup.exe"));
    }

    @Test
    void allowsEnoughTimeToDownloadBundledInstaller() {
        assertTrue(AppConfig.getInstance().updateDownloadTimeoutSeconds >= 600,
                "The bundled installer must have enough time to download on a normal connection");
    }
}
