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

package com.sk89q.worldedit.internal.event;

import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.util.Identifiable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class InteractionDebouncer {
    private final LongSupplier ticks;
    private final Map<UUID, Interaction> lastInteractions = new ConcurrentHashMap<>();

    public InteractionDebouncer(Platform platform) {
        this(platform::getTickCount);
    }

    public InteractionDebouncer(LongSupplier ticks) {
        this.ticks = ticks;
    }

    public void clearInteraction(Identifiable player) {
        lastInteractions.remove(player.getUniqueId());
    }

    public void setLastInteraction(Identifiable player, boolean result) {
        lastInteractions.put(player.getUniqueId(), new Interaction(ticks.getAsLong(), result));
    }

    public Optional<Boolean> getDuplicateInteractionResult(Identifiable player) {
        Interaction last = lastInteractions.get(player.getUniqueId());
        if (last == null) {
            return Optional.empty();
        }

        long now = ticks.getAsLong();
        if (now - last.tick <= 1) {
            return Optional.of(last.result);
        }

        return Optional.empty();
    }

    private record Interaction(long tick, boolean result) {
    }
}
