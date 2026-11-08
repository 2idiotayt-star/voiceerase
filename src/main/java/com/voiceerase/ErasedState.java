package com.voiceerase;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Guarda (por mundo salvo) tudo o que ja foi apagado por voz. */
public class ErasedState extends PersistentState {
    public final Set<String> ids = new LinkedHashSet<>();
    public final List<String> labels = new ArrayList<>();

    // Caches em memoria para checagem rapida.
    public final Set<Block> blocks = new HashSet<>();
    public final Set<Item> items = new HashSet<>();
    public final Set<EntityType<?>> entities = new HashSet<>();

    public static ErasedState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(ErasedState::fromNbt, ErasedState::new, "voiceerase");
    }

    public static ErasedState fromNbt(NbtCompound nbt) {
        ErasedState s = new ErasedState();
        for (NbtElement e : nbt.getList("ids", NbtElement.STRING_TYPE)) {
            String id = e.asString();
            s.ids.add(id);
            s.index(id);
        }
        for (NbtElement e : nbt.getList("labels", NbtElement.STRING_TYPE)) {
            s.labels.add(e.asString());
        }
        return s;
    }

    /** @return true se algo novo foi apagado. */
    public boolean add(String label, List<String> newIds) {
        boolean changed = false;
        for (String id : newIds) {
            if (ids.add(id)) {
                index(id);
                changed = true;
            }
        }
        if (changed) labels.add(label);
        return changed;
    }

    public void clear() {
        ids.clear();
        labels.clear();
        blocks.clear();
        items.clear();
        entities.clear();
    }

    public boolean isEmpty() {
        return blocks.isEmpty() && items.isEmpty() && entities.isEmpty();
    }

    private void index(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) return;
        String kind = id.substring(0, colon);
        Identifier ident = Identifier.tryParse(id.substring(colon + 1));
        if (ident == null) return;
        switch (kind) {
            case "block" -> Registries.BLOCK.getOrEmpty(ident).ifPresent(b -> {
                if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) return;
                blocks.add(b);
                Item it = b.asItem();
                if (it != Items.AIR) items.add(it);
            });
            case "item" -> Registries.ITEM.getOrEmpty(ident).ifPresent(i -> {
                if (i != Items.AIR) items.add(i);
            });
            case "entity" -> Registries.ENTITY_TYPE.getOrEmpty(ident).ifPresent(t -> {
                if (t != EntityType.PLAYER) entities.add(t);
            });
            default -> { }
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList idList = new NbtList();
        for (String s : ids) idList.add(NbtString.of(s));
        NbtList labelList = new NbtList();
        for (String s : labels) labelList.add(NbtString.of(s));
        nbt.put("ids", idList);
        nbt.put("labels", labelList);
        return nbt;
    }
}
