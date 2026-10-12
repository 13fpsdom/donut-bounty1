package com.example.donutbounty.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Every item in the game (blocks, tools, food, elytra, enchanted apples...), in the game's own registry order. */
public final class ItemCatalog {

    public record Entry(Item item, Identifier id, String name, String searchKey, ItemStack icon) {}

    private static List<Entry> all;

    private ItemCatalog() {}

    public static synchronized List<Entry> all() {
        if (all == null) {
            List<Entry> list = new ArrayList<>();
            for (Item item : Registries.ITEM) {
                if (item == Items.AIR) continue;
                Identifier id = Registries.ITEM.getId(item);
                ItemStack icon = new ItemStack(item);
                String name = icon.getName().getString();
                String key = (name + " " + id.getPath() + " " + id.getNamespace()).toLowerCase(Locale.ROOT);
                list.add(new Entry(item, id, name, key, icon));
            }
            all = Collections.unmodifiableList(list);
        }
        return all;
    }

    /** Every whitespace-separated word must appear in the item's name or id. */
    public static List<Entry> filter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all();
        String[] tokens = q.split("\\s+");
        List<Entry> out = new ArrayList<>();
        outer:
        for (Entry e : all()) {
            for (String t : tokens) {
                if (!e.searchKey().contains(t)) continue outer;
            }
            out.add(e);
        }
        return out;
    }

    public static Optional<Entry> byId(String id) {
        Identifier parsed = Identifier.tryParse(id == null ? "" : id);
        if (parsed == null) return Optional.empty();
        for (Entry e : all()) {
            if (e.id().equals(parsed)) return Optional.of(e);
        }
        return Optional.empty();
    }
}
