package com.voiceerase.client;

import com.voiceerase.VoiceEraseMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class VoiceEraseClient implements ClientModInitializer {
    private static VoiceListener listener;
    private static Thread thread;
    private static KeyBinding toggleKey;

    /** Nomes (chaves de traducao) ja apagados, sincronizado pelo servidor. */
    private static volatile List<String> erased = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voiceerase.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.voiceerase"));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> startListening());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopListening());

        ClientPlayNetworking.registerGlobalReceiver(VoiceEraseMod.SYNC_PACKET, (client, handler, buf, sender) -> {
            int n = buf.readVarInt();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(buf.readString(256));
            client.execute(() -> erased = list);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                if (listener != null && client.player != null) {
                    listener.setMuted(!listener.isMuted());
                    client.player.sendMessage(Text.literal("[Voz] Microfone " +
                            (listener.isMuted() ? "DESLIGADO" : "LIGADO")), true);
                }
            }
        });

        // /voz apelido criper = creeper
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("voz")
                        .then(ClientCommandManager.literal("apelido")
                                .then(ClientCommandManager.argument("texto", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String t = StringArgumentType.getString(ctx, "texto");
                                            int eq = t.indexOf('=');
                                            if (eq <= 0 || eq == t.length() - 1) {
                                                ctx.getSource().sendFeedback(Text.literal(
                                                        "[Voz] Use assim: /voz apelido criper = creeper"));
                                                return 0;
                                            }
                                            try {
                                                Aliases.add(t.substring(0, eq), t.substring(eq + 1));
                                                ctx.getSource().sendFeedback(Text.literal("[Voz] Apelido salvo!"));
                                                return 1;
                                            } catch (Exception e) {
                                                ctx.getSource().sendFeedback(Text.literal("[Voz] Erro ao salvar: " + e));
                                                return 0;
                                            }
                                        })))));

        HudRenderCallback.EVENT.register(VoiceEraseClient::drawHud);
    }

    private static void startListening() {
        stopListening();
        listener = new VoiceListener();
        thread = new Thread(listener, "VoiceErase-Listener");
        thread.setDaemon(true);
        thread.start();
    }

    private static void stopListening() {
        if (listener != null) listener.stop();
        listener = null;
        thread = null;
        erased = new ArrayList<>();
    }

    private static void drawHud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null) return;
        int w = ctx.getScaledWindowWidth();
        var tr = mc.textRenderer;

        // Caixinha "APAGADOS N" no canto direito
        String title = "APAGADOS";
        String count = String.valueOf(erased.size());
        int boxW = tr.getWidth(title) + tr.getWidth(count) + 20;
        int x2 = w - 4, x1 = x2 - boxW, y1 = 40, y2 = y1 + 14;
        ctx.fill(x1, y1, x2, y2, 0xAA000000);
        ctx.fill(x1, y1, x1 + 2, y2, 0xFFCC2222);
        ctx.drawTextWithShadow(tr, title, x1 + 6, y1 + 3, 0xFFDDDDDD);
        ctx.drawTextWithShadow(tr, count, x2 - tr.getWidth(count) - 5, y1 + 3, 0xFFFF4444);

        // Ultimos 5 apagados
        int y = y2 + 3;
        int start = Math.max(0, erased.size() - 5);
        for (int i = erased.size() - 1; i >= start; i--) {
            Text t = Text.translatable(erased.get(i));
            int tw = tr.getWidth(t);
            ctx.fill(w - 8 - tw - 4, y, w - 4, y + 11, 0x88000000);
            ctx.drawTextWithShadow(tr, t, w - 8 - tw, y + 2, 0xFF999999);
            y += 12;
        }

        // Status do microfone + ultima frase ouvida (ajuda a depurar)
        String line = "🎤 " + VoiceListener.status
                + (VoiceListener.lastHeard.isEmpty() ? "" : " | " + VoiceListener.lastHeard);
        ctx.drawTextWithShadow(tr, line, 6, ctx.getScaledWindowHeight() - 50, 0xFF88CCFF);
    }
}
