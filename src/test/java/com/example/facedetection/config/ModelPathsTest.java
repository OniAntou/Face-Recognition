package com.example.facedetection.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelPathsTest {

    @Test
    void resolvesTheBundledCheckoutLayout() {
        ModelPaths paths = ModelPaths.resolve();

        assertTrue(paths.hasYolo(), paths::describeMissingFiles);
        assertTrue(paths.hasSsd(), paths::describeMissingFiles);
        assertTrue(paths.hasGender(), paths::describeMissingFiles);
        assertTrue(paths.hasHaar(), paths::describeMissingFiles);
        assertTrue(paths.missingFiles().isEmpty(), paths::describeMissingFiles);
    }

    @Test
    void reportsMissingFilesForAnIncompleteLayout() throws Exception {
        Path data = Files.createTempDirectory("face-models-");
        ModelPaths paths = ModelPaths.forDataDirectory(data);

        assertFalse(paths.hasAnyFaceDetector());
        assertEquals(6, paths.missingFiles().size());
    }

    @Test
    void acceptsACompleteExplicitLayout() throws Exception {
        Path data = Files.createTempDirectory("face-models-");
        Files.createDirectories(data.resolve("models/face"));
        Files.createDirectories(data.resolve("models/gender"));
        Files.createDirectories(data.resolve("haarcascade"));

        for (Path file : new Path[]{
                data.resolve("models/face/yolov8n-face.onnx"),
                data.resolve("models/face/res10_300x300_ssd_iter_140000.caffemodel"),
                data.resolve("models/face/deploy.prototxt"),
                data.resolve("models/gender/gender_net.caffemodel"),
                data.resolve("models/gender/gender_deploy.prototxt"),
                data.resolve("haarcascade/haarcascade_frontalface_default.xml")
        }) {
            Files.writeString(file, "test");
        }

        ModelPaths paths = ModelPaths.forDataDirectory(data);
        assertTrue(paths.hasAnyFaceDetector());
        assertTrue(paths.missingFiles().isEmpty());
    }
}
