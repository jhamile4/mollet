package com.mollet.movement;

import com.mollet.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro inmutable de dinero que entra o sale (como un libro contable): no tiene setters
 * y nunca se edita ni se borra. El historial de una cuenta son sus movimientos.
 */
@Entity
@Table(name = "movements")
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    /** Null en depósitos (el dinero viene "de afuera"). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_account_id", nullable = false)
    private Account targetAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 140)
    private String description;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Movement() {
        // requerido por JPA
    }

    public static Movement deposit(Account target, BigDecimal amount, Instant now) {
        Movement m = new Movement();
        m.type = MovementType.DEPOSIT;
        m.targetAccount = target;
        m.amount = amount;
        m.description = "Recarga de saldo";
        m.createdAt = now;
        return m;
    }

    public static Movement transfer(Account source, Account target, BigDecimal amount, String description,
            String idempotencyKey, Instant now) {
        Movement m = new Movement();
        m.type = MovementType.TRANSFER;
        m.sourceAccount = source;
        m.targetAccount = target;
        m.amount = amount;
        m.description = description;
        m.idempotencyKey = idempotencyKey;
        m.createdAt = now;
        return m;
    }

    public Long getId() {
        return id;
    }

    public MovementType getType() {
        return type;
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Account getTargetAccount() {
        return targetAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
