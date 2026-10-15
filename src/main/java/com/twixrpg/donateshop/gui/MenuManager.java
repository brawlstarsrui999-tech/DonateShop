package com.twixrpg.donateshop.gui;

import com.twixrpg.donateshop.DonateShopPlugin;
import com.twixrpg.donateshop.Text;
import com.twixrpg.donateshop.model.DonateRank;
import com.twixrpg.donateshop.model.DurationOption;
import com.twixrpg.donateshop.model.OwnedRank;
import com.twixrpg.donateshop.model.Quote;
import com.twixrpg.donateshop.model.ShopProduct;
import com.twixrpg.donateshop.service.RankService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Все меню магазина в фиолетовом стиле:
 * главное (Привилегии / Предметы) -> привилегии -> срок -> подтверждение;
 * предметы -> подтверждение.
 */
public final class MenuManager {

    private static final String TITLE_MAIN = "<gradient:#e9d5ff:#7c3aed><bold>✦ DonateShop ✦</bold></gradient>";
    private static final String TITLE_RANKS = "<gradient:#e9d5ff:#7c3aed><bold>✦ Привилегии ✦</bold></gradient>";
    private static final String TITLE_ITEMS = "<gradient:#e9d5ff:#7c3aed><bold>✦ Предметы ✦</bold></gradient>";
    private static final String TITLE_CONFIRM = "<gradient:#f5d0fe:#a855f7><bold>Подтверждение покупки</bold></gradient>";

    /** Слоты контента для списков (2 ряда по центру). */
    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final DonateShopPlugin plugin;

    public MenuManager(DonateShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ============================ ГЛАВНОЕ МЕНЮ ============================

    public void openMain(Player player) {
        ShopHolders.MainHolder holder = new ShopHolders.MainHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm(TITLE_MAIN));
        holder.bind(inv);
        decorate(inv);
        inv.setItem(4, balanceHead(player));
        inv.setItem(11, button(Material.NETHERITE_CHESTPLATE,
                "<gradient:#e9d5ff:#a855f7><bold>Привилегии</bold></gradient>",
                List.of(
                        "<#c4b5fd>Coal, Iron, Gold, Diamond,</#c4b5fd>",
                        "<#c4b5fd>Emerald и Netherite</#c4b5fd>",
                        "",
                        "<#a78bfa>На 7 дней, 15 дней или навсегда</#a78bfa>",
                        "<#8b5cf6>Вечную можно улучшать доплатой</#8b5cf6>",
                        "",
                        "<#f0abfc><bold>Нажмите, чтобы открыть ➜</bold></#f0abfc>"),
                true));
        inv.setItem(15, button(Material.NETHERITE_PICKAXE,
                "<gradient:#e9d5ff:#a855f7><bold>Предметы</bold></gradient>",
                List.of(
                        "<#c4b5fd>Аметистовая Кирка, Лабрис Гефеста,</#c4b5fd>",
                        "<#c4b5fd>Фарм-Меч и ячейки хранения</#c4b5fd>",
                        "",
                        "<#a78bfa>Настоящие супер-предметы Twix</#a78bfa>",
                        "",
                        "<#f0abfc><bold>Нажмите, чтобы открыть ➜</bold></#f0abfc>"),
                true));
        inv.setItem(22, button(Material.AMETHYST_SHARD,
                "<gradient:#d8b4fe:#8b5cf6>TwixCoin</gradient>",
                List.of(
                        "<#c4b5fd>Валюта магазина — TwixCoin (tk)</#c4b5fd>",
                        "<#c4b5fd>Ваш баланс: <#f0abfc>" + balance(player) + " tk</#f0abfc></#c4b5fd>"),
                false));
        player.openInventory(inv);
        click(player);
    }

    // ============================ ПРИВИЛЕГИИ ============================

    public void openRanks(Player player) {
        ShopHolders.RanksHolder holder = new ShopHolders.RanksHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm(TITLE_RANKS));
        holder.bind(inv);
        decorate(inv);
        inv.setItem(4, balanceHead(player));

