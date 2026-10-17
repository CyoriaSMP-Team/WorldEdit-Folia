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

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Synchronous bridge for WorldEdit's synchronous extent API. Callers that need
 * to cross a region must be off-region; the Bukkit access itself always runs
 * on the owning region (or entity scheduler).
 */
public final class FoliaScheduler {

    private static final ThreadLocal<AtomicInteger> REGION_CALLBACK_DEPTH =
        ThreadLocal.withInitial(AtomicInteger::new);

    private FoliaScheduler() {
    }

    public static <T> T callAt(World world, int blockX, int blockZ, Supplier<T> action) {
        if (!isFolia() || Bukkit.isOwnedByCurrentRegion(world, blockX >> 4, blockZ >> 4)) {
            return action.get();
        }
        checkCanWait();

        CompletableFuture<T> result = new CompletableFuture<>();
        WorldEditPlugin plugin = WorldEditPlugin.getInstance();
        Bukkit.getRegionScheduler().execute(plugin, world, blockX >> 4, blockZ >> 4, () ->
            inRegionCallback(() -> complete(result, action))
        );
        return await(result);
    }

    public static void runAt(World world, int blockX, int blockZ, Runnable action) {
        callAt(world, blockX, blockZ, () -> {
            action.run();
            return null;
        });
    }

    public static void executeAt(World world, int blockX, int blockZ, Runnable action) {
        if (!isFolia() || Bukkit.isOwnedByCurrentRegion(world, blockX >> 4, blockZ >> 4)) {
            action.run();
            return;
        }
        Bukkit.getRegionScheduler().execute(WorldEditPlugin.getInstance(), world, blockX >> 4, blockZ >> 4,
            () -> inRegionCallback(action));
    }

    public static <T> T callEntity(Entity entity, Supplier<T> action) {
        if (!isFolia() || Bukkit.isOwnedByCurrentRegion(entity)) {
            return action.get();
        }
        checkCanWait();

        CompletableFuture<T> result = new CompletableFuture<>();
        boolean scheduled = entity.getScheduler().execute(
            WorldEditPlugin.getInstance(),
            () -> inRegionCallback(() -> complete(result, action)),
            () -> result.completeExceptionally(new IllegalStateException("Entity was removed before the Folia task ran")),
            1L
        );
        if (!scheduled) {
            throw new IllegalStateException("Entity scheduler is retired");
        }
        return await(result);
    }

    public static <T> T callGlobal(Supplier<T> action) {
        if (!isFolia() || Bukkit.isGlobalTickThread()) {
            return action.get();
        }
        checkCanWait();

        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(WorldEditPlugin.getInstance(), () ->
            inRegionCallback(() -> complete(result, action))
        );
        return await(result);
    }

    /** Marks a plugin event callback as region-owned while it is being handled. */
    public static void runInRegionCallback(Runnable action) {
        inRegionCallback(action);
    }

    private static boolean isFolia() {
        return WorldEditPlugin.getInstance().isFolia();
    }

    private static void checkCanWait() {
        if (Bukkit.isGlobalTickThread() || REGION_CALLBACK_DEPTH.get().get() > 0) {
            throw new IllegalStateException("A Folia region thread cannot wait for another region; run the WorldEdit operation asynchronously");
        }
    }

    private static void inRegionCallback(Runnable action) {
        AtomicInteger depth = REGION_CALLBACK_DEPTH.get();
        depth.incrementAndGet();
        try {
            action.run();
        } finally {
            if (depth.decrementAndGet() == 0) {
                REGION_CALLBACK_DEPTH.remove();
            }
        }
    }

    private static <T> void complete(CompletableFuture<T> result, Supplier<T> action) {
        try {
            result.complete(action.get());
        } catch (Throwable throwable) {
            result.completeExceptionally(throwable);
        }
    }

    private static <T> T await(CompletableFuture<T> result) {
        try {
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a Folia-owned task", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new CompletionException(cause);
        }
    }
}
