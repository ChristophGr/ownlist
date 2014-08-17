/*
 * Copyright Christoph Gritschenberger 2014.
 *
 * This file is part of OwnList.
 *
 * OwnList is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OwnList is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OwnList.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.example.listsync;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ListSyncer implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListSyncer.class);

    private final List<CheckItem> local = new ArrayList<>();
    private final List<CheckItem> uncommitted = new ArrayList<>();
    private final List<CheckItem> notYetDeleted = new ArrayList<>();
    private final ListRepository remote;

    private final List<Consumer<List<CheckItem>>> changeListeners = new ArrayList<>();
    private final List<Consumer<Exception>> exceptionHandlers = new LinkedList<>();
    private boolean running;

    public ListSyncer(ListRepository remote) {
        this.remote = remote;
    }

    public ListSyncer(List<CheckItem> local, ListRepository remote) {
        this.local.addAll(local);
        this.remote = remote;
    }

    public void add(CheckItem item) {
        LOGGER.info("adding; running? {}", running);
        local.add(item);
        synchronized (notYetDeleted) {
            synchronized (uncommitted) {
                if (notYetDeleted.contains(item)) {
                    LOGGER.info("readding removed item {}", item);
                    notYetDeleted.remove(item);
                } else {
                    LOGGER.info("locally adding item {}", item);
                    uncommitted.add(item);
                }
            }
        }

    }

    public void remove(CheckItem item) {
        LOGGER.info("removing; running? {}", running);
        local.remove(item);
        synchronized (uncommitted) {
            synchronized (notYetDeleted) {
                if (uncommitted.contains(item)) {
                    LOGGER.info("removing not yet committed item {}", item);
                    uncommitted.remove(item);
                } else {
                    LOGGER.info("locally removing item {}", item);
                    notYetDeleted.add(item);
                }
            }
        }
    }

    public void refresh() throws IOException {
        remote.refresh();
        refreshLocal();
    }

    private void refreshLocal() {
        synchronized (notYetDeleted) {
            synchronized (uncommitted) {
                local.clear();
                local.addAll(remote.getContent());
                LOGGER.info("refreshed: server-content {}", local);
                LOGGER.info("applying notYetDeleted: {}; uncommitted: {}", notYetDeleted, uncommitted);
                local.removeAll(notYetDeleted);
                local.addAll(uncommitted);
            }
        }
    }

    public List<CheckItem> getLocal() {
        return Collections.unmodifiableList(local);
    }

    @Override
    public void run() {
        running = true;
        List<CheckItem> reference = Lists.newArrayList(local);
        if (uncommitted.isEmpty() && notYetDeleted.isEmpty()) {
            LOGGER.info("nothing to commit, just refreshing");
            try {
                refresh();
            } catch (IOException e) {
                notifyException(e);
            }
        }
        List<CheckItem> localUncommitted;
        synchronized (uncommitted) {
            localUncommitted = Lists.newArrayList(this.uncommitted);
            uncommitted.clear();
        }
        if (!localUncommitted.isEmpty()) {
            try {
                LOGGER.info("now committing {}", localUncommitted);
                remote.add(localUncommitted);
                refreshLocal();
            } catch (IOException e) {
                LOGGER.info("exception while committing readding {} to uncommitted-state", localUncommitted);
                synchronized (uncommitted) {
                    uncommitted.addAll(localUncommitted);
                }
                notifyException(e);
            }
        }
        List<CheckItem> localNotYetDelted;
        synchronized (notYetDeleted) {
            localNotYetDelted = Lists.newArrayList(notYetDeleted);
            notYetDeleted.clear();
        }
        if (!localNotYetDelted.isEmpty()) {
            try {
                LOGGER.info("now deleting {}", localNotYetDelted);
                remote.remove(localNotYetDelted);
                refreshLocal();
            } catch (IOException e) {
                LOGGER.info("exception while removing. readding {} to notyetdeleted-state", localUncommitted);
                synchronized (notYetDeleted) {
                    notYetDeleted.addAll(localNotYetDelted);
                }
                notifyException(e);
            }
        }
        if (!local.equals(reference)) {
            LOGGER.info("change detected: {}", local);
            notifyListChanged();
        }
        running = false;
    }

    public void registerChangeListener(Consumer<List<CheckItem>> mock) {
        changeListeners.add(mock);
    }

    public void registerExceptionHandler(Consumer<Exception> handler) {
        exceptionHandlers.add(handler);
    }

    protected void notifyListChanged() {
        for (Consumer<List<CheckItem>> listChangeHandler : changeListeners) {
            listChangeHandler.consume(local);
        }
    }

    protected void notifyException(Exception e) {
        LOGGER.error("notifying of Exception", e);
        for (Consumer<Exception> exceptionHandler : exceptionHandlers) {
            exceptionHandler.consume(e);
        }
    }

    public boolean isRunning() {
        return running;
    }
}
