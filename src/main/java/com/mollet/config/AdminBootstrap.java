package com.mollet.config;

import com.mollet.user.Role;
import com.mollet.user.User;
import com.mollet.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Crea el administrador inicial al arrancar, a partir de ADMIN_EMAIL y ADMIN_PASSWORD.
 * Así no hay credenciales en el código ni en las migraciones. Si ya existe, no hace nada.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final MolletProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminBootstrap(MolletProperties properties, UserRepository userRepository,
            PasswordEncoder passwordEncoder, Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        MolletProperties.Admin admin = properties.admin();
        if (admin == null || !StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD no configurados: no se crea administrador inicial");
            return;
        }
        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode(admin.password()), "Administrador",
                Role.ADMIN, Instant.now(clock)));
        log.info("Administrador inicial creado: {}", email);
    }
}
