package com.slfrontier.world.design;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/** A town, landmark or dungeon template that stands at its fixed spot on the designed map. */
public class DesignedStructure extends Structure {
    public static final MapCodec<DesignedStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i), Codec.STRING.fieldOf("site").forGetter(s -> s.site)).apply(i, DesignedStructure::new));

    private final String site;

    public DesignedStructure(StructureSettings settings, String site) {
        super(settings);
        this.site = site;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        FrontierDesign.Site s = FrontierDesign.get().site(site);
        if (s == null || !ctx.chunkPos().equals(s.startChunk())) return Optional.empty();
        return Optional.of(new GenerationStub(s.center(),
                builder -> builder.addPiece(new DesignedPiece(ctx.structureTemplateManager(), s))));
    }

    @Override
    public StructureType<?> type() {
        return DesignRegistries.DESIGNED_STRUCTURE.get();
    }
}
