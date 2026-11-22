package model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import service.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsBlankLogin(String login) {
        assertThrows(ValidationException.class, () -> new User(login, "hash"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsBlankHash(String hash) {
        assertThrows(ValidationException.class, () -> new User("anna", hash));
    }

    @Test
    void normalizeLoginTrimsAndLowercases() {
        assertEquals("anna", User.normalizeLogin("  Anna "));
    }

    @Test
    void normalizeLoginTurnsNullIntoEmpty() {
        assertEquals("", User.normalizeLogin(null));
    }
}
