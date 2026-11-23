package com.mollet.account;

import com.mollet.account.dto.AdminAccountResponse;
import com.mollet.common.error.BusinessException;
import com.mollet.common.error.ErrorCode;
import com.mollet.common.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final Clock clock;

    public AdminAccountService(AccountRepository accountRepository, AccountMapper accountMapper, Clock clock) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAccountResponse> list(AccountStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Account> result = status == null
                ? accountRepository.findAll(pageable)
                : accountRepository.findByStatus(status, pageable);
        return PageResponse.from(result, accountMapper::toAdminResponse);
    }

    /**
     * También bloquea la fila: si un admin bloquea una cuenta justo mientras se procesa una
     * transferencia, sin bloqueo Hibernate podría guardar un saldo viejo al actualizar el estado.
     */
    @Transactional
    public AdminAccountResponse changeStatus(String accountNumber, AccountStatus status) {
        Long id = accountRepository.findIdByAccountNumber(accountNumber).orElseThrow(
                () -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "La cuenta " + accountNumber + " no existe"));
        Account account = accountRepository.findByIdForUpdate(id).orElseThrow();
        account.changeStatus(status, Instant.now(clock));
        return accountMapper.toAdminResponse(account);
    }
}
