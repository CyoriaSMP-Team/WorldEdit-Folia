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

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.folia.FoliaScheduler;
import com.sk89q.worldedit.extension.platform.AbstractNonPlayerActor;
import com.sk89q.worldedit.session.SessionKey;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.adapter.bukkit.TextAdapter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class BukkitCommandSender extends AbstractNonPlayerActor {

    /**
     * One time generated ID.
     */
    private static final UUID DEFAULT_ID = UUID.fromString("a233eb4b-4cab-42cd-9fd9-7e7b9a3f74be");

    private final CommandSender sender;
    private final String senderName;

    public BukkitCommandSender(WorldEditPlugin plugin, CommandSender sender) {
        checkNotNull(plugin);
        checkNotNull(sender);
        checkArgument(!(sender instanceof Player), "Cannot wrap a player");

        this.sender = sender;
        this.senderName = sender.getName();
    }

    @Override
    public UUID getUniqueId() {
        return DEFAULT_ID;
    }

    @Override
    public String getName() {
        return senderName;
    }

    @Override
    @Deprecated
    public void printRaw(String msg) {
        sendParts(msg, "");
    }

    @Override
    @Deprecated
    public void print(String msg) {
        sendParts(msg, "§d");
    }

    @Override
    @Deprecated
    public void printDebug(String msg) {
        sendParts(msg, "§7");
    }

    @Override
    @Deprecated
    public void printError(String msg) {
        sendParts(msg, "§c");
    }

    @Override
    public void print(Component component) {
        FoliaScheduler.callGlobal(() -> {
            TextAdapter.sendMessage(sender, WorldEditText.format(component, getLocale()));
            return null;
        });
    }

    private void sendParts(String message, String prefix) {
        FoliaScheduler.callGlobal(() -> {
            for (String part : message.split("\n", 0)) {
                sender.sendMessage(prefix + part);
            }
            return null;
        });
    }

    @Override
    public String[] getGroups() {
        return new String[0];
    }

    @Override
    public boolean hasPermission(String perm) {
        return true;
    }

    @Override
    public void checkPermission(String permission) {
    }

    @Override
    public Locale getLocale() {
        return WorldEdit.getInstance().getConfiguration().defaultLocale;
    }

    public CommandSender getSender() {
        return this.sender;
    }

    @Override
    public SessionKey getSessionKey() {
        return new SessionKey() {
            @Nullable
            @Override
            public String getName() {
                return senderName;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public boolean isPersistent() {
                return true;
            }

            @Override
            public UUID getUniqueId() {
                return DEFAULT_ID;
            }
        };
    }
}
