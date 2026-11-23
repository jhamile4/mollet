package com.mollet.movement;

import com.mollet.account.Account;
import com.mollet.account.AccountRepository;
import com.mollet.common.error.BusinessException;
import com.mollet.common.error.ErrorCode;
import com.mollet.config.MolletProperties;
import com.mollet.movement.dto.MovementResponse;
import com.mollet.movement.dto.TransferRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transferencias entre cuentas Mollet. Todo ocurre en UNA transacción: si cualquier paso falla,
 * se revierte todo (no puede salir dinero de una cuenta sin llegar a la otra).
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final MovementRepository movementRepository;
    private final MovementMapper movementMapper;
    private final MolletProperties properties;
    private final Clock clock;

    public TransferService(AccountRepository accountRepository, MovementRepository movementRepository,
            MovementMapper movementMapper, MolletProperties properties, Clock clock) {
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.movementMapper = movementMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** Resultado + si fue una repetición (el controlador responde 200 en vez de 201). */
    public record TransferResult(MovementResponse movement, boolean replayed) {
    }

    @Transactional
    public TransferResult transfer(Long userId, String idempotencyKey, TransferRequest request) {
        BigDecimal amount = request.amount().setScale(2);

        // 1. Ubicar ambas cuentas (solo ids, todavía sin bloquear)
        Long sourceId = accountRepository.findIdByUserId(userId).orElseThrow(
                () -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "No tienes una cuenta Mollet"));
        Long targetId = accountRepository.findIdByAccountNumber(request.targetAccountNumber()).orElseThrow(
                () -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "La cuenta destino no existe"));
        if (sourceId.equals(targetId)) {
            throw new BusinessException(ErrorCode.SAME_ACCOUNT_TRANSFER, "No puedes transferirte a tu propia cuenta");
        }

        // 2. Bloquear SIEMPRE en el mismo orden (id menor primero) para evitar deadlocks:
        //    si A->B y B->A llegan a la vez, ambas intentan bloquear primero la misma fila.
        Account first = lock(Math.min(sourceId, targetId));
        Account second = lock(Math.max(sourceId, targetId));
        Account source = first.getId().equals(sourceId) ? first : second;
        Account target = source == first ? second : first;

        // 3. Idempotencia: si el cliente reintenta con la misma clave, devolvemos la transferencia
        //    original en lugar de cobrar dos veces. Se revisa DESPUÉS del bloqueo, así dos reintentos
        //    simultáneos quedan en fila y el segundo ya ve el movimiento del primero.
        Optional<Movement> previous = movementRepository.findBySourceAccountIdAndIdempotencyKey(sourceId, idempotencyKey);
        if (previous.isPresent()) {
            return replay(previous.get(), target, amount);
        }

        // 4. Reglas de negocio
        if (!source.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED, "Tu cuenta está bloqueada");
        }
        if (!target.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED, "La cuenta destino no puede recibir transferencias");
        }
        checkDailyLimit(source, amount);

        // 5. Mover el dinero (debit valida saldo suficiente) y registrar el movimiento
        Instant now = Instant.now(clock);
        source.debit(amount, now);
        target.credit(amount, now);
        Movement movement = movementRepository.save(
                Movement.transfer(source, target, amount, request.description(), idempotencyKey, now));

        log.info("Transferencia {}: S/ {} de cuenta {} a {}", movement.getId(), amount, sourceId, targetId);
        return new TransferResult(movementMapper.toResponse(movement, sourceId), false);
    }

    private Account lock(Long id) {
        return accountRepository.findByIdForUpdate(id).orElseThrow(
                () -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Cuenta no encontrada"));
    }

    private TransferResult replay(Movement previous, Account target, BigDecimal amount) {
        boolean samePayload = previous.getTargetAccount().getId().equals(target.getId())
                && previous.getAmount().compareTo(amount) == 0;
        if (!samePayload) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Esta Idempotency-Key ya se usó para otra transferencia");
        }
        return new TransferResult(movementMapper.toResponse(previous, previous.getSourceAccount().getId()), true);
    }

    /** El "día" es el de Lima, no UTC: a las 7 p. m. en Perú ya es otro día en UTC. */
    private void checkDailyLimit(Account source, BigDecimal amount) {
        ZoneId zone = properties.zone();
        Instant startOfDay = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        BigDecimal sentToday = Optional.ofNullable(
                movementRepository.sumOutgoingSince(source.getId(), MovementType.TRANSFER, startOfDay))
                .orElse(BigDecimal.ZERO);
        if (sentToday.add(amount).compareTo(source.getDailyLimit()) > 0) {
            BigDecimal available = source.getDailyLimit().subtract(sentToday).max(BigDecimal.ZERO);
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED,
                    "Superas tu límite diario de S/ " + source.getDailyLimit() + ". Hoy aún puedes enviar S/ " + available);
        }
    }
}
