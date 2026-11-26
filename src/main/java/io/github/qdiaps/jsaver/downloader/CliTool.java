package io.github.qdiaps.jsaver.downloader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum CliTool {
    YT_DLP("yt-dlp"),
    GALLERY_DL("gallery-dl"),
    FFMPEG("ffmpeg"),
    FFPROBE("ffprobe"),
    QUICKJS("qjs"),
    CURL_CFFI("curl-cffi");

    private final String binaryName;

    CliTool(String binaryName) {
        this.binaryName = binaryName;
    }

    public String binaryName() {
        return binaryName;
    }

    public boolean isAvailable() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return false;
        }

        for (String dir : pathEnv.split(File.pathSeparator)) {
            Path candidate = Path.of(dir, binaryName);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static boolean verifyAllAvailable() {
        List<String> missing = new ArrayList<>();

        for (var tool : values()) {
            if (tool.isAvailable()) {
                log.debug("CLI tool verified in PATH: tool={}", tool.binaryName);
            } else {
                missing.add(tool.binaryName);
                log.error("Required CLI tool not found in PATH: tool={}", tool.binaryName);
            }
        }

        if (!missing.isEmpty())
            throw new IllegalStateException("Missing required CLI tools in PATH: " + String.join(", ", missing));

        log.info("All {} required CLI tools are available in PATH", values().length);
        return false;
    }
}
