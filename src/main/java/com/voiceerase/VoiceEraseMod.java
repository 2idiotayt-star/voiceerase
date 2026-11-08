package com.voiceerase;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class VoiceEraseMod implements ModInitializer {
    public static final String MOD_ID = "voiceerase";
    /** cliente -> servidor: "apague isso" */
    public static final Identifier ERASE_PACKET = new Identifier(MOD_ID, "erase");
    /** servidor -> cliente: lista do que ja foi apagado (para o HUD) */
    public static final Identifier SYNC_PACKET = new Identifier(MOD_ID, "sync");

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(ERASE_PACKET, (server, player, handler, buf, sender) -> {
            // le tudo ANTES de mandar pra thread do servidor
            String label = buf.readString(256);
            int n = buf.readVarInt();
            if (n < 0 || n > 64) return;
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) ids.add(buf.readString(256));
            server.execute(() -> Eraser.erase(server, player, label, ids));
        });

        ServerTickEvents.END_WORLD_TICK.register(Eraser::tickWorld);
        ServerChunkEvents.CHUNK_LOAD.register(Eraser::onChunkLoad);
        ServerEntityEvents.ENTITY_LOAD.register(Eraser::onEntityLoad);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                Eraser.syncTo(handler.getPlayer(), server));

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
                dispatcher.register(CommandManager.literal("voiceerase")
                        .then(CommandManager.literal("list").executes(ctx -> {
                            ErasedState st = ErasedState.get(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("Apagados: " + st.labels.size()), false);
                            for (String l : st.labels) {
                                ctx.getSource().sendFeedback(() -> Text.literal(" - ").append(Text.translatable(l)), false);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(CommandManager.literal("reset")
                                .requires(src -> src.hasPermissionLevel(2))
                                .executes(ctx -> {
                                    Eraser.reset(ctx.getSource().getServer());
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "Lista zerada (o que ja sumiu nao volta)."), true);
                                    return Command.SINGLE_SUCCESS;
                                }))));
    }
}
