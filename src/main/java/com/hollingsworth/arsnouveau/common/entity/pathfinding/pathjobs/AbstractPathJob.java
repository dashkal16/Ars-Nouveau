package com.hollingsworth.arsnouveau.common.entity.pathfinding.pathjobs;

import com.hollingsworth.arsnouveau.common.entity.pathfinding.*;
import com.hollingsworth.arsnouveau.common.util.Log;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.Half;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.Callable;

import static com.hollingsworth.arsnouveau.common.entity.pathfinding.PathingConstants.*;

/**
 * Abstract class for Jobs that run in the multithreaded path finder.
 */
public abstract class AbstractPathJob implements Callable<Path> {
    public static boolean DEBUG_DRAW = false;
    public static Set<Node> lastDebugNodesVisited;
    public static Set<Node> lastDebugNodesNotVisited;
    public static Set<Node> lastDebugNodesPath;

    /**
     * Start position to path from.
     */
    protected final BlockPos start;

    /**
     * The pathing cache.
     */
    protected final IWorldReader world;

    /**
     * The result of the path calculation.
     */
    protected final PathResult result;

    /**
     * Max range used to calculate the number of nodes we visit (square of maxrange).
     */
    protected final int maxRange;

    /**
     * Queue of all open nodes.
     */
    public Queue<Node> nodesOpen = new PriorityQueue<>(500);

    /**
     * Queue of all the visited nodes.
     */
    public Map<Integer, Node> nodesVisited = new HashMap<>();

    //  Debug Rendering
    protected boolean debugDrawEnabled = false;

    protected Set<Node> debugNodesVisited = null;

    protected Set<Node> debugNodesNotVisited = null;

    protected Set<Node> debugNodesPath = null;
    //  May be faster, but can produce strange results
    private final boolean allowJumpPointSearchTypeWalk;
    private int totalNodesAdded = 0;
    public int totalNodesVisited = 0;

    /**
     * Are there xz restrictions.
     */
    private final boolean xzRestricted;

    /**
     * Are xz restrictions hard or soft.
     */
    private final boolean hardXzRestriction;

    /**
     * The cost values for certain nodes.
     */
    private PathingOptions pathingOptions = new PathingOptions();

    /**
     * The restriction parameters
     */
    private int maxX;
    private int minX;
    private int maxZ;
    private int minZ;

    /**
     * The entity this job belongs to.
     */
    protected WeakReference<LivingEntity> entity;

    /**
     * AbstractPathJob constructor.
     *
     * @param world  the world within which to path.
     * @param start  the start position from which to path from.
     * @param end    the end position to path to.
     * @param range  maximum path range.
     * @param entity the entity.
     */
    public AbstractPathJob(final World world, final BlockPos start, final BlockPos end, final int range, final LivingEntity entity) {
        this(world, start, end, range, new PathResult<AbstractPathJob>(), entity);
    }

    /**
     * AbstractPathJob constructor.
     *
     * @param world  the world within which to path.
     * @param start  the start position from which to path from.
     * @param end    the end position to path to
     * @param range  maximum path range.
     * @param result path result.
     * @param entity the entity.
     * @see AbstractPathJob#AbstractPathJob(World, BlockPos, BlockPos, int, LivingEntity)
     */
    public AbstractPathJob(final World world, final BlockPos start, final BlockPos end, final int range, final PathResult result, final LivingEntity entity) {
        final int minX = Math.min(start.getX(), end.getX()) - (range / 2);
        final int minZ = Math.min(start.getZ(), end.getZ()) - (range / 2);
        final int maxX = Math.max(start.getX(), end.getX()) + (range / 2);
        final int maxZ = Math.max(start.getZ(), end.getZ()) + (range / 2);

        this.xzRestricted = false;
        this.hardXzRestriction = false;

        this.world = new ChunkCache(world, new BlockPos(minX, 0, minZ), new BlockPos(maxX, 256, maxZ), range);

        this.start = new BlockPos(start);
        this.maxRange = range;

        this.result = result;
        result.setJob(this);
        allowJumpPointSearchTypeWalk = false;

        if (DEBUG_DRAW) // this is automatically false when on server
        {
            debugDrawEnabled = true;
            debugNodesVisited = new HashSet<>();
            debugNodesNotVisited = new HashSet<>();
            debugNodesPath = new HashSet<>();
        }
        this.entity = new WeakReference<>(entity);
    }

    /**
     * AbstractPathJob constructor.
     *
     * @param world            the world within which to path.
     * @param start            the start position from which to path from.
     * @param startRestriction start of restricted area.
     * @param endRestriction   end of restricted area.
     * @param range            range^2 is used as cap for visited node count
     * @param hardRestriction  if <code>true</code> start has to be inside the restricted area (otherwise the search immidiately finishes) -
     *                         node visits outside the area are not allowed, isAtDestination is called on every node, if <code>false</code>
     *                         restricted area only applies to calling isAtDestination thus searching outside area is allowed
     * @param result           path result.
     * @param entity           the entity.
     */
    public AbstractPathJob(final World world,
                           final BlockPos start,
                           final BlockPos startRestriction,
                           final BlockPos endRestriction,
                           final int range,
                           final boolean hardRestriction,
                           final PathResult<AbstractPathJob> result,
                           final LivingEntity entity) {
        this(world, start, startRestriction, endRestriction, range, Vector3i.ZERO, hardRestriction, result, entity);
    }

