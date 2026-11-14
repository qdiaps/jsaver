package io.github.qdiaps.jsaver.cli;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
public enum CliTool {
    YT_DLP("yt-dlp"),
    GALLERY_DL("gallery-dl"),
    FFMPEG("ffmpeg"),
    FFPROBE("ffprobe"),
    QUICKJS("qjs"),
    PYTHON3("python3");

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
            Path candidate = Paths.get(dir, binaryName);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static Map<CliTool, Boolean> checkAll() {
        Map<CliTool, Boolean> status = new EnumMap<>(CliTool.class);
        for (CliTool tool : values()) {
            boolean available = tool.isAvailable();
            status.put(tool, available);
            if (available) {
                log.info("CLI Tool '{}' is available in PATH.", tool.binaryName());
            } else {
                log.warn("CLI Tool '{}' is NOT found in PATH. Relevant commands might fail.", tool.binaryName());
            }
        }
        return status;
    }
}
