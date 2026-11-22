package config;

import java.nio.file.Path;
import java.util.Objects;

public record ApplicationConfig(Path storageFile, Path logsDirectory, int passwordIterations) {
    public ApplicationConfig {
        Objects.requireNonNull(storageFile, "storageFile");
        Objects.requireNonNull(logsDirectory, "logsDirectory");
        if (storageFile.toString().isBlank() || !storageFile.toString().endsWith(".json")) {
            throw new IllegalArgumentException("storage.file должен указывать на JSON-файл");
        }
        if (logsDirectory.toString().isBlank()) {
            throw new IllegalArgumentException("logs.directory не должен быть пустым");
        }
        if (passwordIterations < 1000 || passwordIterations > 10_000_000) {
            throw new IllegalArgumentException("password.iterations должен быть от 1000 до 10000000");
        }
    }
}
