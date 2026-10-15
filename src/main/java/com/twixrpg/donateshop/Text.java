package com.twixrpg.donateshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

/** Рендер MiniMessage-строк из конфига в Adventure-компоненты. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MM.deserialize(text);
    }

    /** Компонент без курсива (для названий/описаний предметов в GUI). */
    public static Component itemText(String text) {
        return mm(text).decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemLore(List<String> lines) {
        List<Component> lore = new ArrayList<>(lines.size());
        for (String line : lines) {
            lore.add(itemText(line));
        }
        return lore;
    }

    /** Подстановка {ключ} -> значение в шаблоне сообщения (до парсинга MiniMessage). */
    public static String fill(String template, String... pairs) {
        String out = template == null ? "" : template;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return out;
    }
}
