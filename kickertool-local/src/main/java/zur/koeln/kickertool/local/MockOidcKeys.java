package zur.koeln.kickertool.local;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import zur.koeln.kickertool.local.MockUsers.MockUser;

/**
 * Schlüsselpaar des simulierten Providers. Wird bei jedem Start neu erzeugt, Tokens eines früheren Starts sind
 * danach ungültig.
 */
class MockOidcKeys {

    private static final long TOKEN_LIFETIME_SECONDS = 8 * 3600;

    private final RSAKey key;
    private final String issuer;

    MockOidcKeys(String issuer) {
        this.issuer = issuer;
        try {
            this.key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Schlüssel für den simulierten OIDC-Provider konnte nicht erzeugt werden", e);
        }
    }

    String issuer() {
        return issuer;
    }

    RSAPublicKey publicKey() {
        try {
            return key.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    Map<String, Object> publicJwks() {
        return new JWKSet(key.toPublicJWK()).toJSONObject();
    }

    long lifetimeSeconds() {
        return TOKEN_LIFETIME_SECONDS;
    }

    /** Ein Access-Token im Stil von Keycloak: Admins tragen die Rolle {@code kicker-admin} in {@code realm_access.roles}. */
    String issueAccessToken(MockUser user) {
        Date now = new Date();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(user.subject())
                .issueTime(now)
                .expirationTime(new Date(now.getTime() + TOKEN_LIFETIME_SECONDS * 1000))
                .claim("name", user.displayName())
                .claim("preferred_username", user.username())
                .claim("email", user.email());
        if (user.admin()) {
            claims.claim("realm_access", Map.of("roles", List.of("offline_access", "kicker-admin")));
        }
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
