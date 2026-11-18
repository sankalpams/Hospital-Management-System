package lk.hms.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Test clock. Starts at Monday 2030-06-03 08:30 Asia/Colombo (03:00 UTC) so slot, check-in and
 * no-show rules are deterministic; tests may move it with {@link #set} / {@link #advance}.
 */
public class MutableClock extends Clock {

    public static final Instant START = Instant.parse("2030-06-03T03:00:00Z");

    private volatile Instant now = START;

    public void set(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration d) {
        this.now = now.plus(d);
    }

    public void reset() {
        this.now = START;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock outer = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId z) {
                return outer.withZone(z);
            }

            @Override
            public Instant instant() {
                return outer.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        return now;
    }
}
