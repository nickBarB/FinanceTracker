package model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import service.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryTest {

    @Test
    void trimsName() {
        assertEquals("Еда", new Category("  Еда ").name());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsBlankName(String name) {
        assertThrows(ValidationException.class, () -> new Category(name));
    }

    @Test
    void keyIgnoresCase() {
        assertEquals(new Category("Еда").key(), new Category("еДА").key());
    }
}
