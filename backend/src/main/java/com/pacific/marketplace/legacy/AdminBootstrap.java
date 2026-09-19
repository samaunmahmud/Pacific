package com.pacific.marketplace.legacy;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first admin on a fresh install from ADMIN_USERNAME / ADMIN_PASSWORD, if no admin exists yet. */
@Component
@Order(2)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminBootstrap(AppProperties props, UserRepository users, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.AdminBootstrap cfg = props.adminBootstrap();
        if (cfg == null || isBlank(cfg.username()) || isBlank(cfg.password())) return;
        if (users.existsByRole(Role.ADMIN)) return;
        if (cfg.password().length() < 8 || cfg.password().length() > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must be 8 to 72 characters.");
        }
        String username = cfg.username().strip();
        users.save(new User(username, null, username, encoder.encode(cfg.password()), Role.ADMIN));
        log.info("Created admin account '{}'.", username);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
