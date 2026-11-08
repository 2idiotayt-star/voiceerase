package com.voiceerase.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Apelidos: "o que o reconhecimento de voz escreve" = "nome do bloco/mob no jogo".
 * Fica no arquivo  (pasta do jogo)/voiceerase/aliases.txt  e pode ser editado a mao.
 */
public final class Aliases {
    private Aliases() { }

    private static final String DEFAULTS = """
            # Apelidos de voz.
            # Formato:   o que o jogo ENTENDEU = nome do bloco/mob (no idioma do jogo)
            # Voce pode editar este arquivo, ou usar no chat:  /voz apelido criper = creeper
            # Dica: o texto azul no canto de baixo da tela mostra o que o jogo entendeu.

            criper = creeper
            cripe = creeper
            creper = creeper
            crepa = creeper
            kriper = creeper
            cri per = creeper
            cree per = creeper
            crip = creeper

            ender man = enderman
            ender men = enderman
            ender ma = enderman

            neter rac = netherrack
            neter rack = netherrack
            nether rac = netherrack
            nether rack = netherrack
            neder rac = netherrack
            neter raque = netherrack
            netherack = netherrack

            gast = ghast
            blaiz = blaze
            bleiz = blaze
            pig lin = piglin
            piglim = piglin
            """;

    private static Map<String, String> map = new LinkedHashMap<>();
    private static long stamp = -1;

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("voiceerase").resolve("aliases.txt");
    }

    /** Devolve os apelidos (ja normalizados). Relê o arquivo se ele mudou. */
    public static synchronized Map<String, String> get() {
        try {
            Path f = file();
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                Files.writeString(f, DEFAULTS, StandardCharsets.UTF_8);
            }
            long s = Files.getLastModifiedTime(f).toMillis();
            if (s != stamp) {
                reload(f);
                stamp = s;
            }
        } catch (IOException ignored) { }
        return map;
    }

    private static void reload(Path f) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String from = NameMatcher.norm(line.substring(0, eq));
            String to = NameMatcher.norm(line.substring(eq + 1));
            if (!from.isEmpty() && !to.isEmpty()) m.put(from, to);
        }
        map = m;
    }

    public static synchronized void add(String from, String to) throws IOException {
        get(); // garante que o arquivo existe
        Files.writeString(file(), "\n" + from.trim() + " = " + to.trim() + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        stamp = -1; // forca reler
    }
}
