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
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ListRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListRepository.class);

    private final String name;
    private final Repository repository;

    protected List<CheckItem> content;

    public ListRepository(String name, Repository repository) {
        this(name, repository, new ArrayList<CheckItem>());
    }

    public ListRepository(String name, Repository repository, List<CheckItem> content) {
        this.name = name;
        this.repository = repository;
        this.content = content;
    }

    public synchronized void add(final Collection<CheckItem> entry) throws IOException {
        if (entry.isEmpty()) {
            return;
        }
        repository.lock(name);
        LOGGER.info("refreshing content before adding {}", entry);
        doRefresh();
        content.addAll(entry);
        repository.upload(name, toStringList(content));
        repository.unlock(name);
    }

    public synchronized void remove(final Collection<CheckItem> entry) throws IOException {
        if (entry.isEmpty()) {
            return;
        }
        repository.lock(name);
        LOGGER.info("refreshing content before removing {}", entry);
        doRefresh();
        content.removeAll(entry);
        repository.upload(name, toStringList(content));
        repository.unlock(name);
    }

    public void refresh() throws IOException {
        doRefresh();
    }

    private void doRefresh() throws IOException {
        content = fromStringList(repository.download(name));
        LOGGER.info("refreshed content {}", content);
    }

    private List<CheckItem> fromStringList(List<String> download) {
        return Lists.newArrayList(Iterables.filter(Lists.transform(download, new Function<String, CheckItem>() {
            @Override
            public CheckItem apply(String input) {
                return CheckItem.fromString(input);
            }
        }), Predicates.notNull()));
    }

    private List<String> toStringList(List<CheckItem> download) {
        return Lists.transform(download, Functions.toStringFunction());
    }

    public List<CheckItem> getContent() {
        return content;
    }

}
