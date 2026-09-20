package com.pacific.marketplace.notify;

import com.pacific.marketplace.domain.SentEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no mail server is configured (development, tests): nothing leaves the machine. The email is still
 * recorded, so it can be read in the admin area. Only the recipient and subject are logged, not the body, which has
 * customers' addresses in it.
 */
public class LogMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LogMailer.class);

    @Override
    public SentEmail.Status send(Email email) {
        log.info("Email not sent (no MAIL_HOST): to={} subject=\"{}\"", email.to(), email.subject());
        return SentEmail.Status.LOGGED;
    }
}
