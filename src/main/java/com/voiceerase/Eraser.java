package com.voiceerase;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Toda a logica de "apagar" do lado do servidor. */
public final class Eraser {
    private Eraser() { }

    /** Raio (em chunks) varrido ao redor de cada jogador. */
    private static final int RADIUS_NOW = 8;
    private static final int RADIUS_TICK = 4;

    // flags: NOTIFY_LISTENERS (2) | FORCE_STATE (16) | SKIP_DROPS (32)
    private static final int FLAGS = 2 | 16 | 32;

    public static void erase(MinecraftServer server, ServerPlayerEntity source, String label, List<String> ids) {
        ErasedState st = ErasedState.get(server);
        if (!st.add(label, ids)) return;
        st.markDirty();

        server.getPlayerManager().broadcast(
                Text.literal("✖ Apagado: ").formatted(Formatting.RED)
                        .append(Text.translatable(label).formatted(Formatting.WHITE)),
                false);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) syncTo(p, server);
        for (ServerWorld w : server.getWorlds()) sweepWorld(w, st, RADIUS_NOW);
    }

    public static void reset(MinecraftServer server) {
        ErasedState st = ErasedState.get(server);
        st.clear();
        st.markDirty();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) syncTo(p, server);
    }

    public static void syncTo(ServerPlayerEntity player, MinecraftServer server) {
        ErasedState st = ErasedState.get(server);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(st.labels.size());
        for (String l : st.labels) buf.writeString(l, 256);
        ServerPlayNetworking.send(player, VoiceEraseMod.SYNC_PACKET, buf);
    }

    // ---------------------------------------------------------------- eventos

    public static void tickWorld(ServerWorld world) {
        if (world.getTime() % 20 != 0) return;
        ErasedState st = ErasedState.get(world.getServer());
        if (st.isEmpty()) return;
        sweepWorld(world, st, RADIUS_TICK);
    }

    public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        ErasedState st = ErasedState.get(world.getServer());
        if (st.blocks.isEmpty()) return;
        sweepChunk(world, chunk, st, false);
    }

    public static void onEntityLoad(Entity entity, ServerWorld world) {
        ErasedState st = ErasedState.get(world.getServer());
        if (st.isEmpty()) return;
        if (shouldRemove(entity, st)) entity.discard();
    }

    // ---------------------------------------------------------------- varredura

    private static boolean shouldRemove(Entity e, ErasedState st) {
        if (e instanceof PlayerEntity) return false;
        if (st.entities.contains(e.getType())) return true;
        return e instanceof ItemEntity ie && st.items.contains(ie.getStack().getItem());
    }

    private static void sweepWorld(ServerWorld world, ErasedState st, int radius) {
        // Blocos ao redor de cada jogador
        if (!st.blocks.isEmpty()) {
            for (ServerPlayerEntity p : world.getPlayers()) {
                int cx = p.getBlockPos().getX() >> 4;
                int cz = p.getBlockPos().getZ() >> 4;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        WorldChunk c = world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                        if (c != null) sweepChunk(world, c, st, true);
                    }
                }
            }
        }

        // Entidades / itens no chao
        List<Entity> toRemove = new ArrayList<>();
        for (Entity e : world.iterateEntities()) {
            if (shouldRemove(e, st)) toRemove.add(e);
        }
        for (Entity e : toRemove) e.discard();

        // Inventarios dos jogadores
        if (!st.items.isEmpty()) {
            for (ServerPlayerEntity p : world.getPlayers()) {
                clean(p.getInventory(), st);
                clean(p.getEnderChestInventory(), st);
            }
        }
    }

    private static void clean(Inventory inv, ErasedState st) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && st.items.contains(s.getItem())) inv.setStack(i, ItemStack.EMPTY);
        }
    }

    private static void sweepChunk(ServerWorld world, WorldChunk chunk, ErasedState st, boolean live) {
        Predicate<BlockState> match = s -> st.blocks.contains(s.getBlock());
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        ChunkPos cp = chunk.getPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty() || !section.hasAny(match)) continue;
            int baseY = (bottom + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!match.test(section.getBlockState(x, y, z))) continue;
                        pos.set(cp.getStartX() + x, baseY + y, cp.getStartZ() + z);
                        if (live) world.setBlockState(pos, air, FLAGS);
                        else chunk.setBlockState(pos, air, false);
                    }
                }
            }
        }
    }
}
