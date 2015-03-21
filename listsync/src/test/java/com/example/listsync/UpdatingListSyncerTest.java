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

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.fail;

public class UpdatingListSyncerTest {

    private UpdatingListSyncer listSyncer;
    private Semaphore repoWorkSemaphore;
    private Semaphore repoWorkDoneSemaphore;

    @Before
    public void setUp() throws Exception {
        repoWorkSemaphore = new Semaphore(0);
        repoWorkDoneSemaphore = new Semaphore(0);
        final SimpleListRepository simpleListRepository = new SimpleListRepository();
        ListRepository repository = (ListRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ListRepository.class}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("getContent")) {
                    return method.invoke(simpleListRepository, args);
                }
                repoWorkSemaphore.acquire();
                System.out.println("repo-method " + method.getName() + " acquired. now: " + repoWorkSemaphore.availablePermits());
                try {
                    return method.invoke(simpleListRepository, args);
                } finally {
                    repoWorkDoneSemaphore.release();
                    System.out.println("repo-method " + method.getName() + " done. workdone now: " + repoWorkDoneSemaphore.availablePermits());
                }
            }
        });
        listSyncer = new UpdatingListSyncer(repository);
        listSyncer.setUpdateTimeout(3, TimeUnit.SECONDS);
    }

    @Ignore("somethings still wrong here")
    @Test
    public void testToggle4Times() throws Exception {
        CheckItem foo = new CheckItem("foo");
        listSyncer.add(foo);
        repoWorkSemaphore.release(1);
        repoWorkDoneSemaphore.acquire(1);
        listSyncer.toggle(foo);
        listSyncer.toggle(foo.toggleChecked());
        listSyncer.toggle(foo);
        listSyncer.toggle(foo.toggleChecked());
        repoWorkSemaphore.release(2);
        //repoWorkDoneSemaphore.acquire(2);
        List<CheckItem> local = listSyncer.getLocal();
        waitForAssertThat(local, contains(foo), 10);
    }

    public <T> void waitForAssertThat(T actual, Matcher<T> matcher, long seconds) {
        long retries = seconds;
        while(retries > 0) {
            try {
                assertThat(actual, matcher);
                return;
            } catch (AssertionError e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    fail("interrupted");
                }
                retries--;
            }
        }
        assertThat("assertion did not become true after " + seconds + " seconds", actual, matcher);
    }

    @Test
    public void testToggle3Times() throws Exception {
        CheckItem foo = new CheckItem("foo");
        listSyncer.add(foo);
        listSyncer.toggle(foo);
        listSyncer.toggle(foo.toggleChecked());
        listSyncer.toggle(foo);
        repoWorkSemaphore.release(2);
        repoWorkDoneSemaphore.acquire(2);
        List<CheckItem> local = listSyncer.getLocal();
        waitForAssertThat(local, contains(foo.toggleChecked()), 10);
    }

    private class SimpleListRepository implements ListRepository {
        private List<CheckItem> content = new ArrayList<>();

        @Override
        public List<CheckItem> getContent() throws IOException {
            return Collections.unmodifiableList(content);
        }

        @Override
        public void remove(CheckItem item) throws IOException {
            content.remove(item);
        }

        @Override
        public void add(CheckItem item) throws IOException {
            content.add(item);
        }

        @Override
        public void toggle(CheckItem item) throws IOException {
            int i = content.indexOf(item);
            content.set(i, item.toggleChecked());
        }
    }
}
