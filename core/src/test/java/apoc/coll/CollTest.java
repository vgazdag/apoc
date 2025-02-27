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
package apoc.coll;

import static apoc.util.TestUtil.testCall;
import static apoc.util.TestUtil.testResult;
import static apoc.util.Util.map;
import static apoc.util.collection.Iterables.asSet;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

import apoc.convert.Json;
import apoc.util.TestUtil;
import java.util.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.test.rule.DbmsRule;
import org.neo4j.test.rule.ImpermanentDbmsRule;

public class CollTest {
    // query that procedures a list,
    // with both entity types, via collect(..), and hardcoded one
    private static final String QUERY_WITH_MIXED_TYPES =
            """
            WITH COLLECT {
            MATCH (n:Test)
            RETURN {something: n.something}
            ORDER BY n.a} + { something: [] } + {something: 'alpha'} + {something: [1,2,3]} AS collection
            """;

    private static final String QUERY_WITH_ARRAY =
            "CREATE (:Test {a: 1, something: 'alpha' }), " + "(:Test { a: 2, something: [] }), "
                    + "(:Test { a: 3, something: 'beta' }),"
                    + "(:Test { a: 4, something: [1,2,3] })";

    public static final Map<String, String> MAP_WITH_ALPHA = Map.of("something", "alpha");
    public static final Map<String, String> MAP_WITH_BETA = Map.of("something", "beta");

    @ClassRule
    public static DbmsRule db = new ImpermanentDbmsRule();

    @BeforeClass
    public static void setUp() {
        TestUtil.registerProcedure(db, Coll.class, Json.class);
    }

    @AfterClass
    public static void teardown() {
        db.shutdown();
    }

    @After
    public void after() {
        db.executeTransactionally("MATCH (n) DETACH DELETE n");
    }

    @Test
    public void testRunningTotal() {
        testCall(
                db,
                "RETURN apoc.coll.runningTotal([1,2,3,4,5.5,1]) as value",
                (row) -> assertEquals(asList(1L, 3L, 6L, 10L, 15.5D, 16.5D), row.get("value")));
    }

    @Test
    public void testStandardDeviation() {
        testCall(
                db,
                "RETURN apoc.coll.stdev([10, 12, 23, 23, 16, 23, 21, 16]) as value",
                (row) -> assertEquals(5.237229365663817D, row.get("value")));

        testCall(
                db,
                "RETURN apoc.coll.stdev([10, 12, 23, 23, 16, 23, 21, 16], false) as value",
                (row) -> assertEquals(4.898979485566356D, row.get("value")));

        // conversion from double to long
        testCall(db, "RETURN apoc.coll.stdev([10, 12, 23]) as value", (row) -> assertEquals(7L, row.get("value")));
    }

    @Test
    public void testZip() {
        testCall(db, "RETURN apoc.coll.zip([1,2,3],[4,5]) as value", (row) -> {
            Object value = row.get("value");
            List<List<Long>> expected = asList(asList(1L, 4L), asList(2L, 5L), asList(3L, null));
            assertEquals(expected, value);
        });
    }

    @Test
    public void testPairs() {
        testCall(
                db,
                "RETURN apoc.coll.pairs([1,2,3]) as value",
                (row) -> assertEquals(asList(asList(1L, 2L), asList(2L, 3L), asList(3L, null)), row.get("value")));
    }

    @Test
    public void testPairsMin() {
        testCall(
                db,
                "RETURN apoc.coll.pairsMin([1,2,3]) as value",
                (row) -> assertEquals(asList(asList(1L, 2L), asList(2L, 3L)), row.get("value")));
    }

    @Test
    public void testPairsMinListResult() {
        testCall(
                db, "RETURN apoc.coll.pairsMin([1,2,3])[0][0] as result", (row) -> assertEquals(1L, row.get("result")));
    }

    @Test
    public void testToSet() {
        testCall(
                db,
                "RETURN apoc.coll.toSet([1,2,1,3]) as value",
                (row) -> assertEquals(asList(1L, 2L, 3L), row.get("value")));
    }

    @Test
    public void testSum() {
        testCall(db, "RETURN apoc.coll.sum([1,2,3]) as value", (row) -> assertEquals(6D, row.get("value")));
    }

    @Test
    public void testAvg() {
        testCall(
                db,
                "RETURN apoc.coll.avg([1.4,2,3.2]) as value",
                (row) -> assertEquals(2.2D, (double) row.get("value"), 0.1));
    }

    @Test
    public void testMin() {
        testCall(db, "RETURN apoc.coll.min([1,2]) as value", (row) -> assertEquals(1L, row.get("value")));
        testCall(db, "RETURN apoc.coll.min([1,2,3]) as value", (row) -> assertEquals(1L, row.get("value")));
        testCall(db, "RETURN apoc.coll.min([0.5,1,2.3]) as value", (row) -> assertEquals(0.5D, row.get("value")));
    }

    @Test
    public void testMax() {
        testCall(db, "RETURN apoc.coll.max([1,2,3]) as value", (row) -> assertEquals(3L, row.get("value")));
        testCall(db, "RETURN apoc.coll.max([0.5,1,2.3]) as value", (row) -> assertEquals(2.3D, row.get("value")));
    }

    @Test
    public void testMaxDate() {
        testCall(
                db,
                "RETURN apoc.coll.max([date('2020-04-01'), date('2020-03-01')]) as value",
                (row) -> assertEquals("2020-04-01", row.get("value").toString()));
        testCall(
                db,
                "RETURN apoc.coll.max([datetime('2020-03-30T12:17:43.175Z'), datetime('2020-03-30T12:17:39.982Z')]) as value",
                (row) -> assertEquals(
                        "2020-03-30T12:17:43.175Z", row.get("value").toString()));
        testCall(
                db,
                "RETURN apoc.coll.max([null, datetime('2020-03-30T11:17:39.982Z'), datetime('2020-03-30T12:17:39.982Z'), datetime('2020-03-30T11:17:39.982Z')]) as value",
                (row) -> assertEquals(
                        "2020-03-30T12:17:39.982Z", row.get("value").toString()));
    }

