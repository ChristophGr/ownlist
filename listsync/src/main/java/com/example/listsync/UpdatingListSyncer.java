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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class UpdatingListSyncer extends ListSyncer {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdatingListSyncer.class);

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> scheduledFuture;
    private long time;
    private TimeUnit unit;

    public UpdatingListSyncer(ListRepository remote) {
        super(remote);
    }

    public UpdatingListSyncer(List<CheckItem> local, ListRepository remote) {
        super(local, remote);
    }

    public synchronized void setUpdateTimeout(long time, TimeUnit unit) {
        this.time = time;
        this.unit = unit;
        reschedule();
    }

    @Override
    public synchronized void add(CheckItem item) {
        super.add(item);
        reschedule();
    }

    @Override
    public synchronized void remove(CheckItem item) {
        super.remove(item);
        reschedule();
    }

    private void reschedule() {
        LOGGER.info("rescheduling...");
        if (scheduledFuture != null) {
            boolean cancelled = scheduledFuture.cancel(true);
            if (cancelled) {
                LOGGER.warn("canceled running!");
            }
        }
        scheduledFuture = executorService.scheduleWithFixedDelay(this, 0, this.time, this.unit);
    }

    public void deactivate() {
        if (scheduledFuture == null) {
            return;
        }
        scheduledFuture.cancel(true);
        scheduledFuture = null;
    }

}
