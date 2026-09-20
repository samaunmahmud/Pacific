package com.pacific.marketplace.notify;

import com.pacific.marketplace.domain.SentEmail;

/** Delivers an email. Throws if it could not be delivered. */
public interface Mailer {

    /** SENT when handed to a mail server, LOGGED when there is no mail server and it was only recorded. */
    SentEmail.Status send(Email email) throws Exception;
}