    /**
     * AbstractPathJob constructor.
     *
     * @param world            the world within which to path.
     * @param start            the start position from which to path from.
     * @param startRestriction start of restricted area.
     * @param endRestriction   end of restricted area.
     * @param range            range^2 is used as cap for visited node count
     * @param grow             adjustment for restricted area, can be either shrink or grow, is applied in both of xz directions after
     *                         getting min/max box values
     * @param hardRestriction  if <code>true</code> start has to be inside the restricted area (otherwise the search immidiately finishes) -
     *                         node visits outside the area are not allowed, isAtDestination is called on every node, if <code>false</code>
     *                         restricted area only applies to calling isAtDestination thus searching outside area is allowed
     * @param result           path result.
     * @param entity           the entity.
     */
    public AbstractPathJob(final World world,
                           final BlockPos start,
                           final BlockPos startRestriction,
                           final BlockPos endRestriction,
                           final int range,
                           final Vector3i grow,
                           final boolean hardRestriction,
                           final PathResult<AbstractPathJob> result,
                           final LivingEntity entity) {
        this.minX = Math.min(startRestriction.getX(), endRestriction.getX()) - grow.getX();
        this.minZ = Math.min(startRestriction.getZ(), endRestriction.getZ()) - grow.getZ();
        this.maxX = Math.max(startRestriction.getX(), endRestriction.getX()) + grow.getX();
        this.maxZ = Math.max(startRestriction.getZ(), endRestriction.getZ()) + grow.getZ();

        this.xzRestricted = true;
        this.hardXzRestriction = hardRestriction;

        this.world = new ChunkCache(world, new BlockPos(minX, 0, minZ), new BlockPos(maxX, 256, maxZ), range);

        this.start = start;
        this.maxRange = range;

        this.result = result;
        result.setJob(this);

        this.allowJumpPointSearchTypeWalk = false;

        if (DEBUG_DRAW) // this is automatically false when on server
        {
            debugDrawEnabled = true;
            debugNodesVisited = new HashSet<>();
            debugNodesNotVisited = new HashSet<>();
            debugNodesPath = new HashSet<>();
        }
        this.entity = new WeakReference<>(entity);
    }

    protected boolean onLadderGoingUp(final Node currentNode, final BlockPos dPos) {
        return currentNode.isLadder() && (dPos.getY() >= 0 || dPos.getX() != 0 || dPos.getZ() != 0);
    }

    /**
     * Generates a good path starting location for the entity to path from, correcting for the following conditions. - Being in water: pathfinding in water occurs along the
     * surface; adjusts position to surface. - Being in a fence space: finds correct adjacent position which is not a fence space, to prevent starting path. from within the fence
     * block.
     *
     * @param entity Entity for the pathfinding operation.
     * @return ChunkCoordinates for starting location.
     */
    public static BlockPos prepareStart(final LivingEntity entity) {
        BlockPos.Mutable pos = new BlockPos.Mutable(MathHelper.floor(entity.getX()),
                MathHelper.floor(entity.getY()),
                MathHelper.floor(entity.getZ()));
        BlockState bs = entity.level.getBlockState(pos);

        // 1 Up when we're standing within this collision shape
        final VoxelShape collisionShape = bs.getCollisionShape(entity.level, pos);
        if (bs.getMaterial().blocksMotion() && collisionShape.max(Direction.Axis.Y) > 0) {
            final double relPosX = Math.abs(entity.getX() % 1);
            final double relPosZ = Math.abs(entity.getZ() % 1);

            for (final AxisAlignedBB box : collisionShape.toAabbs()) {
                if (relPosX >= box.minX && relPosX <= box.maxX
                        && relPosZ >= box.minZ && relPosZ <= box.maxZ
                        && box.maxY > 0) {
                    pos.set(pos.getX(), pos.getY() + 1, pos.getZ());
                    bs = entity.level.getBlockState(pos);
                    break;
                }
            }
        }

        BlockState down = entity.level.getBlockState(pos.below());
        while (!bs.getMaterial().blocksMotion() && !down.getMaterial().blocksMotion() && !down.getBlock().isLadder(down, entity.getCommandSenderWorld(), pos.below(), entity) && bs.getFluidState().isEmpty()) {
            pos.move(Direction.DOWN, 1);
            bs = down;
            down = entity.level.getBlockState(pos.below());

            if (pos.getY() < 0) {
                return entity.blockPosition();
            }
        }

        final Block b = bs.getBlock();

        if (entity.isInWater()) {
            while (!bs.getFluidState().isEmpty()) {
                pos.set(pos.getX(), pos.getY() + 1, pos.getZ());
                bs = entity.level.getBlockState(pos);
            }
        } else if (b instanceof FenceBlock || b instanceof WallBlock || bs.getMaterial().isSolid()) {
            //Push away from fence
            final double dX = entity.getX() - Math.floor(entity.getX());
            final double dZ = entity.getZ() - Math.floor(entity.getZ());

            if (dX < 0.25) {
                pos.set(pos.getX() - 1, pos.getY(), pos.getZ());
            } else if (dX > 0.75) {
                pos.set(pos.getX() + 1, pos.getY(), pos.getZ());
            }

            if (dZ < 0.25) {
                pos.set(pos.getX(), pos.getY(), pos.getZ() - 1);
            } else if (dZ > 0.75) {
                pos.set(pos.getX(), pos.getY(), pos.getZ() + 1);
            }
        }

        return pos.immutable();
    }

