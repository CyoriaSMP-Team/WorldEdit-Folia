/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.sk89q.worldedit.bukkit.folia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliaChunkIndexTest {

    private final UUID worldId = UUID.randomUUID();

    @AfterEach
    void clearIndex() {
        FoliaChunkIndex.worldUnloaded(worldId);
    }

    @Test
    void tracksChunkLoadsAndUnloadsIncludingNegativeCoordinates() {
        FoliaChunkIndex.worldLoaded(worldId);
        FoliaChunkIndex.loaded(worldId, -17, 23);
        FoliaChunkIndex.loaded(worldId, 4, -9);

        assertEquals(Set.of(
            new FoliaChunkIndex.ChunkPosition(-17, 23),
            new FoliaChunkIndex.ChunkPosition(4, -9)
        ), Set.copyOf(FoliaChunkIndex.snapshot(worldId)));

        FoliaChunkIndex.unloaded(worldId, -17, 23);
        assertEquals(Set.of(new FoliaChunkIndex.ChunkPosition(4, -9)),
            Set.copyOf(FoliaChunkIndex.snapshot(worldId)));
    }

    @Test
    void initializationReplacesThePreviousSnapshotAndWorldUnloadClearsIt() {
        FoliaChunkIndex.loaded(worldId, 1, 2);
        FoliaChunkIndex.initialize(worldId, Set.of(
            new FoliaChunkIndex.ChunkPosition(-1, -2),
            new FoliaChunkIndex.ChunkPosition(3, 4)
        ));

        assertEquals(Set.of(
            new FoliaChunkIndex.ChunkPosition(-1, -2),
            new FoliaChunkIndex.ChunkPosition(3, 4)
        ), Set.copyOf(FoliaChunkIndex.snapshot(worldId)));
        assertTrue(FoliaChunkIndex.isWorldLoaded(worldId));

        FoliaChunkIndex.worldUnloaded(worldId);
        assertFalse(FoliaChunkIndex.isWorldLoaded(worldId));
        assertEquals(Set.of(), Set.copyOf(FoliaChunkIndex.snapshot(worldId)));
    }
}
