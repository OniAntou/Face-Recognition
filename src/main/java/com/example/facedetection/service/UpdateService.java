package com.example.facedetection.service;

import com.example.facedetection.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitHub releases and installs only an executable whose checksum has
 * been verified. Authenticode verification is configurable for environments
 * that cannot publish a signed installer.
 */
public class UpdateService {

    private static final Logger logger = LoggerFactory.getLogger(UpdateService.class);
    private static final Pattern PUBLISHED_AT_PATTERN = Pattern.compile(
            "\"published_at\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSET_PATTERN = Pattern.compile(
            "\"name\"\\s*:\\s*\"([^\"]+)\"(?:(?!\"name\"\\s*:).)*?"
                    + "\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
    private static final Pattern CHECKSUM_PATTERN = Pattern.compile(
            "(?i)\\b([0-9a-f]{64})\\b(?:\\s+\\*?([^\\s]+))?");

    private final AppConfig config;
    private final SecurityService securityService;
    private final boolean requireAuthenticode;

    private volatile boolean updateAvailable;
    private volatile String downloadUrl;
    private volatile String expectedChecksum;
    private volatile Instant latestReleaseTime;

    public UpdateService() {
        this(AppConfig.getInstance(), new SecurityService());
    }

    public UpdateService(AppConfig config, SecurityService securityService) {
        this(config, securityService, config.updateRequireAuthenticode);
    }

    UpdateService(AppConfig config, SecurityService securityService, boolean requireAuthenticode) {
        this.config = config;
        this.securityService = securityService;
        this.requireAuthenticode = requireAuthenticode;
    }

    /** Checks the configured GitHub release endpoint asynchronously. */
    public void checkForUpdates(Consumer<String> onUpdateFound, Consumer<String> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(requireHttpsUri(config.updateGithubApiUrl))
                        .header("Accept", "application/json")
                        .header("User-Agent", "FaceRecognition-App")
                        .timeout(Duration.ofSeconds(config.updateCheckTimeoutSeconds))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    onError.accept("GitHub API returned " + response.statusCode());
                    return;
                }

                parseAndEvaluateRelease(client, response.body(), getLocalBuildTime(), onUpdateFound, onError);
            } catch (Exception e) {
                logger.warn("Update check failed: {}", e.getMessage());
                onError.accept(e.getMessage() == null ? "Update check failed" : e.getMessage());
            }
        });
    }

    private void parseAndEvaluateRelease(HttpClient client, String body, Instant buildInstant,
                                         Consumer<String> onUpdateFound, Consumer<String> onError)
            throws Exception {
        Matcher dateMatcher = PUBLISHED_AT_PATTERN.matcher(body);
        if (!dateMatcher.find()) {
            onError.accept("Could not parse release date");
            return;
        }

        Instant latestInstant;
        try {
            latestInstant = Instant.parse(dateMatcher.group(1));
        } catch (Exception e) {
            onError.accept("Invalid release date format");
            return;
        }

        List<ReleaseAsset> assets = parseAssets(body);
        Optional<ReleaseAsset> installer = assets.stream()
                .filter(asset -> asset.name().toLowerCase(Locale.ROOT).endsWith(".exe"))
                .findFirst();
        Optional<ReleaseAsset> checksumAsset = assets.stream()
                .filter(asset -> isChecksumAsset(asset.name()))
                .findFirst();

        if (installer.isEmpty() || checksumAsset.isEmpty()) {
            if (latestInstant.isAfter(buildInstant.plus(Duration.ofHours(1)))) {
                onError.accept("Latest release has no executable and matching SHA-256 asset");
            }
            return;
        }

        if (!latestInstant.isAfter(buildInstant.plus(Duration.ofHours(1)))) {
            logger.debug("No update available: local build is up to date");
            return;
        }

        String checksumBody = downloadChecksum(client, checksumAsset.get().url());
        Optional<String> checksum = parseChecksum(checksumBody, installer.get().name());
        if (checksum.isEmpty()) {
            onError.accept("Latest release checksum does not contain the executable hash");
            return;
        }

        this.updateAvailable = true;
        this.downloadUrl = requireHttpsUri(installer.get().url()).toString();
        this.expectedChecksum = checksum.get();
        this.latestReleaseTime = latestInstant;
        logger.info("Update available: {} published at {}", downloadUrl, latestInstant);
        onUpdateFound.accept(downloadUrl);
    }

    List<ReleaseAsset> parseAssets(String body) {
        List<ReleaseAsset> assets = new ArrayList<>();
        Matcher matcher = ASSET_PATTERN.matcher(body);
        while (matcher.find()) {
            assets.add(new ReleaseAsset(matcher.group(1), matcher.group(2)));
        }
        return assets;
    }

    private String downloadChecksum(HttpClient client, String checksumUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(requireHttpsUri(checksumUrl))
                .header("Accept", "text/plain, application/octet-stream")
                .header("User-Agent", "FaceRecognition-App")
                .timeout(Duration.ofSeconds(config.updateCheckTimeoutSeconds))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Checksum asset returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    Optional<String> parseChecksum(String content, String executableName) {
        if (content == null) {
            return Optional.empty();
        }

        String fallback = null;
        for (String line : content.split("\\R")) {
            Matcher matcher = CHECKSUM_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }

            String hash = matcher.group(1).toLowerCase(Locale.ROOT);
            String listedName = matcher.group(2);
            if (listedName == null || listedName.isBlank()) {
                fallback = hash;
            } else if (listedName.equalsIgnoreCase(executableName)
                    || listedName.endsWith("/" + executableName)
                    || listedName.endsWith("\\" + executableName)) {
                return Optional.of(hash);
            }
        }
        return Optional.ofNullable(fallback);
    }

    static boolean isChecksumAsset(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".sha256") || lower.endsWith(".sha256.txt")
                || lower.contains("checksum") || lower.contains("checksums");
    }

    /**
     * Downloads and verifies an update. Installer launch occurs only after all
     * verification steps succeed.
     */
    public void downloadAndInstall(String url,
                                   Consumer<Integer> onProgress,
                                   Runnable onComplete,
                                   Consumer<String> onError) {
        if (url == null || !url.equals(this.downloadUrl)) {
            onError.accept("URL mismatch - security check failed");
            return;
        }
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            onError.accept("No release checksum is available");
            return;
        }

        CompletableFuture.runAsync(() -> {
            Path tempFile = null;
            try {
                onProgress.accept(0);
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(requireHttpsUri(url))
                        .timeout(Duration.ofSeconds(config.updateDownloadTimeoutSeconds))
                        .build();

                tempFile = Files.createTempFile("FaceRecognition_Setup_", ".exe");
                tempFile.toFile().deleteOnExit();
                HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
                if (response.statusCode() != 200) {
                    Files.deleteIfExists(tempFile);
                    onError.accept("Download failed with HTTP " + response.statusCode());
                    return;
                }
                onProgress.accept(100);

                if (!verifyDownloadedFile(tempFile.toString(), expectedChecksum)) {
                    Files.deleteIfExists(tempFile);
                    onError.accept("Downloaded file failed security verification");
                    return;
                }

                logger.info("Update verified and ready at {}", tempFile);
                launchInstaller(tempFile.toString());
                onComplete.run();
            } catch (Exception e) {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (Exception ignored) {
                        // Preserve the original download/verification error.
                    }
                }
                logger.error("Failed to download update", e);
                onError.accept(e.getMessage() == null ? "Update installation failed" : e.getMessage());
            }
        });
    }

    /**
     * Verifies a downloaded executable without launching it. Kept public for
     * updater diagnostics and deterministic tests.
     */
    public boolean verifyDownloadedFile(String filePath, String checksum) {
        if (filePath == null || checksum == null || checksum.isBlank()) {
            return false;
        }

        File file = new File(filePath);
        if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) {
            logger.error("Downloaded update is not an executable file: {}", filePath);
            return false;
        }
        if (file.length() < 10 * 1024 * 1024) {
            logger.error("Downloaded file too small ({} bytes)", file.length());
            return false;
        }
        if (!securityService.verifyChecksum(filePath, checksum)) {
            logger.error("Checksum verification failed for downloaded update");
            return false;
        }

        if (requireAuthenticode) {
            SecurityService.VerificationResult signature = securityService.verifyDigitalSignature(filePath);
            if (!signature.isValid()) {
                logger.error("Digital signature verification failed: {}", signature.getMessage());
                return false;
            }
        } else {
            logger.warn("Authenticode verification is disabled by update configuration");
        }
        return true;
    }

    private void launchInstaller(String installerPath) throws Exception {
        logger.info("Launching verified installer: {}", installerPath);
        new ProcessBuilder(installerPath, "/SILENT", "/SP-", "/CLOSEAPPLICATIONS")
                .inheritIO()
                .start();
        Thread.sleep(1000);
    }

    private Instant getLocalBuildTime() {
        try {
            var manifestUrl = getClass().getResource("/META-INF/MANIFEST.MF");
            if (manifestUrl != null) {
                try (var input = manifestUrl.openStream()) {
                    Manifest manifest = new Manifest(input);
                    String buildTime = manifest.getMainAttributes().getValue("Build-Time");
                    if (buildTime != null) {
                        return Instant.parse(buildTime);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read Build-Time from manifest: {}", e.getMessage());
        }
        return Instant.now().minus(Duration.ofDays(365));
    }

    private static URI requireHttpsUri(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Update URL must use HTTPS: " + value);
        }
        return uri;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public Optional<String> getDownloadUrl() {
        return Optional.ofNullable(downloadUrl);
    }

    public Optional<String> getExpectedChecksum() {
        return Optional.ofNullable(expectedChecksum);
    }

    public Optional<Instant> getLatestReleaseTime() {
        return Optional.ofNullable(latestReleaseTime);
    }

    private record ReleaseAsset(String name, String url) {
    }
}
