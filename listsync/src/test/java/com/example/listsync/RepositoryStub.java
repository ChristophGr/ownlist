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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

class RepositoryStub implements Repository {

    private List<String> fileContent = new ArrayList<>();

    @Override
    public List<String> download(String file) throws IOException {
        return fileContent;
    }

    @Override
    public Collection<String> listFiles() throws IOException {
        return Arrays.asList("foo");
    }

    @Override
    public void upload(String name, List<String> bytes) throws IOException {
        fileContent = Lists.newArrayList(bytes);
    }

    @Override
    public void lock(String name) {
    }

    @Override
    public void unlock(String name) {
    }

    public void add(CheckItem item) {
        fileContent.add(item.toString());
    }

    public void remove(CheckItem item) {
        fileContent.remove(item.toString());
    }

    public List<String> getFileContent() {
        return fileContent;
    }
}
