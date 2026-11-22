package controller;

import service.Notification;
import java.util.List;

final class NotificationPrinter {

    private NotificationPrinter() {
    }

    static void show(Terminal terminal, List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        terminal.println("Оповещения:");
        for (Notification notification : notifications) {
            terminal.println("  [!] " + notification.message());
        }
    }
}
