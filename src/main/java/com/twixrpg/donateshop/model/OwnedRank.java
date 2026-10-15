package com.twixrpg.donateshop.model;

/**
 * Активная привилегия игрока (хранится в players.yml).
 * expiresAtMillis: -1 — навсегда, иначе момент окончания (epoch millis).
 */
public record OwnedRank(String rankId, long expiresAtMillis) {

    public static final long PERMANENT = -1L;

    public boolean permanent() {
        return expiresAtMillis == PERMANENT;
    }

    public boolean expired(long now) {
        return !permanent() && expiresAtMillis <= now;
    }
}
