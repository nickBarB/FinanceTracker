package model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import service.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BudgetTest {

    @Test
    void createsBudgetWithTwoDecimals() {
        Budget budget = new Budget(" Еда ", new BigDecimal("4000"));

        assertEquals("Еда", budget.category());
        assertEquals(new BigDecimal("4000.00"), budget.limit());
    }

    @Test
    void rejectsZeroLimit() {
        assertThrows(ValidationException.class,
                () -> new Budget("Еда", BigDecimal.ZERO));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsBlankCategory(String category) {
        assertThrows(ValidationException.class,
                () -> new Budget(category, BigDecimal.TEN));
    }
}
