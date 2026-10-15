package com.twixrpg.donateshop.model;

import org.bukkit.Material;

import java.util.List;

/** Привилегия из секции ranks в config.yml. price — полная цена (навсегда), в TwixCoin. */
public record DonateRank(
        String id,
        String displayName,
        String group,
        int price,
        int order,
        Material material,
        List<String> description
) {
}
