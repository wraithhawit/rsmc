package com.wraithhawit.rsmc.block;

import javax.annotation.Nullable;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.BlockKind;
import com.wraithhawit.rsmc.structure.StructureBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The structure's single point of contact with the world.
 *
 * <p>Takes the place of one Casing on a wall -- never an edge, which keeps the Frame outline
 * unbroken. Exactly one per structure.
 *
 * <p>It is the only block in the mod with a network node. Refined Storage walks its graph
 * outgoing-only, so a cable finds the structure only where a container actually lives; before this
 * block existed that meant a block entity and a node on <em>every</em> shell block, 2,168 of each
 * for a 16x16x16, purely so any face could be cabled. Now it is one of each, and the player decides
 * where by choosing which Casing to replace.
 *
 * <p>It is also the position everything else is derived around -- {@code Result.controllerPos} is
 * found by looking for this block rather than computed from a rule like "the minimum corner", so
 * the host is something the player built rather than something the code picked.
 */
public class ControllerBlock extends Block implements EntityBlock, StructureBlock {
    /**
     * Which way the grid face points. Cosmetic only -- nothing about the structure depends on it,
     * and the shape rules never look at it.
     */
    public static final DirectionProperty FACING = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

    /**
     * What the screen shows. Purely cosmetic -- the shape rules never look at it, and it is
     * recomputed from the world rather than being a thing the block remembers and can be wrong
     * about.
     */
    public static final EnumProperty<ControllerState> STATE =
        EnumProperty.create("state", ControllerState.class);

    private static final MultiblockShape.Component COMPONENT =
        MultiblockShape.Component.of(BlockKind.CONTROLLER);

    public ControllerBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(STATE, ControllerState.UNFORMED));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        // Faces the player, like any machine front. Purely so the grid face is the one you can see.
        return this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public MultiblockShape.Component component() {
        return COMPONENT;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return RsmcBlockEntities.CONTROLLER.get().create(pos, state);
    }

    /**
     * The only ticker in the mod, and only one per structure.
     *
     * <p>It exists to keep the screen honest. The shell blocks deliberately have none -- see
     * {@link ShellBlockEntity} -- and this one runs once a second rather than every tick, because
     * all it does is re-read the structure to decide which of three pictures to show.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level,
                                                                 final BlockState state,
                                                                 final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ControllerBlockEntity controller) {
                controller.refreshStateOccasionally();
            }
        };
    }
}
