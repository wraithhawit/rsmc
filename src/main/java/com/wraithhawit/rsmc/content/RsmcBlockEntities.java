package com.wraithhawit.rsmc.content;

import com.wraithhawit.rsmc.RSMC;
import com.wraithhawit.rsmc.block.PatternStorageBlockEntity;
import com.wraithhawit.rsmc.block.ShellBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Two block entity types for seven blocks.
 *
 * <p>The Frame and the Casing share one type: they behave identically and differ only in which
 * position of the box they may occupy, so a separate type for each would be two registrations for
 * one behaviour. The CPUs have no type at all -- see {@link com.wraithhawit.rsmc.block.CpuBlock}.
 */
public final class RsmcBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RSMC.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShellBlockEntity>> SHELL =
        BLOCK_ENTITIES.register("shell", () -> BlockEntityType.Builder
            .of(ShellBlockEntity::new, RsmcBlocks.FRAME.get(), RsmcBlocks.CASING.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatternStorageBlockEntity>>
        PATTERN_STORAGE = BLOCK_ENTITIES.register("pattern_storage", () -> BlockEntityType.Builder
            .of(PatternStorageBlockEntity::new, RsmcBlocks.PATTERN_STORAGE.get())
            .build(null));

    private RsmcBlockEntities() {
    }
}
