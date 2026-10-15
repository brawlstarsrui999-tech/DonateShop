package com.twixrpg.donateshop.model;

/**
 * Расчёт покупки привилегии: можно ли купить и за сколько.
 * action — что произойдёт: NEW / EXTEND / REPLACE / CONVERT / UPGRADE.
 */
public record Quote(boolean allowed, int price, String action, String denyReason) {

    public static Quote allow(int price, String action) {
        return new Quote(true, price, action, null);
    }

    public static Quote deny(String reason) {
        return new Quote(false, 0, "DENY", reason);
    }
}
