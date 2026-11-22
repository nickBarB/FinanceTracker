package controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import libs.storage.StorageException;
import service.ValidationException;
import service.NotificationService;
import service.StatisticsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportCommandTest {

    @TempDir
    Path tempDir;

    private final CommandFixture fixture = new CommandFixture();
    private final StatisticsService statistics = new StatisticsService();
    private final ReportCommand command =
            new ReportCommand(statistics, new NotificationService(statistics));

    @BeforeEach
    void setUp() {
        support.FinancialFixtures.populate(fixture.wallet);
    }

    @Test
    void reportForSelectedCategoriesAndPeriod() {
        fixture.run(command,
                "report --categories \"Еда,Такси\" --from 2020-02-01 --to 2020-02-28");

        String out = fixture.output();
        assertTrue(out.contains("Категории: Еда, Такси"));
        assertTrue(out.contains("Общие расходы: 2,000.00"));
    }

    @Test
    void unknownCategoriesAreAllListed() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> fixture.run(command, "report --categories \"Кино,Театр\""));

        assertTrue(e.getMessage().contains("Кино"));
        assertTrue(e.getMessage().contains("Театр"));
    }

    @Test
    void trailingCommaInCategoriesIsRejected() {
        assertThrows(ValidationException.class,
                () -> fixture.run(command, "report --categories \"Еда,\""));
    }

    @Test
    void emptyPeriodSaysNothingFound() {
        fixture.run(command, "report --from 2021-01-01");

        assertTrue(fixture.output().contains("не найдено"));
    }

    @Test
    void outOptionWritesReportToFileInsteadOfScreen() throws IOException {
        Path file = tempDir.resolve("r.txt");

        fixture.run(command, "report --out \"" + file + "\"");

        assertTrue(Files.readString(file).contains("Общий доход: 63,000.00"));
        assertTrue(fixture.output().contains("Сохранено в файл"));
        assertFalse(fixture.output().contains("Общий доход"));
    }

    @Test
    void outOptionRefusesToOverwriteExistingFile() throws IOException {
        Path file = tempDir.resolve("r.txt");
        Files.writeString(file, "старое");

        ValidationException e = assertThrows(ValidationException.class,
                () -> fixture.run(command, "report --out \"" + file + "\""));

        assertTrue(e.getMessage().contains("уже существует"));
        assertEquals("старое", Files.readString(file));
    }

    @Test
    void outToMissingFolderGivesStorageError() {
        Path file = tempDir.resolve("no").resolve("such").resolve("r.txt");

        assertThrows(StorageException.class,
                () -> fixture.run(command, "report --out \"" + file + "\""));
    }
}
