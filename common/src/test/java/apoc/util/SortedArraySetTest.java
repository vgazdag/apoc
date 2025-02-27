/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package apoc.util;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author mh
 * @since 22.06.17
 */
public class SortedArraySetTest {
    @Test
    public void add() {
        SortedArraySet<Integer> set = new SortedArraySet<>(Integer.class, 3);
        assertEquals(3, set.getCapacity());
        assertEquals(0, set.getSize());
        assertNull(set.find(0));

        Integer zero = 0;
        Integer one = 1;
        Integer two = 2;
        Integer three = 3;
        Integer four = 4;

        assertSame(zero, set.add(zero));
        assertEquals(3, set.getCapacity());
        assertEquals(1, set.getSize());
        assertSame(zero, set.add(zero));
        assertSame(zero, set.find(zero));
        assertSame(zero, set.get(0));
        assertEquals(3, set.getCapacity());
        assertEquals(1, set.getSize());

        assertSame(two, set.add(two));
        assertSame(two, set.find(two));
        assertSame(two, set.get(1));
        assertEquals(3, set.getCapacity());
        assertEquals(2, set.getSize());
        assertSame(two, set.add(two));
        assertEquals(3, set.getCapacity());
        assertEquals(2, set.getSize());
        assertArrayEquals(new Integer[] {zero, two}, set.items());
        assertArrayEquals(new Integer[] {zero, two, null}, set.data);

        assertSame(one, set.add(one));
        assertSame(one, set.find(one));
        assertSame(one, set.get(1));
        assertSame(two, set.get(2));
        assertEquals(3, set.getCapacity());
        assertEquals(3, set.getSize());
        assertSame(one, set.add(one));
        assertEquals(3, set.getCapacity());
        assertEquals(3, set.getSize());
        assertArrayEquals(new Integer[] {zero, one, two}, set.items());
        assertArrayEquals(new Integer[] {zero, one, two}, set.data);

        assertSame(four, set.add(four));
        assertSame(four, set.find(four));
        assertSame(four, set.get(3));
        assertEquals(13, set.getCapacity());
        assertEquals(4, set.getSize());
        assertSame(four, set.add(four));
        assertEquals(13, set.getCapacity());
        assertEquals(4, set.getSize());
        assertArrayEquals(new Integer[] {zero, one, two, four}, set.items());
        assertArrayEquals(
                new Integer[] {zero, one, two, four, null, null, null, null, null, null, null, null, null}, set.data);

        assertSame(three, set.add(three));
        assertSame(three, set.find(three));
        assertSame(three, set.get(3));
        assertSame(four, set.get(4));
        assertEquals(13, set.getCapacity());
        assertEquals(5, set.getSize());
        assertSame(three, set.add(three));
        assertEquals(13, set.getCapacity());
        assertEquals(5, set.getSize());
        assertArrayEquals(new Integer[] {zero, one, two, three, four}, set.items());
        assertArrayEquals(
                new Integer[] {zero, one, two, three, four, null, null, null, null, null, null, null, null}, set.data);
    }
}