    @Test
    public void testPartitionProcedure() {
        testResult(db, "CALL apoc.coll.partition([1,2,3,4,5],2)", (result) -> {
            Map<String, Object> row = result.next();
            assertEquals(asList(1L, 2L), row.get("value"));
            row = result.next();
            assertEquals(asList(3L, 4L), row.get("value"));
            row = result.next();
            assertEquals(asList(5L), row.get("value"));
            assertFalse(result.hasNext());
        });
    }

    @Test
    public void testPartitionFunction() {
        testResult(db, "UNWIND apoc.coll.partition([1,2,3,4,5],2) AS value RETURN value", (result) -> {
            Map<String, Object> row = result.next();
            assertEquals(asList(1L, 2L), row.get("value"));
            row = result.next();
            assertEquals(asList(3L, 4L), row.get("value"));
            row = result.next();
            assertEquals(asList(5L), row.get("value"));
            assertFalse(result.hasNext());
        });
    }

    @Test
    public void testSumLongs() {
        testCall(db, "RETURN apoc.coll.sumLongs([1,2,3]) AS value", (row) -> assertEquals(6L, row.get("value")));
    }

    @Test
    public void testSort() {
        testCall(
                db,
                "RETURN apoc.coll.sort([3,2,1]) as value",
                (row) -> assertEquals(asList(1L, 2L, 3L), row.get("value")));
    }

    @Test
    public void testIN() {
        testCall(db, "RETURN apoc.coll.contains([1,2,3],1) AS value", (res) -> assertEquals(true, res.get("value")));
    }

    @Test
    public void testIndexOf() {
        testCall(db, "RETURN apoc.coll.indexOf([1,2,3],1) AS value", r -> assertEquals(0L, r.get("value")));
        testCall(db, "RETURN apoc.coll.indexOf([1,2,3],2) AS value", r -> assertEquals(1L, r.get("value")));
        testCall(db, "RETURN apoc.coll.indexOf([1,2,3],3) AS value", r -> assertEquals(2L, r.get("value")));
        testCall(db, "RETURN apoc.coll.indexOf([1,2,3],4) AS value", r -> assertEquals(-1L, r.get("value")));
        testCall(db, "RETURN apoc.coll.indexOf([1,2,3],0) AS value", r -> assertEquals(-1L, r.get("value")));
        testCall(db, "RETURN apoc.coll.indexOf([1,2,3],null) AS value", r -> assertEquals(-1L, r.get("value")));
    }

    @Test
    public void testIndexOfWithCollections() {
        db.executeTransactionally(QUERY_WITH_ARRAY);
        testCall(
                db,
                QUERY_WITH_MIXED_TYPES + "RETURN apoc.coll.indexOf(collection, { something: [] }) AS index",
                r -> assertEquals(1L, r.get("index")));
    }

    @Test
    public void testSplit() {
        testResult(db, "CALL apoc.coll.split([1,2,3,2,4,5],2)", r -> {
            assertEquals(asList(1L), r.next().get("value"));
            assertEquals(asList(3L), r.next().get("value"));
            assertEquals(asList(4L, 5L), r.next().get("value"));
            assertFalse(r.hasNext());
        });
        testResult(db, "CALL apoc.coll.split([1,2,3],2)", r -> {
            assertEquals(asList(1L), r.next().get("value"));
            assertEquals(asList(3L), r.next().get("value"));
            assertFalse(r.hasNext());
        });
        testResult(db, "CALL apoc.coll.split([1,2,3],1)", r -> {
            assertEquals(asList(2L, 3L), r.next().get("value"));
            assertFalse(r.hasNext());
        });
        testResult(db, "CALL apoc.coll.split([1,2,3],3)", r -> {
            assertEquals(asList(1L, 2L), r.next().get("value"));
            assertFalse(r.hasNext());
        });
        testResult(db, "CALL apoc.coll.split([1,2,3],4)", r -> {
            assertEquals(asList(1L, 2L, 3L), r.next().get("value"));
            assertFalse(r.hasNext());
        });
    }

    @Test
    public void testSplitOfWithBothHardcodedAndEntityTypes() {
        db.executeTransactionally(QUERY_WITH_ARRAY);
        testResult(
                db,
                QUERY_WITH_MIXED_TYPES + "CALL apoc.coll.split(collection, { something: [] }) YIELD value RETURN value",
                r -> {
                    Map<String, Object> row = r.next();
                    List<Map<String, Object>> value = (List<Map<String, Object>>) row.get("value");
                    assertEquals(List.of(MAP_WITH_ALPHA), value);
                    row = r.next();
                    value = (List<Map<String, Object>>) row.get("value");
                    assertEquals(2, value.size());
                    assertEquals(MAP_WITH_BETA, value.get(0));
                    // in this case the `[1,2,3]` in `{ something: [1,2,3] }` is an array
                    assertMapWithNumericArray(value.get(1));

                    row = r.next();
                    value = (List<Map<String, Object>>) row.get("value");

                    assertEquals(2, value.size());
                    assertEquals(MAP_WITH_ALPHA, value.get(0));
                    // in this case the `[1,2,3]` in `{ something: [1,2,3] }` is an ArrayList
                    assertEquals(Map.of("something", List.of(1L, 2L, 3L)), value.get(1));

                    assertFalse(r.hasNext());
                });
    }

