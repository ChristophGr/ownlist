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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ListSyncer implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListSyncer.class);

    private final List<CheckItem> local = new ArrayList<>();
    private final Queue<Operation> operationQueue = new LinkedList<>();
    private final Lock queueLock = new ReentrantLock();
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
        synchronized (local) {
            if (local.contains(item)) {
                return;
            }
            if (local.contains(item.toggleChecked())){
                toggle(item);
            }
        }
        LOGGER.info("adding; running? {}", running);
        local.add(item);
        queueLock.lock();
        operationQueue.add(new AddOperation(item));
        queueLock.unlock();
    }

    public void toggle(final CheckItem item) {
        synchronized (local) {
            local.remove(item);
            local.add(item.toggleChecked());
        }
        queueLock.lock();
        operationQueue.add(new ToggleOperation(item));
        queueLock.unlock();
    }

    public void remove(CheckItem item) {
        synchronized (local) {
            if (!local.contains(item)){
                return;
            }
            local.remove(item);
        }
        queueLock.lock();
        operationQueue.add(new RemoveOperation(item));
        queueLock.unlock();
    }

    public List<CheckItem> getLocal() {
        return Collections.unmodifiableList(local);
    }

    @Override
    public void run() {
        running = true;
        try {
            Operation nextOp;
            queueLock.lock();
            while((nextOp = operationQueue.poll()) != null) {
                queueLock.unlock();
                final Operation finalNextOp = nextOp;
                Thread opThread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            finalNextOp.perform();
                        } catch (IOException e) {
                            notifyException(e);
                        }
                    }
                };
                opThread.start();
                Thread compactThread = new Thread() {
                    @Override
                    public void run() {
                        queueLock.lock();
                        List<Operation> copy = Lists.newArrayList(operationQueue);
                        operationQueue.clear();
                        operationQueue.addAll(compactOperations(copy));
                        queueLock.unlock();
                    }
                };
                compactThread.start();
                opThread.join();
                compactThread.join();
                queueLock.lock();
            }
            synchronized (local) {
                List<CheckItem> reference = Lists.newArrayList(local);
                local.clear();
                local.addAll(remote.getContent());
                queueLock.unlock();
                if (!local.equals(reference)) {
                    LOGGER.info("change detected: {}", local);
                    notifyListChanged();
                }
            }
        } catch (IOException e) {
            notifyException(e);
        } catch (InterruptedException e) {
            // ignore, done anyway
        } finally {
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
            if (itemOps.size() <= 1) {
                continue;
            }
            Iterator<Operation> iterator = itemOps.iterator();
            Operation current = iterator.next();
            while(iterator.hasNext()) {
                Operation next = iterator.next();
                Operation merged = current.merge(next);
                if (merged == current) {
                    copy.remove(next);
                } else if (merged == next) {
                    copy.remove(current);
                } else {
                    copy.remove(current);
                    int i = copy.indexOf(next);
                    copy.set(i, merged);
                }
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

    abstract class Operation {

        protected final CheckItem item;

        protected Operation(CheckItem item) {
            this.item = item;
        }

        abstract void perform() throws IOException;

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
        public void perform() throws IOException {
            remote.add(item);
        }

        @Override
        public Operation merge(Operation other) {
            if (other instanceof RemoveOperation) {
                return null;
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
        public void perform() throws IOException {
            remote.remove(item);
        }

        @Override
        public Operation merge(Operation other) {
            if (other instanceof RemoveOperation) {
                return this;
            }
            if (other instanceof AddOperation) {
                if (item.isChecked() == other.item.isChecked()){
                    return null;
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
        public void perform() throws IOException {
            remote.toggle(item);
        }

        @Override
        public Operation merge(Operation other) {
            if (other instanceof RemoveOperation) {
                return other;
            }
            if (other instanceof AddOperation) {
                if (item.isChecked() == other.item.isChecked()){
                    return null;
                }
                return this;
            }
            if (other instanceof ToggleOperation) {
                if (item.isChecked() == other.item.isChecked()){
                    return this;
                }
                return null;
            }
            return super.merge(other);
        }
    }

    private class Noop extends Operation {

        protected Noop(CheckItem item) {
            super(item);
        }

        @Override
        void perform() {
        }

        @Override
        public Operation merge(Operation other) {
            return other;
        }

    }

}
