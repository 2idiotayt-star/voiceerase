package com.voiceerase.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voiceerase.VoiceEraseMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Thread que fica ouvindo o microfone e manda o que for reconhecido pro servidor. */
@Environment(EnvType.CLIENT)
public class VoiceListener implements Runnable {
    /** Reage ja nas palavras parciais (mais rapido). Deduplicado, entao nao repete. */
    private static final boolean USE_PARTIAL = true;

    private volatile boolean running = true;
    private volatile boolean muted = false;
    private final Set<String> alreadySent = new HashSet<>();

    public static volatile String lastHeard = "";
    public static volatile String status = "iniciando...";

    public boolean isMuted() { return muted; }
    public void setMuted(boolean m) { muted = m; status = m ? "mutado" : "ouvindo"; }
    public void stop() { running = false; }
    public void resetSent() { synchronized (alreadySent) { alreadySent.clear(); } }

    @Override
    public void run() {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("voiceerase");
            status = "baixando modelo";
            Path modelPath = ModelManager.ensureModel(dir, this::say);

            status = "carregando modelo";
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            try (Model model = new Model(modelPath.toString());
                 Recognizer rec = new Recognizer(model, 16000f)) {

                AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, fmt));
                line.open(fmt);
                line.start();

                status = muted ? "mutado" : "ouvindo";
                say("Microfone ligado! Fale o nome de algo (ex: \"zumbi\", \"pedra\", \"terra\") e vai sumir. Tecla K liga/desliga.");

                byte[] buf = new byte[2048];
                while (running) {
                    int n = line.read(buf, 0, buf.length);
                    if (n <= 0 || muted) continue;
                    String json;
                    boolean isFinal = rec.acceptWaveForm(buf, n);
                    if (isFinal) {
                        json = rec.getResult();
                        handle(json, "text");
                    } else if (USE_PARTIAL) {
                        json = rec.getPartialResult();
                        handle(json, "partial");
                    }
                }
                line.stop();
                line.close();
            }
        } catch (Throwable t) {
            status = "erro";
            say("Erro no microfone/voz: " + t);
            t.printStackTrace();
        }
    }

    private void handle(String json, String field) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (!o.has(field)) return;
            String text = o.get(field).getAsString().trim();
            if (text.isEmpty()) return;
            lastHeard = text;
            MinecraftClient.getInstance().execute(() -> process(text));
        } catch (Exception ignored) { }
    }

    /** Roda na thread principal do cliente. */
    private void process(String text) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) return;
        for (NameMatcher.Group g : NameMatcher.match(text)) {
            synchronized (alreadySent) {
                if (!alreadySent.add(g.labelKey())) continue;
            }
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(g.labelKey(), 256);
            buf.writeVarInt(g.ids().size());
            for (String id : g.ids()) buf.writeString(id, 256);
            ClientPlayNetworking.send(VoiceEraseMod.ERASE_PACKET, buf);
        }
    }

    private void say(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("[Voz] ").formatted(Formatting.AQUA)
                        .append(Text.literal(msg).formatted(Formatting.GRAY)), false);
            }
        });
    }
}
