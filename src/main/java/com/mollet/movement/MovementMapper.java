package com.mollet.movement;

import com.mollet.account.Account;
import com.mollet.movement.dto.MovementResponse;
import com.mollet.movement.dto.MovementResponse.Direction;
import org.springframework.stereotype.Component;

/**
 * Mapper escrito a mano (no MapStruct) porque tiene lógica: el mismo movimiento es OUT para
 * quien envía e IN para quien recibe, según desde qué cuenta se mire.
 */
@Component
public class MovementMapper {

    public MovementResponse toResponse(Movement movement, Long viewerAccountId) {
        boolean incoming = movement.getTargetAccount().getId().equals(viewerAccountId);
        Account counterpart = incoming ? movement.getSourceAccount() : movement.getTargetAccount();
        return new MovementResponse(
                movement.getId(),
                movement.getType(),
                incoming ? Direction.IN : Direction.OUT,
                movement.getAmount(),
                counterpart != null ? counterpart.getAccountNumber() : null,
                movement.getDescription(),
                movement.getCreatedAt());
    }
}
