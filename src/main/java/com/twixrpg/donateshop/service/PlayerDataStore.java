package com.twixrpg.donateshop.service;

import com.twixrpg.donateshop.model.OwnedRank;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Хранение активных привилегий игроков в plugins/DonateShop/players.yml. */
public final class PlayerDataStore {

    private final File file;
    private final Logger logger;
    private final Map<UUID, OwnedRank> owned = new ConcurrentHashMap<>();

    public PlayerDataStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "players.yml");
        this.logger = logger;
    }

    public void load() {
        owned.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getConfigurationSection("players") == null) return;
        for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String rankId = yaml.getString("players." + key + ".rank");
                long expires = yaml.getLong("players." + key + ".expires", OwnedRank.PERMANENT);
                if (rankId != null && !rankId.isBlank()) {
                    owned.put(uuid, new OwnedRank(rankId.toLowerCase(), expires));
                }
            } catch (IllegalArgumentException ignored) {
                logger.warning("[DonateShop] Битый UUID в players.yml: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, OwnedRank> entry : owned.entrySet()) {
            yaml.set("players." + entry.getKey() + ".rank", entry.getValue().rankId());
            yaml.set("players." + entry.getKey() + ".expires", entry.getValue().expiresAtMillis());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.warning("[DonateShop] Не удалось сохранить players.yml: " + e.getMessage());
        }
    }

    public OwnedRank get(UUID uuid) {
        return owned.get(uuid);
    }

    public void set(UUID uuid, OwnedRank rank) {
        owned.put(uuid, rank);
        save();
    }

    public void remove(UUID uuid) {
        if (owned.remove(uuid) != null) {
            save();
        }
    }
}
