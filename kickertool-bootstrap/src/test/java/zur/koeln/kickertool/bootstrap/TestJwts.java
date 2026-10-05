package zur.koeln.kickertool.bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/** Erzeugt und prüft Test-Tokens mit einem gemeinsamen Schlüssel, ersetzt in Tests den OIDC-Provider. */
final class TestJwts {

    private static final byte[] SECRET = "nur-fuer-tests-mindestens-32-bytes-lang!!".getBytes(StandardCharsets.UTF_8);

    private TestJwts() {
    }

    static JwtDecoder decoder() {
        SecretKey key = new SecretKeySpec(SECRET, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Ein Token wie von Keycloak: Admins tragen die Rolle {@code kicker-admin} in {@code realm_access.roles}. */
    static String token(String subject, String name, boolean admin) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("name", name)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000));
        if (admin) {
            claims.claim("realm_access", Map.of("roles", List.of("offline_access", "kicker-admin")));
        }
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(SECRET));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
