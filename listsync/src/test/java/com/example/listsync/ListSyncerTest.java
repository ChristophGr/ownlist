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

import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ListSyncerTest {

    @Rule
    public final JUnitRuleMockery context = new JUnitRuleMockery();

    private ListSyncer listSyncer;
    private RepositoryStub repository;

    @Before
    public void setUp() throws Exception {
        repository = new RepositoryStub();
        ListRepository remote = new ListRepository("foo", repository);
        listSyncer = new ListSyncer(remote);
    }

    @Test
    public void testUploadsSingleEntry() throws Exception {
        CheckItem foo = new CheckItem("foo");
        listSyncer.add(foo);

        listSyncer.run();

        assertThat(repository.getFileContent(), contains(
                is(foo.toString())
        ));
    }

    @Test
    public void testAddsMultipleEntriesInABatch() throws Exception {
        CheckItem anItem = new CheckItem("foo");
        listSyncer.add(anItem);
        CheckItem anotherItem = new CheckItem("foo2");
        listSyncer.add(anotherItem);

        listSyncer.run();

        assertThat(repository.getFileContent(), contains(
                is(anItem.toString()),
                is(anotherItem.toString())
        ));
    }

    @Test
    public void testNotifiesChangeListenerOnChange() throws Exception {
        final CheckItem anItem = new CheckItem("foo");
        repository.add(anItem);
        final Consumer mock = context.mock(Consumer.class);
        listSyncer.registerChangeListener(mock);
        context.checking(new Expectations() {{
            oneOf(mock).consume(Arrays.asList(anItem));
        }});
        listSyncer.run();
    }

}