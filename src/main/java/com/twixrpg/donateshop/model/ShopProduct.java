package com.twixrpg.donateshop.model;

import org.bukkit.Material;

import java.util.List;

/**
 * Предмет из секции items. Выдаётся плагином супер-предметов
 * (TwixRPGItems / SuperItemsTwix) консольной командой giveCommand,
 * где {player} — ник покупателя.
 */
public record ShopProduct(
        String id,
        String displayName,
        int price,
        Material material,
        String giveCommand,
        List<String> description
) {
}
