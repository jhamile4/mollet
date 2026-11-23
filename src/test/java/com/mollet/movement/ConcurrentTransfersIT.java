package com.mollet.movement;

import static org.assertj.core.api.Assertions.assertThat;

import com.mollet.IntegrationTest;
import com.mollet.account.AccountRepository;
import com.mollet.common.error.BusinessException;
import com.mollet.common.error.ErrorCode;
import com.mollet.movement.dto.TransferRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * La prueba más importante del proyecto: 10 transferencias de S/ 100 salen AL MISMO TIEMPO de una
 * cuenta con S/ 500. Sin el bloqueo pesimista, varias leerían "saldo 500" a la vez y todas pasarían
 * (saldo final negativo o dinero creado de la nada). Con el bloqueo, exactamente 5 pasan.
 */
@TestPropertySource(properties = "mollet.transfer.daily-limit=100000.00") // aislar la prueba del límite diario
class ConcurrentTransfersIT extends IntegrationTest {

    @Autowired
    private TransferService transferService;
    @Autowired
    private AccountRepository accountRepository;

    @Test
    void concurrentTransfersNeverOverdrawTheAccount() throws Exception {
        Session ana = registerUser();
        Session luis = registerUser();
        deposit(ana, "500.00");
        Long anaUserId = userIdOf(ana);

        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(); // todos arrancan a la vez
                    try {
                        transferService.transfer(anaUserId, UUID.randomUUID().toString(),
                                new TransferRequest(luis.accountNumber(), new BigDecimal("100.00"), null));
                        ok.incrementAndGet();
                    } catch (BusinessException e) {
                        assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertThat(ok.get()).isEqualTo(5);
        assertThat(rejected.get()).isEqualTo(5);
        assertThat(myAccount(ana).get("balance").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(myAccount(luis).get("balance").decimalValue()).isEqualByComparingTo("500.00");
    }

    @Test
    void crossedTransfersDoNotDeadlock() throws Exception {
        Session ana = registerUser();
        Session luis = registerUser();
        deposit(ana, "100.00");
        deposit(luis, "100.00");
        Long anaUserId = userIdOf(ana);
        Long luisUserId = userIdOf(luis);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                boolean fromAna = i % 2 == 0;
                futures.add(pool.submit(() -> {
                    start.await();
                    transferService.transfer(fromAna ? anaUserId : luisUserId, UUID.randomUUID().toString(),
                            new TransferRequest(fromAna ? luis.accountNumber() : ana.accountNumber(),
                                    new BigDecimal("10.00"), null));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(); // si hubiera deadlock, PostgreSQL abortaría una y esto lanzaría excepción
            }
        }

        // 4 en cada sentido de S/ 10: los saldos quedan igual
        assertThat(myAccount(ana).get("balance").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(myAccount(luis).get("balance").decimalValue()).isEqualByComparingTo("100.00");
    }

    private Long userIdOf(Session session) {
        return accountRepository.findAll().stream()
                .filter(a -> a.getAccountNumber().equals(session.accountNumber()))
                .findFirst().orElseThrow().getUser().getId();
    }
}
