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

// $Id$

package com.sk89q.worldedit.bukkit;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.folia.FoliaScheduler;
import com.sk89q.worldedit.command.tool.BlockTool;
import com.sk89q.worldedit.command.tool.DoubleActionBlockTool;
import com.sk89q.worldedit.command.tool.DoubleActionTraceTool;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.command.tool.TraceTool;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.platform.SessionIdleEvent;
import com.sk89q.worldedit.internal.event.InteractionDebouncer;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.HandSide;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import org.bukkit.block.Block;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/**
 * Handles all events thrown in relation to a Player.
 */
public class WorldEditListener implements Listener {

    private final WorldEditPlugin plugin;
    private final InteractionDebouncer debouncer;

    /**
     * Construct the object.
     *
     * @param plugin the plugin
     */
    public WorldEditListener(WorldEditPlugin plugin) {
        this.plugin = plugin;
        debouncer = plugin.isFolia()
            ? new InteractionDebouncer(() -> System.nanoTime() / 50_000_000L)
            : new InteractionDebouncer(plugin.getInternalPlatform());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGamemode(PlayerGameModeChangeEvent event) {
        if (!plugin.getInternalPlatform().isHookingEvents()) {
            return;
        }

        if (plugin.isFolia()) {
            plugin.runFoliaPlayerTask(event.getPlayer().getUniqueId(), () -> refreshPlayerSession(event.getPlayer()));
        } else {
            refreshPlayerSession(event.getPlayer());
        }
    }

    private void refreshPlayerSession(org.bukkit.entity.Player player) {
        // This will automatically refresh their session, we don't have to do anything.
        WorldEdit.getInstance().getSessionManager().get(plugin.wrapPlayer(player));
    }

    /**
     * Called when a player interacts.
     *
     * @param event Relevant event details
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getInternalPlatform().isHookingEvents()
                || event.useItemInHand() == Result.DENY
                || event.getHand() == EquipmentSlot.OFF_HAND
                || event.getAction() == Action.PHYSICAL) {
            return;
        }

        if (plugin.isFolia()) {
            FoliaScheduler.runInRegionCallback(() -> onFoliaPlayerInteract(event));
            return;
        }

        final Player player = plugin.wrapPlayer(event.getPlayer());

        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            Optional<Boolean> previousResult = debouncer.getDuplicateInteractionResult(player);
            if (previousResult.isPresent()) {
                if (previousResult.get()) {
                    event.setCancelled(true);
                }
                return;
            }
        }

        final World world = player.getWorld();
        final WorldEdit we = plugin.getWorldEdit();
        final Direction direction = BukkitAdapter.adapt(event.getBlockFace());
        final Block clickedBlock = event.getClickedBlock();
        final Location pos = clickedBlock == null ? null : new Location(world, clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ());

        boolean result = switch (event.getAction()) {
            case LEFT_CLICK_BLOCK -> we.handleBlockLeftClick(player, pos, direction) || we.handleArmSwing(player);
            case LEFT_CLICK_AIR -> we.handleArmSwing(player);
            case RIGHT_CLICK_BLOCK -> we.handleBlockRightClick(player, pos, direction) || we.handleRightClick(player);
            case RIGHT_CLICK_AIR -> we.handleRightClick(player);
            default -> false;
        };

        debouncer.setLastInteraction(player, result);
        if (result) {
            event.setCancelled(true);
        }
    }

    private void onFoliaPlayerInteract(PlayerInteractEvent event) {
        final Player player = plugin.wrapPlayer(event.getPlayer());
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            Optional<Boolean> previousResult = debouncer.getDuplicateInteractionResult(player);
            if (previousResult.isPresent()) {
                if (previousResult.get()) {
                    event.setCancelled(true);
                }
                return;
            }
        }

        if (!hasFoliaToolFor(player, event.getAction())) {
            debouncer.setLastInteraction(player, false);
            return;
        }

        final Action action = event.getAction();
        final Block clickedBlock = event.getClickedBlock();
        final int x = clickedBlock == null ? 0 : clickedBlock.getX();
        final int y = clickedBlock == null ? 0 : clickedBlock.getY();
        final int z = clickedBlock == null ? 0 : clickedBlock.getZ();
        final boolean hasBlock = clickedBlock != null;
        final Direction direction = BukkitAdapter.adapt(event.getBlockFace());
        event.setCancelled(true);
        debouncer.setLastInteraction(player, true);

        plugin.runFoliaPlayerTask(player.getUniqueId(), () -> {
            WorldEdit we = plugin.getWorldEdit();
            final World world = player.getWorld();
            final Location position = hasBlock ? new Location(world, x, y, z) : null;
            switch (action) {
                case LEFT_CLICK_BLOCK -> {
                    if (!we.handleBlockLeftClick(player, position, direction)) {
                        we.handleArmSwing(player);
                    }
                }
                case LEFT_CLICK_AIR -> we.handleArmSwing(player);
                case RIGHT_CLICK_BLOCK -> {
                    if (!we.handleBlockRightClick(player, position, direction)) {
                        we.handleRightClick(player);
                    }
                }
                case RIGHT_CLICK_AIR -> we.handleRightClick(player);
                default -> { }
            }
        });
    }

    private boolean hasFoliaToolFor(Player player, Action action) {
        LocalSession session = plugin.getWorldEdit().getSessionManager().get(player);
        Tool tool = session.getTool(player.getItemInHand(HandSide.MAIN_HAND).getType());
        return switch (action) {
            case LEFT_CLICK_BLOCK -> {
                BlockTool superPickaxe = session.getSuperPickaxe();
                yield (session.hasSuperPickAxe() && player.isHoldingPickAxe()
                    && superPickaxe != null && superPickaxe.canUse(player))
                    || (tool instanceof DoubleActionBlockTool && tool.canUse(player))
                    || (tool instanceof DoubleActionTraceTool && tool.canUse(player));
            }
            case RIGHT_CLICK_BLOCK -> (tool instanceof BlockTool || tool instanceof TraceTool) && tool.canUse(player);
            case LEFT_CLICK_AIR -> tool instanceof DoubleActionTraceTool && tool.canUse(player);
            case RIGHT_CLICK_AIR -> tool instanceof TraceTool && tool.canUse(player);
            default -> false;
        };
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        debouncer.clearInteraction(plugin.wrapPlayer(event.getPlayer()));

        SessionIdleEvent idleEvent = new SessionIdleEvent(new BukkitPlayer.SessionKeyImpl(event.getPlayer()));
        if (plugin.isFolia()) {
            plugin.runFoliaPlayerTask(event.getPlayer().getUniqueId(),
                () -> plugin.getWorldEdit().getEventBus().post(idleEvent));
        } else {
            plugin.getWorldEdit().getEventBus().post(idleEvent);
        }
    }
}
