/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.bukkit;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.bukkit.adapter.BukkitImplAdapter;
import com.sk89q.worldedit.bukkit.adapter.UnsupportedVersionEditException;
import com.sk89q.worldedit.bukkit.folia.FoliaChunkIndex;
import com.sk89q.worldedit.bukkit.folia.FoliaScheduler;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.internal.wna.WorldNativeAccess;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.TreeGenerator;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.world.AbstractWorld;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.generation.ConfiguredFeatureType;
import com.sk89q.worldedit.world.generation.StructureType;
import com.sk89q.worldedit.world.generation.WorldEditTreeGeneration;
import com.sk89q.worldedit.world.weather.WeatherType;
import com.sk89q.worldedit.world.weather.WeatherTypes;
import io.papermc.lib.PaperLib;
import org.apache.logging.log4j.Logger;
import org.bukkit.Effect;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class BukkitWorld extends AbstractWorld {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    private static final Map<Integer, Effect> effects = new HashMap<>();

    static {
        for (Effect effect : Effect.values()) {
            @SuppressWarnings("deprecation")
            int id = effect.getId();
            effects.put(id, effect);
        }
    }

    private final WeakReference<World> worldRef;
    private final WorldNativeAccess<?, ?, ?> worldNativeAccess;
    private final UUID worldId;
    private final String worldName;
    private final Path storagePath;
    private final int minY;
    private final int maxY;

    /**
     * Construct the object.
     *
     * @param world the world
     */
    public BukkitWorld(World world) {
        this.worldRef = new WeakReference<>(world);
        this.worldId = world.getUID();
        this.worldName = world.getName();
        Path worldFolder = world.getWorldFolder().toPath();
        this.storagePath = switch (world.getEnvironment()) {
            case NETHER -> worldFolder.resolve("DIM-1");
            case THE_END -> worldFolder.resolve("DIM1");
            default -> worldFolder;
        };
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight() - 1;
        BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
        if (adapter != null) {
            this.worldNativeAccess = adapter.createWorldNativeAccess(world);
        } else {
            this.worldNativeAccess = null;
        }
    }

    @Override
    public List<com.sk89q.worldedit.entity.Entity> getEntities(Region region) {
        World world = getWorld();
        if (!WorldEditPlugin.getInstance().isFolia()) {
            List<com.sk89q.worldedit.entity.Entity> entities = new ArrayList<>();
            for (Entity entity : world.getEntities()) {
                if (region.contains(BukkitAdapter.asBlockVector(entity.getLocation()))) {
                    entities.add(BukkitAdapter.adapt(entity));
                }
            }
            return entities;
        }
        return getLoadedEntities(region);
    }

    @Override
    public List<com.sk89q.worldedit.entity.Entity> getEntities() {
        if (!WorldEditPlugin.getInstance().isFolia()) {
            List<com.sk89q.worldedit.entity.Entity> list = new ArrayList<>();
            for (Entity entity : getWorld().getEntities()) {
                list.add(BukkitAdapter.adapt(entity));
            }
            return list;
        }
        return getLoadedEntities(null);
    }

    private List<com.sk89q.worldedit.entity.Entity> getLoadedEntities(@Nullable Region region) {
        ensureLoaded();
        World world = getWorld();
        List<com.sk89q.worldedit.entity.Entity> entities = new ArrayList<>();
        int minChunkX = region == null ? Integer.MIN_VALUE : region.getMinimumPoint().x() >> 4;
        int maxChunkX = region == null ? Integer.MAX_VALUE : region.getMaximumPoint().x() >> 4;
        int minChunkZ = region == null ? Integer.MIN_VALUE : region.getMinimumPoint().z() >> 4;
        int maxChunkZ = region == null ? Integer.MAX_VALUE : region.getMaximumPoint().z() >> 4;
        for (FoliaChunkIndex.ChunkPosition position : FoliaChunkIndex.snapshot(worldId)) {
            int chunkX = position.x();
            int chunkZ = position.z();
            if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                continue;
            }
            List<com.sk89q.worldedit.entity.Entity> inChunk = FoliaScheduler.callAt(
                world, chunkX << 4, chunkZ << 4, () -> {
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        return List.of();
                    }
                    List<com.sk89q.worldedit.entity.Entity> found = new ArrayList<>();
                    for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
                        if (region == null || region.contains(BukkitAdapter.asBlockVector(entity.getLocation()))) {
                            found.add(BukkitAdapter.adapt(entity));
                        }
                    }
                    return found;
                }
            );
            entities.addAll(inChunk);
        }
        return entities;
    }

    @Nullable
    @Override
    public com.sk89q.worldedit.entity.Entity createEntity(com.sk89q.worldedit.util.Location location, BaseEntity entity) {
        World bukkitWorld = getWorld();
        BlockVector3 position = BlockVector3.at(location.getX(), location.getY(), location.getZ());
        return at(position, () -> createEntityAt(bukkitWorld, location, entity));
    }

    @Nullable
    private com.sk89q.worldedit.entity.Entity createEntityAt(
        World bukkitWorld, com.sk89q.worldedit.util.Location location, BaseEntity entity
    ) {
        BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
        if (adapter != null) {
            try {
                Entity createdEntity = adapter.createEntity(BukkitAdapter.adapt(bukkitWorld, location), entity);
                if (createdEntity != null) {
                    return new BukkitEntity(createdEntity);
                } else {
                    return null;
                }
            } catch (Exception e) {
                LOGGER.warn("Corrupt entity found when creating: " + entity.getType().id(), e);
                if (entity.getNbt() != null) {
                    LOGGER.warn(entity.getNbt().toString());
                }
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Get the world handle.
     *
     * @return the world
     */
    public World getWorld() {
        return checkNotNull(worldRef.get(), "The world was unloaded and the reference is unavailable");
    }

    @Override
    public String getName() {
        return worldName;
    }

    @Override
    public String id() {
        return worldName.replace(" ", "_").toLowerCase(Locale.ROOT);
    }

    @Override
    public Path getStoragePath() {
        return storagePath;
    }

    @Override
    public int getBlockLightLevel(BlockVector3 pt) {
        return at(pt, () -> getWorld().getBlockAt(pt.x(), pt.y(), pt.z()).getLightLevel());
    }

    @Override
    public boolean regenerate(Region region, Extent extent, RegenOptions options) {
        if (WorldEditPlugin.getInstance().isFolia()) {
            throw new RuntimeException(new RegionOperationException(TranslatableComponent.of("worldedit.bukkit.unsupported-on-folia")));
        }

        BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
        try {
            if (adapter != null) {
                return adapter.regenerate(getWorld(), region, extent, options);
            } else {
                throw new UnsupportedOperationException("Missing BukkitImplAdapter for this version.");
            }
        } catch (Exception e) {
            LOGGER.warn("Regeneration via adapter failed.", e);
            return false;
        }
    }

    @Override
    public boolean clearContainerBlockContents(BlockVector3 pt) {
        return at(pt, () -> clearContainerBlockContentsAt(pt));
    }

    private boolean clearContainerBlockContentsAt(BlockVector3 pt) {
        checkNotNull(pt);

        BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
        if (adapter != null) {
            try {
                return adapter.clearContainerBlockContents(getWorld(), pt);
            } catch (Exception _) {
                // It's fine if we can't, we'll try the generic way below.
            }
        }

        if (!getBlock(pt).getBlockType().getMaterial().hasContainer()) {
            return false;
        }

        Block block = getWorld().getBlockAt(pt.x(), pt.y(), pt.z());
        BlockState state = PaperLib.isPaper() ? block.getState(false) : block.getState();
        if (!(state instanceof InventoryHolder inventoryHolder)) {
            return false;
        }

        Inventory inven = inventoryHolder.getInventory();
        if (inventoryHolder instanceof Chest chest) {
            inven = chest.getBlockInventory();
        }
        inven.clear();
        return true;
    }

    /**
     * An EnumMap that stores which WorldEdit TreeTypes apply to which Bukkit TreeTypes.
     * @deprecated because {@link com.sk89q.worldedit.util.TreeGenerator.TreeType} is
     */
    @Deprecated
    private static final EnumMap<TreeGenerator.TreeType, TreeType> treeTypeMapping =
            new EnumMap<>(TreeGenerator.TreeType.class);

    static {
        generateTreeMap();
    }

    @SuppressWarnings("deprecation")
    private static void generateTreeMap() {
        for (TreeGenerator.TreeType type : TreeGenerator.TreeType.values()) {
            try {
                TreeType bukkitType = TreeType.valueOf(type.name());
                treeTypeMapping.put(type, bukkitType);
            } catch (IllegalArgumentException _) {
                // Unhandled TreeType
            }
        }
        // Other mappings for WE-specific values
        treeTypeMapping.put(TreeGenerator.TreeType.SHORT_JUNGLE, TreeType.SMALL_JUNGLE);
        treeTypeMapping.put(TreeGenerator.TreeType.RANDOM, TreeType.BROWN_MUSHROOM);
        treeTypeMapping.put(TreeGenerator.TreeType.RANDOM_REDWOOD, TreeType.REDWOOD);
        treeTypeMapping.put(TreeGenerator.TreeType.PINE, TreeType.REDWOOD);
        treeTypeMapping.put(TreeGenerator.TreeType.RANDOM_BIRCH, TreeType.BIRCH);
        treeTypeMapping.put(TreeGenerator.TreeType.RANDOM_JUNGLE, TreeType.JUNGLE);
        treeTypeMapping.put(TreeGenerator.TreeType.RANDOM_MUSHROOM, TreeType.BROWN_MUSHROOM);
        for (TreeGenerator.TreeType type : TreeGenerator.TreeType.values()) {
            if (treeTypeMapping.get(type) == null) {
                WorldEdit.logger.error("No TreeType mapping for TreeGenerator.TreeType." + type);
            }
        }
    }

    @Deprecated
    public static TreeType toBukkitTreeType(TreeGenerator.TreeType type) {
        return treeTypeMapping.get(type);
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public boolean generateTree(TreeGenerator.TreeType type, EditSession editSession, BlockVector3 pt) {
        return at(pt, () -> generateTreeAt(type, editSession, pt));
    }

    @SuppressWarnings("deprecation")
    private boolean generateTreeAt(TreeGenerator.TreeType type, EditSession editSession, BlockVector3 pt) {
        World world = getWorld();
        TreeType bukkitType = toBukkitTreeType(type);
        if (bukkitType == TreeType.CHORUS_PLANT) {
            pt = pt.add(0, 1, 0); // bukkit skips the feature gen which does this offset normally, so we have to add it back
        }
        return type != null && world.generateTree(
            BukkitAdapter.adapt(world, pt),
            ThreadLocalRandom.current(),
            bukkitType,
            block -> {
                Mask mask = editSession.getMask();
                var blockVector = BukkitAdapter.asBlockVector(block.getLocation());
                if (mask != null && !mask.test(blockVector)) {
                    return false;
                }
                try {
                    editSession.setBlock(blockVector, BukkitAdapter.adapt(block.getBlockData()));
                } catch (MaxChangedBlocksException _) {
                    // It's fine, we just stop generating.
                }
                return false;
            }
        );
    }

    @Override
    public boolean generateTree(com.sk89q.worldedit.world.generation.TreeType type, EditSession editSession, BlockVector3 position) throws MaxChangedBlocksException {
        ensureLoaded();
        return FoliaScheduler.callAtChecked(getWorld(), position.x(), position.z(), () -> {
            Boolean customResult = WorldEditTreeGeneration.handleWorldEditTrees(this, type, editSession, position);
            if (customResult != null) {
                return customResult;
            }
            BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
            return adapter != null && adapter.generateTree(type, getWorld(), editSession, position);
        });
    }

    @Override
    public void dropItem(Vector3 pt, BaseItemStack item) {
        BlockVector3 block = BlockVector3.at(pt.x(), pt.y(), pt.z());
        at(block, () -> {
            World world = getWorld();
            world.dropItemNaturally(BukkitAdapter.adapt(world, pt), BukkitAdapter.adapt(item));
            return null;
        });
    }

    @Override
    public void checkLoadedChunk(BlockVector3 pt) {
        at(pt, () -> {
            World world = getWorld();
            world.getChunkAt(pt.x() >> 4, pt.z() >> 4);
            return null;
        });
    }

    @Override
    public boolean equals(Object other) {
        return switch (other) {
            case BukkitWorld bukkitWorld -> worldId.equals(bukkitWorld.worldId);
            case com.sk89q.worldedit.world.World world -> worldName.equals(world.getName());
            case null, default -> false;
        };
    }

    @Override
    public int hashCode() {
        return worldId.hashCode();
    }

    @Override
    public int getMaxY() {
        return maxY;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public void fixAfterFastMode(Iterable<BlockVector2> chunks) {
        ensureLoaded();
        World world = getWorld();
        for (BlockVector2 chunkPos : chunks) {
            FoliaScheduler.callAt(world, chunkPos.x() << 4, chunkPos.z() << 4, () -> {
                world.refreshChunk(chunkPos.x(), chunkPos.z());
                return null;
            });
        }
    }

    @Override
    public void sendBiomeUpdates(Iterable<BlockVector2> chunks) {
        ensureLoaded();
        BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
        if (adapter != null) {
            World world = getWorld();
            for (BlockVector2 chunk : chunks) {
                FoliaScheduler.callAt(world, chunk.x() << 4, chunk.z() << 4, () -> {
                    adapter.sendBiomeUpdates(world, List.of(chunk));
                    return null;
                });
            }
        }
    }

    @Deprecated
    @Override
    public boolean playEffect(Vector3 position, int type, int data) {
        final Effect effect = effects.get(type);
        if (effect == null) {
            return false;
        }
        BlockVector3 block = BlockVector3.at(position.x(), position.y(), position.z());
        return at(block, () -> {
            World world = getWorld();
            world.playEffect(BukkitAdapter.adapt(world, position), effect, data);
            return true;
        });
    }

    @Deprecated
    @Override
    @SuppressWarnings("deprecation")
    public boolean queueBlockBreakEffect(Platform server, BlockVector3 position, BlockType blockType, double priority) {
        ensureLoaded();
        World world = getWorld();
        FoliaScheduler.executeAt(world, position.x(), position.z(), () ->
            playEffect(position.toVector3(), 2001, blockType.getLegacyId())
        );
        return true;
    }

    @Override
    public WeatherType getWeather() {
        ensureLoaded();
        return FoliaScheduler.callGlobal(() -> {
            if (getWorld().isThundering()) {
                return WeatherTypes.THUNDER_STORM;
            } else if (getWorld().hasStorm()) {
                return WeatherTypes.RAIN;
            }
            return WeatherTypes.CLEAR;
        });
    }

    @Override
    public long getRemainingWeatherDuration() {
        ensureLoaded();
        return FoliaScheduler.callGlobal(() -> (long) getWorld().getWeatherDuration());
    }

    @Override
    public void setWeather(WeatherType weatherType) {
        ensureLoaded();
        FoliaScheduler.callGlobal(() -> {
            if (weatherType == WeatherTypes.THUNDER_STORM) {
                getWorld().setThundering(true);
            } else if (weatherType == WeatherTypes.RAIN) {
                getWorld().setStorm(true);
            } else {
                getWorld().setStorm(false);
                getWorld().setThundering(false);
            }
            return null;
        });
    }

    @Override
    public void setWeather(WeatherType weatherType, long duration) {
        // Who named these methods...
        ensureLoaded();
        FoliaScheduler.callGlobal(() -> {
            if (weatherType == WeatherTypes.THUNDER_STORM) {
                getWorld().setThundering(true);
                getWorld().setThunderDuration((int) duration);
                getWorld().setWeatherDuration((int) duration);
            } else if (weatherType == WeatherTypes.RAIN) {
                getWorld().setStorm(true);
                getWorld().setWeatherDuration((int) duration);
            } else {
                getWorld().setStorm(false);
                getWorld().setThundering(false);
                getWorld().setWeatherDuration((int) duration);
            }
            return null;
        });
    }

    @Override
    public BlockVector3 getSpawnPosition() {
        ensureLoaded();
        return FoliaScheduler.callGlobal(() -> BukkitAdapter.asBlockVector(getWorld().getSpawnLocation()));
    }

    @Override
    public void simulateBlockMine(BlockVector3 pt) {
        at(pt, () -> getWorld().getBlockAt(pt.x(), pt.y(), pt.z()).breakNaturally());
    }

    @Override
    public boolean canPlaceAt(BlockVector3 position, com.sk89q.worldedit.world.block.BlockState blockState) {
        return at(position, () -> {
            BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
            return adapter == null || adapter.canPlaceAt(getWorld(), position, blockState);
        });
    }

    @Override
    public boolean generateFeature(ConfiguredFeatureType type, EditSession editSession, BlockVector3 position) {
        return at(position, () -> {
            BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
            return adapter != null && adapter.generateFeature(type, getWorld(), editSession, position);
        });
    }

    @Override
    public boolean generateStructure(StructureType type, EditSession editSession, BlockVector3 position) {
        return at(position, () -> {
            BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
            return adapter != null && adapter.generateStructure(type, getWorld(), editSession, position);
        });
    }

    private static volatile boolean hasWarnedImplError = false;

    @Override
    public com.sk89q.worldedit.world.block.BlockState getBlock(BlockVector3 position) {
        return at(position, () -> getBlockAt(position));
    }

    private com.sk89q.worldedit.world.block.BlockState getBlockAt(BlockVector3 position) {
        BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
        if (adapter != null) {
            try {
                return adapter.getBlock(BukkitAdapter.adapt(getWorld(), position));
            } catch (Exception e) {
                if (!hasWarnedImplError) {
                    hasWarnedImplError = true;
                    LOGGER.warn("Unable to retrieve block via impl adapter", e);
                }
            }
        }
        if (WorldEditPlugin.getInstance().getLocalConfiguration().unsupportedVersionEditing) {
            Block bukkitBlock = getWorld().getBlockAt(position.x(), position.y(), position.z());
            return BukkitAdapter.adapt(bukkitBlock.getBlockData());
        } else {
            throw new RuntimeException(new UnsupportedVersionEditException());
        }
    }

    @Override
    public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 position, B block, SideEffectSet sideEffects) {
        return at(position, () -> setBlockAt(position, block, sideEffects));
    }

    private <B extends BlockStateHolder<B>> boolean setBlockAt(
        BlockVector3 position, B block, SideEffectSet sideEffects
    ) {
        clearContainerBlockContents(position);
        if (worldNativeAccess != null) {
            try {
                return worldNativeAccess.setBlock(position, block, sideEffects);
            } catch (Exception e) {
                if (block instanceof BaseBlock baseBlock && baseBlock.getNbt() != null) {
                    LOGGER.warn("Tried to set a corrupt tile entity at " + position.toString()
                        + ": " + baseBlock.getNbt(), e);
                } else {
                    LOGGER.warn("Failed to set block via adapter, falling back to generic", e);
                }
            }
        }
        if (WorldEditPlugin.getInstance().getLocalConfiguration().unsupportedVersionEditing) {
            Block bukkitBlock = getWorld().getBlockAt(position.x(), position.y(), position.z());
            bukkitBlock.setBlockData(BukkitAdapter.adapt(block), sideEffects.doesApplyAny());
            return true;
        } else {
            throw new RuntimeException(new UnsupportedVersionEditException());
        }
    }

    @Override
    public BaseBlock getFullBlock(BlockVector3 position) {
        return at(position, () -> getFullBlockAt(position));
    }

    private BaseBlock getFullBlockAt(BlockVector3 position) {
        BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
        if (adapter != null) {
            return adapter.getFullBlock(BukkitAdapter.adapt(getWorld(), position));
        } else {
            return getBlock(position).toBaseBlock();
        }
    }

    @Override
    public Set<SideEffect> applySideEffects(BlockVector3 position, com.sk89q.worldedit.world.block.BlockState previousType,
            SideEffectSet sideEffectSet) {
        return at(position, () -> applySideEffectsAt(position, previousType, sideEffectSet));
    }

    private Set<SideEffect> applySideEffectsAt(BlockVector3 position,
            com.sk89q.worldedit.world.block.BlockState previousType, SideEffectSet sideEffectSet) {
        if (worldNativeAccess != null) {
            worldNativeAccess.applySideEffects(position, previousType, sideEffectSet);
            return Sets.intersection(
                    WorldEditPlugin.getInstance().getInternalPlatform().getSupportedSideEffects(),
                    sideEffectSet.getSideEffectsToApply()
            );
        }

        return ImmutableSet.of();
    }

    @Override
    public boolean useItem(BlockVector3 position, BaseItem item, Direction face) {
        return at(position, () -> useItemAt(position, item, face));
    }

    private boolean useItemAt(BlockVector3 position, BaseItem item, Direction face) {
        BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
        if (adapter != null) {
            return adapter.simulateItemUse(getWorld(), position, item, face);
        }

        return false;
    }

    @Override
    public BiomeType getBiome(BlockVector3 position) {
        return at(position, () -> getBiomeAt(position));
    }

    private BiomeType getBiomeAt(BlockVector3 position) {
        BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
        if (adapter != null) {
            return adapter.getBiome(BukkitAdapter.adapt(getWorld(), position));
        } else {
            return BukkitAdapter.adapt(getWorld().getBiome(position.x(), position.y(), position.z()));
        }
    }

    @Override
    public boolean setBiome(BlockVector3 position, BiomeType biome) {
        return at(position, () -> setBiomeAt(position, biome));
    }

    private boolean setBiomeAt(BlockVector3 position, BiomeType biome) {
        BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
        if (adapter != null) {
            adapter.setBiome(BukkitAdapter.adapt(getWorld(), position), biome);
        } else {
            getWorld().setBiome(position.x(), position.y(), position.z(), BukkitAdapter.adapt(biome));
        }
        return true;
    }

    @Override
    public boolean isValid() {
        return worldRef.get() != null
            && (!WorldEditPlugin.getInstance().isFolia() || FoliaChunkIndex.isWorldLoaded(worldId));
    }

    private <T> T at(BlockVector3 position, Supplier<T> action) {
        ensureLoaded();
        return FoliaScheduler.callAt(getWorld(), position.x(), position.z(), action);
    }

    private void ensureLoaded() {
        if (WorldEditPlugin.getInstance().isFolia() && !FoliaChunkIndex.isWorldLoaded(worldId)) {
            throw new IllegalStateException("World " + worldName + " is unloaded or has not initialized for Folia");
        }
    }
}
