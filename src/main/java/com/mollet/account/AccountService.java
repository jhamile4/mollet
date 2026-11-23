package com.mollet.account;

import com.mollet.account.dto.AccountResponse;
import com.mollet.account.dto.DepositRequest;
import com.mollet.common.error.BusinessException;
import com.mollet.common.error.ErrorCode;
import com.mollet.common.web.PageResponse;
import com.mollet.config.MolletProperties;
import com.mollet.movement.Movement;
import com.mollet.movement.MovementMapper;
import com.mollet.movement.MovementRepository;
import com.mollet.movement.dto.MovementResponse;
import com.mollet.user.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int MAX_NUMBER_ATTEMPTS = 5;

    private final AccountRepository accountRepository;
    private final MovementRepository movementRepository;
    private final AccountMapper accountMapper;
    private final MovementMapper movementMapper;
    private final AccountNumberGenerator numberGenerator;
    private final MolletProperties properties;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, MovementRepository movementRepository,
            AccountMapper accountMapper, MovementMapper movementMapper, AccountNumberGenerator numberGenerator,
            MolletProperties properties, Clock clock) {
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.accountMapper = accountMapper;
        this.movementMapper = movementMapper;
        this.numberGenerator = numberGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    /** Se llama al registrarse: cada usuario tiene exactamente una cuenta (UNIQUE en user_id). */
    @Transactional
    public Account openAccountFor(User user) {
        String number = uniqueAccountNumber();
        Account account = Account.open(user, number, properties.transfer().dailyLimit(), Instant.now(clock));
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getMyAccount(Long userId) {
        Account account = accountRepository.findByUserId(userId).orElseThrow(AccountService::noAccount);
        return accountMapper.toResponse(account);
    }

    /** Recarga simulada. Usa el mismo bloqueo que las transferencias para no pisar saldos. */
    @Transactional
    public MovementResponse deposit(Long userId, DepositRequest request) {
        if (request.amount().compareTo(properties.transfer().maxDeposit()) > 0) {
            throw new BusinessException(ErrorCode.DEPOSIT_LIMIT_EXCEEDED,
                    "El monto máximo por recarga es S/ " + properties.transfer().maxDeposit());
        }
        Long accountId = accountRepository.findIdByUserId(userId).orElseThrow(AccountService::noAccount);
        Account account = accountRepository.findByIdForUpdate(accountId).orElseThrow(AccountService::noAccount);
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED, "Tu cuenta está bloqueada");
        }
        Instant now = Instant.now(clock);
        account.credit(request.amount(), now);
        Movement movement = movementRepository.save(Movement.deposit(account, request.amount(), now));
        return movementMapper.toResponse(movement, account.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> history(Long userId, int page, int size) {
        Long accountId = accountRepository.findIdByUserId(userId).orElseThrow(AccountService::noAccount);
        return PageResponse.from(
                movementRepository.findHistory(accountId, PageRequest.of(page, size)),
                m -> movementMapper.toResponse(m, accountId));
    }

    private String uniqueAccountNumber() {
        for (int i = 0; i < MAX_NUMBER_ATTEMPTS; i++) {
            String candidate = numberGenerator.next();
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un número de cuenta único");
    }

    static BusinessException noAccount() {
        return new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "No tienes una cuenta Mollet");
    }
}
