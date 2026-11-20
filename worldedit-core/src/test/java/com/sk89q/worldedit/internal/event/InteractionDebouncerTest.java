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

package com.sk89q.worldedit.internal.event;

import com.sk89q.worldedit.util.Identifiable;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InteractionDebouncerTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final Identifiable PLAYER = () -> PLAYER_ID;

    @Test
    void expiresDuplicateResultsAfterOneTick() {
        AtomicLong ticks = new AtomicLong(10);
        InteractionDebouncer debouncer = new InteractionDebouncer(ticks::get);

        debouncer.setLastInteraction(PLAYER, true);
        assertEquals(Optional.of(true), debouncer.getDuplicateInteractionResult(PLAYER));

        ticks.set(11);
        assertEquals(Optional.of(true), debouncer.getDuplicateInteractionResult(PLAYER));

        ticks.set(12);
        assertEquals(Optional.empty(), debouncer.getDuplicateInteractionResult(PLAYER));
    }
}
