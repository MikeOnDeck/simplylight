package com.flanks255.simplylight.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.stream.Stream;

@SuppressWarnings("deprecation")
public class LampPost extends LampBase implements SimpleWaterloggedBlock {
    public static final EnumProperty<Position> POSITION = EnumProperty.create("position", Position.class);
    public static final BooleanProperty LOWERED = BooleanProperty.create("lowered");

    private static final double HALF_BLOCK = 0.5D;
    private static final double LOWER_OFFSET = 0.5D;
    private static final double HALF_BLOCK_EPSILON = 1.0E-5D;

    private static final VoxelShape TOP_SHAPE = Stream.of(Block.box(6,0,6, 10, 5, 10), Block.box(5,5,5,11,6,11), Block.box(3,6,3,13,16,13)).reduce((a, b) -> Shapes.join(a,b, BooleanOp.OR)).get();
    private static final VoxelShape MIDDLE_SHAPE = Stream.of(Block.box(6,0,6,10,10,10), Block.box(5,10,5,11,16,11)).reduce((a,b) -> Shapes.join(a,b, BooleanOp.OR)).get();
    private static final VoxelShape BOTTOM_SHAPE = Stream.of(Block.box(3,0,3,13,2,13), Block.box(4,2,4,12,4,12), Block.box(6,4,6,10,16,10)).reduce((a,b) -> Shapes.join(a,b, BooleanOp.OR)).get();

    public final DyeColor color;

    public LampPost(DyeColor color, Block.Properties properties) {
        super(properties
            .mapColor(MapColor.COLOR_BLACK)
            .pushReaction(PushReaction.DESTROY)
            .lightLevel( (bState) -> bState.getValue(POSITION) == Position.TOP?15:0)
            .strength(1.0f));

        registerDefaultState(getStateDefinition().any()
            .setValue(POSITION, Position.BOTTOM)
            .setValue(LOWERED, false)
            .setValue(BlockStateProperties.WATERLOGGED, false));

        this.color = color;
    }

    public static boolean shouldBeLowered(BlockGetter level, BlockPos belowPos) {
        BlockState below = level.getBlockState(belowPos);
        if (below.isAir()) {
            return false;
        }
        VoxelShape shape = below.getCollisionShape(level, belowPos);
        if (shape.isEmpty()) {
            return false;
        }
        return Math.abs(shape.max(Direction.Axis.Y) - HALF_BLOCK) < HALF_BLOCK_EPSILON;
    }

