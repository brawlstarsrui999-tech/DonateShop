package com.twixrpg.donateshop.model;

import org.bukkit.Material;

/** Срок покупки привилегии. days == 0 — навсегда, percent — доля полной цены. */
public record DurationOption(
        String id,
        int days,
        int percent,
        Material material,
        String displayName
) {
    public boolean permanent() {
        return days <= 0;
    }
}
