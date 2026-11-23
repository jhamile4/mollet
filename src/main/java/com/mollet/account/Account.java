package com.mollet.account;

import com.mollet.common.error.BusinessException;
import com.mollet.common.error.ErrorCode;
import com.mollet.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cuenta de billetera en soles. El saldo NO tiene setter: solo cambia con debit() y credit(),
 * que validan las reglas. Así es imposible, desde cualquier parte del código, dejar un saldo
 * negativo o mover montos no positivos.
 *
 * Dinero siempre en BigDecimal (nunca double: 0.1 + 0.2 != 0.3 en punto flotante).
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
        // requerido por JPA
    }

    public static Account open(User user, String accountNumber, BigDecimal dailyLimit, Instant now) {
        Account account = new Account();
        account.user = user;
        account.accountNumber = accountNumber;
        account.currency = "PEN";
        account.balance = BigDecimal.ZERO.setScale(2);
        account.dailyLimit = dailyLimit;
        account.status = AccountStatus.ACTIVE;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    public void debit(BigDecimal amount, Instant now) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS,
                    "Saldo insuficiente: tienes S/ " + balance + " y quieres enviar S/ " + amount);
        }
        balance = balance.subtract(amount);
        updatedAt = now;
    }

    public void credit(BigDecimal amount, Instant now) {
        requirePositive(amount);
        balance = balance.add(amount);
        updatedAt = now;
    }

    public void changeStatus(AccountStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
