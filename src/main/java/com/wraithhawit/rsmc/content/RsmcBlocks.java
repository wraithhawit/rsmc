package com.wraithhawit.rsmc.content;

import java.util.EnumMap;
import java.util.Map;

import com.wraithhawit.rsmc.RSMC;
import com.wraithhawit.rsmc.block.ControllerBlock;
import com.wraithhawit.rsmc.block.CpuBlock;
import com.wraithhawit.rsmc.block.PatternStorageBlock;
import com.wraithhawit.rsmc.block.ShellBlock;
import com.wraithhawit.rsmc.structure.CpuTier;
import com.wraithhawit.rsmc.structure.MultiblockShape.BlockKind;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every block rsmc adds: the two shell blocks, four CPU tiers, and the pattern storage. */
public final class RsmcBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RSMC.MODID);

    public static final DeferredBlock<ShellBlock> FRAME =
        BLOCKS.register(BlockNames.FRAME, () -> new ShellBlock(properties(), BlockKind.FRAME));

    public static final DeferredBlock<ShellBlock> CASING =
        BLOCKS.register(BlockNames.CASING, () -> new ShellBlock(properties(), BlockKind.CASING));

    public static final DeferredBlock<ControllerBlock> CONTROLLER =
        BLOCKS.register(BlockNames.CONTROLLER, () -> new ControllerBlock(properties()));

    public static final DeferredBlock<PatternStorageBlock> PATTERN_STORAGE =
        BLOCKS.register(BlockNames.PATTERN_STORAGE, () -> new PatternStorageBlock(properties()));

    /**
     * The four CPU tiers, registered from the enum rather than written out four times.
     *
     * <p>{@link CpuTier} already knows every tier's registry name and weight, so a fifth tier is a
     * line in that enum plus its assets -- not a line here, a line in the item registry, a line in
     * the creative tab and a constant somewhere to forget.
     */
    public static final Map<CpuTier, DeferredBlock<CpuBlock>> CPUS = new EnumMap<>(CpuTier.class);

    static {
        for (final CpuTier tier : CpuTier.values()) {
            CPUS.put(tier, BLOCKS.register(tier.blockName(), () -> new CpuBlock(properties(), tier)));
        }
    }

    private RsmcBlocks() {
    }

    /**
     * Shared by all seven. They are the same material at different jobs, so a difference in
     * hardness or tool between them would be a difference the player has to learn for no reason.
     */
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.9F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops();
    }

    /** Every block, for the creative tab and the loot table check, in a stable order. */
    public static Iterable<DeferredBlock<? extends Block>> all() {
        final java.util.List<DeferredBlock<? extends Block>> blocks = new java.util.ArrayList<>();
        blocks.add(FRAME);
        blocks.add(CASING);
        blocks.add(CONTROLLER);
        for (final CpuTier tier : CpuTier.values()) {
            blocks.add(CPUS.get(tier));
        }
        blocks.add(PATTERN_STORAGE);
        return blocks;
    }
}
