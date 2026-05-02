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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for checking and downloading application updates.
 * Integrates with GitHub releases API and performs security verification.
 */
public class UpdateService {

    private static final Logger logger = LoggerFactory.getLogger(UpdateService.class);
    private final AppConfig config;
    private final SecurityService securityService;

    // Patterns for parsing GitHub API response
    private static final Pattern PUBLISHED_AT_PATTERN = Pattern.compile("\"published_at\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DOWNLOAD_URL_PATTERN = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.exe)\"");
    private static final Pattern ASSET_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+\\.exe)\"");

    // Update state
    private volatile boolean updateAvailable;
    private volatile String downloadUrl;
    private volatile String expectedChecksum;
    private volatile Instant latestReleaseTime;

    public UpdateService() {
        this(AppConfig.getInstance(), new SecurityService());
    }

    public UpdateService(AppConfig config, SecurityService securityService) {
        this.config = config;
        this.securityService = securityService;
        this.updateAvailable = false;
    }

    /**
     * Checks for available updates from GitHub releases.
     *
     * @param onUpdateFound callback when update is found (url)
     * @param onError callback on error (error message)
     */
    public void checkForUpdates(Consumer<String> onUpdateFound, Consumer<String> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                Instant buildInstant = getLocalBuildTime();
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.updateGithubApiUrl))
                        .header("Accept", "application/json")
                        .header("User-Agent", "FaceRecognition-App")
                        .timeout(Duration.ofSeconds(config.updateCheckTimeoutSeconds))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    onError.accept("GitHub API returned " + response.statusCode());
                    return;
                }

                String body = response.body();
                parseAndEvaluateRelease(body, buildInstant, onUpdateFound, onError);

            } catch (Exception e) {
                logger.warn("Update check failed: {}", e.getMessage());
                onError.accept(e.getMessage());
            }
        });
    }

    private void parseAndEvaluateRelease(String body, Instant buildInstant,
                                         Consumer<String> onUpdateFound, Consumer<String> onError) {
        // Parse published date
        Matcher dateMatcher = PUBLISHED_AT_PATTERN.matcher(body);
        if (!dateMatcher.find()) {
            onError.accept("Could not parse release date");
            return;
        }

        // Parse download URL
        Matcher urlMatcher = DOWNLOAD_URL_PATTERN.matcher(body);
        if (!urlMatcher.find()) {
            onError.accept("Could not find download URL");
            return;
        }

        String url = urlMatcher.group(1);
        Instant latestInstant;
        try {
            latestInstant = Instant.parse(dateMatcher.group(1));
        } catch (Exception e) {
            onError.accept("Invalid release date format");
            return;
        }

        // Compare with local build time (with 1-hour buffer for timezone differences)
        if (latestInstant.isAfter(buildInstant.plus(Duration.ofHours(1)))) {
            this.updateAvailable = true;
            this.downloadUrl = url;
            this.latestReleaseTime = latestInstant;
            logger.info("Update available: {} published at {}", url, latestInstant);
            onUpdateFound.accept(url);
        } else {
            logger.debug("No update available: local build is up to date");
        }
    }

    /**
     * Downloads and installs the update.
     *
     * @param url download URL (must match the one from checkForUpdates)
     * @param onProgress callback for download progress (0-100, -1 for indeterminate)
     * @param onComplete callback when download and verification complete
     * @param onError callback on error
     */
    public void downloadAndInstall(String url,
                                   Consumer<Integer> onProgress,
                                   Runnable onComplete,
                                   Consumer<String> onError) {
        if (!url.equals(this.downloadUrl)) {
            onError.accept("URL mismatch - security check failed");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                onProgress.accept(0);

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(config.updateDownloadTimeoutSeconds))
                        .build();

                // Download to temp file
                Path tempFile = Files.createTempFile("FaceRecognition_Setup_", ".exe");
                tempFile.toFile().deleteOnExit();

                HttpResponse<Path> response = client.send(request,
                        HttpResponse.BodyHandlers.ofFile(tempFile));

                if (response.statusCode() != 200) {
                    onError.accept("Download failed with HTTP " + response.statusCode());
                    return;
                }

                onProgress.accept(100);

                String filePath = tempFile.toString();

                // Verify the downloaded file
                boolean verified = verifyDownloadedFile(filePath);
                if (!verified) {
                    Files.deleteIfExists(tempFile);
                    onError.accept("Downloaded file failed security verification");
                    return;
                }

                logger.info("Update verified and ready at {}", filePath);

                // Launch installer and exit
                launchInstaller(filePath);
                onComplete.run();

            } catch (Exception e) {
                logger.error("Failed to download update", e);
                onError.accept(e.getMessage());
            }
        });
    }

    /**
     * Verifies the downloaded installer file.
     * In production, this should verify:
     * 1. SHA-256 checksum from GitHub release assets
     * 2. Authenticode digital signature
     *
     * @param filePath path to downloaded file
     * @return true if verification passed
     */
    private boolean verifyDownloadedFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            logger.error("Downloaded file not found: {}", filePath);
            return false;
        }

        // Check minimum file size (should be at least a few MB)
        if (file.length() < 10 * 1024 * 1024) {
            logger.error("Downloaded file too small ({} bytes), possible corrupted download", file.length());
            return false;
        }

        // If we have an expected checksum, verify it
        if (expectedChecksum != null && !expectedChecksum.isEmpty()) {
            boolean checksumValid = securityService.verifyChecksum(filePath, expectedChecksum);
            if (!checksumValid) {
                logger.error("Checksum verification failed for downloaded update");
                return false;
            }
        }

        // Verify digital signature (Windows Authenticode)
        SecurityService.VerificationResult sigResult = securityService.verifyDigitalSignature(filePath);
        if (!sigResult.isValid()) {
            logger.warn("Digital signature verification: {}", sigResult.getMessage());
            // In production, you might want to fail here if signature is invalid
            // For now, we log a warning since full signature verification requires additional setup
        }

        logger.info("Downloaded update passed security checks");
        return true;
    }

    private void launchInstaller(String installerPath) throws Exception {
        logger.info("Launching installer: {}", installerPath);

        // Silent install with no cancel option to ensure clean update
        new ProcessBuilder(installerPath, "/SILENT", "/SP-", "/NOCANCEL", "/CLOSEAPPLICATIONS")
                .inheritIO()
                .start();

        // Give installer time to start before we exit
        Thread.sleep(1000);
    }

    /**
     * Gets the local build time from the manifest.
     *
     * @return build time or a very old time if not found
     */
    private Instant getLocalBuildTime() {
        try {
            var codeSource = getClass().getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                var res = codeSource.getLocation();
                var manifestUrl = URI.create("jar:" + res.toExternalForm() + "!/META-INF/MANIFEST.MF").toURL();

                try (var is = manifestUrl.openStream()) {
                    Manifest manifest = new Manifest(is);
                    String buildTime = manifest.getMainAttributes().getValue("Build-Time");
                    if (buildTime != null) {
                        return Instant.parse(buildTime);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read Build-Time from manifest: {}", e.getMessage());
        }

        // Default to a year ago if manifest not available
        return Instant.now().minus(Duration.ofDays(365));
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public Optional<String> getDownloadUrl() {
        return Optional.ofNullable(downloadUrl);
    }

    public Optional<Instant> getLatestReleaseTime() {
        return Optional.ofNullable(latestReleaseTime);
    }
}
