package com.mollet.auth;

import com.mollet.auth.dto.TokenResponse;
import com.mollet.config.MolletProperties;
import com.mollet.config.SecurityConfig;
import com.mollet.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emite el JWT. Contenido del token:
 *  sub   = id del usuario (lo usan los controladores para saber "quién soy")
 *  roles = ["USER"] o ["ADMIN"]
 *  iss / iat / exp = emisor, emitido en, expira en
 * El token está firmado, no cifrado: no guardamos datos sensibles dentro.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final MolletProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, MolletProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse issue(User user) {
        Instant now = Instant.now(clock);
        long ttl = properties.jwt().expiration().toSeconds();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttl))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim(SecurityConfig.ROLES_CLAIM, List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return TokenResponse.bearer(token, ttl);
    }
}