    private void updateLoweredForColumn(Level level, BlockPos bottomPos) {
        boolean lowered = shouldBeLowered(level, bottomPos.below());
        for (int i = 0; i < 3; i++) {
            BlockPos partPos = bottomPos.above(i);
            BlockState partState = level.getBlockState(partPos);
            if (!(partState.getBlock() instanceof LampPost)) {
                continue;
            }
            if (partState.getValue(LOWERED) != lowered) {
                level.setBlock(partPos, partState.setValue(LOWERED, lowered), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public boolean isPathfindable(@NotNull BlockState pState, @NotNull PathComputationType pType) {
        return false;
    }

    public boolean canPlaceLiquid(Player player, @NotNull BlockGetter pLevel, @NotNull BlockPos pPos, @NotNull BlockState pState, @NotNull Fluid pFluid) {
        return SimpleWaterloggedBlock.super.canPlaceLiquid(player, pLevel, pPos, pState, pFluid);
    }

    @NotNull
    @Override
    public FluidState getFluidState(BlockState pState) {
        return pState.getValue(BlockStateProperties.WATERLOGGED)? Fluids.WATER.getSource(false) : super.getFluidState(pState);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState pState, @NotNull BlockGetter pLevel, @NotNull BlockPos pPos, @NotNull CollisionContext pContext) {
        VoxelShape shape = switch (pState.getValue(POSITION)) {
            case TOP -> TOP_SHAPE;
            case MIDDLE -> MIDDLE_SHAPE;
            default -> BOTTOM_SHAPE;
        };
        if (pState.getValue(LOWERED)) {
            shape = shape.move(0, -LOWER_OFFSET, 0);
        }
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockStateBuilder) {
        blockStateBuilder.add(POSITION, LOWERED, BlockStateProperties.WATERLOGGED);
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, @NotNull ItemStack pStack) {
        boolean lowered = pState.getValue(LOWERED);
        pLevel.setBlock(pPos.above(), pState.setValue(POSITION, Position.MIDDLE).setValue(LOWERED, lowered), 3);
        pLevel.setBlock(pPos.above(2), pState.setValue(POSITION, Position.TOP).setValue(LOWERED, lowered), 3);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        BlockPos clickedPos = pContext.getClickedPos();
        Level level = pContext.getLevel();
        if (clickedPos.getY() < level.getMaxY() - 3  && level.getBlockState(clickedPos.above()).canBeReplaced(pContext) && level.getBlockState(clickedPos.above(2)).canBeReplaced(pContext)) {
            boolean lowered = shouldBeLowered(level, clickedPos.below());
            return defaultBlockState()
                .setValue(POSITION, Position.BOTTOM)
                .setValue(LOWERED, lowered)
                .setValue(BlockStateProperties.WATERLOGGED, false);
        }
        return null;
    }

    @Override
    protected void neighborChanged(@NotNull BlockState pState, @NotNull Level pLevel, @NotNull BlockPos pPos, @NotNull Block pBlock, @Nullable net.minecraft.world.level.redstone.Orientation pOrientation, boolean pIsMoving) {
        super.neighborChanged(pState, pLevel, pPos, pBlock, pOrientation, pIsMoving);
        if (pLevel.isClientSide()) {
            return;
        }
        if (pState.getValue(POSITION) != Position.BOTTOM) {
            return;
        }
        updateLoweredForColumn(pLevel, pPos);
    }

    protected void onRemove(@NotNull BlockState pState, @NotNull Level pLevel, @NotNull BlockPos pPos, @NotNull BlockState pNewState, boolean pIsMoving) {
        if (pNewState.getBlock() == this)
            return;
        switch (pState.getValue(POSITION)) {
            case TOP -> {
                if (pLevel.getBlockState(pPos.below()).getBlock() instanceof LampPost)
                    pLevel.setBlockAndUpdate(pPos.below(), Blocks.AIR.defaultBlockState());
                if (pLevel.getBlockState(pPos.below(2)).getBlock() instanceof LampPost)
                    pLevel.setBlockAndUpdate(pPos.below(2), Blocks.AIR.defaultBlockState());
            }
            case MIDDLE -> {
                if (pLevel.getBlockState(pPos.below()).getBlock() instanceof LampPost)
                    pLevel.setBlockAndUpdate(pPos.below(), Blocks.AIR.defaultBlockState());
                if (pLevel.getBlockState(pPos.above()).getBlock() instanceof LampPost)
                    pLevel.setBlockAndUpdate(pPos.above(), Blocks.AIR.defaultBlockState());
            }
            case BOTTOM -> {
                if (pLevel.getBlockState(pPos.above()).getBlock() instanceof LampPost)
                    pLevel.setBlockAndUpdate(pPos.above(), Blocks.AIR.defaultBlockState());
                if (pLevel.getBlockState(pPos.above(2)).getBlock() instanceof LampPost)
                    pLevel.setBlockAndUpdate(pPos.above(2), Blocks.AIR.defaultBlockState());
            }
        }
    }

    @Override
    public void addLang(BiConsumer<String, String> consumer) {
        String base = this.getDescriptionId();

        String colorname = switch (color) {
            case RED -> "Red";
            case BLUE -> "Blue";
            case CYAN -> "Cyan";
            case GRAY -> "Gray";
            case LIME -> "Lime";
            case MAGENTA -> "Magenta";
            case PINK -> "Pink";
            case BLACK -> "Black";
            case BROWN -> "Brown";
            case GREEN -> "Green";
            case ORANGE -> "Orange";
            case PURPLE -> "Purple";
            case YELLOW -> "Yellow";
            case LIGHT_BLUE -> "Light Blue";
            case LIGHT_GRAY -> "Light Gray";
            default -> "";
        };
        if (color == DyeColor.WHITE)
            consumer.accept(base, "Illuminant Column");
        else
            consumer.accept(base, "Illuminant " + colorname + " Column");

        if (color != DyeColor.WHITE)
            return;
        consumer.accept(base + ".info", "3 Block tall lamp post.");
        consumer.accept(base + ".info2", "Top block emits light.");
    }

    public enum Position implements StringRepresentable {
        BOTTOM("bottom"),
        MIDDLE("middle"),
        TOP("top");

        Position(String name) {
            this.name = name;
        }

        private final String name;
        @NotNull
        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