    /**
     * Sets the direction where the ladder is facing.
     *
     * @param world the world in.
     * @param pos   the position.
     * @param p     the path.
     */
    private static void setLadderFacing(final IWorldReader world, final BlockPos pos, final PathPointExtended p) {
        final BlockState state = world.getBlockState(pos);
        final Block block = state.getBlock();
        if (block instanceof VineBlock) {
            if (state.getValue(VineBlock.SOUTH)) {
                p.setLadderFacing(Direction.NORTH);
            } else if (state.getValue(VineBlock.WEST)) {
                p.setLadderFacing(Direction.EAST);
            } else if (state.getValue(VineBlock.NORTH)) {
                p.setLadderFacing(Direction.SOUTH);
            } else if (state.getValue(VineBlock.EAST)) {
                p.setLadderFacing(Direction.WEST);
            }
        } else if (block instanceof LadderBlock) {
            p.setLadderFacing(state.getValue(LadderBlock.FACING));
        } else {
            p.setLadderFacing(Direction.UP);
        }
    }

    /**
     * Checks if entity is on a ladder.
     *
     * @param node       the path node.
     * @param nextInPath the next path point.
     * @param pos        the position.
     * @return true if on a ladder.
     */
    private static boolean onALadder(final Node node, final Node nextInPath, final BlockPos pos) {
        return nextInPath != null && node.isLadder()
                &&
                (nextInPath.pos.getX() == pos.getX() && nextInPath.pos.getZ() == pos.getZ());
    }

    /**
     * Generate a pseudo-unique key for identifying a given node by it's coordinates Encodes the lowest 12 bits of x,z and all useful bits of y. This creates unique keys for all
     * blocks within a 4096x256x4096 cube, which is FAR bigger volume than one should attempt to pathfind within This version takes a BlockPos
     *
     * @param pos BlockPos to generate key from
     * @return key for node in map
     */
    private static int computeNodeKey(final BlockPos pos) {
        return ((pos.getX() & 0xFFF) << 20)
                | ((pos.getY() & 0xFF) << 12)
                | (pos.getZ() & 0xFFF);
    }

    /**
     * Compute the cost (immediate 'g' value) of moving from the parent space to the new space.
     *
     * @param dPos       The delta from the parent to the new space; assumes dx,dy,dz in range of [-1..1].
     * @param isSwimming true is the current node would require the citizen to swim.
     * @param onPath     checks if the node is on a path.
     * @param onRails    checks if the node is a rail block.
     * @param railsExit  the exit of the rails.
     * @param blockPos   the position.
     * @param swimStart  if its the swim start.
     * @return cost to move from the parent to the new position.
     */
    protected double computeCost(
            final BlockPos dPos,
            final boolean isSwimming,
            final boolean onPath,
            final boolean onRails,
            final boolean railsExit,
            final boolean swimStart,
            final boolean corner,
            final BlockPos blockPos) {
        double cost = Math.sqrt(dPos.getX() * dPos.getX() + dPos.getY() * dPos.getY() + dPos.getZ() * dPos.getZ());

        if (dPos.getY() != 0 && !(Math.abs(dPos.getY()) <= 1 && world.getBlockState(blockPos).getBlock() instanceof StairsBlock)) {
            if (dPos.getY() > 0) {
                cost *= pathingOptions.jumpCost * Math.abs(dPos.getY());
            } else {
                cost *= pathingOptions.dropCost * Math.abs(dPos.getY());
            }
        }

        if (world.getBlockState(blockPos).hasProperty(BlockStateProperties.OPEN)) {
            cost *= pathingOptions.traverseToggleAbleCost;
        }

        if (onPath) {
            cost *= pathingOptions.onPathCost;
        }

        if (onRails) {
            cost *= pathingOptions.onRailCost;
        }

        if (railsExit) {
            cost *= pathingOptions.railsExitCost;
        }

        if (isSwimming) {
            if (swimStart) {
                cost *= pathingOptions.swimCostEnter;
            } else {
                cost *= pathingOptions.swimCost;
            }
        }

        return cost;
    }

    private static boolean nodeClosed(final Node node) {
        return node != null && node.isClosed();
    }

    private static boolean calculateSwimming(final IWorldReader world, final BlockPos pos, final Node node) {
        return (node == null) ? isWater(world, pos.below()) : node.isSwimming();
    }

    /**
     * Check if the block at this position is actually some kind of waterly fluid.
     *
     * @param pos the pos in the world.
     * @return true if so.
     */
    private static boolean isWater(final IWorldReader world, final BlockPos pos) {
        return isWater(world, pos, null, null);
    }

