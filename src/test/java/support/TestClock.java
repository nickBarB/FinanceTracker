package support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class TestClock {
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
    public static final LocalDate TODAY = LocalDate.now(CLOCK);

    private TestClock() {
    }
}