        OwnedRank owned = plugin.ranks().activeOwned(player.getUniqueId());
        List<DonateRank> ranks = plugin.config().ranks();
        for (int i = 0; i < ranks.size() && i < CONTENT_SLOTS.length; i++) {
            DonateRank rank = ranks.get(i);
            List<String> lore = new ArrayList<>();
            for (String line : rank.description()) lore.add(line);
            lore.add("");
            for (DurationOption d : plugin.config().durations()) {
                lore.add("<#a78bfa>" + plainDuration(d) + " — <#f0abfc>"
                        + plugin.config().priceFor(rank, d) + " tk</#f0abfc></#a78bfa>");
            }
            if (owned != null && owned.rankId().equals(rank.id())) {
                lore.add("");
                lore.add(owned.permanent()
                        ? "<#a7f3d0><bold>✔ Ваша привилегия (навсегда)</bold></#a7f3d0>"
                        : "<#a7f3d0><bold>✔ Ваша привилегия (до "
                        + RankService.formatDate(owned.expiresAtMillis()) + ")</bold></#a7f3d0>");
            } else if (owned != null && owned.permanent()) {
                DonateRank current = plugin.config().rank(owned.rankId());
                if (current != null && rank.order() > current.order()) {
                    lore.add("");
                    lore.add("<#f0abfc>Апгрейд с " + RankService.plainName(current)
                            + ": доплата <#fef08a>" + (rank.price() - current.price()) + " tk</#fef08a></#f0abfc>");
                }
            }
            lore.add("");
            lore.add("<#f0abfc><bold>Нажмите, чтобы выбрать срок ➜</bold></#f0abfc>");
            inv.setItem(CONTENT_SLOTS[i], button(rank.material(), rank.displayName(), lore,
                    owned != null && owned.rankId().equals(rank.id())));
        }
        inv.setItem(18, backButton());
        player.openInventory(inv);
        click(player);
    }

    // ============================ СРОК ============================

    public void openDurations(Player player, DonateRank rank) {
        ShopHolders.DurationHolder holder = new ShopHolders.DurationHolder(rank.id());
        Inventory inv = Bukkit.createInventory(holder, 27,
                Text.mm("<gradient:#e9d5ff:#7c3aed><bold>✦ " + stripTags(rank.displayName()) + " — срок ✦</bold></gradient>"));
        holder.bind(inv);
        decorate(inv);

        List<String> previewLore = new ArrayList<>(rank.description());
        OwnedRank owned = plugin.ranks().activeOwned(player.getUniqueId());
        if (owned != null) {
            DonateRank current = plugin.config().rank(owned.rankId());
            previewLore.add("");
            previewLore.add("<#c4b5fd>Сейчас у вас: <#f0abfc>"
                    + (current == null ? owned.rankId() : RankService.plainName(current))
                    + "</#f0abfc> (" + plugin.ranks().durationText(owned) + ")</#c4b5fd>");
        }
        inv.setItem(4, button(rank.material(), rank.displayName(), previewLore, true));

        int[] slots = {11, 13, 15, 20, 22, 24};
        List<DurationOption> durations = plugin.config().durations();
        for (int i = 0; i < durations.size() && i < slots.length; i++) {
            DurationOption duration = durations.get(i);
            Quote quote = plugin.ranks().quote(player, rank, duration);
            List<String> lore = new ArrayList<>();
            if (!quote.allowed()) {
                lore.add("<#f87171>" + quote.denyReason() + "</#f87171>");
                inv.setItem(slots[i], button(Material.BARRIER, duration.displayName(), lore, false));
                continue;
            }
            lore.add("<#c4b5fd>Цена: <#f0abfc><bold>" + quote.price() + " tk</bold></#f0abfc></#c4b5fd>");
            lore.add("<#a78bfa>Ваш баланс: " + balance(player) + " tk</#a78bfa>");
            switch (quote.action()) {
                case "UPGRADE" -> lore.add("<#f0abfc>Доплата до старшей привилегии навсегда</#f0abfc>");
                case "EXTEND" -> lore.add("<#a7f3d0>Срок прибавится к текущему</#a7f3d0>");
                case "CONVERT" -> lore.add("<#f0abfc>Временная станет вечной</#f0abfc>");
                case "REPLACE" -> lore.add("<#fbbf24>Заменит текущую привилегию, срок — с момента покупки</#fbbf24>");
                default -> lore.add(duration.permanent()
                        ? "<#c4b5fd>Привилегия навсегда</#c4b5fd>"
                        : "<#c4b5fd>Привилегия на " + duration.days() + " дн.</#c4b5fd>");
            }
            lore.add("");
            lore.add(balance(player) >= quote.price()
                    ? "<#a7f3d0><bold>Нажмите, чтобы продолжить ➜</bold></#a7f3d0>"
                    : "<#f87171>Не хватает " + (quote.price() - balance(player)) + " tk</#f87171>");
            inv.setItem(slots[i], button(duration.material(), duration.displayName(), lore, duration.permanent()));
        }
        inv.setItem(18, backButton());
        player.openInventory(inv);
        click(player);
    }

    // ============================ ПРЕДМЕТЫ ============================

    public void openItems(Player player) {
        ShopHolders.ItemsHolder holder = new ShopHolders.ItemsHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm(TITLE_ITEMS));
        holder.bind(inv);
        decorate(inv);
        inv.setItem(4, balanceHead(player));

        List<ShopProduct> products = plugin.config().products();
        for (int i = 0; i < products.size() && i < CONTENT_SLOTS.length; i++) {
            ShopProduct product = products.get(i);
            List<String> lore = new ArrayList<>(product.description());
            lore.add("");
            lore.add("<#c4b5fd>Цена: <#f0abfc><bold>" + product.price() + " tk</bold></#f0abfc></#c4b5fd>");
            lore.add("<#a78bfa>Ваш баланс: " + balance(player) + " tk</#a78bfa>");
            lore.add("");
            lore.add(balance(player) >= product.price()
                    ? "<#f0abfc><bold>Нажмите, чтобы купить ➜</bold></#f0abfc>"
                    : "<#f87171>Не хватает " + (product.price() - balance(player)) + " tk</#f87171>");
            inv.setItem(CONTENT_SLOTS[i], button(product.material(), product.displayName(), lore, true));
        }
        inv.setItem(18, backButton());
        player.openInventory(inv);
        click(player);
    }

    // ============================ ПОДТВЕРЖДЕНИЕ ============================

    public void openConfirmRank(Player player, DonateRank rank, DurationOption duration) {
        Quote quote = plugin.ranks().quote(player, rank, duration);
        ShopHolders.ConfirmHolder holder = ShopHolders.ConfirmHolder.rank(rank.id(), duration.id());
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm(TITLE_CONFIRM));
        holder.bind(inv);
        decorate(inv);
        inv.setItem(4, button(rank.material(), rank.displayName(),
                List.of("<#c4b5fd>Срок: </#c4b5fd>" + duration.displayName(),
                        "<#c4b5fd>Цена: <#f0abfc><bold>" + quote.price() + " tk</bold></#f0abfc></#c4b5fd>"), true));
        inv.setItem(13, infoPaper(player, quote.price()));
        inv.setItem(11, confirmButton(quote.price()));
        inv.setItem(15, cancelButton());
        player.openInventory(inv);
        click(player);
    }

    public void openConfirmItem(Player player, ShopProduct product) {
        ShopHolders.ConfirmHolder holder = ShopHolders.ConfirmHolder.item(product.id());
        Inventory inv = Bukkit.createInventory(holder, 27, Text.mm(TITLE_CONFIRM));
        holder.bind(inv);
        decorate(inv);
        List<String> lore = new ArrayList<>(product.description());
        lore.add("");
        lore.add("<#c4b5fd>Цена: <#f0abfc><bold>" + product.price() + " tk</bold></#f0abfc></#c4b5fd>");
        inv.setItem(4, button(product.material(), product.displayName(), lore, true));
        inv.setItem(13, infoPaper(player, product.price()));
        inv.setItem(11, confirmButton(product.price()));
        inv.setItem(15, cancelButton());
        player.openInventory(inv);
        click(player);
    }

    // ============================ УТИЛИТЫ ОФОРМЛЕНИЯ ============================

    /** Фиолетовая рамка + угловые аметисты. */
    private void decorate(Inventory inv) {
        ItemStack filler = pane();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot < 9 || slot >= inv.getSize() - 9 || slot % 9 == 0 || slot % 9 == 8) {
                inv.setItem(slot, filler);
            }
        }
        ItemStack shard = button(Material.AMETHYST_SHARD, " ", List.of(), false);
        inv.setItem(0, shard);
        inv.setItem(8, shard);
        inv.setItem(inv.getSize() - 9, shard);
        inv.setItem(inv.getSize() - 1, shard);
    }

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack balanceHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.displayName(Text.itemText("<gradient:#f5d0fe:#a855f7><bold>" + player.getName() + "</bold></gradient>"));
            OwnedRank owned = plugin.ranks().activeOwned(player.getUniqueId());
            List<String> lore = new ArrayList<>();
            lore.add("<#c4b5fd>Баланс: <#f0abfc><bold>" + balance(player) + " tk</bold></#f0abfc> (TwixCoin)</#c4b5fd>");
            if (owned == null) {
                lore.add("<#a78bfa>Привилегия: нет</#a78bfa>");
            } else {
                DonateRank rank = plugin.config().rank(owned.rankId());
                lore.add("<#a78bfa>Привилегия: </#a78bfa>" + (rank == null ? owned.rankId() : rank.displayName())
                        + " <#c4b5fd>(" + plugin.ranks().durationText(owned) + ")</#c4b5fd>");
            }
            skull.lore(Text.itemLore(lore));
            head.setItemMeta(skull);
        }
        return head;
    }

    private ItemStack infoPaper(Player player, int price) {
        int balance = balance(player);
        return button(Material.PAPER,
                "<gradient:#e9d5ff:#a78bfa><bold>Итого</bold></gradient>",
                List.of(
                        "<#c4b5fd>К оплате: <#f0abfc><bold>" + price + " tk</bold></#f0abfc></#c4b5fd>",
                        "<#c4b5fd>Ваш баланс: <#f0abfc>" + balance + " tk</#f0abfc></#c4b5fd>",
                        "<#c4b5fd>После покупки: <#f0abfc>" + Math.max(0, balance - price) + " tk</#f0abfc></#c4b5fd>"),
                false);
    }

    private ItemStack confirmButton(int price) {
        return button(Material.GREEN_CONCRETE,
                "<#a7f3d0><bold>✔ Подтвердить покупку</bold></#a7f3d0>",
                List.of("<#c4b5fd>Спишется <#f0abfc>" + price + " tk</#f0abfc> (TwixCoin)</#c4b5fd>"),
                false);
    }

    private ItemStack cancelButton() {
        return button(Material.RED_CONCRETE,
                "<#f87171><bold>✘ Отмена</bold></#f87171>",
                List.of("<#c4b5fd>Вернуться назад</#c4b5fd>"),
                false);
    }

    private ItemStack backButton() {
        return button(Material.ARROW,
                "<gradient:#d8b4fe:#8b5cf6><bold>⬅ Назад</bold></gradient>",
                List.of("<#a78bfa>В главное меню</#a78bfa>"),
                false);
    }

    private ItemStack button(Material material, String name, List<String> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.itemText(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(Text.itemLore(lore));
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private int balance(Player player) {
        return plugin.points().available() ? plugin.points().balance(player.getUniqueId()) : 0;
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.1f);
    }

    private static String plainDuration(DurationOption d) {
        return d.permanent() ? "Навсегда" : d.days() + " дней";
    }

    /** Убрать MiniMessage-теги (для заголовка окна, где градиент строится заново). */
    private static String stripTags(String text) {
        return text == null ? "" : text.replaceAll("<[^>]+>", "");
    }
}
