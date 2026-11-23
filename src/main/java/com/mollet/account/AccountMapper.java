package com.mollet.account;

import com.mollet.account.dto.AccountResponse;
import com.mollet.account.dto.AdminAccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct genera la implementación de esta interfaz al compilar (ver target/generated-sources).
 * Es código normal, sin reflexión, así que es rápido y si un campo no calza falla al compilar.
 */
@Mapper
public interface AccountMapper {

    AccountResponse toResponse(Account account);

    @Mapping(target = "ownerName", source = "user.fullName")
    @Mapping(target = "ownerEmail", source = "user.email")
    AdminAccountResponse toAdminResponse(Account account);
}
