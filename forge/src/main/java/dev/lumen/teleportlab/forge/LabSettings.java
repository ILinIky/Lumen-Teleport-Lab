package dev.lumen.teleportlab.forge;

import dev.lumen.teleportlab.core.LabConfig;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Explicit local configuration. Recording is never persisted as enabled. */
final class LabSettings {
    static LabConfig load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("lab.properties");
        if (!Files.exists(file)) {
            Files.writeString(file, """
                    # Restart Minecraft to apply. Recording is always OFF at launch.
                    observation_ms=10000
                    checkpoints_ms=1000,3000,5000,10000
                    target_tolerance_blocks=1.5
                    stationary_tolerance_blocks=0.10
                    stable_ms=1000
                    max_sample_gap_ms=500
                    max_attempts=500
                    """);
        }
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(file)) { p.load(reader); }
        return new LabConfig(Long.parseLong(p.getProperty("observation_ms", "10000")),
                Arrays.stream(p.getProperty("checkpoints_ms", "1000,3000,5000,10000").split(","))
                        .map(String::trim).map(Long::parseLong).toList(),
                Double.parseDouble(p.getProperty("target_tolerance_blocks", "1.5")),
                Double.parseDouble(p.getProperty("stationary_tolerance_blocks", "0.10")),
                Long.parseLong(p.getProperty("stable_ms", "1000")),
                Long.parseLong(p.getProperty("max_sample_gap_ms", "500")),
                Integer.parseInt(p.getProperty("max_attempts", "500")));
    }
}
