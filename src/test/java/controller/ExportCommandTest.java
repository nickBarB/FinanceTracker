package controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import service.ValidationException;
import model.Budget;
import model.Category;
import model.Operation;
import model.OperationType;
import service.FormatRegistry;
import libs.csv.CsvWalletFormat;
import libs.json.JsonCodec;
import repository.json.JsonWalletFormat;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportCommandTest {

    @TempDir
    Path tempDir;

    private final CommandFixture fixture = new CommandFixture();
    private final ExportCommand export = new ExportCommand(new FormatRegistry(
            List.of(new CsvWalletFormat(), new JsonWalletFormat(JsonCodec.create()))));

    private void fillWallet() {
        fixture.wallet.addCategory(new Category("Еда"));
        fixture.wallet.setBudget(new Budget("Еда", new BigDecimal("4000")));
        fixture.wallet.addOperation(new Operation(OperationType.EXPENSE, "Еда",
                new BigDecimal("300"), LocalDate.of(2020, 1, 1), "обед", null));
    }

    private String commandFor(Path file) {
        return "export \"" + file + "\"";
    }

    @Test
    void exportJsonCreatesSnapshotAndConfirms() throws IOException {
        fillWallet();
        Path file = tempDir.resolve("backup.json");

        fixture.run(export, commandFor(file));

        String content = Files.readString(file);
        assertTrue(content.contains("Еда"));
        assertTrue(content.contains("4000"));
        assertTrue(fixture.output().contains("Данные сохранены"));
    }

    @Test
    void exportCsvWritesHeaderAndOperations() throws IOException {
        fillWallet();
        Path file = tempDir.resolve("ops.csv");

        fixture.run(export, commandFor(file));

        String content = Files.readString(file);
        assertTrue(content.startsWith("date,type,category,amount,description,counterparty"));
        assertTrue(content.contains("2020-01-01,EXPENSE,Еда,300.00,обед,"));
    }

    @Test
    void existingFileIsNotOverwritten() throws IOException {
        Path file = tempDir.resolve("backup.json");
        Files.writeString(file, "old");

        ValidationException e = assertThrows(ValidationException.class,
                () -> fixture.run(export, commandFor(file)));

        assertTrue(e.getMessage().contains("уже существует"));
        assertEquals("old", Files.readString(file));
    }

    @Test
    void unknownExtensionIsRejectedWithoutCreatingFile() {
        Path file = tempDir.resolve("report.txt");

        ValidationException e = assertThrows(ValidationException.class,
                () -> fixture.run(export, commandFor(file)));

        assertTrue(e.getMessage().contains(".json"));
        assertFalse(Files.exists(file));
    }

    @Test
    void missingArgumentShowsUsage() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> fixture.run(export, "export"));

        assertTrue(e.getMessage().contains("export <файл"));
    }

    @Test
    void exportDoesNotChangeData() {
        assertFalse(export.changesData());
    }
}
