package com.example.facedetection.config;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves the bundled model layout for both a development checkout and a
 * jpackage/Inno Setup installation.
 */
public final class ModelPaths {

    private static final String FACE_DIRECTORY = "models" + java.io.File.separator + "face";
    private static final String GENDER_DIRECTORY = "models" + java.io.File.separator + "gender";

    private final Path dataDirectory;
    private final Path yoloModel;
    private final Path ssdModel;
    private final Path ssdConfig;
    private final Path genderModel;
    private final Path genderConfig;
    private final Path haarCascade;

    private ModelPaths(Path dataDirectory) {
        this.dataDirectory = normalize(dataDirectory);
        this.yoloModel = this.dataDirectory.resolve(FACE_DIRECTORY).resolve("yolov8n-face.onnx");
        this.ssdModel = this.dataDirectory.resolve(FACE_DIRECTORY)
                .resolve("res10_300x300_ssd_iter_140000.caffemodel");
        this.ssdConfig = this.dataDirectory.resolve(FACE_DIRECTORY).resolve("deploy.prototxt");
        this.genderModel = this.dataDirectory.resolve(GENDER_DIRECTORY).resolve("gender_net.caffemodel");
        this.genderConfig = this.dataDirectory.resolve(GENDER_DIRECTORY).resolve("gender_deploy.prototxt");
        this.haarCascade = this.dataDirectory.resolve("haarcascade")
                .resolve("haarcascade_frontalface_default.xml");
    }

    /**
     * Resolves model data relative to the current process and code-source
     * locations. The first directory containing the complete bundled layout is
     * preferred; an existing incomplete directory is retained for diagnostics.
     */
    public static ModelPaths resolve() {
        Set<Path> candidates = new LinkedHashSet<>();
        Path workingDirectory = Paths.get(System.getProperty("user.dir", "."));
        addCandidates(candidates, workingDirectory);
        addCodeSourceCandidates(candidates);

        Path firstExisting = null;
        for (Path candidate : candidates) {
            if (hasCompleteLayout(candidate)) {
                return new ModelPaths(candidate);
            }
            if (firstExisting == null && Files.isDirectory(candidate)) {
                firstExisting = candidate;
            }
        }

        return new ModelPaths(firstExisting != null ? firstExisting : workingDirectory.resolve("data"));
    }

    /**
     * Creates a resolver rooted at an explicit data directory. This is useful
     * for tests and callers that already own the installation root.
     */
    public static ModelPaths forDataDirectory(Path dataDirectory) {
        return new ModelPaths(Objects.requireNonNull(dataDirectory, "dataDirectory"));
    }

    private static void addCandidates(Set<Path> candidates, Path root) {
        Path normalizedRoot = normalize(root);
        candidates.add(normalizedRoot.resolve("data"));
        candidates.add(normalizedRoot.resolve("app").resolve("data"));

        Path ancestor = normalizedRoot;
        for (int i = 0; i < 5 && ancestor != null; i++) {
            candidates.add(ancestor.resolve("data"));
            candidates.add(ancestor.resolve("app").resolve("data"));
            ancestor = ancestor.getParent();
        }
    }

    private static void addCodeSourceCandidates(Set<Path> candidates) {
        try {
            URI location = ModelPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path codeSource = Paths.get(location);
            if (Files.isRegularFile(codeSource)) {
                codeSource = codeSource.getParent();
            }
            addCandidates(candidates, codeSource);
        } catch (Exception ignored) {
            // The working-directory candidates still provide a useful result.
        }
    }

    private static boolean hasCompleteLayout(Path dataDirectory) {
        return Files.isRegularFile(dataDirectory.resolve(FACE_DIRECTORY).resolve("yolov8n-face.onnx"))
                && Files.isRegularFile(dataDirectory.resolve(FACE_DIRECTORY)
                .resolve("res10_300x300_ssd_iter_140000.caffemodel"))
                && Files.isRegularFile(dataDirectory.resolve(FACE_DIRECTORY).resolve("deploy.prototxt"))
                && Files.isRegularFile(dataDirectory.resolve(GENDER_DIRECTORY).resolve("gender_net.caffemodel"))
                && Files.isRegularFile(dataDirectory.resolve(GENDER_DIRECTORY).resolve("gender_deploy.prototxt"))
                && Files.isRegularFile(dataDirectory.resolve("haarcascade")
                .resolve("haarcascade_frontalface_default.xml"));
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public Path yoloModel() {
        return yoloModel;
    }

    public Path ssdModel() {
        return ssdModel;
    }

    public Path ssdConfig() {
        return ssdConfig;
    }

    public Path genderModel() {
        return genderModel;
    }

    public Path genderConfig() {
        return genderConfig;
    }

    public Path haarCascade() {
        return haarCascade;
    }

    public boolean hasYolo() {
        return Files.isRegularFile(yoloModel);
    }

    public boolean hasSsd() {
        return Files.isRegularFile(ssdModel) && Files.isRegularFile(ssdConfig);
    }

    public boolean hasGender() {
        return Files.isRegularFile(genderModel) && Files.isRegularFile(genderConfig);
    }

    public boolean hasHaar() {
        return Files.isRegularFile(haarCascade);
    }

    public boolean hasAnyFaceDetector() {
        return hasYolo() || hasSsd() || hasHaar();
    }

    /**
     * Returns every missing bundled asset in stable order for diagnostics.
     */
    public List<Path> missingFiles() {
        List<Path> missing = new ArrayList<>();
        for (Path path : List.of(yoloModel, ssdModel, ssdConfig, genderModel, genderConfig, haarCascade)) {
            if (!Files.isRegularFile(path)) {
                missing.add(path);
            }
        }
        return Collections.unmodifiableList(missing);
    }

    public String describeMissingFiles() {
        if (missingFiles().isEmpty()) {
            return "All bundled model files are available";
        }
        return "Missing model files: " + missingFiles();
    }
}
