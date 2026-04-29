package com.example.facedetection.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PathValidator utility.
 */
class PathValidatorTest {

    @Test
    void acceptsValidImagePaths() {
        assertTrue(PathValidator.isValidImagePath("image.jpg"));
        assertTrue(PathValidator.isValidImagePath("image.jpeg"));
        assertTrue(PathValidator.isValidImagePath("image.png"));
        assertTrue(PathValidator.isValidImagePath("data/image.jpg"));
        assertTrue(PathValidator.isValidImagePath("data\\image.jpg"));
    }

    @Test
    void rejectsPathTraversal() {
        assertFalse(PathValidator.isValidPath("../etc/passwd"));
        assertFalse(PathValidator.isValidPath("..\\windows\\system32"));
        assertFalse(PathValidator.isValidPath(".../etc/passwd"));
        assertFalse(PathValidator.isValidPath("image/../../../etc/passwd"));
    }

    @Test
    void rejectsNullBytes() {
        assertFalse(PathValidator.isValidPath("image.jpg\u0000"));
        assertFalse(PathValidator.isValidPath("image\u0000.jpg"));
    }

    @Test
    void rejectsInvalidExtensions() {
        assertFalse(PathValidator.isValidImagePath("script.exe"));
        assertFalse(PathValidator.isValidImagePath("file.bat"));
        assertFalse(PathValidator.isValidImagePath("document.pdf"));
    }

    @Test
    void acceptsValidModelPaths() {
        assertTrue(PathValidator.isValidModelPath("model.onnx"));
        assertTrue(PathValidator.isValidModelPath("model.caffemodel"));
        assertTrue(PathValidator.isValidModelPath("config.prototxt"));
        assertTrue(PathValidator.isValidModelPath("haarcascade.xml"));
    }

    @Test
    void acceptsAbsolutePaths() {
        assertTrue(PathValidator.isValidPath("C:/data/image.jpg"));
        assertTrue(PathValidator.isValidPath("/usr/share/image.png"));
    }

    @Test
    void rejectsEmptyAndNullPaths() {
        assertFalse(PathValidator.isValidPath(null));
        assertFalse(PathValidator.isValidPath(""));
    }

    @Test
    void sanitizesFilenames() {
        assertEquals("image_file.jpg", PathValidator.sanitizeFilename("image/file.jpg"));
        assertEquals("image_file.jpg", PathValidator.sanitizeFilename("image:file.jpg"));
        assertEquals("image_file.jpg", PathValidator.sanitizeFilename("image*file.jpg"));
        assertEquals("image____file.jpg", PathValidator.sanitizeFilename("image<>?file.jpg"));
    }

    @Test
    void sanitizeReplacesEmptyWithUnnamed() {
        assertEquals("unnamed", PathValidator.sanitizeFilename(""));
        assertEquals("unnamed", PathValidator.sanitizeFilename("."));
        assertEquals("unnamed", PathValidator.sanitizeFilename("..."));
    }

    @Test
    void resolveSafePathWithinBounds() {
        Path resolved = PathValidator.resolveSafePath("/data", "image.jpg");
        assertNotNull(resolved);
        assertTrue(resolved.toString().contains("image.jpg"));
    }

    @Test
    void resolveSafePathRejectsTraversal() {
        Path resolved = PathValidator.resolveSafePath("/data", "../etc/passwd");
        assertNull(resolved);
    }

    @Test
    void resolveSafePathRejectsEscape() {
        Path resolved = PathValidator.resolveSafePath("/data", "image/../../../etc/passwd");
        assertNull(resolved);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "normal.jpg",
        "CamelCase.PNG",
        "file-with-dash.bmp",
        "file_with_underscore.webp"
    })
    void acceptsVariousValidFilenames(String filename) {
        assertTrue(PathValidator.isValidImagePath(filename));
    }
}
