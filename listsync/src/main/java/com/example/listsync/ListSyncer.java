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

import com.google.common.base.Function;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class ListSyncer implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListSyncer.class);

    private final AtomicList<CheckItem> local = new AtomicList<>(new ArrayList<CheckItem>());
    private final Queue<Operation> operationQueue = new LinkedList<>();
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

    public void add(final CheckItem item) {
        if (!local.addIfAbsent(item)) {
            return;
        }
        LOGGER.info("adding; running? {}", running);
        operationQueue.add(new AddOperation(item));
    }

    public void toggle(final CheckItem item) {
        local.replace(item, item.toggleChecked());
        operationQueue.add(new ToggleOperation(item));
    }

    public void remove(CheckItem item) {
        if (!local.removeIfPresent(item)) {
            return;
        }
        operationQueue.add(new RemoveOperation(item));
    }

    public List<CheckItem> getLocal() {
        return local.getDelegate();
    }

    @Override
    public void run() {
        running = true;
        LOGGER.info("running ListSyncer");
        try {
            Operation nextOp;
            while((nextOp = operationQueue.poll()) != null) {
                Thread opThread = new Thread(new OperationTask(nextOp));
                opThread.start();
                Thread compactThread = new Thread(new CompacterTask());
                compactThread.start();
                LOGGER.info("waiting for Threads to join");
                opThread.join();
                compactThread.join();
            }
            if (local.replaceAll(remote.getContent())) {
                LOGGER.info("change detected: {}", local);
                synchronized (operationQueue) {
                    for (Operation operation : operationQueue) {
                        operation.performLocal();
                    }
                }
                notifyListChanged();
            }
        } catch (IOException e) {
            notifyException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.info("{} interrupted", Thread.currentThread().getName());
            // ignore, done anyway
        } finally {
            LOGGER.info("DONE");
            running = false;
        }
    }

    private List<Operation> compactOperations(List<Operation> copy) {
        Multimap<String,Operation> operations = Multimaps.index(copy, new Function<Operation, String>() {
            @Override
            public String apply(Operation input) {
                return input.item.getText();
            }
        });
        for (Map.Entry<String, Collection<Operation>> entry : operations.asMap().entrySet()) {
            Collection<Operation> itemOps = entry.getValue();
            LOGGER.info("{} has {} operations", entry.getKey(), itemOps.size());
            if (itemOps.size() <= 1) {
                continue;
            }
            Iterator<Operation> iterator = itemOps.iterator();
            Operation current = iterator.next();
            while(iterator.hasNext()) {
                Operation next = iterator.next();
                Operation merged = current.merge(next);
                if (merged == current) {
                    LOGGER.info("{} invalidates {}", current, next);
                    copy.remove(next);
                } else if (merged == next) {
                    LOGGER.info("{} supersedes {}", next, current);
                    copy.remove(current);
                } else {
                    LOGGER.info("{} and {} have been merged to {}", current, next, merged);
                    copy.remove(current);
                    int i = copy.indexOf(next);
                    copy.set(i, merged);
                }
                current = merged;
            }
        }
        return copy;
    }

    public void registerChangeListener(Consumer<List<CheckItem>> mock) {
        changeListeners.add(mock);
    }

    public void registerExceptionHandler(Consumer<Exception> handler) {
        exceptionHandlers.add(handler);
    }

    protected void notifyListChanged() {
        for (Consumer<List<CheckItem>> listChangeHandler : changeListeners) {
            listChangeHandler.consume(local.getDelegate());
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

    abstract class Operation {

        protected final CheckItem item;

        protected Operation(CheckItem item) {
            this.item = item;
        }

        abstract void performRemote() throws IOException;

        abstract void performLocal() throws IOException;

        public Operation merge(Operation other) {
            if (other instanceof Noop) {
                return this;
            }
            throw new IllegalStateException("unknown Operation-type " + other.getClass());
        }

    }

    private class AddOperation extends Operation {

        public AddOperation(CheckItem item) {
            super(item);
        }

        @Override
        public void performRemote() throws IOException {
            remote.add(item);
        }

        @Override
        void performLocal() throws IOException {
            local.add(item);
        }

        @Override
        public Operation merge(Operation other) {
            if (other instanceof RemoveOperation) {
                return new Noop(item);
            }
            if (other instanceof AddOperation) {
                return other;
            }
            if (other instanceof ToggleOperation) {
                return new AddOperation(item.toggleChecked());
            }
            return super.merge(other);
        }
    }

    private class RemoveOperation extends Operation {

        public RemoveOperation(CheckItem item) {
            super(item);
        }

        @Override
        public void performRemote() throws IOException {
            remote.remove(item);
        }

        @Override
        void performLocal() throws IOException {
            local.remove(item);
        }

        @Override
        public Operation merge(Operation other) {
            if (other instanceof RemoveOperation) {
                return this;
            }
            if (other instanceof AddOperation) {
                if (item.isChecked() == other.item.isChecked()){
                    return new Noop(item);
                }
                return new ToggleOperation(item);
            }
            if (other instanceof ToggleOperation) {
                return this;
            }
            return super.merge(other);
        }
    }

    private class ToggleOperation extends Operation {
        public ToggleOperation(CheckItem item) {
            super(item);
        }

        @Override
        public void performRemote() throws IOException {
            remote.toggle(item);
        }

        @Override
        void performLocal() throws IOException {
            local.replace(item, item.toggleChecked());
        }

        @Override
        public Operation merge(Operation other) {
            if (other instanceof RemoveOperation) {
                return other;
            }
            if (other instanceof AddOperation) {
                if (item.isChecked() == other.item.isChecked()){
                    return new Noop(item);
                }
                return this;
            }
            if (other instanceof ToggleOperation) {
                if (item.isChecked() == other.item.isChecked()){
                    return this;
                }
                return new Noop(item);
            }
            return super.merge(other);
        }
    }

    private class Noop extends Operation {

        protected Noop(CheckItem item) {
            super(item);
        }

        @Override
        void performRemote() {
        }

        @Override
        void performLocal() throws IOException {
        }

        @Override
        public Operation merge(Operation other) {
            return other;
        }

    }

    private class OperationTask implements Runnable {
        private final Operation finalNextOp;

        public OperationTask(Operation finalNextOp) {
            this.finalNextOp = finalNextOp;
        }

        @Override
        public void run() {
            try {
                LOGGER.info("{} performing operation {}", Thread.currentThread().getName(), finalNextOp);
                finalNextOp.performRemote();
                LOGGER.info("{} operation done {}", Thread.currentThread().getName(), finalNextOp);
            } catch (IOException e) {
                notifyException(e);
            }
        }
    }

    private class CompacterTask implements Runnable {
        @Override
        public void run() {
            LOGGER.info("locking queue for reordering");
            synchronized (operationQueue) {
                List<Operation> copy = Lists.newArrayList(operationQueue);
                operationQueue.clear();
                operationQueue.addAll(compactOperations(copy));
            }
            LOGGER.info("unlocking queue after reordering");
        }
    }
}
