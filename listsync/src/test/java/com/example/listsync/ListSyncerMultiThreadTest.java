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

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Semaphore;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ListSyncerMultiThreadTest {

    private ListSyncer listSyncer;
    private DelayingRepository repository;

    @Before
    public void setUp() throws Exception {
        repository = new DelayingRepository();
        ListRepository remote = new ListRepository("foo", repository);
        listSyncer = new ListSyncer(remote);
    }

    @Test
    public void testChecksBothItemsWhenRemoteListItemIsChecked() throws Exception {
        CheckItem anItem = new CheckItem("foo");
        repository.add(anItem);
        CheckItem anotherItem = new CheckItem("bar");
        repository.add(anotherItem);
        repository.letDownload();
        listSyncer.run();

        listSyncer.remove(anItem);
        repository.remove(anotherItem);

        repository.letLock();
        repository.letDownload();
        repository.letUpload();
        repository.letUnlock();
        Thread thread = new Thread(listSyncer);
        thread.start();
        Thread.sleep(1000);
        listSyncer.add(anItem.toggleChecked());
        repository.add(anotherItem.toggleChecked());
        repository.letLock();
        repository.letDownload();
        repository.letUpload();
        repository.letUnlock();
        thread.join();
        listSyncer.run();
        assertThat(repository.getFileContent(), containsInAnyOrder(
                is(anItem.toggleChecked().toString()),
                is(anotherItem.toggleChecked().toString())
        ));
        assertThat(listSyncer.getLocal(), containsInAnyOrder(
                is(anItem.toggleChecked()),
                is(anotherItem.toggleChecked())
        ));
    }

    private static class DelayingRepository extends RepositoryStub {
        private Semaphore downloadLock = new Semaphore(0);
        private Semaphore listFilesLock = new Semaphore(0);
        private Semaphore uploadLock = new Semaphore(0);
        private Semaphore lockLock = new Semaphore(0);
        private Semaphore unlockLock = new Semaphore(0);

        @Override
        public List<String> download(String file) throws IOException {
            try {
                downloadLock.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.download(file);
        }

        @Override
        public Collection<String> listFiles() throws IOException {
            try {
                listFilesLock.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.listFiles();
        }

        @Override
        public void upload(String name, List<String> bytes) throws IOException {
            try {
                uploadLock.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            super.upload(name, bytes);
        }

        @Override
        public void lock(String name) {
            try {
                lockLock.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            super.lock(name);
        }

        @Override
        public void unlock(String name) {
            try {
                unlockLock.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            super.unlock(name);
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
