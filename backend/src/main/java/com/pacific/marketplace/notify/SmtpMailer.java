package com.pacific.marketplace.notify;

import com.pacific.marketplace.domain.SentEmail;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Sends real email through the configured SMTP server, as text with an HTML alternative. */
public class SmtpMailer implements Mailer {

    private final JavaMailSender sender;
    private final String from;

    public SmtpMailer(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public SentEmail.Status send(Email email) throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(from));
        helper.setTo(email.to());
        helper.setSubject(email.subject());
        helper.setText(email.text(), email.html());
        sender.send(message);
        return SentEmail.Status.SENT;
    }
}
