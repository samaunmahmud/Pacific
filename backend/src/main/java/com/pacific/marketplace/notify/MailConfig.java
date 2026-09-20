package com.pacific.marketplace.notify;

import com.pacific.marketplace.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);
    static final String DEFAULT_FROM = "Pacific <no-reply@pacific.example>";

    @Bean
    Mailer mailer(ObjectProvider<JavaMailSender> senders, @Value("${spring.mail.host:}") String host,
                  AppProperties props) {
        JavaMailSender sender = senders.getIfAvailable();
        if (StringUtils.hasText(host) && sender != null) {
            log.info("Order emails: sending through the SMTP server at {}.", host);
            return new SmtpMailer(sender, from(props));
        }
        log.warn("Order emails: no MAIL_HOST set, so emails are only logged and recorded, never sent.");
        return new LogMailer(props.security() != null && props.security().production());
    }

    /** Emails go out on a background thread so a slow mail server never slows a customer's request. */
    @Bean(name = "notificationExecutor")
    TaskExecutor notificationExecutor(AppProperties props) {
        if (props.mail() != null && !props.mail().async()) return new SyncTaskExecutor();
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(500);
        pool.setThreadNamePrefix("mail-");
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(10);
        return pool;
    }

    static String from(AppProperties props) {
        String from = props.mail() == null ? null : props.mail().from();
        return StringUtils.hasText(from) ? from : DEFAULT_FROM;
    }
}
