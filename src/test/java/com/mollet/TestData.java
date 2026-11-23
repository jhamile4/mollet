package com.mollet;

import com.mollet.account.Account;
import com.mollet.config.MolletProperties;
import com.mollet.user.Role;
import com.mollet.user.User;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Datos de prueba para tests unitarios (sin base de datos, los ids se asignan a mano). */
public final class TestData {

    private TestData() {
    }

    public static MolletProperties properties() {
        return new MolletProperties(
                "America/Lima",
                new MolletProperties.Jwt("test-secret-test-secret-test-secret-123", Duration.ofHours(1), "mollet"),
                new MolletProperties.Transfer(new BigDecimal("500.00"), new BigDecimal("5000.00")),
                new MolletProperties.Admin(null, null));
    }

    public static User user(long id, String email) {
        User user = new User(email, "hash", "Usuario " + id, Role.USER, Instant.EPOCH);
        setId(user, id);
        return user;
    }

    public static Account account(long id, String number, String balance) {
        Account account = Account.open(user(id, "u" + id + "@mollet.dev"), number, new BigDecimal("500.00"),
                Instant.EPOCH);
        setId(account, id);
        if (new BigDecimal(balance).signum() > 0) {
            account.credit(new BigDecimal(balance), Instant.EPOCH);
        }
        return account;
    }

    public static void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
