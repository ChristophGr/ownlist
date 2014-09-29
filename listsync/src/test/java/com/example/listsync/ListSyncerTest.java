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
import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jmock.lib.concurrent.Synchroniser;
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
    public final JUnitRuleMockery context = new JUnitRuleMockery(){{
        setThreadingPolicy(new Synchroniser());
    }};

    private ListSyncer listSyncer;
    @Mock
    private ListRepository repository;

    @Before
    public void setUp() throws Exception {
        listSyncer = new ListSyncer(repository);
    }

    @Test
    public void testUploadsSingleEntry() throws Exception {
        final CheckItem foo = new CheckItem("foo");
        listSyncer.add(foo);

        context.checking(new Expectations(){{
            allowing(repository).getContent();
            will(returnValue(new ArrayList<>()));
            oneOf(repository).add(foo);
        }});
        listSyncer.run();
    }

    @Test
    public void testAddsMultipleEntriesInABatch() throws Exception {
        final CheckItem anItem = new CheckItem("foo");
        listSyncer.add(anItem);
        final CheckItem anotherItem = new CheckItem("foo2");
        listSyncer.add(anotherItem);

        context.checking(new Expectations(){{
            allowing(repository).getContent();
            will(returnValue(new ArrayList<>()));
            oneOf(repository).add(anItem);
            oneOf(repository).add(anotherItem);
        }});
        listSyncer.run();
    }

    @Test
    public void testAddAndRemoveCancelEachOtherOut() throws Exception {
        final CheckItem barItem = new CheckItem("bar");
        listSyncer.add(barItem);
        final CheckItem anItem = new CheckItem("foo");
        listSyncer.add(anItem);
        listSyncer.remove(anItem);

        context.checking(new Expectations(){{
            oneOf(repository).add(barItem);
            allowing(repository).getContent();
            will(returnValue(new ArrayList<>()));
        }});
        listSyncer.run();
    }

    @Test
    public void testAddAndToggleGetCompacted() throws Exception {
        final CheckItem barItem = new CheckItem("bar");
        listSyncer.add(barItem);
        final CheckItem anItem = new CheckItem("foo");
        listSyncer.add(anItem);
        listSyncer.toggle(anItem);

        context.checking(new Expectations(){{
            oneOf(repository).add(barItem);
            oneOf(repository).add(anItem.toggleChecked());
            allowing(repository).getContent();
            will(returnValue(new ArrayList<>()));
        }});
        listSyncer.run();
    }

    @Test
    public void testDoubleToggleIsCancelled() throws Exception {
        final CheckItem barItem = new CheckItem("bar");
        listSyncer.add(barItem);
        listSyncer.toggle(barItem);
        listSyncer.toggle(barItem.toggleChecked());

        context.checking(new Expectations(){{
            oneOf(repository).add(barItem);
            allowing(repository).getContent();
            will(returnValue(new ArrayList<>()));
        }});
        listSyncer.run();
    }

    @Test
    public void testReaddingCheckedItemUntoggles() throws Exception {
        final CheckItem barItem = new CheckItem("bar");
        listSyncer.add(barItem);
        listSyncer.toggle(barItem);
        listSyncer.add(barItem);

        context.checking(new Expectations(){{
            oneOf(repository).add(barItem);
            allowing(repository).getContent();
            will(returnValue(new ArrayList<>()));
        }});
        listSyncer.run();
    }

    @Test
    public void testNotifiesChangeListenerOnChange() throws Exception {
        final Consumer mock = context.mock(Consumer.class);
        listSyncer.registerChangeListener(mock);
        context.checking(new Expectations() {{
            CheckItem anItem = new CheckItem("foo");
            oneOf(repository).getContent();
            will(returnValue(Arrays.asList(anItem)));
            oneOf(mock).consume(Arrays.asList(anItem));
        }});
        listSyncer.run();
    }

}