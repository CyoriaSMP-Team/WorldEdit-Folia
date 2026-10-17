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

import com.sk89q.util.StringUtil;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.bukkit.adapter.BukkitImplAdapter;
import com.sk89q.worldedit.bukkit.folia.FoliaScheduler;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.extension.platform.AbstractPlayerActor;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.internal.cui.CUIEvent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.session.SessionKey;
import com.sk89q.worldedit.util.HandSide;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import com.sk89q.worldedit.util.formatting.component.TextUtils;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.formatting.text.adapter.bukkit.TextAdapter;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.gamemode.GameMode;
import com.sk89q.worldedit.world.gamemode.GameModes;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.enginehub.linbus.tree.LinCompoundTag;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;

public class BukkitPlayer extends AbstractPlayerActor {

    private final Player player;
    private final WorldEditPlugin plugin;

    public BukkitPlayer(Player player) {
        this(WorldEditPlugin.getInstance(), player);
    }

    public BukkitPlayer(WorldEditPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public UUID getUniqueId() {
        return FoliaScheduler.callEntity(player, player::getUniqueId);
    }

    @Override
    public BaseItemStack getItemInHand(HandSide handSide) {
        return FoliaScheduler.callEntity(player, () -> {
            ItemStack itemStack = handSide == HandSide.MAIN_HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
            return BukkitAdapter.adapt(itemStack);
        });
    }

    @Override
    public BaseBlock getBlockInHand(HandSide handSide) throws WorldEditException {
        return FoliaScheduler.callEntityChecked(player, () -> {
            ItemStack itemStack = handSide == HandSide.MAIN_HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
            return BukkitAdapter.asBlockState(itemStack).toBaseBlock();
        });
    }

    @Override
    public String getName() {
        return FoliaScheduler.callEntity(player, player::getName);
    }

    @SuppressWarnings("deprecation") // Paper's deprecation, we need to support Spigot still
    @Override
    public String getDisplayName() {
        return FoliaScheduler.callEntity(player, player::getDisplayName);
    }

    @Override
    public void giveItem(BaseItemStack itemStack) {
        FoliaScheduler.callEntity(player, () -> {
            player.getInventory().addItem(BukkitAdapter.adapt(itemStack));
            return null;
        });
    }

    @Override
    @Deprecated
    public void printRaw(String msg) {
        FoliaScheduler.callEntity(player, () -> sendParts(msg, ""));
    }

    @Override
    @Deprecated
    public void print(String msg) {
        FoliaScheduler.callEntity(player, () -> sendParts(msg, "§d"));
    }

    @Override
    @Deprecated
    public void printDebug(String msg) {
        FoliaScheduler.callEntity(player, () -> sendParts(msg, "§7"));
    }

    @Override
    @Deprecated
    public void printError(String msg) {
        FoliaScheduler.callEntity(player, () -> sendParts(msg, "§c"));
    }

    @Override
    public void print(Component component) {
        FoliaScheduler.callEntity(player, () -> {
            TextAdapter.sendMessage(player, WorldEditText.format(component, getLocale()));
            return null;
        });
    }

    @Override
    public boolean trySetPosition(Vector3 pos, float pitch, float yaw) {
        return FoliaScheduler.callEntity(player, () -> {
            Location location = new Location(player.getWorld(), pos.x(), pos.y(), pos.z(), yaw, pitch);
            if (WorldEditPlugin.getInstance().isFolia()) {
                var _ = player.teleportAsync(location);
                return true;
            }
            return player.teleport(location);
        });
    }

    @Override
    public String[] getGroups() {
        return FoliaScheduler.callEntity(player, () -> plugin.getPermissionsResolver().getGroups(player));
    }

    @Override
    public BlockBag getInventoryBlockBag() {
        return new BukkitPlayerBlockBag(player);
    }

    @Override
    public GameMode getGameMode() {
        return FoliaScheduler.callEntity(player,
            () -> GameModes.get(player.getGameMode().name().toLowerCase(Locale.ROOT)));
    }

    @Override
    public void setGameMode(GameMode gameMode) {
        FoliaScheduler.callEntity(player, () -> {
            player.setGameMode(org.bukkit.GameMode.valueOf(gameMode.id().toUpperCase(Locale.ROOT)));
            return null;
        });
    }

    @Override
    public boolean hasPermission(String perm) {
        return FoliaScheduler.callEntity(player, () -> (!plugin.getLocalConfiguration().noOpPermissions && player.isOp())
                || plugin.getPermissionsResolver().hasPermission(
                        player.getWorld().getName(), player, perm));
    }

    @Override
    public World getWorld() {
        return FoliaScheduler.callEntity(player, () -> BukkitAdapter.adapt(player.getWorld()));
    }

    @Override
    public void dispatchCUIEvent(CUIEvent event) {
        String[] params = event.getParameters();
        String send = event.getTypeId();
        if (params.length > 0) {
            send = send + "|" + StringUtil.joinString(params, "|");
        }
        final byte[] message = send.getBytes(StandardCharsets.UTF_8);
        FoliaScheduler.callEntity(player, () -> {
            player.sendPluginMessage(plugin, WorldEditPlugin.CUI_PLUGIN_CHANNEL, message);
            return null;
        });
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean isAllowedToFly() {
        return FoliaScheduler.callEntity(player, player::getAllowFlight);
    }

    @Override
    public void setFlying(boolean flying) {
        FoliaScheduler.callEntity(player, () -> {
            player.setFlying(flying);
            return null;
        });
    }

    @Override
    public BaseEntity getState() {
        throw new UnsupportedOperationException("Cannot create a state from this object");
    }

    @Override
    public com.sk89q.worldedit.util.Location getLocation() {
        return FoliaScheduler.callEntity(player, () -> {
            Location nativeLocation = player.getLocation();
            Vector3 position = BukkitAdapter.asVector(nativeLocation);
            return new com.sk89q.worldedit.util.Location(
                BukkitAdapter.adapt(nativeLocation.getWorld()),
                position,
                nativeLocation.getYaw(),
                nativeLocation.getPitch());
        });
    }

    @Override
    public boolean setLocation(com.sk89q.worldedit.util.Location location) {
        return FoliaScheduler.callEntity(player, () -> {
            if (WorldEditPlugin.getInstance().isFolia()) {
                var _ = player.teleportAsync(BukkitAdapter.adapt(location));
                return true;
            }
            return player.teleport(BukkitAdapter.adapt(location));
        });
    }

    @SuppressWarnings("deprecation") // Paper's deprecation, we need to support Spigot still
    @Override
    public Locale getLocale() {
        return FoliaScheduler.callEntity(player, () -> TextUtils.getLocaleByMinecraftTag(player.getLocale()));
    }

    @Override
    public void sendAnnouncements() {
        if (!WorldEditPlugin.getInstance().getLifecycledBukkitImplAdapter().isValid()) {
            printError(TranslatableComponent.of("worldedit.version.bukkit.unsupported-version")
                .append(TextComponent.newline())
                .append(TextComponent.of("https://enginehub.org/worldedit/#downloads", TextColor.AQUA).clickEvent(ClickEvent.openUrl("https://enginehub.org/worldedit/#downloads")))
            );
        }
    }

    @Nullable
    @Override
    public <T> T getFacet(Class<? extends T> cls) {
        return null;
    }

    @Override
    public SessionKey getSessionKey() {
        return FoliaScheduler.callEntity(player, () -> new SessionKeyImpl(player));
    }

    private Void sendParts(String msg, String prefix) {
        for (String part : msg.split("\n", 0)) {
            player.sendMessage(prefix + part);
        }
        return null;
    }

    static class SessionKeyImpl implements SessionKey {
        private final java.lang.ref.WeakReference<Player> playerRef;
        private final UUID uuid;
        private final String name;

        SessionKeyImpl(Player player) {
            this.playerRef = new java.lang.ref.WeakReference<>(player);
            this.uuid = player.getUniqueId();
            this.name = player.getName();
        }

        @Override
        public UUID getUniqueId() {
            return uuid;
        }

        @Nullable
        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isActive() {
            Player player = playerRef.get();
            if (player == null) {
                return false;
            }
            try {
                return FoliaScheduler.callEntity(player, player::isOnline);
            } catch (IllegalStateException _) {
                return false;
            }
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

    }

    @Override
    public <B extends BlockStateHolder<B>> void sendFakeBlock(BlockVector3 pos, B block) {
        org.bukkit.World world = FoliaScheduler.callEntity(player, player::getWorld);
        BlockData blockData = block == null
            ? FoliaScheduler.callAt(world, pos.x(), pos.z(),
                () -> world.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData())
            : BukkitAdapter.adapt(block);
        FoliaScheduler.callEntity(player, () -> {
            Location loc = new Location(world, pos.x(), pos.y(), pos.z());
            player.sendBlockChange(loc, blockData);
            if (block instanceof BaseBlock baseBlock && baseBlock.getBlockType() == BlockTypes.STRUCTURE_BLOCK) {
                LinCompoundTag nbt = baseBlock.getNbt();
                BukkitImplAdapter adapter = WorldEditPlugin.getInstance().getBukkitImplAdapter();
                if (adapter != null && nbt != null) {
                    adapter.sendFakeNBT(player, pos, nbt);
                    adapter.sendFakeOP(player);
                }
            }
            return null;
        });
    }
}
