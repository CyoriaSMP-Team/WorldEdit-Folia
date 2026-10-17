/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */

package com.sk89q.worldedit.bukkit.folia;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import io.papermc.paper.threadedregions.RegionizedServerInitEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.ArrayList;
import java.util.UUID;

/** Maintains FoliaChunkIndex from region-safe lifecycle callbacks. */
public final class FoliaChunkListener implements Listener {

    private final WorldEditPlugin plugin;

    public FoliaChunkListener(WorldEditPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerReady(RegionizedServerInitEvent event) {
        if (!plugin.isFolia()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            ArrayList<FoliaChunkIndex.ChunkPosition> chunks = new ArrayList<>();
            for (Chunk chunk : world.getLoadedChunks()) {
                chunks.add(new FoliaChunkIndex.ChunkPosition(chunk.getX(), chunk.getZ()));
            }
            FoliaChunkIndex.initialize(world.getUID(), chunks);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (plugin.isFolia()) {
            update(event.getChunk(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (plugin.isFolia()) {
            update(event.getChunk(), false);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (plugin.isFolia()) {
            FoliaChunkIndex.worldLoaded(event.getWorld().getUID());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        if (plugin.isFolia()) {
            FoliaChunkIndex.worldUnloaded(event.getWorld().getUID());
        }
    }

    private static void update(Chunk chunk, boolean loaded) {
        UUID worldId = chunk.getWorld().getUID();
        if (loaded) {
            FoliaChunkIndex.loaded(worldId, chunk.getX(), chunk.getZ());
        } else {
            FoliaChunkIndex.unloaded(worldId, chunk.getX(), chunk.getZ());
        }
    }
}
