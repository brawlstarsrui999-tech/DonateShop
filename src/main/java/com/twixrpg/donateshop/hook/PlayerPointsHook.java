package com.twixrpg.donateshop.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Доступ к валюте TwixCoin через PlayerPoints API (рефлексия — без compile-зависимости
 * от jar'а PlayerPoints, чтобы проект собирался без него).
 * API PlayerPoints: look(UUID), take(UUID, int), give(UUID, int).
 */
public final class PlayerPointsHook {

    private final Logger logger;
    private Object api;
    private Method lookMethod;
    private Method takeMethod;
    private Method giveMethod;

    public PlayerPointsHook(Logger logger) {
        this.logger = logger;
    }

    public boolean init() {
        api = null;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (plugin == null || !plugin.isEnabled()) {
            logger.warning("[DonateShop] PlayerPoints не найден — покупки за TwixCoin будут недоступны.");
            return false;
        }
        try {
            Object apiInstance = plugin.getClass().getMethod("getAPI").invoke(plugin);
            if (apiInstance == null) {
                logger.warning("[DonateShop] PlayerPoints API недоступен (getAPI вернул null).");
                return false;
            }
            Class<?> apiClass = apiInstance.getClass();
            lookMethod = apiClass.getMethod("look", UUID.class);
            takeMethod = apiClass.getMethod("take", UUID.class, int.class);
            giveMethod = apiClass.getMethod("give", UUID.class, int.class);
            this.api = apiInstance;
            logger.info("[DonateShop] PlayerPoints подключён: валюта TwixCoin активна.");
            return true;
        } catch (Exception e) {
            logger.warning("[DonateShop] Не удалось подключиться к PlayerPoints API: " + e.getMessage());
            return false;
        }
    }

    public boolean available() {
        return api != null;
    }

    public int balance(UUID uuid) {
        if (!available()) return 0;
        try {
            Object result = lookMethod.invoke(api, uuid);
            return result instanceof Number n ? n.intValue() : 0;
        } catch (Exception e) {
            logger.warning("[DonateShop] Ошибка чтения баланса PlayerPoints: " + e.getMessage());
            return 0;
        }
    }

    /** Списать TwixCoin. Возвращает false, если не хватило средств или API недоступен. */
    public boolean take(UUID uuid, int amount) {
        if (!available() || amount <= 0) return amount <= 0;
        try {
            Object result = takeMethod.invoke(api, uuid, amount);
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            logger.warning("[DonateShop] Ошибка списания PlayerPoints: " + e.getMessage());
            return false;
        }
    }

    /** Начислить/вернуть TwixCoin. */
    public boolean give(UUID uuid, int amount) {
        if (!available() || amount <= 0) return amount <= 0;
        try {
            Object result = giveMethod.invoke(api, uuid, amount);
            return !(result instanceof Boolean b) || b;
        } catch (Exception e) {
            logger.warning("[DonateShop] Ошибка начисления PlayerPoints: " + e.getMessage());
            return false;
        }
    }
}
