package com.pacific.marketplace;

import com.pacific.marketplace.security.AttemptLimiter;
import com.pacific.marketplace.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The rules for slowing down password guessing, with a clock the test moves by hand. */
class AttemptLimiterTest {

    /** A clock that only moves when told to. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final TestClock clock = new TestClock();
    private final AttemptLimiter limiter = new AttemptLimiter(clock, 3, Duration.ofMinutes(15), "Too many tries.");

    @Test
    void allowsTheConfiguredNumberOfFailuresThenBlocksWithATimeToWait() {
        for (int i = 0; i < 3; i++) {
            limiter.check("a");
            limiter.hit("a");
        }
        assertThatThrownBy(() -> limiter.check("a")).isInstanceOf(ApiException.class)
                .hasMessage("Too many tries. Please try again in 15 minutes.");
        clock.advance(Duration.ofMinutes(14).plusSeconds(1));
        assertThatThrownBy(() -> limiter.check("a")).hasMessageContaining("1 minute."); // singular
    }

    @Test
    void recoversWhenTheWindowEndsAndLaterFailuresDoNotExtendIt() {
        for (int i = 0; i < 3; i++) limiter.hit("a");
        clock.advance(Duration.ofMinutes(10));
        limiter.hit("a"); // still blocked: this must not push the end of the block back
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThatCode(() -> limiter.check("a")).doesNotThrowAnyException();
    }

    @Test
    void keysAreIndependentAndSuccessClearsTheCount() {
        for (int i = 0; i < 3; i++) limiter.hit("a");
        assertThatCode(() -> limiter.check("b")).doesNotThrowAnyException();
        limiter.clear("a");
        assertThatCode(() -> limiter.check("a")).doesNotThrowAnyException();
        limiter.hit("a");
        limiter.hit("a");
        assertThatCode(() -> limiter.check("a")).doesNotThrowAnyException(); // two since the clear, not five
    }

    @Test
    void failuresOutsideTheWindowDontCount() {
        limiter.hit("a");
        limiter.hit("a");
        clock.advance(Duration.ofMinutes(16));
        limiter.hit("a"); // a fresh window: this is the first failure again
        limiter.hit("a");
        assertThatCode(() -> limiter.check("a")).doesNotThrowAnyException();
        limiter.hit("a");
        assertThat(catchStatus()).isEqualTo(429);
    }

    private int catchStatus() {
        try {
            limiter.check("a");
            return 200;
        } catch (ApiException e) {
            return e.getStatus().value();
        }
    }
}