    @Test
    public void testRemoveWithBothHardcodedAndEntityTypes() {
        db.executeTransactionally(QUERY_WITH_ARRAY);
        testCall(
                db,
                QUERY_WITH_MIXED_TYPES
                        + "RETURN apoc.coll.removeAll(collection, [{ something: [] }, { something: 'alpha' }]) AS value",
                row -> {
                    List<Map<String, Object>> value = (List<Map<String, Object>>) row.get("value");
                    assertEquals(3, value.size());
                    assertEquals(MAP_WITH_BETA, value.get(0));
                    // in this case the `[1,2,3]` in `{ something: [1,2,3] }` is an array
                    assertMapWithNumericArray(value.get(1));
                    // in this case the `[1,2,3]` in `{ something: [1,2,3] }` is an ArrayList
                    assertEquals(Map.of("something", List.of(1L, 2L, 3L)), value.get(2));
                });
    }

    @Test
    public void testCollToSetWithBothHardcodedAndEntityTypes() {
        db.executeTransactionally(QUERY_WITH_ARRAY);

        testCall(db, QUERY_WITH_MIXED_TYPES + "RETURN apoc.coll.toSet(collection) AS value", row -> {
            List<Map<String, Object>> value = (List<Map<String, Object>>) row.get("value");
            assertEquals(4, value.size());
            assertEquals(MAP_WITH_ALPHA, value.get(0));
            assertMapWithEmptyArray(value.get(1));
            assertEquals(MAP_WITH_BETA, value.get(2));
            assertMapWithNumericArray(value.get(3));
        });
    }

