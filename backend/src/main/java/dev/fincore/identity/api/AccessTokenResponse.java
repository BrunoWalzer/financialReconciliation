package dev.fincore.identity.api;

/** Resposta de login e de refresh — o mesmo formato para os dois (TDS 19.2). */
public record AccessTokenResponse(String accessToken, String tokenType, long expiresInSeconds) {

    public static AccessTokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new AccessTokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
