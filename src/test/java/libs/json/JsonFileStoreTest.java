package libs.json;

import static org.junit.jupiter.api.Assertions.*;

import libs.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

class JsonFileStoreTest {
    @TempDir
    Path directory;

    static final class State {
        double value;
    }

    @Test
    void serializationFailurePreservesOriginalFileAndReleasesTransaction() throws Exception {
        Path file = directory.resolve("state.json");
        try (var store = new JsonFileStore<>(file, JsonCodec.create(), State.class, State::new, state -> { })) {
            // disableJdkUnsafe requires an accessible constructor for ordinary classes.
            store.update(state -> {
                state.value = 10;
                return null;
            });
            String before = Files.readString(file);
            assertThrows(StorageException.class, () -> store.update(state -> {
                state.value = Double.NaN;
                return null;
            }));
            assertEquals(before, Files.readString(file));
            assertEquals(10.0, store.read(state -> state.value).doubleValue());
            try (var paths = Files.list(directory)) {
                assertFalse(paths.anyMatch(path -> path.toString().endsWith(".tmp")));
            }
        }
    }

    @Test
    void closedStoreRejectsFurtherAccess() {
        var store = new JsonFileStore<>(directory.resolve("state.json"), JsonCodec.create(), State.class,
            State::new, state -> { });
        store.close();
        assertThrows(IllegalStateException.class, () -> store.read(state -> state.value));
    }
}
