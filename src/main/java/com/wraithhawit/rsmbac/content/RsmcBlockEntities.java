package com.wraithhawit.rsmbac.content;

import com.wraithhawit.rsmbac.RSMBAC;
import com.wraithhawit.rsmbac.block.ControllerBlockEntity;
import com.wraithhawit.rsmbac.block.PatternStorageBlockEntity;
import com.wraithhawit.rsmbac.block.PortBlockEntity;
import com.wraithhawit.rsmbac.block.ShellBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Four block entity types for nine blocks.
 *
 * <p>The CPUs are the only blocks with none: they contribute a number to the shape scan and nothing
 * else. Every shell block carries one so that a cable can attach to any face -- see
 * {@link com.wraithhawit.rsmbac.block.ShellBlock}.
 *
 * <p>The Port has a type of its own rather than sharing the shell's, and that is not tidiness:
 * capabilities are registered per type, so sharing would put an item handler on every Frame and
 * Casing in the world. See {@link com.wraithhawit.rsmbac.block.PortBlock#newBlockEntity}.
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortBlockEntity>> PORT =
        BLOCK_ENTITIES.register("port", () -> BlockEntityType.Builder
            .of(PortBlockEntity::new, RsmcBlocks.PORT.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatternStorageBlockEntity>>
        PATTERN_STORAGE = BLOCK_ENTITIES.register("pattern_storage", () -> BlockEntityType.Builder
            .of(PatternStorageBlockEntity::new, RsmcBlocks.PATTERN_STORAGE.get())
            .build(null));

    private RsmcBlockEntities() {
    }
}
