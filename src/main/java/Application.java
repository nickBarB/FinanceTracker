

import libs.json.JsonCodec;
import repository.json.JsonStorage;
import config.ApplicationConfig;
import java.time.Clock;
import controller.AddCategoryCommand;
import controller.AddOperationCommand;
import controller.CategoriesCommand;
import controller.CommandLoop;
import controller.CommandRegistry;
import controller.ExitCommand;
import controller.HelpCommand;
import controller.HistoryCommand;
import controller.LoginCommand;
import controller.LogoutCommand;
import controller.RegisterCommand;
import controller.RemoveBudgetCommand;
import controller.RemoveCategoryCommand;
import controller.RenameCategoryCommand;
import controller.ReportCommand;
import controller.Session;
import controller.SetBudgetCommand;
import controller.Terminal;
import controller.TransferCommand;
import model.OperationType;
import repository.UserRepository;
import repository.WalletRepository;
import service.AuthService;
import service.BudgetService;
import service.CategoryService;
import service.NotificationService;
import service.OperationService;
import service.StatisticsService;
import service.TransferService;
import libs.security.Pbkdf2PasswordHasher;
import repository.json.JsonUserRepository;
import repository.json.JsonWalletRepository;
import controller.ExportCommand;
import controller.ImportCommand;
import service.FormatRegistry;
import service.ImportService;
import libs.csv.CsvWalletFormat;
import repository.json.JsonWalletFormat;
import java.util.List;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

public class Application {

    private final ApplicationConfig config;
    private final Clock clock;

    public Application(Path dataDir, int passwordIterations) {
        this(new ApplicationConfig(dataDir.resolve("finance.json"), Path.of("logs"), passwordIterations),
            Clock.systemDefaultZone());
    }

    public Application(ApplicationConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    public void run(InputStream in, OutputStream out) {
        try (JsonStorage storage = new JsonStorage(config.storageFile())) {
            run(in, out, storage);
        }
    }

    private void run(InputStream in, OutputStream out, JsonStorage storage) {
        var mapper = JsonCodec.create();
        UserRepository users = new JsonUserRepository(storage);
        WalletRepository wallets = new JsonWalletRepository(storage);

        StatisticsService statistics = new StatisticsService();
        NotificationService notifications = new NotificationService(statistics);
        AuthService auth = new AuthService(users, new Pbkdf2PasswordHasher(config.passwordIterations()));
        CategoryService categories = new CategoryService();
        BudgetService budgets = new BudgetService();
        OperationService operations = new OperationService(clock);
        TransferService transfers = new TransferService(users, wallets, storage, clock);
        FormatRegistry formats =
                new FormatRegistry(List.of(new CsvWalletFormat(clock), new JsonWalletFormat(mapper, clock)));
        ImportService importService = new ImportService(clock);

        CommandRegistry registry = new CommandRegistry();
        registry.register(new HelpCommand(registry));
        registry.register(new RegisterCommand(auth, wallets));
        registry.register(new LoginCommand(auth, wallets, notifications));
        registry.register(new LogoutCommand(wallets));
        registry.register(new CategoriesCommand());
        registry.register(new AddCategoryCommand(categories));
        registry.register(new RenameCategoryCommand(categories));
        registry.register(new RemoveCategoryCommand(categories));
        registry.register(new SetBudgetCommand(budgets));
        registry.register(new RemoveBudgetCommand(budgets));
        registry.register(new AddOperationCommand(OperationType.INCOME, operations, notifications));
        registry.register(new AddOperationCommand(OperationType.EXPENSE, operations, notifications));
        registry.register(new TransferCommand(transfers, notifications));
        registry.register(new ReportCommand(statistics, notifications));
        registry.register(new HistoryCommand(statistics));
        registry.register(new ExportCommand(formats));
        registry.register(new ImportCommand(formats, importService));
        registry.register(new ExitCommand());

        new CommandLoop(Terminal.forStreams(in, out), new Session(), registry, wallets, storage).run();
    }
}
