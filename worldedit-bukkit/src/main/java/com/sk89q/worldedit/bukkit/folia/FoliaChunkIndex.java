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

package com.sk89q.worldedit.bukkit.folia;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks loaded chunks using chunk lifecycle events, without querying a world from an arbitrary thread. */
public final class FoliaChunkIndex {

    public record ChunkPosition(int x, int z) {
    }

    private static final ConcurrentHashMap<UUID, Set<Long>> LOADED = new ConcurrentHashMap<>();

    private FoliaChunkIndex() {
    }

    public static void initialize(UUID worldId, Iterable<ChunkPosition> chunks) {
        Set<Long> positions = ConcurrentHashMap.newKeySet();
        for (ChunkPosition chunk : chunks) {
            positions.add(pack(chunk.x(), chunk.z()));
        }
        LOADED.put(worldId, positions);
    }

    public static void loaded(UUID worldId, int chunkX, int chunkZ) {
        LOADED.computeIfAbsent(worldId, ignored -> ConcurrentHashMap.newKeySet())
            .add(pack(chunkX, chunkZ));
    }

    public static void worldLoaded(UUID worldId) {
        LOADED.computeIfAbsent(worldId, ignored -> ConcurrentHashMap.newKeySet());
    }

    public static void unloaded(UUID worldId, int chunkX, int chunkZ) {
        Set<Long> positions = LOADED.get(worldId);
        if (positions != null) {
            positions.remove(pack(chunkX, chunkZ));
        }
    }

    public static void worldUnloaded(UUID worldId) {
        LOADED.remove(worldId);
    }

    public static void clear() {
        LOADED.clear();
    }

    public static List<ChunkPosition> snapshot(UUID worldId) {
        Set<Long> positions = LOADED.get(worldId);
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        List<ChunkPosition> snapshot = new ArrayList<>(positions.size());
        for (long packed : positions) {
            snapshot.add(new ChunkPosition((int) (packed >> 32), (int) packed));
        }
        return snapshot;
    }

    public static boolean isWorldLoaded(UUID worldId) {
        return LOADED.containsKey(worldId);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
