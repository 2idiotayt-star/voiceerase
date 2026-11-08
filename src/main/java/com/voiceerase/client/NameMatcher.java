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
 * Ordem: apelidos (aliases.txt) -> nomes exatos -> nomes parecidos (tolera erro de pronuncia).
 * Precisa rodar na thread principal do cliente.
 */
public final class NameMatcher {
    private NameMatcher() { }

    public record Group(String labelKey, Set<String> ids) { }

    private static Map<String, Group> dict;
    private static List<String> sortedNames;
    private static List<String> fuzzyNames;
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

        // candidatos para busca aproximada: nomes de uma palavra so, com 6+ letras
        fuzzyNames = new ArrayList<>();
        for (String n : sortedNames) {
            if (n.length() >= 6 && n.indexOf(' ') < 0) fuzzyNames.add(n);
        }
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

        // 1) apelidos (arquivo aliases.txt)
        List<Map.Entry<String, String>> aliases = new ArrayList<>(Aliases.get().entrySet());
        aliases.sort((a, b) -> b.getKey().length() - a.getKey().length());
        for (Map.Entry<String, String> e : aliases) {
            padded = consume(padded, e.getKey(), dict.get(e.getValue()), out);
        }

        // 2) nomes exatos
        for (String name : sortedNames) {
            padded = consume(padded, name, dict.get(name), out);
        }

        // 3) nomes parecidos (erro de pronuncia: "creper" -> "creeper")
        for (String tok : padded.trim().split("\\s+")) {
            if (tok.length() < 5) continue;
            for (String name : fuzzyNames) {
                if (name.charAt(0) != tok.charAt(0)) continue;
                if (Math.abs(name.length() - tok.length()) > 2) continue;
                int maxDist = name.length() >= 9 ? 2 : 1;
                if (distance(tok, name, maxDist) <= maxDist) {
                    Group g = dict.get(name);
                    if (!out.contains(g)) out.add(g);
                }
            }
        }
        return out;
    }

    /** Se o nome aparece no texto, registra o grupo e apaga o trecho (pra nao casar de novo). */
    private static String consume(String padded, String name, Group g, List<Group> out) {
        if (g == null) return padded;
        String needle = " " + name + " ";
        int idx = padded.indexOf(needle);
        if (idx < 0) return padded;
        if (!out.contains(g)) out.add(g);
        return padded.substring(0, idx + 1) + " ".repeat(name.length()) + padded.substring(idx + 1 + name.length());
    }

    /** Distancia de edicao (Levenshtein) com corte: devolve max+1 se passar do limite. */
    private static int distance(String a, String b, int max) {
        int n = a.length(), m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > max) return max + 1;
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[m];
    }
}
