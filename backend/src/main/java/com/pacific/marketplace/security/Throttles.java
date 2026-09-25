package com.pacific.marketplace.security;

import com.pacific.marketplace.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The shop's limits on guessing passwords and on asking for reset emails. */
@Component
public class Throttles {

    /** Wrong passwords per account and address. */
    public final AttemptLimiter login;
    /** Wrong passwords per address, across accounts (someone trying many accounts). */
    public final AttemptLimiter loginByAddress;
    /** Password reset emails asked for, per address. */
    public final AttemptLimiter resetRequests;
    /** Email confirmation links asked for again, per account. */
    public final AttemptLimiter verifyRequests;
    /** Messages sent, per account (a brake on spam, far above what a conversation needs). */
    public final AttemptLimiter messages;
    /** Wrong current password when changing it, per account. */
    public final AttemptLimiter passwordChange;

    public Throttles(AppProperties props) {
        AppProperties.Security s = props.security();
        Clock clock = Clock.systemUTC();
        Duration window = Duration.ofMinutes(s.loginWindowMinutes());
        this.login = new AttemptLimiter(clock, s.loginMaxFailures(), window, "Too many failed sign-in attempts.");
        this.loginByAddress = new AttemptLimiter(clock, s.ipMaxFailures(), window, "Too many failed sign-in attempts from your network.");
        this.resetRequests = new AttemptLimiter(clock, 10, Duration.ofHours(1), "Too many password reset requests.");
        this.verifyRequests = new AttemptLimiter(clock, 10, Duration.ofHours(1), "Too many confirmation emails asked for.");
        this.messages = new AttemptLimiter(clock, 60, Duration.ofHours(1), "You've sent a lot of messages.");
        this.passwordChange = new AttemptLimiter(clock, s.loginMaxFailures(), window, "Too many wrong passwords.");
    }

    @Scheduled(fixedDelay = 300_000)
    void purge() {
        login.purge();
        loginByAddress.purge();
        resetRequests.purge();
        verifyRequests.purge();
        messages.purge();
        passwordChange.purge();
    }
}
