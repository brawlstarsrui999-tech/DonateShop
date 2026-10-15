package com.twixrpg.donateshop;

import com.twixrpg.donateshop.command.DonshopCommand;
import com.twixrpg.donateshop.gui.GuiListener;
import com.twixrpg.donateshop.gui.MenuManager;
import com.twixrpg.donateshop.hook.PlayerPointsHook;
import com.twixrpg.donateshop.service.PlayerDataStore;
import com.twixrpg.donateshop.service.PurchaseService;
import com.twixrpg.donateshop.service.RankService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DonateShop — донат-магазин Twix (Paper 1.21.11).
 * /donshop: Привилегии (LuckPerms-группы на 7/15 дней или навсегда, с апгрейдом
 * доплатой) и Предметы (супер-предметы плагина TwixRPGItems/SuperItemsTwix).
 * Валюта — TwixCoin (PlayerPoints).
 */
public final class DonateShopPlugin extends JavaPlugin implements Listener {

    private ShopConfig config;
    private PlayerPointsHook points;
    private PlayerDataStore dataStore;
    private RankService rankService;
    private PurchaseService purchaseService;
    private MenuManager menuManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new ShopConfig(getLogger());
        config.load(getConfig());

        dataStore = new PlayerDataStore(getDataFolder(), getLogger());
        dataStore.load();

        points = new PlayerPointsHook(getLogger());
        points.init();
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("[DonateShop] LuckPerms не найден — привилегии выдаваться не будут.");
        }
        logItemsPluginStatus();

        rankService = new RankService(this, dataStore);
        purchaseService = new PurchaseService(this);
        menuManager = new MenuManager(this);

        DonshopCommand commandHandler = new DonshopCommand(this);
        PluginCommand command = getCommand("donshop");
        if (command != null) {
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
        } else {
            getLogger().severe("[DonateShop] Команда donshop не найдена в plugin.yml!");
        }

        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(this, this);

        // проверка истечения привилегий у онлайн-игроков — раз в минуту
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                rankService.checkExpiry(player);
            }
        }, 1200L, 1200L);

        getLogger().info("[DonateShop] Включён. Привилегий: " + config.ranks().size()
                + ", предметов: " + config.products().size());
    }

    @Override
    public void onDisable() {
        if (dataStore != null) {
            dataStore.save();
        }
    }

    /** При входе: проверить истечение срока и пересинхронизировать временную группу LuckPerms. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            if (!rankService.checkExpiry(player)) {
                rankService.resyncOnJoin(player);
            }
        }, 20L);
    }

    public void reloadShop() {
        reloadConfig();
        config.load(getConfig());
        dataStore.load();
        points.init();
        logItemsPluginStatus();
    }

    private void logItemsPluginStatus() {
        for (String name : config.itemsPluginNames()) {
            if (Bukkit.getPluginManager().getPlugin(name) != null) {
                getLogger().info("[DonateShop] Плагин предметов найден: " + name);
                return;
            }
        }
        getLogger().warning("[DonateShop] Плагин предметов (" + String.join(", ", config.itemsPluginNames())
                + ") не найден — выдача предметов работать не будет, TwixCoin при покупке вернутся игроку.");
    }

    public ShopConfig config() {
        return config;
    }

    public PlayerPointsHook points() {
        return points;
    }

    public RankService ranks() {
        return rankService;
    }

    public PurchaseService purchases() {
        return purchaseService;
    }

    public MenuManager menus() {
        return menuManager;
    }
}
