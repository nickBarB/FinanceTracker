package model;

import service.ValidationException;
import java.util.Locale;

public record User(String login, String passwordHash) {

    public User {
        if (login == null || login.isBlank()) {
            throw new ValidationException("Логин не указан.");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new ValidationException("Хеш пароля не указан.");
        }
        login = login.trim();
    }

    public static String normalizeLogin(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }
}
