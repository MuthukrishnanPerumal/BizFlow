package com.aveline.bizflow.identity;

import com.aveline.bizflow.security.AccountPrincipal;
import com.aveline.bizflow.security.AuthMessages;
import com.aveline.bizflow.security.AuthenticatedAccount;
import com.aveline.bizflow.security.AuthenticatedAccountSource;
import com.aveline.bizflow.security.ClientAuthenticationException;
import com.aveline.bizflow.security.JwtPrincipal;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived cache in front of {@link DatabaseAccountSource}.
 * Always re-checks token revocation (cheap PK lookup) so logout stays immediate.
 * User/role/permission graphs are cached briefly to cut per-request join cost.
 */
@Service
@Primary
public class CachedAccountSource implements AuthenticatedAccountSource {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final int MAX_ENTRIES = 500;

    private final DatabaseAccountSource delegate;
    private final RevokedTokenRepository revokedTokenRepository;
    private final Clock clock;
    private final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    public CachedAccountSource(
            DatabaseAccountSource delegate,
            RevokedTokenRepository revokedTokenRepository,
            Clock clock) {
        this.delegate = delegate;
        this.revokedTokenRepository = revokedTokenRepository;
        this.clock = clock;
    }

    @Override
    public AuthenticatedAccount load(JwtPrincipal principal) {
        if (principal.tokenId() != null && revokedTokenRepository.existsById(principal.tokenId())) {
            evictUsername(principal.username());
            throw new ClientAuthenticationException(AuthMessages.SESSION_ENDED);
        }

        CacheKey key = new CacheKey(principal.username(), principal.sessionVersion());
        long now = clock.millis();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAtMillis() > now) {
            return withToken(cached.account(), principal);
        }

        AuthenticatedAccount loaded = delegate.loadWithoutRevocationCheck(principal);
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new CacheEntry(loaded, now + TTL.toMillis()));
        return withToken(loaded, principal);
    }

    /**
     * Cached authorities are keyed by username+sessionVersion; principal must carry the
     * current token id / expiry for filters that inspect AccountPrincipal.
     */
    private static AuthenticatedAccount withToken(AuthenticatedAccount cached, JwtPrincipal principal) {
        AccountPrincipal p = cached.principal();
        if (Objects.equals(p.tokenId(), principal.tokenId())
                && Objects.equals(p.tokenExpiresAt(), principal.expiresAt())) {
            return cached;
        }
        return new AuthenticatedAccount(
                new AccountPrincipal(
                        p.userId(),
                        p.username(),
                        principal.tokenId(),
                        principal.expiresAt(),
                        p.mustChangePassword()),
                cached.authorities());
    }

    private void evictUsername(String username) {
        cache.keySet().removeIf(key -> Objects.equals(key.username(), username));
    }

    private record CacheKey(String username, long sessionVersion) {
    }

    private record CacheEntry(AuthenticatedAccount account, long expiresAtMillis) {
    }
}
