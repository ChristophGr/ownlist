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
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ListSyncerTest {

    @Rule
    public final JUnitRuleMockery context = new JUnitRuleMockery();

    private MyRepository repository;

    private ListSyncer listSyncer;

    @Before
    public void setUp() throws Exception {
        repository = new MyRepository();
        ListRepository remote = new ListRepository("foo", repository);
        listSyncer = new ListSyncer(remote);
    }

    @Test
    public void testUploadsSingleEntry() throws Exception {
        CheckItem foo = new CheckItem("foo");
        listSyncer.add(foo);

        Thread thread = new Thread(listSyncer);
        thread.start();
        repository.letLock();
        repository.letDownload();
        repository.letUpload();
        repository.letUnlock();
        thread.join();

        assertThat(repository.content.get("foo"), contains(
                is(foo.toString())
        ));
    }

    @Test
    public void testAddsMultipleEntriesInABatch() throws Exception {
        CheckItem anItem = new CheckItem("foo");
        listSyncer.add(anItem);
        CheckItem anotherItem = new CheckItem("foo2");
        listSyncer.add(anotherItem);

        Thread thread = new Thread(listSyncer);
        thread.start();
        repository.letLock();
        repository.letDownload();
        repository.letUpload();
        repository.letUnlock();
        thread.join();

        assertThat(repository.content.get("foo"), contains(
                is(anItem.toString()),
                is(anotherItem.toString())
        ));
    }

    @Test
    public void testJustRefreshesWhenNothingHasChanged() throws Exception {
        Thread thread = new Thread(listSyncer);

        thread.start();
        repository.letDownload();
        thread.join();
    }

    @Test
    public void testNotifiesChangeListenerOnChange() throws Exception {
        final CheckItem anItem = new CheckItem("foo");
        repository.content.put("foo", Lists.newArrayList(anItem.toString()));
        final Consumer mock = context.mock(Consumer.class);
        listSyncer.registerChangeListener(mock);
        context.checking(new Expectations() {{
            oneOf(mock).consume(Arrays.asList(anItem));
        }});
        repository.letDownload();
        listSyncer.run();
    }

    private static class MyRepository implements Repository {
        private Semaphore downloadLock = new Semaphore(0);
        private Semaphore listFilesLock = new Semaphore(0);
        private Semaphore uploadLock = new Semaphore(0);
        private Semaphore lockLock = new Semaphore(0);
        private Semaphore unlockLock = new Semaphore(0);

        private Map<String, List<String>> content = new HashMap<>();

        @Override
        public List<String> download(String file) throws IOException {
            try {
                downloadLock.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!content.containsKey(file)) {
                content.put(file, new ArrayList<String>());
            }
            return content.get(file);
        }

        @Override
        public Collection<String> listFiles() throws IOException {
            try {
                listFilesLock.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return content.keySet();
        }

        @Override
        public void upload(String name, List<String> bytes) throws IOException {
            try {
                uploadLock.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            content.put(name, bytes);
        }

        @Override
        public void lock(String name) {
            try {
                lockLock.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void unlock(String name) {
            try {
                unlockLock.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public void letDownload() {
            downloadLock.release();
        }

        public void letListFiles() {
            listFilesLock.release();
        }

        public void letUpload() {
            uploadLock.release();
        }

        public void letLock() {
            lockLock.release();
        }

        public void letUnlock() {
            unlockLock.release();
        }
    }
}