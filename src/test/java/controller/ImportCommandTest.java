package controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import service.ValidationException;
import service.FormatRegistry;
import service.ImportService;
import libs.csv.CsvWalletFormat;
import libs.json.JsonCodec;
import repository.json.JsonWalletFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportCommandTest {

    private static final String HEADER = "date,type,category,amount,description,counterparty\n";

    @TempDir
    Path tempDir;

    private final CommandFixture fixture = new CommandFixture();
    private final JsonWalletFormat json = new JsonWalletFormat(JsonCodec.create(), support.TestClock.CLOCK);
    private final ImportCommand importer = new ImportCommand(
            new FormatRegistry(List.of(new CsvWalletFormat(support.TestClock.CLOCK), json)), new ImportService(support.TestClock.CLOCK));

    private String commandFor(Path file) {
        return "import \"" + file + "\"";
    }

    @Test
    void invalidFileLeavesWalletUnchanged() throws IOException {
        Path file = tempDir.resolve("ops.csv");
        Files.writeString(file, HEADER
                + "2020-01-05,EXPENSE,Еда,300,,\n"
                + "bad,EXPENSE,Еда,1,,\n");

        ValidationException e = assertThrows(ValidationException.class,
                () -> fixture.run(importer, commandFor(file)));

        assertTrue(e.getMessage().contains("Строка 3"));
        assertTrue(fixture.wallet.getOperations().isEmpty());
        assertTrue(fixture.wallet.getCategories().isEmpty());
    }

    @Test
    void missingFileIsReported() {
        Path file = tempDir.resolve("missing.csv");

        ValidationException e = assertThrows(ValidationException.class,
                () -> fixture.run(importer, commandFor(file)));

        assertTrue(e.getMessage().contains("Файл не найден"));
    }
}
