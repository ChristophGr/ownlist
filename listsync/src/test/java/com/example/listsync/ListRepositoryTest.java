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
import org.jmock.Sequence;
import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ListRepositoryTest {

    @Rule
    public final JUnitRuleMockery context = new JUnitRuleMockery();

    @Mock
    private Repository repository;

    @Test
    public void testSyncAddedItemToRepository() throws Exception {
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("add-transaction");
            oneOf(repository).lock("foo");
            inSequence(sequence);
            oneOf(repository).download("foo");
            inSequence(sequence);
            will(returnValue(Arrays.asList()));
            oneOf(repository).upload(with(is("foo")), with(equalTo(Arrays.asList("[_] bar"))));
            inSequence(sequence);
            oneOf(repository).unlock("foo");
            inSequence(sequence);
        }});
        ListRepository list = new ListRepository("foo", repository);
        list.change(Arrays.asList(new CheckItem("bar")), Collections.<CheckItem>emptyList());

        assertThat(list.getContent(), contains(new CheckItem("bar")));
    }

    @Test
    public void testItemAddedInTheMeantime() throws Exception {
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("add-transaction");
            oneOf(repository).lock("foo");
            inSequence(sequence);
            oneOf(repository).download("foo");
            inSequence(sequence);
            will(returnValue(Arrays.asList("[_] bar2")));
            oneOf(repository).upload(with(is("foo")), with(equalTo(Arrays.asList("[_] bar2", "[_] bar"))));
            inSequence(sequence);
            oneOf(repository).unlock("foo");
            inSequence(sequence);
        }});
        ListRepository list = new ListRepository("foo", repository);
        list.change(Arrays.asList(new CheckItem("bar")), Collections.<CheckItem>emptyList());

        assertThat(list.getContent(), contains(new CheckItem("bar2"), new CheckItem("bar")));
    }

}