    /**
     * Check if the block at this position is actually some kind of waterly fluid.
     *
     * @param pos         the pos in the world.
     * @param pState      existing blockstate or null
     * @param pFluidState existing fluidstate or null
     * @return true if so.
     */
    private static boolean isWater(final IWorldReader world, final BlockPos pos, BlockState pState, FluidState pFluidState) {
        BlockState state = pState;
        if (state == null) {
            state = world.getBlockState(pos);
        }

        if (state.canOcclude()) {
            return false;
        }
        if (state.getBlock() == Blocks.WATER) {
            return true;
        }

        FluidState fluidState = pFluidState;
        if (fluidState == null) {
            fluidState = world.getFluidState(pos);
        }

        if (fluidState == null || fluidState.isEmpty()) {
            return false;
        }

        final Fluid fluid = fluidState.getType();
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    public PathResult getResult() {
        return result;
    }

    /**
     * Callable method for initiating asynchronous task.
     *
     * @return path to follow or null.
     */
    @Override
    public final Path call() {
        try {
            return search();
        } catch (final Exception e) {
            // Log everything, so exceptions of the pathfinding-thread show in Log
            Log.getLogger().warn("Pathfinding Exception", e);
        }

        return null;
    }

    /**
     * Perform the search.
     *
     * @return Path of a path to the given location, a best-effort, or null.
     */

    protected Path search() {
        Node bestNode = getAndSetupStartNode();

        double bestNodeResultScore = Double.MAX_VALUE;

        while (!nodesOpen.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            final Node currentNode = nodesOpen.poll();

            totalNodesVisited++;

            // Limiting max amount of nodes mapped
            if (totalNodesVisited > maxRange * maxRange) {
                break;
            }
            currentNode.setCounterVisited(totalNodesVisited);

            handleDebugOptions(currentNode);
            currentNode.setClosed();

            final boolean isViablePosition = isInRestrictedArea(currentNode.pos) && isWalkableSurface(world.getBlockState(currentNode.pos.below()), currentNode.pos.below()) == SurfaceType.WALKABLE;
            if (isViablePosition && isAtDestination(currentNode)) {
                bestNode = currentNode;
                result.setPathReachesDestination(true);
                break;
            }

            //  If this is the closest node to our destination, treat it as our best node
            final double nodeResultScore =
                    getNodeResultScore(currentNode);
            if (isViablePosition && nodeResultScore < bestNodeResultScore && !currentNode.isCornerNode()) {
                bestNode = currentNode;
                bestNodeResultScore = nodeResultScore;
            }

            // if xz soft-restricted we can walk outside the restricted area to be able to find ways around back to the area
            if (!hardXzRestriction || isViablePosition) {
                walkCurrentNode(currentNode);
            }
        }

        final Path path = finalizePath(bestNode);

        handleDebugDraw();

        return path;
    }

    private void handleDebugOptions(final Node currentNode) {
        if (debugDrawEnabled && debugNodesNotVisited != null && debugNodesVisited != null && currentNode != null) {
            addNodeToDebug(currentNode);
        }
    }

    private void addNodeToDebug(final Node currentNode) {
        debugNodesNotVisited.remove(currentNode);
        debugNodesVisited.add(currentNode);
    }

    private void addPathNodeToDebug(final Node node) {
        debugNodesVisited.remove(node);
        debugNodesPath.add(node);
    }

    private void walkCurrentNode(final Node currentNode) {
        BlockPos dPos = BLOCKPOS_IDENTITY;
        if (currentNode.parent != null) {
            dPos = currentNode.pos.subtract(currentNode.parent.pos);
        }

        //  On a ladder, we can go 1 straight-up
        if (onLadderGoingUp(currentNode, dPos)) {
            walk(currentNode, BLOCKPOS_UP);
        }

        //  We can also go down 1, if the lower block is a ladder
        if (onLadderGoingDown(currentNode, dPos)) {
            walk(currentNode, BLOCKPOS_DOWN);
        }

        // Only explore downwards when dropping
        if ((currentNode.parent == null || !currentNode.parent.pos.equals(currentNode.pos.below())) && currentNode.isCornerNode()) {
            walk(currentNode, BLOCKPOS_DOWN);
            return;
        }

        // Walk downwards node if passable
        if (isPassable(currentNode.pos.below(), false, currentNode.parent)) {
            walk(currentNode, BLOCKPOS_DOWN);
        }

        // N
        if (dPos.getZ() <= 0) {
            walk(currentNode, BLOCKPOS_NORTH);
        }

        // E
        if (dPos.getX() >= 0) {
            walk(currentNode, BLOCKPOS_EAST);
        }

        // S
        if (dPos.getZ() >= 0) {
            walk(currentNode, BLOCKPOS_SOUTH);
        }

        // W
        if (dPos.getX() <= 0) {
            walk(currentNode, BLOCKPOS_WEST);
        }
    }

    protected boolean onLadderGoingDown(final Node currentNode, final BlockPos dPos) {
        return (dPos.getY() <= 0 || dPos.getX() != 0 || dPos.getZ() != 0) && isLadder(currentNode.pos.below());
    }

    private void handleDebugDraw() {
        if (debugDrawEnabled) {
            synchronized (debugNodeMonitor) {
                lastDebugNodesNotVisited = debugNodesNotVisited;
                lastDebugNodesVisited = debugNodesVisited;
                lastDebugNodesPath = debugNodesPath;
            }
        }
    }


    private Node getAndSetupStartNode() {
        final Node startNode = new Node(start,
                computeHeuristic(start));

        if (isLadder(start)) {
            startNode.setLadder();
        } else if (isLiquid(world.getBlockState(start.below()))) {
            startNode.setSwimming();
        }

        startNode.setOnRails(pathingOptions.canUseRails() && world.getBlockState(start).getBlock() instanceof AbstractRailBlock);

        nodesOpen.offer(startNode);
        nodesVisited.put(computeNodeKey(start), startNode);

        ++totalNodesAdded;

        return startNode;
    }

    /**
     * Check if this is a liquid state for swimming.
     *
     * @param state the state to check.
     * @return true if so.
     */
    public boolean isLiquid(final BlockState state) {
        return state.getMaterial().isLiquid() || (!state.getMaterial().blocksMotion() && !state.getFluidState().isEmpty());
    }

    /**
     * Generate the path to the target node.
     *
     * @param targetNode the node to path to.
     * @return the path.
     */

    private Path finalizePath(final Node targetNode) {
        //  Compute length of path, since we need to allocate an array.  This is cheaper/faster than building a List
        //  and converting it.  Yes, we have targetNode.steps, but I do not want to rely on that being accurate (I might
        //  fudge that value later on for cutoff purposes
        int pathLength = 1;
        int railsLength = 0;
        Node node = targetNode;
        while (node.parent != null) {
            ++pathLength;
            if (node.isOnRails()) {
                ++railsLength;
            }
            node = node.parent;
        }

        final PathPoint[] points = new PathPoint[pathLength];
        points[0] = new PathPointExtended(node.pos);
        if (debugDrawEnabled) {
            addPathNodeToDebug(node);
        }

        Node nextInPath = null;
        PathPoint next = null;
        node = targetNode;
        while (node.parent != null) {
            if (debugDrawEnabled) {
                addPathNodeToDebug(node);
            }

            --pathLength;

            final BlockPos pos = node.pos;

            if (node.isSwimming()) {
                //  Not truly necessary but helps prevent them spinning in place at swimming nodes
                pos.offset(BLOCKPOS_DOWN);
            }

            final PathPointExtended p = new PathPointExtended(pos);
            if (railsLength >= 8) {
                p.setOnRails(node.isOnRails());
                if (p.isOnRails() && (!node.parent.isOnRails() || node.parent.parent == null)) {
                    p.setRailsEntry();
                } else if (p.isOnRails() && points.length > pathLength + 1) {
                    final PathPointExtended point = ((PathPointExtended) points[pathLength + 1]);
                    if (!point.isOnRails()) {
                        point.setRailsExit();
                    }
                }
            }

            //  Climbing on a ladder?
            if (nextInPath != null && onALadder(node, nextInPath, pos)) {
                p.setOnLadder(true);
                if (nextInPath.pos.getY() > pos.getY()) {
                    //  We only care about facing if going up
                    //In the case of BlockVines (Which does not have Direction) we have to check the metadata of the vines... bitwise...
                    setLadderFacing(world, pos, p);
                }
            } else if (onALadder(node.parent, node.parent, pos)) {
                p.setOnLadder(true);
            }

            if (next != null) {
                next.cameFrom = p;
            }
            next = p;
            points[pathLength] = p;

            nextInPath = node;
            node = node.parent;
        }

        return new Path(Arrays.asList(points), getPathTargetPos(targetNode), isAtDestination(targetNode));
    }

    /**
     * Creates the path for the given points
     *
     * @param finalNode
     * @return
     */
    protected BlockPos getPathTargetPos(final Node finalNode) {
        return finalNode.pos;
    }

    /**
     * Compute the heuristic cost ('h' value) of a given position x,y,z.
     * <p>
     * Returning a value of 0 performs a breadth-first search. Returning a value less than actual possible cost to goal guarantees shortest path, but at computational expense.
     * Returning a value exactly equal to the cost to the goal guarantees shortest path and least expense (but generally. only works when path is straight and unblocked). Returning
     * a value greater than the actual cost to goal produces good, but not perfect paths, and is fast. Returning a very high value (such that 'h' is very high relative to 'g') then
     * only 'h' (the heuristic) matters as the search will be a very fast greedy best-first-search, ignoring cost weighting and distance.
     *
     * @param pos Position to compute heuristic from.
     * @return the heuristic.
     */
    protected abstract double computeHeuristic(BlockPos pos);

    /**
     * Return true if the given node is a viable final destination, and the path should generate to here.
     *
     * @param n Node to test.
     * @return true if the node is a viable destination.
     */
    protected abstract boolean isAtDestination(Node n);

    /**
     * Compute a 'result score' for the Node; if no destination is determined, the node that had the highest 'result' score is used.
     *
     * @param n Node to test.
     * @return score for the node.
     */
    protected abstract double getNodeResultScore(Node n);

    /**
     * "Walk" from the parent in the direction specified by the delta, determining the new x,y,z position for such a move and adding or updating a node, as appropriate.
     *
     * @param parent Node being walked from.
     * @param dPos   Delta from parent, expected in range of [-1..1].
     * @return true if a node was added or updated when attempting to move in the given direction.
     */
    protected final boolean walk(final Node parent, BlockPos dPos) {
        BlockPos pos = parent.pos.offset(dPos);

        //  Can we traverse into this node?  Fix the y up
        final int newY = getGroundHeight(parent, pos);

        if (newY < 0) {
            return false;
        }

        boolean corner = false;
        if (pos.getY() != newY) {
            if (parent.isCornerNode() && (dPos.getX() != 0 || dPos.getZ() != 0)) {
                return false;
            }

            // if the new position is above the current node, we're taking the node directly above
            if (!parent.isCornerNode() && newY - parent.pos.getY() > 0 && (parent.parent == null || !parent.parent.pos.equals(parent.pos.offset(new BlockPos(0,
                    newY - pos.getY(),
                    0))))) {
                dPos = new BlockPos(0, newY - pos.getY(), 0);
                pos = parent.pos.offset(dPos);
                corner = true;
            }
            // If we're going down, take the air-corner before going to the lower node
            else if (!parent.isCornerNode() && newY - parent.pos.getY() < 0 && (dPos.getX() != 0 || dPos.getZ() != 0) && (parent.parent == null || !parent.pos.below()
                    .equals(parent.parent.pos))) {
                dPos = new BlockPos(dPos.getX(), 0, dPos.getZ());
                pos = parent.pos.offset(dPos);
                corner = true;
            }
            // Fix up normal y
            else {
                dPos = dPos.offset(0, newY - pos.getY(), 0);
                pos = new BlockPos(pos.getX(), newY, pos.getZ());
            }
        }

        int nodeKey = computeNodeKey(pos);
        Node node = nodesVisited.get(nodeKey);
        if (nodeClosed(node)) {
            //  Early out on closed nodes (closed = expanded from)
            return false;
        }

        final boolean isSwimming = calculateSwimming(world, pos, node);

        if (isSwimming && !pathingOptions.canSwim()) {
            return false;
        }

        final boolean swimStart = isSwimming && !parent.isSwimming();
        final boolean onRoad = pathingOptions.getIsRoad().apply(world.getBlockState(pos.below())); // if you want to check for a special block to follow.
        final boolean onRails = pathingOptions.canUseRails() && world.getBlockState(corner ? pos.below() : pos).getBlock() instanceof AbstractRailBlock;
        final boolean railsExit = !onRails && parent != null && parent.isOnRails();
        //  Cost may have changed due to a jump up or drop
        final double stepCost = computeCost(dPos, isSwimming, onRoad, onRails, railsExit, swimStart, corner, pos);
        final double heuristic = computeHeuristic(pos);
        final double cost = parent.getCost() + stepCost;
        final double score = cost + heuristic;

        if (node == null) {
            node = createNode(parent, pos, nodeKey, isSwimming, heuristic, cost, score);
            node.setOnRails(onRails);
            node.setCornerNode(corner);
        } else if (updateCurrentNode(parent, node, heuristic, cost, score)) {
            return false;
        }

        nodesOpen.offer(node);

        //  Jump Point Search-ish optimization:
        // If this node was a (heuristic-based) improvement on our parent,
        // lets go another step in the same direction...
        performJumpPointSearch(parent, dPos, node);

        return true;
    }

    private void performJumpPointSearch(final Node parent, final BlockPos dPos, final Node node) {
        if (allowJumpPointSearchTypeWalk && node.getHeuristic() <= parent.getHeuristic()) {
            walk(node, dPos);
        }
    }


    private Node createNode(
            final Node parent, final BlockPos pos, final int nodeKey,
            final boolean isSwimming, final double heuristic, final double cost, final double score) {
        final Node node;
        node = new Node(parent, pos, cost, heuristic, score);
        nodesVisited.put(nodeKey, node);
        if (debugDrawEnabled) {
            debugNodesNotVisited.add(node);
        }

        if (isLadder(pos)) {
            node.setLadder();
        } else if (isSwimming) {
            node.setSwimming();
        }

        totalNodesAdded++;
        node.setCounterAdded(totalNodesAdded);
        return node;
    }

    private boolean updateCurrentNode(final Node parent, final Node node, final double heuristic, final double cost, final double score) {
        //  This node already exists
        if (score >= node.getScore()) {
            return true;
        }

        if (!nodesOpen.remove(node)) {
            return true;
        }

        node.parent = parent;
        node.setSteps(parent.getSteps() + 1);
        node.setCost(cost);
        node.setHeuristic(heuristic);
        node.setScore(score);
        return false;
    }

    /**
     * Get the height of the ground at the given x,z coordinate, within 1 step of y.
     *
     * @param parent parent node.
     * @param pos    coordinate of block.
     * @return y height of first open, viable block above ground, or -1 if blocked or too far a drop.
     */
    protected int getGroundHeight(final Node parent, final BlockPos pos) {
        //  Check (y+1) first, as it's always needed, either for the upper body (level),
        //  lower body (headroom drop) or lower body (jump up)
        if (checkHeadBlock(parent, pos)) {
            return handleTargetNotPassable(parent, pos.above(), world.getBlockState(pos.above()));
        }

        //  Now check the block we want to move to
        final BlockState target = world.getBlockState(pos);
        if (!isPassable(target, pos, parent)) {
            return handleTargetNotPassable(parent, pos, target);
        }

        //  Do we have something to stand on in the target space?
        final BlockState below = world.getBlockState(pos.below());
        final SurfaceType walkability = isWalkableSurface(below, pos);
        if (walkability == SurfaceType.WALKABLE) {
            //  Level path
            return pos.getY();
        } else if (walkability == SurfaceType.NOT_PASSABLE) {
            return -1;
        }

        return handleNotStanding(parent, pos, below);
    }

    private int handleNotStanding(final Node parent, final BlockPos pos, final BlockState below) {
        final boolean isSwimming = parent != null && parent.isSwimming();

        if (isLiquid(below)) {
            return handleInLiquid(pos, below, isSwimming);
        }

        if (isLadder(below.getBlock(), pos.below())) {
            return pos.getY();
        }

        return checkDrop(parent, pos, isSwimming);
    }

    private int checkDrop(final Node parent, final BlockPos pos, final boolean isSwimming) {
        final boolean canDrop = parent != null && !parent.isLadder();
        //  Nothing to stand on
        if (!canDrop || isSwimming || ((parent.pos.getX() != pos.getX() || parent.pos.getZ() != pos.getZ()) && isPassable(parent.pos.below(), false, parent)
                && isWalkableSurface(world.getBlockState(parent.pos.below()), parent.pos.below()) == SurfaceType.DROPABLE)) {
            return -1;
        }

        for (int i = 2; i <= 10; i++) {
            final BlockState below = world.getBlockState(pos.below(i));
            if (isWalkableSurface(below, pos) == SurfaceType.WALKABLE && i <= 3 || isLiquid(below)) {
                //  Level path
                return pos.getY() - i + 1;
            } else if (below.getMaterial() != Material.AIR) {
                return -1;
            }
        }

        return -1;
    }

    private int handleInLiquid(final BlockPos pos, final BlockState below, final boolean isSwimming) {
        if (isSwimming) {
            //  Already swimming in something, or allowed to swim and this is water
            return pos.getY();
        }

        if (pathingOptions.canSwim() && below.getMaterial() == Material.WATER) {
            //  This is water, and we are allowed to swim
            return pos.getY();
        }

        //  Not allowed to swim or this isn't water, and we're on dry land
        return -1;
    }

    private int handleTargetNotPassable(final Node parent, final BlockPos pos, final BlockState target) {
        final boolean canJump = parent != null && !parent.isLadder() && !parent.isSwimming();
        //  Need to try jumping up one, if we can
        if (!canJump || isWalkableSurface(target, pos) != SurfaceType.WALKABLE) {
            return -1;
        }

        //  Check for headroom in the target space
        if (!isPassable(pos.above(2), false, parent)) {
            final VoxelShape bb1 = world.getBlockState(pos).getCollisionShape(world, pos);
            final VoxelShape bb2 = world.getBlockState(pos.above(2)).getCollisionShape(world, pos.above(2));
            if ((pos.above(2).getY() + getStartY(bb2, 1)) - (pos.getY() + getEndY(bb1, 0)) < 2) {
                return -1;
            }
        }

        //  Check for jump room from the origin space
        if (!isPassable(parent.pos.above(2), false, parent)) {
            final VoxelShape bb1 = world.getBlockState(pos).getCollisionShape(world, pos);
            final VoxelShape bb2 = world.getBlockState(parent.pos.above(2)).getCollisionShape(world, parent.pos.above(2));
            if ((parent.pos.above(2).getY() + getStartY(bb2, 1)) - (pos.getY() + getEndY(bb1, 0)) < 2) {
                return -1;
            }
        }

        final BlockState parentBelow = world.getBlockState(parent.pos.below());
        final VoxelShape parentBB = parentBelow.getCollisionShape(world, parent.pos.below());

        double parentY = parentBB.max(Direction.Axis.Y);
        double parentMaxY = parentY + parent.pos.below().getY();
        final double targetMaxY = target.getCollisionShape(world, pos).max(Direction.Axis.Y) + pos.getY();
        if (targetMaxY - parentMaxY < MAX_JUMP_HEIGHT) {
            return pos.getY() + 1;
        }
        if (target.getBlock() instanceof StairsBlock
                && parentY - HALF_A_BLOCK < MAX_JUMP_HEIGHT
                && target.getValue(StairsBlock.HALF) == Half.BOTTOM
                && getXZFacing(parent.pos, pos) == target.getValue(StairsBlock.FACING)) {
            return pos.getY() + 1;
        }
        return -1;
    }

    /**
     * Calculate in which direction a pos is facing. Ignoring y.
     *
     * @param pos      the pos.
     * @param neighbor the block its facing.
     * @return the directions its facing.
     */
    public static Direction getXZFacing(final BlockPos pos, final BlockPos neighbor) {
        final BlockPos vector = neighbor.subtract(pos);
        return Direction.getNearest(vector.getX(), 0, vector.getZ());
    }

    private boolean checkHeadBlock(final Node parent, final BlockPos pos) {
        BlockPos localPos = pos;
        final VoxelShape bb = world.getBlockState(localPos).getCollisionShape(world, localPos);
        if (bb.max(Direction.Axis.Y) < 1) {
            localPos = pos.above();
        }
        //  System.out.println(pos.above());
        boolean isSmall = pathingOptions.canFitInOneCube();
        if (isSmall ? !isPassable(pos, true, parent) : !isPassable(pos.above(), true, parent)) {
            final VoxelShape bb1 = world.getBlockState(pos.below()).getCollisionShape(world, pos.below());
            final VoxelShape bb2 = world.getBlockState(pos.above()).getCollisionShape(world, pos.above());
            if ((pos.above().getY() + getStartY(bb2, 1)) - (pos.below().getY() + getEndY(bb1, 0)) < 2) {
                return true;
            }
            if (parent != null) {
                final VoxelShape bb3 = world.getBlockState(parent.pos.below()).getCollisionShape(world, pos.below());
                if ((pos.above().getY() + getStartY(bb2, 1)) - (parent.pos.below().getY() + getEndY(bb3, 0)) < 1.75) {
                    return true;
                }
            }
        }

        if (parent != null) {
            final BlockState hereState = world.getBlockState(localPos.below());
            final VoxelShape bb1 = world.getBlockState(pos).getCollisionShape(world, pos);
            final VoxelShape bb2 = world.getBlockState(localPos.above()).getCollisionShape(world, localPos.above());
            if ((localPos.above().getY() + getStartY(bb2, 1)) - (pos.getY() + getEndY(bb1, 0)) >= 2) {
                return false;
            }

            return isLiquid(hereState) && !isPassable(pos, false, parent);
        }
        return false;
    }

    /**
     * Get the start y of a voxelshape.
     *
     * @param bb  the voxelshape.
     * @param def the default if empty.
     * @return the start y.
     */
    private double getStartY(final VoxelShape bb, final int def) {
        return bb.isEmpty() ? def : bb.min(Direction.Axis.Y);
    }

    /**
     * Get the end y of a voxelshape.
     *
     * @param bb  the voxelshape.
     * @param def the default if empty.
     * @return the end y.
     */
    private double getEndY(final VoxelShape bb, final int def) {
        return bb.isEmpty() ? def : bb.max(Direction.Axis.Y);
    }

    /**
     * Is the space passable.
     *
     * @param block the block we are checking.
     * @return true if the block does not block movement.
     */
    protected boolean isPassable(final BlockState block, final BlockPos pos, final Node parent) {
        if (block.getMaterial() != Material.AIR) {
            if (block.getMaterial().blocksMotion() && !canPassShape(block.getCollisionShape(world, pos))) {
                if (block.getBlock() instanceof TrapDoorBlock) {
                    final BlockPos parentPos = parent == null ? start : parent.pos;
                    final BlockPos dir = pos.subtract(parentPos);
                    if (dir.getY() != 0 && dir.getX() == 0 && dir.getZ() == 0) {
                        return true;
                    }

                    final Direction facing = block.getValue(TrapDoorBlock.FACING);
                    if (dir.getX() != 0) {
                        return facing == Direction.NORTH || facing == Direction.SOUTH;
                    }
                    return facing == Direction.EAST || facing == Direction.WEST;
                } else {
                    return pathingOptions.canEnterDoors() && (block.getBlock() instanceof DoorBlock || block.getBlock() instanceof FenceGateBlock)
                            || block.getBlock() instanceof PressurePlateBlock
                            || block.getBlock() instanceof AbstractSignBlock
                            || block.getBlock() instanceof AbstractBannerBlock;
                }
            } else if (block.getBlock() instanceof FireBlock) {
                return false;
            } else {
                final VoxelShape shape = block.getCollisionShape(world, pos);
                return isLadder(block.getBlock(), pos) ||
                        ((shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.1)
                                && !isLiquid((block))
                                && (block.getBlock() != Blocks.SNOW || block.getValue(SnowBlock.LAYERS) == 1)
                                && block.getBlock() != Blocks.SWEET_BERRY_BUSH);
            }
        }

        return true;
    }

    public boolean canPassShape(VoxelShape shape) {
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.1;
    }

    protected boolean isPassable(final BlockPos pos, final boolean head, final Node parent) {
        final BlockState state = world.getBlockState(pos);
        final VoxelShape shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.1) {
            return !head
                    || !(state.getBlock() instanceof CarpetBlock)
                    || isLadder(state.getBlock(), pos);
        }
        return isPassable(state, pos, parent);
    }

