package com.voiceerase.client;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Transforma o que foi falado em "coisas do Minecraft" usando os nomes traduzidos
 * no idioma do jogo (coloque o jogo em Portugues (Brasil)!).
 * Precisa rodar na thread principal do cliente.
 */
public final class NameMatcher {
    private NameMatcher() { }

    public record Group(String labelKey, Set<String> ids) { }

    private static Map<String, Group> dict;
    private static List<String> sortedNames;
    private static String dictLang;

    public static String norm(String s) {
        s = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replaceAll("[^a-z0-9 ]", " ");
        StringBuilder sb = new StringBuilder();
        for (String tok : s.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            // plural simples: "pedras" -> "pedra", "zumbis" -> "zumbi"
            if (tok.length() > 3 && tok.endsWith("s")) tok = tok.substring(0, tok.length() - 1);
            if (sb.length() > 0) sb.append(' ');
            sb.append(tok);
        }
        return sb.toString();
    }

    private static void ensureDict() {
        String lang = MinecraftClient.getInstance().options.language;
        if (dict != null && lang.equals(dictLang)) return;
        dictLang = lang;
        dict = new LinkedHashMap<>();

        for (Block b : Registries.BLOCK) {
            if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) continue;
            String key = b.getTranslationKey();
            Group g = group(key);
            if (g == null) continue;
            g.ids().add("block:" + Registries.BLOCK.getId(b));
            Item it = b.asItem();
            if (it != Items.AIR) g.ids().add("item:" + Registries.ITEM.getId(it));
        }
        for (Item it : Registries.ITEM) {
            if (it == Items.AIR) continue;
            Group g = group(it.getTranslationKey());
            if (g != null) g.ids().add("item:" + Registries.ITEM.getId(it));
        }
        for (EntityType<?> t : Registries.ENTITY_TYPE) {
            if (t == EntityType.PLAYER) continue;
            Group g = group(t.getTranslationKey());
            if (g != null) g.ids().add("entity:" + Registries.ENTITY_TYPE.getId(t));
        }

        sortedNames = new ArrayList<>(dict.keySet());
        // nomes maiores primeiro: "pedra lisa" ganha de "pedra"
        sortedNames.sort(Comparator.comparingInt(String::length).reversed());
    }

    private static Group group(String translationKey) {
        if (!I18n.hasTranslation(translationKey)) return null;
        String name = norm(I18n.translate(translationKey));
        if (name.length() < 3) return null;
        return dict.computeIfAbsent(name, n -> new Group(translationKey, new LinkedHashSet<>()));
    }

    public static List<Group> match(String spoken) {
        ensureDict();
        List<Group> out = new ArrayList<>();
        String padded = " " + norm(spoken) + " ";
        for (String name : sortedNames) {
            String needle = " " + name + " ";
            int idx = padded.indexOf(needle);
            if (idx < 0) continue;
            out.add(dict.get(name));
            // consome o trecho para nomes menores nao casarem dentro dele
            padded = padded.substring(0, idx + 1) + " ".repeat(name.length()) + padded.substring(idx + 1 + name.length());
        }
        return out;
    }
}
