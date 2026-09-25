package com.pacific.marketplace.notify;

import com.pacific.marketplace.domain.SentEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no mail server is configured (development, tests): nothing leaves the machine. The email is still
 * recorded, so it can be read in the admin area. Only the recipient and subject are logged, not the body, which has
 * customers' addresses in it. The exceptions are password reset and email confirmation links, logged in
 * development (never in production) so those flows can be tried without a mail server.
 */
public class LogMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LogMailer.class);

    private final boolean production;

    public LogMailer(boolean production) {
        this.production = production;
    }

    @Override
    public SentEmail.Status send(Email email) {
        log.info("Email not sent (no MAIL_HOST): to={} subject=\"{}\"", email.to(), email.subject());
        if (!production && "PASSWORD_RESET".equals(email.kind())) {
            log.warn("DEVELOPMENT ONLY, password reset for {}:\n{}", email.to(), email.text());
        }
        if (!production && "VERIFY_EMAIL".equals(email.kind())) {
            log.warn("DEVELOPMENT ONLY, email confirmation for {}:\n{}", email.to(), email.text());
        }
        return SentEmail.Status.LOGGED;
    }
}
