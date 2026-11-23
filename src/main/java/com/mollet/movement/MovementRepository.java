package com.mollet.movement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    @EntityGraph(attributePaths = {"sourceAccount", "targetAccount"})
    Optional<Movement> findBySourceAccountIdAndIdempotencyKey(Long sourceAccountId, String idempotencyKey);

    /** Total enviado hoy por una cuenta: base del límite diario. Devuelve null si no hay movimientos. */
    @Query("""
            select sum(m.amount) from Movement m
            where m.sourceAccount.id = :accountId and m.type = :type and m.createdAt >= :from
            """)
    BigDecimal sumOutgoingSince(@Param("accountId") Long accountId, @Param("type") MovementType type,
            @Param("from") Instant from);

    /** Historial (entradas y salidas) del más reciente al más antiguo, con las cuentas ya cargadas. */
    @Query(value = """
            select m from Movement m
            left join fetch m.sourceAccount s
            join fetch m.targetAccount t
            where s.id = :accountId or t.id = :accountId
            order by m.createdAt desc, m.id desc
            """,
            countQuery = """
            select count(m) from Movement m
            where m.sourceAccount.id = :accountId or m.targetAccount.id = :accountId
            """)
    Page<Movement> findHistory(@Param("accountId") Long accountId, Pageable pageable);
}
