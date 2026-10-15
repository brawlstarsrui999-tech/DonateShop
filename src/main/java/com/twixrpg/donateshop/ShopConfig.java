package com.twixrpg.donateshop;

import com.twixrpg.donateshop.model.DonateRank;
import com.twixrpg.donateshop.model.DurationOption;
import com.twixrpg.donateshop.model.ShopProduct;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Загрузка и доступ к config.yml: привилегии, предметы, сроки, сообщения. */
public final class ShopConfig {

    private final Logger logger;
    private List<DonateRank> ranks = List.of();
    private Map<String, DonateRank> ranksById = Map.of();
    private List<ShopProduct> products = List.of();
    private Map<String, ShopProduct> productsById = Map.of();
    private List<DurationOption> durations = List.of();
    private Map<String, DurationOption> durationsById = Map.of();
    private Map<String, String> messages = Map.of();
    private String prefix = "";
    private String rounding = "UP";
    private List<String> itemsPluginNames = List.of("TwixRPGItems", "SuperItemsTwix");

    public ShopConfig(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration cfg) {
        rounding = cfg.getString("price-rounding", "UP");
        itemsPluginNames = cfg.getStringList("items-plugin-names");
        if (itemsPluginNames.isEmpty()) {
            itemsPluginNames = List.of("TwixRPGItems", "SuperItemsTwix");
        }

        // ---- привилегии ----
        List<DonateRank> loadedRanks = new ArrayList<>();
        Map<String, DonateRank> byId = new LinkedHashMap<>();
        ConfigurationSection ranksSection = cfg.getConfigurationSection("ranks");
        if (ranksSection != null) {
            for (String id : ranksSection.getKeys(false)) {
                ConfigurationSection s = ranksSection.getConfigurationSection(id);
                if (s == null) continue;
                Material material = parseMaterial(s.getString("material"), Material.PAPER, "ranks." + id);
                DonateRank rank = new DonateRank(
                        id.toLowerCase(),
                        s.getString("display-name", id),
                        s.getString("group", id.toLowerCase()),
                        Math.max(0, s.getInt("price", 0)),
                        s.getInt("order", 0),
                        material,
                        s.getStringList("description"));
                loadedRanks.add(rank);
                byId.put(rank.id(), rank);
            }
        }
        loadedRanks.sort(Comparator.comparingInt(DonateRank::order));
        this.ranks = List.copyOf(loadedRanks);
        this.ranksById = Map.copyOf(byId);

        // ---- предметы ----
        List<ShopProduct> loadedProducts = new ArrayList<>();
        Map<String, ShopProduct> productsMap = new LinkedHashMap<>();
        ConfigurationSection itemsSection = cfg.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String id : itemsSection.getKeys(false)) {
                ConfigurationSection s = itemsSection.getConfigurationSection(id);
                if (s == null) continue;
                Material material = parseMaterial(s.getString("material"), Material.PAPER, "items." + id);
                ShopProduct product = new ShopProduct(
                        id.toLowerCase(),
                        s.getString("display-name", id),
                        Math.max(0, s.getInt("price", 0)),
                        material,
                        s.getString("give-command", ""),
                        s.getStringList("description"));
                loadedProducts.add(product);
                productsMap.put(product.id(), product);
            }
        }
        this.products = List.copyOf(loadedProducts);
        this.productsById = Map.copyOf(productsMap);

        // ---- сроки ----
        List<DurationOption> loadedDurations = new ArrayList<>();
        Map<String, DurationOption> durationsMap = new LinkedHashMap<>();
        for (Map<?, ?> raw : cfg.getMapList("durations")) {
            Object idRaw = raw.get("id");
            String id = idRaw == null ? "" : String.valueOf(idRaw).toLowerCase();
            if (id.isEmpty()) continue;
            int days = raw.get("days") instanceof Number n ? n.intValue() : 0;
            int percent = raw.get("percent") instanceof Number n ? n.intValue() : 100;
            Material material = parseMaterial(
                    raw.get("material") == null ? null : String.valueOf(raw.get("material")),
                    Material.CLOCK, "durations." + id);
            String name = raw.get("display-name") == null ? id : String.valueOf(raw.get("display-name"));
            DurationOption duration = new DurationOption(id, days, percent, material, name);
            loadedDurations.add(duration);
            durationsMap.put(id, duration);
        }
        if (loadedDurations.isEmpty()) {
            logger.warning("[DonateShop] В конфиге нет сроков (durations) — добавлены значения по умолчанию.");
            loadedDurations = new ArrayList<>(List.of(
                    new DurationOption("7d", 7, 25, Material.CLOCK, "7 дней"),
                    new DurationOption("15d", 15, 50, Material.RECOVERY_COMPASS, "15 дней"),
                    new DurationOption("forever", 0, 100, Material.NETHER_STAR, "Навсегда")));
            durationsMap.clear();
            for (DurationOption d : loadedDurations) durationsMap.put(d.id(), d);
        }
        this.durations = List.copyOf(loadedDurations);
        this.durationsById = Map.copyOf(durationsMap);

        // ---- сообщения ----
        Map<String, String> msgs = new LinkedHashMap<>();
        ConfigurationSection msgSection = cfg.getConfigurationSection("messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                msgs.put(key, msgSection.getString(key, ""));
            }
        }
        this.messages = Map.copyOf(msgs);
        this.prefix = msgs.getOrDefault("prefix", "");
    }

    private Material parseMaterial(String name, Material fallback, String where) {
        if (name == null || name.isBlank()) return fallback;
        Material material = Material.matchMaterial(name.trim());
        if (material == null) {
            logger.warning("[DonateShop] Неизвестный материал '" + name + "' в " + where + ", использую " + fallback);
            return fallback;
        }
        return material;
    }

    /** Цена за срок: процент от полной цены с округлением из конфига. Для навсегда — полная цена. */
    public int priceFor(DonateRank rank, DurationOption duration) {
        if (duration.permanent()) return rank.price();
        long numerator = (long) rank.price() * duration.percent();
        return switch (rounding == null ? "UP" : rounding.toUpperCase()) {
            case "DOWN" -> (int) (numerator / 100);
            case "NEAREST" -> (int) ((numerator + 50) / 100);
            default -> (int) ((numerator + 99) / 100); // UP
        };
    }

    public List<DonateRank> ranks() {
        return ranks;
    }

    public DonateRank rank(String id) {
        return id == null ? null : ranksById.get(id.toLowerCase());
    }

    public List<ShopProduct> products() {
        return products;
    }

    public ShopProduct product(String id) {
        return id == null ? null : productsById.get(id.toLowerCase());
    }

    public List<DurationOption> durations() {
        return durations;
    }

    public DurationOption duration(String id) {
        return id == null ? null : durationsById.get(id.toLowerCase());
    }

    public DurationOption permanentDuration() {
        for (DurationOption d : durations) {
            if (d.permanent()) return d;
        }
        return null;
    }

    public List<String> itemsPluginNames() {
        return itemsPluginNames;
    }

    public String message(String key) {
        return messages.getOrDefault(key, "");
    }

    /** Сообщение с префиксом и плейсхолдерами. */
    public String msg(String key, String... pairs) {
        return prefix + Text.fill(message(key), pairs);
    }
}
