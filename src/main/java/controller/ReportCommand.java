package controller;

import model.Wallet;
import service.Notification;
import service.NotificationService;
import service.OperationFilter;
import service.StatisticsService;
import service.Summary;
import java.util.List;

public class ReportCommand implements Command {

    private final StatisticsService statistics;
    private final NotificationService notifications;

    public ReportCommand(StatisticsService statistics, NotificationService notifications) {
        this.statistics = statistics;
        this.notifications = notifications;
    }

    @Override
    public String name() {
        return "report";
    }

    @Override
    public String usage() {
        return "report [--categories \"Еда,Такси\"] [--from ГГГГ-ММ-ДД] [--to ГГГГ-ММ-ДД]"
                + " [--out файл]";
    }

    @Override
    public String description() {
        return "Доходы, расходы, категории и остаток бюджета; можно выбрать категории и период "
                + "и сохранить отчёт в файл";
    }

    @Override
    public String example() {
        return "report --categories \"Еда,Такси\" --from 2026-11-01 --to 2026-11-30"
                + " --out report.txt";
    }

    @Override
    public void execute(CommandLine line, Session session, Terminal terminal) {
        line.check(0, 0, ReportOptions.ALLOWED, usage());
        Wallet wallet = session.wallet();
        OperationFilter filter = ReportOptions.filter(line);
        Summary summary = statistics.summarize(wallet, filter);
        List<Notification> alerts = notifications.currentState(wallet);
        String text = ReportFormatter.format(session.user().login(), filter, summary, alerts);
        ReportOptions.deliver(line, text, terminal);
    }
}
