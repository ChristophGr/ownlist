/*
 * Copyright Christoph Gritschenberger 2015.
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AtomicList<T> {

    private final List<T> delegate;

    public AtomicList(List<T> delegate) {
        this.delegate = delegate;
    }

    public synchronized void addAll(Collection<T> items) {
        this.delegate.addAll(items);
    }

    public synchronized void add(T item) {
        this.delegate.add(item);
    }

    public synchronized boolean addIfAbsent(T item) {
        if (this.delegate.contains(item)) {
            return false;
        }
        this.delegate.add(item);
        return true;
    }

    public synchronized void remove(T item) {
        this.delegate.remove(item);
    }

    public synchronized boolean removeIfPresent(T item) {
        if (!this.delegate.contains(item)) {
            return false;
        }
        this.delegate.remove(item);
        return true;
    }

    public synchronized void replace(T old, T newItem) {
        this.delegate.remove(old);
        this.delegate.add(newItem);
    }

    public synchronized boolean replaceAll(List<T> newItems) {
        if (delegate.equals(newItems)) {
            return false;
        }
        delegate.clear();
        delegate.addAll(newItems);
        return true;
    }

    public List<T> getDelegate() {
        return Collections.unmodifiableList(delegate);
    }

}
