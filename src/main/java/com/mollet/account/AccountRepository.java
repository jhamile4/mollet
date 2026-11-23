package com.mollet.account;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserId(Long userId);

    boolean existsByAccountNumber(String accountNumber);

    /*
     * Para operaciones de dinero primero buscamos SOLO el id (sin cargar la entidad) y luego la
     * cargamos con bloqueo. Si cargáramos la entidad antes del bloqueo, Hibernate reutilizaría esa
     * copia (con un saldo que pudo cambiar) en lugar de leer el valor fresco de la base de datos.
     */
    @Query("select a.id from Account a where a.user.id = :userId")
    Optional<Long> findIdByUserId(@Param("userId") Long userId);

    @Query("select a.id from Account a where a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    /**
     * SELECT ... FOR UPDATE: bloquea la fila hasta que termine la transacción. Si llegan dos
     * transferencias a la vez desde la misma cuenta, la segunda espera a que termine la primera
     * y lee el saldo ya actualizado. Así el saldo nunca queda negativo por concurrencia.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    // @EntityGraph trae el usuario en la misma consulta (evita el problema N+1)
    @Override
    @EntityGraph(attributePaths = "user")
    Page<Account> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Account> findByStatus(AccountStatus status, Pageable pageable);
}