    @Test
    public void testSet() {
        testCall(db, "RETURN apoc.coll.set(null,0,4) AS value", r -> assertNull(r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.set([1,2,3],-1,4) AS value",
                r -> assertEquals(asList(1L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.set([1,2,3],0,null) AS value",
                r -> assertEquals(asList(1L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.set([1,2,3],0,4) AS value",
                r -> assertEquals(asList(4L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.set([1,2,3],1,4) AS value",
                r -> assertEquals(asList(1L, 4L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.set([1,2,3],2,4) AS value",
                r -> assertEquals(asList(1L, 2L, 4L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.set([1,2,3],3,4) AS value",
                r -> assertEquals(asList(1L, 2L, 3L), r.get("value")));
    }

    @Test
    public void testInsert() {
        testCall(db, "RETURN apoc.coll.insert(null,0,4) AS value", r -> assertNull(r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insert([1,2,3],-1,4) AS value",
                r -> assertEquals(asList(1L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insert([1,2,3],0,null) AS value",
                r -> assertEquals(asList(1L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insert([1,2,3],0,4) AS value",
                r -> assertEquals(asList(4L, 1L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insert([1,2,3],1,4) AS value",
                r -> assertEquals(asList(1L, 4L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insert([1,2,3],2,4) AS value",
                r -> assertEquals(asList(1L, 2L, 4L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insert([1,2,3],3,4) AS value",
                r -> assertEquals(asList(1L, 2L, 3L, 4L), r.get("value")));
    }

    @Test
    public void testInsertList() {
        testCall(db, "RETURN apoc.coll.insertAll(null,0,[4,5,6]) AS value", r -> assertNull(r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insertAll([1,2,3],-1,[4,5,6]) AS value",
                r -> assertEquals(asList(1L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insertAll([1,2,3],0,null) AS value",
                r -> assertEquals(asList(1L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insertAll([1,2,3],0,[4,5,6]) AS value",
                r -> assertEquals(asList(4L, 5L, 6L, 1L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insertAll([1,2,3],1,[4,5,6]) AS value",
                r -> assertEquals(asList(1L, 4L, 5L, 6L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insertAll([1,2,3],2,[4,5,6]) AS value",
                r -> assertEquals(asList(1L, 2L, 4L, 5L, 6L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.insertAll([1,2,3],3,[4,5,6]) AS value",
                r -> assertEquals(asList(1L, 2L, 3L, 4L, 5L, 6L), r.get("value")));
    }

    @Test
    public void testRemove() {
        testCall(db, "RETURN apoc.coll.remove(null,0,0) AS value", r -> assertNull(r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.remove([1,2,3],-1,0) AS value",
                r -> assertEquals(asList(1L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.remove([1,2,3],0,-1) AS value",
                r -> assertEquals(asList(1L, 2L, 3L), r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.remove([1,2,3],0,0) AS value",
                r -> assertEquals(asList(1L, 2L, 3L), r.get("value")));
        testCall(
                db, "RETURN apoc.coll.remove([1,2,3],0,1) AS value", r -> assertEquals(asList(2L, 3L), r.get("value")));
        testCall(db, "RETURN apoc.coll.remove([1,2,3],0) AS value", r -> assertEquals(asList(2L, 3L), r.get("value")));
        testCall(
                db, "RETURN apoc.coll.remove([1,2,3],1,1) AS value", r -> assertEquals(asList(1L, 3L), r.get("value")));
        testCall(db, "RETURN apoc.coll.remove([1,2,3],1,2) AS value", r -> assertEquals(asList(1L), r.get("value")));
    }

    @Test
    public void testContainsAll() {
        testCall(
                db,
                "RETURN apoc.coll.containsAll([1,2,3],[1,2]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAll([1,2,3],[2,1]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAll([1,2,3],[1,4]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db, "RETURN apoc.coll.containsAll([1,2,3],[]) AS value", (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAll([1,2,3],[1]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAll([1,2,3],[1,2,3,4]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAll([1,1,2,3],[1,2,2,3]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAll(null,[1,2,3]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db, "RETURN apoc.coll.containsAll(null,null) AS value", (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAll([1,2,3],null) AS value",
                (res) -> assertEquals(false, res.get("value")));
    }

    @Test
    public void testContainsAllOfWithCollections() {
        db.executeTransactionally(QUERY_WITH_ARRAY);

        testCall(
                db,
                QUERY_WITH_MIXED_TYPES + "RETURN apoc.coll.containsAll(collection, [{ something: [] }]) AS value",
                row -> assertTrue((boolean) row.get("value")));
    }

    private static void assertMapWithEmptyArray(Map map) {
        assertEquals(1, map.size());
        assertArrayEquals(new String[] {}, (String[]) map.get("something"));
    }

    private static void assertMapWithNumericArray(Map map) {
        assertEquals(1, map.size());
        assertArrayEquals(new long[] {1, 2, 3}, (long[]) map.get("something"));
    }

    @Test
    public void testContainsAllSorted() {
        testCall(
                db,
                "RETURN apoc.coll.containsAllSorted([1,2,3],[1,2]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAllSorted([1,2,3],[1,4]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAllSorted([1,2,3],[]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAllSorted([1,2,3],[1]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsAllSorted([1,2,3],[1,2,3,4]) AS value",
                (res) -> assertEquals(false, res.get("value")));
    }

    @Test
    public void testIsEqualCollection() {
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,2,3],[1,2,3]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,2,3],[3,2,1]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,1,2,2,3],[1,1,2,2,3]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,1,2,3],[1,2,2,3]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,2,3],[1,2]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,2,3],[1,4]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,2,3],[]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,2,3],[1]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,2,3],[1,2,3,4]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,2,3],null) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([1,2,3],[]) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([],null) AS value",
                (res) -> assertEquals(false, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection([],[]) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection(null,null) AS value",
                (res) -> assertEquals(true, res.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.isEqualCollection(null,[]) AS value",
                (res) -> assertEquals(false, res.get("value")));
    }

    @Test
    public void testIN2() {
        int elements = 1_000_000;
        ArrayList<Long> list = new ArrayList<>(elements);
        for (long i = 0; i < elements; i++) {
            list.add(i);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("list", list);
        params.put("value", list.get(list.size() - 1));
        testCall(
                db,
                "RETURN apoc.coll.contains($list,$value) AS value",
                params,
                (res) -> assertEquals(true, res.get("value")));
    }

    @Test
    public void testContainsSorted() {
        int elements = 1_000_000;
        ArrayList<Long> list = new ArrayList<>(elements);
        for (long i = 0; i < elements; i++) {
            list.add(i);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("list", list);
        params.put("value", list.get(list.size() / 2));
        testCall(
                db,
                "RETURN apoc.coll.containsSorted($list,$value) AS value",
                params,
                (res) -> assertEquals(true, res.get("value")));
    }

    @Test
    public void testSortNodes() {
        testCall(
                db,
                "CREATE (n {name:'foo'}),(m {name:'bar'}) WITH n,m RETURN apoc.coll.sortNodes([n,m], 'name') AS nodes",
                (row) -> {
                    List<Node> nodes = (List<Node>) row.get("nodes");
                    assertEquals("foo", nodes.get(0).getProperty("name"));
                    assertEquals("bar", nodes.get(1).getProperty("name"));
                });
    }

    @Test
    public void testSortNodesReverse() {
        testCall(
                db,
                "CREATE (n {name:'foo'}),(m {name:'bar'}) WITH n,m RETURN apoc.coll.sortNodes([n,m], '^name') AS nodes",
                (row) -> {
                    List<Node> nodes = (List<Node>) row.get("nodes");
                    assertEquals("bar", nodes.get(0).getProperty("name"));
                    assertEquals("foo", nodes.get(1).getProperty("name"));
                });
    }

    @Test
    public void testElements() {
        testCall(
                db,
                "CREATE p=(n {name:'foo'})-[r:R]->(n) WITH n,r,p CALL apoc.coll.elements([0,null,n,r,p,42,3.14,true,[42],{a:42},13], 9,1) YIELD elements,_1,_7,_10,_2n,_3r,_4p,_5i,_5f,_6i,_6f,_7b,_8l,_9m RETURN *",
                (row) -> {
                    assertEquals(9L, row.get("elements"));
                    assertEquals(null, row.get("_1"));
                    assertEquals(row.get("n"), row.get("_2n"));
                    assertEquals(row.get("r"), row.get("_3r"));
                    assertEquals(row.get("p"), row.get("_4p"));
                    assertEquals(42L, row.get("_5i"));
                    assertEquals(42D, row.get("_5f"));
                    assertEquals(3.14D, row.get("_6f"));
                    assertEquals(true, row.get("_7"));
                    assertEquals(true, row.get("_7b"));
                    assertEquals(singletonList(42L), row.get("_8l"));
                    assertEquals(map("a", 42L), row.get("_9m"));
                    assertEquals(null, row.get("_10"));
                });
    }

    @Test
    public void testSortMaps() {
        testCall(db, "RETURN apoc.coll.sortMaps([{name:'foo'},{name:'bar'}], 'name') as maps", (row) -> {
            List<Map> nodes = (List<Map>) row.get("maps");
            assertEquals("foo", nodes.get(0).get("name"));
            assertEquals("bar", nodes.get(1).get("name"));
        });
    }

    @Test
    public void testSortMapsMulti() {
        testCall(
                db,
                "RETURN apoc.coll.sortMulti([{name:'foo'},{name:'bar',age:32},{name:'bar',age:42}], ['^name','age'],1,1) as maps",
                (row) -> {
                    List<Map> maps = (List<Map>) row.get("maps");
                    assertEquals(1, maps.size());
                    assertEquals("bar", maps.get(0).get("name"));
                    assertEquals(32L, maps.get(0).get("age")); // 2nd element
                });
    }

    @Test
    public void testSortMapsCount() {

        testCall(
                db,
                "WITH ['a','b','c','c','c','b','a','d'] AS l RETURN apoc.coll.sortMaps(apoc.coll.frequencies(l),'count') as maps",
                (row) -> {
                    List<Map> maps = (List<Map>) row.get("maps");
                    assertEquals(4, maps.size());
                    assertEquals("c", maps.get(0).get("item"));
                    assertEquals("a", maps.get(1).get("item"));
                    assertEquals("b", maps.get(2).get("item"));
                    assertEquals("d", maps.get(3).get("item"));
                });
    }

    @Test
    public void testSortMapsCountReverse() {

        testCall(
                db,
                "WITH ['b','a','c','c','c','b','a','d'] AS l RETURN apoc.coll.sortMaps(apoc.coll.frequencies(l),'^count') as maps",
                (row) -> {
                    List<Map> maps = (List<Map>) row.get("maps");
                    assertEquals(4, maps.size());
                    assertEquals("d", maps.get(0).get("item"));
                    assertEquals("b", maps.get(1).get("item"));
                    assertEquals("a", maps.get(2).get("item"));
                    assertEquals("c", maps.get(3).get("item"));
                });
    }

    @Test
    public void testSetOperations() {
        testCall(
                db,
                "RETURN apoc.coll.union([1,2],[3,2]) AS value",
                r -> assertEquals(asSet(asList(1L, 2L, 3L)), asSet((Iterable) r.get("value"))));
        testCall(
                db,
                "RETURN apoc.coll.intersection([1,2],[3,2]) AS value",
                r -> assertEquals(asSet(asList(2L)), asSet((Iterable) r.get("value"))));
        testCall(
                db,
                "RETURN apoc.coll.intersection([1,2],[2,3]) AS value",
                r -> assertEquals(asSet(asList(2L)), asSet((Iterable) r.get("value"))));
        testCall(
                db,
                "RETURN apoc.coll.intersection([1.2,2.3],[2.3,3.4]) AS value",
                r -> assertEquals(asSet(asList(2.3D)), asSet((Iterable) r.get("value"))));
        testCall(
                db,
                "RETURN apoc.coll.disjunction([1,2],[3,2]) AS value",
                r -> assertEquals(asSet(asList(1L, 3L)), asSet((Iterable) r.get("value"))));
        testCall(
                db,
                "RETURN apoc.coll.subtract([1,2],[3,2]) AS value",
                r -> assertEquals(asSet(asList(1L)), asSet((Iterable) r.get("value"))));
        testCall(
                db,
                "RETURN apoc.coll.unionAll([1,2],[3,2]) AS value",
                r -> assertEquals(asList(1L, 2L, 3L, 2L), r.get("value")));
        testCall(db, "RETURN apoc.coll.removeAll([1,2],[3,2]) AS value", r -> assertEquals(asList(1L), r.get("value")));
    }

    @Test
    public void testIntersectionWithJsonMap() {
        testCall(
                db,
                "WITH apoc.convert.fromJsonMap('{\"numbers\":[1,2]}') as set1, [2,3] as set2\n"
                        + "WITH apoc.coll.intersection(set1.numbers, set2) as value\n"
                        + "RETURN value",
                r -> assertEquals(asSet(asList(2L)), asSet((Iterable) r.get("value"))));
    }

    @Test
    public void testIntersectionWithJsonMapDouble() {
        testCall(
                db,
                "WITH apoc.convert.fromJsonMap('{\"numbers\":[1.2,2.3]}') as set1, [2.3,3.4] as set2\n"
                        + "WITH apoc.coll.intersection(set1.numbers, set2) as value\n"
                        + "RETURN value",
                r -> assertEquals(asSet(asList(2.3D)), asSet((Iterable) r.get("value"))));
    }

    @Test
    public void testShuffleOnNullAndEmptyList() {
        testCall(
                db,
                "RETURN apoc.coll.shuffle([]) as value",
                (row) -> assertEquals(Collections.EMPTY_LIST, row.get("value")));

        testCall(
                db,
                "RETURN apoc.coll.shuffle(null) as value",
                (row) -> assertEquals(Collections.EMPTY_LIST, row.get("value")));
    }

    @Test
    public void testShuffle() {
        // with 10k elements, very remote chance of randomly getting same order
        int elements = 10_000;
        ArrayList<Long> original = new ArrayList<>(elements);
        for (long i = 0; i < elements; i++) {
            original.add(i);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("list", original);

        testCall(db, "RETURN apoc.coll.shuffle($list) as value", params, (row) -> {
            List<Object> result = (List<Object>) row.get("value");
            assertEquals(original.size(), result.size());
            assertTrue(original.containsAll(result));
            assertFalse(original.equals(result));
        });
    }

    @Test
    public void testRandomItemOnNullAndEmptyList() {
        testCall(db, "RETURN apoc.coll.randomItem([]) as value", (row) -> {
            Object result = row.get("value");
            assertEquals(null, result);
        });

        testCall(db, "RETURN apoc.coll.randomItem(null) as value", (row) -> {
            Object result = row.get("value");
            assertEquals(null, result);
        });
    }

    @Test
    public void testRandomItem() {
        testCall(db, "RETURN apoc.coll.randomItem([1,2,3,4,5]) as value", (row) -> {
            Long result = (Long) row.get("value");
            assertTrue(result >= 1 && result <= 5);
        });
    }

    @Test
    public void testRandomItemsOnNullAndEmptyList() {
        testCall(db, "RETURN apoc.coll.randomItems([], 5) as value", (row) -> {
            List<Object> result = (List<Object>) row.get("value");
            assertTrue(result.isEmpty());
        });

        testCall(db, "RETURN apoc.coll.randomItems(null, 5) as value", (row) -> {
            List<Object> result = (List<Object>) row.get("value");
            assertTrue(result.isEmpty());
        });

        testCall(db, "RETURN apoc.coll.randomItems([], 5, true) as value", (row) -> {
            List<Object> result = (List<Object>) row.get("value");
            assertTrue(result.isEmpty());
        });

        testCall(db, "RETURN apoc.coll.randomItems(null, 5, true) as value", (row) -> {
            List<Object> result = (List<Object>) row.get("value");
            assertTrue(result.isEmpty());
        });
    }

    @Test
    public void testRandomItems() {
        // with 100k elements, very remote chance of randomly getting same order
        int elements = 100_000;
        ArrayList<Long> original = new ArrayList<>(elements);
        for (long i = 0; i < elements; i++) {
            original.add(i);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("list", original);

        testCall(db, "RETURN apoc.coll.randomItems($list, 5000) as value", params, (row) -> {
            List<Object> result = (List<Object>) row.get("value");
            assertEquals(result.size(), 5000);
            assertTrue(original.containsAll(result));
            assertFalse(result.equals(original.subList(0, 5000)));
        });
    }

    @Test
    public void testRandomItemsLargerThanOriginal() {
        // with 10k elements, very remote chance of randomly getting same order
        int elements = 10_000;
        ArrayList<Long> original = new ArrayList<>(elements);
        for (long i = 0; i < elements; i++) {
            original.add(i);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("list", original);

        testCall(db, "RETURN apoc.coll.randomItems($list, 20000) as value", params, (row) -> {
            List<Object> result = (List<Object>) row.get("value");
            assertEquals(result.size(), 10000);
            assertTrue(original.containsAll(result));
            assertFalse(result.equals(original));
        });
    }

    @Test
    public void testRandomItemsLargerThanOriginalAllowingRepick() {
        // with 100k elements, very remote chance of randomly getting same order
        int elements = 100_000;
        ArrayList<Long> original = new ArrayList<>(elements);
        for (long i = 0; i < elements; i++) {
            original.add(i);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("list", original);

        testCall(db, "RETURN apoc.coll.randomItems($list, 11000, true) as value", params, (row) -> {
            List<Object> result = (List<Object>) row.get("value");
            assertEquals(result.size(), 11000);
            assertTrue(original.containsAll(result));
        });
    }

    @Test
    public void testContainsDuplicatesOnNullAndEmptyList() {
        testCall(db, "RETURN apoc.coll.containsDuplicates([]) AS value", r -> assertEquals(false, r.get("value")));
        testCall(db, "RETURN apoc.coll.containsDuplicates(null) AS value", r -> assertEquals(false, r.get("value")));
    }

    @Test
    public void testContainsDuplicates() {
        testCall(
                db,
                "RETURN apoc.coll.containsDuplicates([1,2,3,9,7,5]) AS value",
                r -> assertEquals(false, r.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.containsDuplicates([1,2,1,5,4]) AS value",
                r -> assertEquals(true, r.get("value")));
    }

    @Test
    public void testDuplicatesOnNullAndEmptyList() {
        testCall(
                db,
                "RETURN apoc.coll.duplicates([]) as value",
                (row) -> assertEquals(Collections.EMPTY_LIST, row.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.duplicates(null) as value",
                (row) -> assertEquals(Collections.EMPTY_LIST, row.get("value")));
    }

    @Test
    public void testDuplicates() {
        testCall(
                db,
                "RETURN apoc.coll.duplicates([1,2,1,3,2,5,2,3,1,2]) as value",
                (row) -> assertEquals(asList(1L, 2L, 3L), row.get("value")));
    }

    @Test
    public void testDuplicatesWithCountOnNullAndEmptyList() {
        testCall(
                db,
                "RETURN apoc.coll.duplicatesWithCount([]) as value",
                (row) -> assertEquals(Collections.EMPTY_LIST, row.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.duplicatesWithCount(null) as value",
                (row) -> assertEquals(Collections.EMPTY_LIST, row.get("value")));
    }

    @Test
    public void testDuplicatesWithCount() {
        testCall(db, "RETURN apoc.coll.duplicatesWithCount([1,2,1,3,2,5,2,3,1,2]) as value", (row) -> {
            Map<Long, Long> expectedMap = new HashMap<>(3);
            expectedMap.put(1l, 3l);
            expectedMap.put(2l, 4l);
            expectedMap.put(3l, 2l);

            List<Map<String, Object>> result = (List<Map<String, Object>>) row.get("value");
            assertEquals(3, result.size());

            Set<Long> keys = new HashSet<>(3);

            for (Map<String, Object> map : result) {
                Object item = map.get("item");
                Long count = (Long) map.get("count");
                keys.add((Long) item);
                assertTrue(expectedMap.containsKey(item));
                assertEquals(expectedMap.get(item), count);
            }

            assertEquals(expectedMap.keySet(), keys);
        });
    }

    @Test
    public void testFrequenciesOnNullAndEmptyList() {
        testCall(
                db,
                "RETURN apoc.coll.frequencies([]) as value",
                (row) -> assertEquals(Collections.EMPTY_LIST, row.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.frequencies(null) as value",
                (row) -> assertEquals(Collections.EMPTY_LIST, row.get("value")));
    }

    @Test
    public void testFrequencies() {
        testCall(db, "RETURN apoc.coll.frequencies([1,2,1,3,2,5,2,3,1,2]) as value", (row) -> {
            Map<Long, Long> expectedMap = new HashMap<>(4);
            expectedMap.put(1l, 3l);
            expectedMap.put(2l, 4l);
            expectedMap.put(3l, 2l);
            expectedMap.put(5l, 1l);

            List<Map<String, Object>> result = (List<Map<String, Object>>) row.get("value");
            assertEquals(4, result.size());

            Set<Long> keys = new HashSet<>(4);

            for (Map<String, Object> map : result) {
                Object item = map.get("item");
                Long count = (Long) map.get("count");
                keys.add((Long) item);
                assertTrue(expectedMap.containsKey(item));
                assertEquals(expectedMap.get(item), count);
            }

            assertEquals(expectedMap.keySet(), keys);
        });
    }

    @Test
    public void testFrequenciesAsMapAsMap() {
        testCall(
                db,
                "RETURN apoc.coll.frequenciesAsMap([]) as value",
                (row) -> assertEquals(Collections.emptyMap(), row.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.frequenciesAsMap(null) as value",
                (row) -> assertEquals(Collections.emptyMap(), row.get("value")));
        testCall(db, "RETURN apoc.coll.frequenciesAsMap([1,1]) as value", (row) -> {
            Map<String, Object> maps = (Map<String, Object>) row.get("value");
            assertEquals(1, maps.size());
            assertEquals(2L, maps.get("1"));
        });
        testCall(db, "RETURN apoc.coll.frequenciesAsMap([1,2,1]) as value", (row) -> {
            Map<String, Object> maps = (Map<String, Object>) row.get("value");
            assertEquals(2, maps.size());
            assertEquals(2L, maps.get("1"));
            assertEquals(1L, maps.get("2"));
        });
        testCall(db, "RETURN apoc.coll.frequenciesAsMap([1,2,1,3,2,5,2,3,1,2]) as value", (row) -> {
            Map<String, Object> maps = (Map<String, Object>) row.get("value");
            assertEquals(4, maps.size());
            assertEquals(3L, maps.get("1"));
            assertEquals(4L, maps.get("2"));
            assertEquals(2L, maps.get("3"));
            assertEquals(1L, maps.get("5"));
        });
        testCall(
                db,
                "WITH ['a','b','c','c','c','b','a','d'] AS l RETURN apoc.coll.frequenciesAsMap(l) as value",
                (row) -> {
                    Map<String, Object> maps = (Map<String, Object>) row.get("value");
                    assertEquals(4, maps.size());
                    assertEquals(2L, maps.get("a"));
                    assertEquals(2L, maps.get("b"));
                    assertEquals(3L, maps.get("c"));
                    assertEquals(1L, maps.get("d"));
                });
    }

    @Test
    public void testOccurrencesOnNullAndEmptyList() {
        testCall(db, "RETURN apoc.coll.occurrences([], 5) as value", (row) -> assertEquals(0l, row.get("value")));
        testCall(db, "RETURN apoc.coll.occurrences(null, 5) as value", (row) -> assertEquals(0l, row.get("value")));
    }

    @Test
    public void testOccurrences() {
        testCall(
                db,
                "RETURN apoc.coll.occurrences([1,2,1,3,2,5,2,3,1,2], 1) as value",
                (row) -> assertEquals(3l, row.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.occurrences([1,2,1,3,2,5,2,3,1,2], 2) as value",
                (row) -> assertEquals(4l, row.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.occurrences([1,2,1,3,2,5,2,3,1,2], 3) as value",
                (row) -> assertEquals(2l, row.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.occurrences([1,2,1,3,2,5,2,3,1,2], 5) as value",
                (row) -> assertEquals(1l, row.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.occurrences([1,2,1,3,2,5,2,3,1,2], -5) as value",
                (row) -> assertEquals(0l, row.get("value")));
    }

    @Test
    public void testFlatten() {
        testCall(
                db,
                "RETURN apoc.coll.flatten([[1,2],[3,4],[4],[5,6,7]]) as value",
                (row) -> assertEquals(asList(1L, 2L, 3L, 4L, 4L, 5L, 6L, 7L), row.get("value")));
    }

    @Test
    public void testCombinationsWith0() {
        testCall(
                db,
                "RETURN apoc.coll.combinations([1,2,3,4,5], 0) as value",
                (row) -> assertEquals(Collections.emptyList(), row.get("value")));
    }

    @Test
    public void testCombinationsWithNegative() {
        testCall(
                db,
                "RETURN apoc.coll.combinations([1,2,3,4,5], -1) as value",
                (row) -> assertEquals(Collections.emptyList(), row.get("value")));
    }

    @Test
    public void testCombinationsWithEmptyCollection() {
        testCall(
                db,
                "RETURN apoc.coll.combinations([], 0) as value",
                (row) -> assertEquals(Collections.emptyList(), row.get("value")));
    }

    @Test
    public void testCombinationsWithNullCollection() {
        testCall(
                db,
                "RETURN apoc.coll.combinations(null, 0) as value",
                (row) -> assertEquals(Collections.emptyList(), row.get("value")));
    }

    @Test
    public void testCombinationsWithTooLargeSelect() {
        testCall(
                db,
                "RETURN apoc.coll.combinations([1,2,3,4,5], 6) as value",
                (row) -> assertEquals(Collections.emptyList(), row.get("value")));
    }

    @Test
    public void testCombinationsWithListSizeSelect() {
        testCall(db, "RETURN apoc.coll.combinations([1,2,3,4,5], 5) as value", (row) -> {
            List<List<Object>> result = new ArrayList<>();
            result.add(asList(1l, 2l, 3l, 4l, 5l));
            assertEquals(result, row.get("value"));
        });
    }

    @Test
    public void testCombinationsWithSingleSelect() {
        testCall(db, "RETURN apoc.coll.combinations([1,2,3,4,5], 3) as value", (row) -> {
            List<List<Object>> result = new ArrayList<>();
            result.add(asList(1l, 2l, 3l));
            result.add(asList(1l, 2l, 4l));
            result.add(asList(1l, 3l, 4l));
            result.add(asList(2l, 3l, 4l));
            result.add(asList(1l, 2l, 5l));
            result.add(asList(1l, 3l, 5l));
            result.add(asList(2l, 3l, 5l));
            result.add(asList(1l, 4l, 5l));
            result.add(asList(2l, 4l, 5l));
            result.add(asList(3l, 4l, 5l));
            assertEquals(result, row.get("value"));
        });
    }

    @Test
    public void testCombinationsWithMinSelectGreaterThanMax() {
        testCall(db, "RETURN apoc.coll.combinations([1,2,3,4], 3, 2) as value", (row) -> {
            assertEquals(Collections.emptyList(), row.get("value"));
        });
    }

    @Test
    public void testCombinationsWithMinAndMaxSelect() {
        testCall(db, "RETURN apoc.coll.combinations([1,2,3,4], 2, 3) as value", (row) -> {
            List<List<Object>> result = new ArrayList<>();
            result.add(asList(1l, 2l));
            result.add(asList(1l, 3l));
            result.add(asList(2l, 3l));
            result.add(asList(1l, 4l));
            result.add(asList(2l, 4l));
            result.add(asList(3l, 4l));
            result.add(asList(1l, 2l, 3l));
            result.add(asList(1l, 2l, 4l));
            result.add(asList(1l, 3l, 4l));
            result.add(asList(2l, 3l, 4l));

            assertEquals(result, row.get("value"));
        });
    }

    @Test
    public void testVerifyAllValuesAreDifferent() {
        testCall(db, "RETURN apoc.coll.different([1, 2, 3]) as value", (row) -> {
            assertEquals(true, row.get("value"));
        });
        testCall(db, "RETURN apoc.coll.different([1, 1, 1]) as value", (row) -> {
            assertEquals(false, row.get("value"));
        });
        testCall(db, "RETURN apoc.coll.different([3, 3, 1]) as value", (row) -> {
            assertEquals(false, row.get("value"));
        });
    }

    @Test
    public void testDropNeighboursNodes() {
        db.executeTransactionally("CREATE (n:Person {name:'Foo'}) " + "CREATE (b:Person {name:'Bar'}) "
                + "CREATE (n)-[:KNOWS]->(n)-[:LIVES_WITH]->(n)");
        testResult(
                db,
                "MATCH p=(n)-[:KNOWS]->(m)-[:LIVES_WITH]->(h) RETURN apoc.coll.dropDuplicateNeighbors(nodes(p)) as value",
                (row) -> {
                    assertEquals(true, row.hasNext());
                    assertEquals(1, row.next().size());
                });
    }

    @Test
    public void testDropNeighboursNumbers() {
        testResult(
                db,
                "WITH [1,2,3,4,4,5,6,6,4,7] AS values RETURN apoc.coll.dropDuplicateNeighbors(values) as value",
                (row) -> {
                    assertEquals(
                            asList(1L, 2L, 3L, 4L, 5L, 6L, 4L, 7L), row.next().get("value"));
                });
    }

    @Test
    public void testDropNeighboursStrings() {
        testResult(
                db,
                "WITH ['a','a','hello','hello','hello','foo','bar','apoc','apoc!','hello'] AS values RETURN apoc.coll.dropDuplicateNeighbors(values) as value",
                (row) -> {
                    assertEquals(
                            asList("a", "hello", "foo", "bar", "apoc", "apoc!", "hello"),
                            row.next().get("value"));
                });
    }

    @Test
    public void testDropNeighboursDifferentTypes() {
        testResult(
                db,
                "WITH ['a','a',1,1,'hello','foo','bar','apoc','apoc!',1] AS values RETURN apoc.coll.dropDuplicateNeighbors(values) as value",
                (row) -> {
                    assertEquals(
                            asList("a", 1L, "hello", "foo", "bar", "apoc", "apoc!", 1L),
                            row.next().get("value"));
                });
    }

    @Test
    public void testFill() {
        testResult(db, "RETURN apoc.coll.fill('abc',2) as value", (row) -> {
            assertEquals(asList("abc", "abc"), row.next().get("value"));
        });
    }

    @Test
    public void testSortText() {
        testCall(
                db,
                "RETURN apoc.coll.sortText(['b', 'a']) as value",
                (row) -> assertEquals(asList("a", "b"), row.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.sortText(['Єльська', 'Гусак'], {locale: 'ru'}) as value",
                (row) -> assertEquals(asList("Гусак", "Єльська"), row.get("value")));
    }

    @Test
    public void testPairWithOffsetFn() {
        testCall(
                db,
                "RETURN apoc.coll.pairWithOffset([1,2,3,4], 2) AS value",
                (row) -> assertEquals(
                        asList(asList(1L, 3L), asList(2L, 4L), asList(3L, null), asList(4L, null)), row.get("value")));
        testCall(
                db,
                "RETURN apoc.coll.pairWithOffset([1,2,3,4], -2) AS value",
                (row) -> assertEquals(
                        asList(asList(1L, null), asList(2L, null), asList(3L, 1L), asList(4L, 2L)), row.get("value")));
    }

    @Test
    public void testPairWithOffset() {
        testResult(db, "CALL apoc.coll.pairWithOffset([1,2,3,4], 2)", (result) -> {
            assertEquals(asList(1L, 3L), result.next().get("value"));
            assertEquals(asList(2L, 4L), result.next().get("value"));
            assertEquals(asList(3L, null), result.next().get("value"));
            assertEquals(asList(4L, null), result.next().get("value"));
            assertFalse(result.hasNext());
        });
        testResult(db, "CALL apoc.coll.pairWithOffset([1,2,3,4], -2)", (result) -> {
            assertEquals(asList(1L, null), result.next().get("value"));
            assertEquals(asList(2L, null), result.next().get("value"));
            assertEquals(asList(3L, 1L), result.next().get("value"));
            assertEquals(asList(4L, 2L), result.next().get("value"));
            assertFalse(result.hasNext());
        });
    }
}
