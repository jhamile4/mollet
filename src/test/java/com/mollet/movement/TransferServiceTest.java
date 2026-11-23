package com.mollet.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mollet.TestData;
import com.mollet.account.Account;
import com.mollet.account.AccountRepository;
import com.mollet.account.AccountStatus;
import com.mollet.common.error.BusinessException;
import com.mollet.common.error.ErrorCode;
import com.mollet.movement.TransferService.TransferResult;
import com.mollet.movement.dto.MovementResponse.Direction;
import com.mollet.movement.dto.TransferRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final Long USER_ID = 1L;
    private static final String KEY = "key-12345678";
    // 2026-03-10 15:00 en Lima (UTC-5) = 20:00 UTC
    private static final Instant NOW = Instant.parse("2026-03-10T20:00:00Z");

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private MovementRepository movementRepository;

    private TransferService service;
    private Account source;
    private Account target;

    @BeforeEach
    void setUp() {
        service = new TransferService(accountRepository, movementRepository, new MovementMapper(),
                TestData.properties(), Clock.fixed(NOW, ZoneOffset.UTC));
        source = TestData.account(10, "10000000000010", "300.00");
        target = TestData.account(20, "10000000000020", "0.00");
    }

    private void givenAccountsExist() {
        when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(10L));
        when(accountRepository.findIdByAccountNumber("10000000000020")).thenReturn(Optional.of(20L));
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(target));
    }

    private static TransferRequest request(String amount) {
        return new TransferRequest("10000000000020", new BigDecimal(amount), "Almuerzo");
    }

    @Test
    void transfersMoneyAndRecordsMovement() {
        givenAccountsExist();
        when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> {
            Movement m = inv.getArgument(0);
            TestData.setId(m, 99L);
            return m;
        });

        TransferResult result = service.transfer(USER_ID, KEY, request("120.50"));

        assertThat(source.getBalance()).isEqualByComparingTo("179.50");
        assertThat(target.getBalance()).isEqualByComparingTo("120.50");
        assertThat(result.replayed()).isFalse();
        assertThat(result.movement().direction()).isEqualTo(Direction.OUT);
        assertThat(result.movement().counterpartAccount()).isEqualTo("10000000000020");

        ArgumentCaptor<Movement> saved = ArgumentCaptor.forClass(Movement.class);
        verify(movementRepository).save(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo(KEY);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsInsufficientFundsWithoutSavingAnything() {
        givenAccountsExist();

        assertThatThrownBy(() -> service.transfer(USER_ID, KEY, request("300.01")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void rejectsWhenDailyLimitWouldBeExceeded() {
        givenAccountsExist();
        // ya envió 450 hoy; el límite es 500
        when(movementRepository.sumOutgoingSince(eq(10L), eq(MovementType.TRANSFER), any()))
                .thenReturn(new BigDecimal("450.00"));

        assertThatThrownBy(() -> service.transfer(USER_ID, KEY, request("50.01")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.DAILY_LIMIT_EXCEEDED);
                    assertThat(e.getMessage()).contains("50.00");
                });
        assertThat(source.getBalance()).isEqualByComparingTo("300.00");
    }

    @Test
    void dailyLimitCountsFromMidnightInLimaNotUtc() {
        givenAccountsExist();
        when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(USER_ID, KEY, request("10.00"));

        // Medianoche del 10/03 en Lima = 05:00 UTC
        verify(movementRepository).sumOutgoingSince(10L, MovementType.TRANSFER, Instant.parse("2026-03-10T05:00:00Z"));
    }

    @Test
    void rejectsTransferToOwnAccount() {
        when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(10L));
        when(accountRepository.findIdByAccountNumber("10000000000010")).thenReturn(Optional.of(10L));

        TransferRequest toSelf = new TransferRequest("10000000000010", BigDecimal.TEN, null);

        assertThatThrownBy(() -> service.transfer(USER_ID, KEY, toSelf))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.SAME_ACCOUNT_TRANSFER));
    }

    @Test
    void rejectsWhenTargetAccountIsBlocked() {
        givenAccountsExist();
        target.changeStatus(AccountStatus.BLOCKED, NOW);

        assertThatThrownBy(() -> service.transfer(USER_ID, KEY, request("10.00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ACCOUNT_BLOCKED));
    }

    @Test
    void repeatedKeyWithSamePayloadReturnsOriginalWithoutChargingAgain() {
        givenAccountsExist();
        Movement original = Movement.transfer(source, target, new BigDecimal("20.00"), "Almuerzo", KEY, NOW);
        TestData.setId(original, 7L);
        when(movementRepository.findBySourceAccountIdAndIdempotencyKey(10L, KEY)).thenReturn(Optional.of(original));

        TransferResult result = service.transfer(USER_ID, KEY, request("20.00"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.movement().id()).isEqualTo(7L);
        assertThat(source.getBalance()).isEqualByComparingTo("300.00");
        verify(movementRepository, never()).save(any());
    }

    @Test
    void repeatedKeyWithDifferentAmountIsRejected() {
        givenAccountsExist();
        Movement original = Movement.transfer(source, target, new BigDecimal("20.00"), null, KEY, NOW);
        when(movementRepository.findBySourceAccountIdAndIdempotencyKey(10L, KEY)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.transfer(USER_ID, KEY, request("99.00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void locksAccountsInAscendingIdOrderToAvoidDeadlocks() {
        // La cuenta origen tiene id MAYOR que la destino: igual debe bloquear primero la de id menor
        Account highSource = TestData.account(30, "10000000000030", "100.00");
        when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(30L));
        when(accountRepository.findIdByAccountNumber("10000000000020")).thenReturn(Optional.of(20L));
        when(accountRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(target));
        when(accountRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(highSource));
        when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(USER_ID, KEY, request("10.00"));

        var inOrder = org.mockito.Mockito.inOrder(accountRepository);
        inOrder.verify(accountRepository).findByIdForUpdate(20L);
        inOrder.verify(accountRepository).findByIdForUpdate(30L);
        assertThat(highSource.getBalance()).isEqualByComparingTo("90.00");
    }
}
