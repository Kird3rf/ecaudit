/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ericsson.bss.cassandra.ecaudit.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSizeTrackedFileQueue
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testNewQueueIsEmpty()
    {
        SizeTrackedFileQueue queue = new SizeTrackedFileQueue();

        assertThat(queue.accumulatedFileSize()).isZero();
        assertThat(queue.poll()).isNull();
    }

    @Test
    public void testOfferAccumulatesFileSizes() throws IOException
    {
        SizeTrackedFileQueue queue = new SizeTrackedFileQueue();
        queue.offer(file("first", 10));
        queue.offer(file("second", 25));

        assertThat(queue.accumulatedFileSize()).isEqualTo(35);
    }

    @Test
    public void testPollReturnsFilesInFifoOrderAndSubtractsSize() throws IOException
    {
        SizeTrackedFileQueue queue = new SizeTrackedFileQueue();
        File first = file("first", 10);
        File second = file("second", 25);
        queue.offer(first);
        queue.offer(second);

        assertThat(queue.poll()).isSameAs(first);
        assertThat(queue.accumulatedFileSize()).isEqualTo(25);
        assertThat(queue.poll()).isSameAs(second);
        assertThat(queue.accumulatedFileSize()).isZero();
    }

    @Test
    public void testPollOnEmptyQueueReturnsNullAndKeepsSize() throws IOException
    {
        SizeTrackedFileQueue queue = new SizeTrackedFileQueue();
        File file = file("file", 10);
        queue.offer(file);

        assertThat(queue.poll()).isSameAs(file);
        assertThat(queue.poll()).isNull();
        assertThat(queue.accumulatedFileSize()).isZero();
    }

    @Test
    public void testClearRemovesFilesAndResetsSize() throws IOException
    {
        SizeTrackedFileQueue queue = new SizeTrackedFileQueue();
        queue.offer(file("first", 10));
        queue.offer(file("second", 25));

        queue.clear();

        assertThat(queue.accumulatedFileSize()).isZero();
        assertThat(queue.poll()).isNull();
    }

    @Test
    public void testOfferNonExistentFileAddsZeroBytes()
    {
        SizeTrackedFileQueue queue = new SizeTrackedFileQueue();
        File file = new File(tempFolder.getRoot(), "missing");

        queue.offer(file);

        assertThat(queue.accumulatedFileSize()).isZero();
        assertThat(queue.poll()).isSameAs(file);
    }

    @Test
    public void testPollOfFileDeletedAfterOfferKeepsAccumulatedSize() throws IOException
    {
        SizeTrackedFileQueue queue = new SizeTrackedFileQueue();
        File file = file("file", 10);
        queue.offer(file);

        assertThat(file.delete()).isTrue();
        assertThat(queue.poll()).isSameAs(file);
        assertThat(queue.accumulatedFileSize()).isEqualTo(10);
    }

    private File file(String name, int size) throws IOException
    {
        File file = new File(tempFolder.getRoot(), name);
        Files.write(file.toPath(), new byte[size]);
        return file;
    }
}
