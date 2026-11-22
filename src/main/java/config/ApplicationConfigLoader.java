package config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ApplicationConfigLoader {
    public ApplicationConfig load() {
        return load(Path.of("config/app.properties"));
    }

    public ApplicationConfig load(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return new ApplicationConfig(Path.of(required(properties, "storage.file")),
                Path.of(required(properties, "logs.directory")),
                Integer.parseInt(required(properties, "password.iterations")));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Не удалось загрузить конфигурацию " + file + ": " + e.getMessage(), e);
        }
    }

    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Не задан параметр: " + key);
        }
        return value.strip();
    }
}
