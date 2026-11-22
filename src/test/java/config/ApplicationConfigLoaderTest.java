package config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationConfigLoaderTest {
    @TempDir
    Path directory;

    @Test
    void loadsRealExternalConfiguration() {
        ApplicationConfig config = new ApplicationConfigLoader().load();
        assertEquals(Path.of("data/finance.json"), config.storageFile());
        assertEquals(Path.of("logs"), config.logsDirectory());
        assertEquals(600000, config.passwordIterations());
    }

    @Test
    void missingParameterNamesTheKey() throws Exception {
        Path file = directory.resolve("app.properties");
        Files.writeString(file, "storage.file=data/finance.json\nlogs.directory=logs\n");
        assertTrue(assertThrows(IllegalStateException.class, () -> new ApplicationConfigLoader().load(file))
            .getMessage().contains("password.iterations"));
    }

    @Test
    void rejectsInvalidPathsAndIterations() {
        assertThrows(IllegalArgumentException.class, () -> new ApplicationConfig(Path.of("data.txt"), Path.of("logs"), 1000));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationConfig(Path.of("data.json"), Path.of(""), 1000));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationConfig(Path.of("data.json"), Path.of("logs"), 0));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationConfig(Path.of("data.json"), Path.of("logs"), 10000001));
    }

    @Test
    void missingFileFailsExplicitly() {
        assertThrows(IllegalStateException.class, () -> new ApplicationConfigLoader().load(directory.resolve("missing")));
    }
}
