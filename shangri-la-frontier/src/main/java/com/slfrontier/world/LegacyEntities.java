package com.slfrontier.world;

import com.mojang.serialization.Dynamic;
import com.slfrontier.SLFrontier;
import org.jetbrains.annotations.Nullable;

/**
 * Lets Minecraft's data fixer upgrade the Frontier's creatures in old saves.
 *
 * <p>The fixer only knows vanilla entity ids, so it skips modded entities and leaves their
 * vanilla parts in the old format: 0.2 worlds (Minecraft 1.21.1) hold creatures with
 * {@code generic.*} attribute ids and the old equipment layout, which 26.2 can't read. While an
 * entity chunk is being upgraded, each Frontier entity is relabelled as a vanilla stand-in with
 * no fixes of its own (a blaze; a snowball for thrown holy water), so it gets every generic
 * entity and item fix, and gets its real id back afterwards. Fields the mod adds itself are left
 * alone by the fixer and read as before.
 */
public final class LegacyEntities {
    private static final String REAL_ID = SLFrontier.MODID + ":real_id";
    private static final String PREFIX = SLFrontier.MODID + ":";

    private LegacyEntities() {
    }

    /** The chunk with Frontier entities relabelled, or null if it has none. */
    @Nullable
    public static <T> Dynamic<T> disguise(Dynamic<T> chunk) {
        boolean[] found = {false};
        Dynamic<T> out = chunk.update("Entities", list -> list.createList(list.asStream().map(e -> disguiseEntity(e, found))));
        return found[0] ? out : null;
    }

    public static <T> Dynamic<T> reveal(Dynamic<T> chunk) {
        return chunk.update("Entities", list -> list.createList(list.asStream().map(LegacyEntities::revealEntity)));
    }

    private static <T> Dynamic<T> disguiseEntity(Dynamic<T> entity, boolean[] found) {
        Dynamic<T> e = entity.update("Passengers", list -> list.createList(list.asStream().map(p -> disguiseEntity(p, found))));
        String id = e.get("id").asString("");
        if (!id.startsWith(PREFIX)) return e;
        found[0] = true;
        String standIn = id.equals(PREFIX + "holy_water") ? "minecraft:snowball" : "minecraft:blaze";
        return e.set(REAL_ID, e.createString(id)).set("id", e.createString(standIn));
    }

    private static <T> Dynamic<T> revealEntity(Dynamic<T> entity) {
        Dynamic<T> e = entity.update("Passengers", list -> list.createList(list.asStream().map(LegacyEntities::revealEntity)));
        String real = e.get(REAL_ID).asString("");
        return real.isEmpty() ? e : e.set("id", e.createString(real)).remove(REAL_ID);
    }
}