    /**
     * Is the block solid and can be stood upon.
     *
     * @param blockState Block to check.
     * @param pos        the position.
     * @return true if the block at that location can be walked on.
     */

    protected SurfaceType isWalkableSurface(final BlockState blockState, final BlockPos pos) {
        final Block block = blockState.getBlock();
        if (block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block instanceof WallBlock
                || block instanceof FireBlock
                || block instanceof CampfireBlock
                || block instanceof BambooBlock
                || block instanceof DoorBlock
                || block instanceof MagmaBlock
                || (blockState.getShape(world, pos).max(Direction.Axis.Y) > 1.0)) {
            return SurfaceType.NOT_PASSABLE;
        }

        final FluidState fluid = world.getFluidState(pos);
        if (blockState.getBlock() == Blocks.LAVA || (fluid != null && !fluid.isEmpty() && (fluid.getType() == Fluids.LAVA || fluid.getType() == Fluids.FLOWING_LAVA))) {
            return SurfaceType.NOT_PASSABLE;
        }

        if (isWater(world, pos, blockState, fluid)) {
            return SurfaceType.WALKABLE;
        }

        if (block instanceof AbstractSignBlock) {
            return SurfaceType.DROPABLE;
        }

        if (blockState.getMaterial().isSolid()
                || (blockState.getBlock() == Blocks.SNOW && blockState.getValue(SnowBlock.LAYERS) > 1)
                || block instanceof CarpetBlock) {
            return SurfaceType.WALKABLE;
        }

        return SurfaceType.DROPABLE;
    }

    /**
     * Is the block a ladder.
     *
     * @param block block to check.
     * @param pos   location of the block.
     * @return true if the block is a ladder.
     */
    protected boolean isLadder(final Block block, final BlockPos pos) {
        return block.isLadder(this.world.getBlockState(pos), world, pos, entity.get());
    }

    protected boolean isLadder(final BlockPos pos) {
        return isLadder(world.getBlockState(pos).getBlock(), pos);
    }

    /**
     * Sets the pathing options
     *
     * @param pathingOptions the pathing options to set.
     */
    public void setPathingOptions(final PathingOptions pathingOptions) {
        this.pathingOptions = pathingOptions;
    }

    /**
     * Check if we can walk on a surface, drop into, or neither.
     */
    protected enum SurfaceType {
        WALKABLE,
        DROPABLE,
        NOT_PASSABLE
    }

    /**
     * Check if in restricted area.
     *
     * @param pos the pos to check.
     * @return true if so.
     */
    public boolean isInRestrictedArea(final BlockPos pos) {
        if (!xzRestricted) {
            return true;
        }
        return pos.getX() <= maxX && pos.getZ() <= maxZ && pos.getZ() >= minZ && pos.getX() >= minX;
    }
}
