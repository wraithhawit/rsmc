package com.wraithhawit.rsmbac.content;

import com.wraithhawit.rsmbac.RSMBAC;
import com.wraithhawit.rsmbac.block.ControllerBlockEntity;
import com.wraithhawit.rsmbac.block.PatternStorageBlockEntity;
import com.wraithhawit.rsmbac.block.ShellBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Three block entity types for eight blocks.
 *
 * <p>The Controller is the structure's only point of contact with the network, so it is the only
 * shell block that needs one. Frame and Casing are plain blocks, as are the CPUs -- see
 * {@link com.wraithhawit.rsmbac.block.ShellBlock} for why that changed.
 */
public final class RsmcBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RSMBAC.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControllerBlockEntity>>
        CONTROLLER = BLOCK_ENTITIES.register("controller", () -> BlockEntityType.Builder
            .of(ControllerBlockEntity::new, RsmcBlocks.CONTROLLER.get())
            .build(null));

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
