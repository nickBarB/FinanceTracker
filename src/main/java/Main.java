import config.ApplicationConfig;
import config.ApplicationConfigLoader;
import libs.logging.DailyFileHandler;
import libs.storage.StorageException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import java.time.Clock;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        try {
            ApplicationConfig config = new ApplicationConfigLoader().load();
            DailyFileHandler.configure(config.logsDirectory());
            LOG.info("Запуск FinanceTracker");
            new Application(config, Clock.systemDefaultZone()).run(System.in, out);
            LOG.info("FinanceTracker завершён");
        } catch (IOException | IllegalStateException | IllegalArgumentException | StorageException e) {
            LOG.log(Level.SEVERE, "Ошибка запуска FinanceTracker", e);
            out.println("Не удалось запустить FinanceTracker: " + e.getMessage());
            System.exit(1);
        }
    }
}
