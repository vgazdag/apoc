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
package apoc.it.core;

import static apoc.ApocConfig.APOC_EXPORT_FILE_ENABLED;
import static apoc.ApocConfig.apocConfig;
import static apoc.util.MapUtil.map;
import static apoc.util.s3.S3TestUtil.assertStringFileEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import apoc.export.csv.ExportCSV;
import apoc.graph.Graphs;
import apoc.util.TestUtil;
import apoc.util.s3.S3BaseTest;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.neo4j.test.rule.DbmsRule;
import org.neo4j.test.rule.ImpermanentDbmsRule;

public class ExportCsvS3Test extends S3BaseTest {

    @ClassRule
    public static DbmsRule db = new ImpermanentDbmsRule();

    @BeforeClass
    public static void setUp() {
        baseBeforeClass();

        apocConfig().setProperty(APOC_EXPORT_FILE_ENABLED, true);
        TestUtil.registerProcedure(db, ExportCSV.class, Graphs.class);
        db.executeTransactionally(
                "CREATE (f:User1:User {name:'foo',age:42,male:true,kids:['a','b','c']})-[:KNOWS]->(b:User {name:'bar',age:42}),(c:User {age:12})");
        db.executeTransactionally(
                "CREATE (f:Address1:Address {name:'Andrea', city: 'Milano', street:'Via Garibaldi, 7'})-[:NEXT_DELIVERY]->(a:Address {name: 'Bar Sport'}), (b:Address {street: 'via Benni'})");
    }

    @AfterClass
    public static void teardown() {
        db.shutdown();
    }

    private static final String EXPECTED_QUERY_NODES = String.format("\"u\"%n"
            + "\"{\"\"id\"\":0,\"\"labels\"\":[\"\"User\"\",\"\"User1\"\"],\"\"properties\"\":{\"\"name\"\":\"\"foo\"\",\"\"age\"\":42,\"\"male\"\":true,\"\"kids\"\":[\"\"a\"\",\"\"b\"\",\"\"c\"\"]}}\"%n"
            + "\"{\"\"id\"\":1,\"\"labels\"\":[\"\"User\"\"],\"\"properties\"\":{\"\"name\"\":\"\"bar\"\",\"\"age\"\":42}}\"%n"
            + "\"{\"\"id\"\":2,\"\"labels\"\":[\"\"User\"\"],\"\"properties\"\":{\"\"age\"\":12}}\"%n");
    private static final String EXPECTED_QUERY =
            String.format("\"u.age\",\"u.name\",\"u.male\",\"u.kids\",\"labels(u)\"%n"
                    + "\"42\",\"foo\",\"true\",\"[\"\"a\"\",\"\"b\"\",\"\"c\"\"]\",\"[\"\"User1\"\",\"\"User\"\"]\"%n"
                    + "\"42\",\"bar\",\"\",\"\",\"[\"\"User\"\"]\"%n"
                    + "\"12\",\"\",\"\",\"\",\"[\"\"User\"\"]\"%n");
    private static final String EXPECTED_QUERY_WITHOUT_QUOTES = String.format(
            "u.age,u.name,u.male,u.kids,labels(u)%n" + "42,foo,true,[\"a\",\"b\",\"c\"],[\"User1\",\"User\"]%n"
                    + "42,bar,,,[\"User\"]%n"
                    + "12,,,,[\"User\"]%n");
    private static final String EXPECTED = String.format(
            "\"_id\",\"_labels\",\"age\",\"city\",\"kids\",\"male\",\"name\",\"street\",\"_start\",\"_end\",\"_type\"%n"
                    + "\"0\",\":User:User1\",\"42\",\"\",\"[\"\"a\"\",\"\"b\"\",\"\"c\"\"]\",\"true\",\"foo\",\"\",,,%n"
                    + "\"1\",\":User\",\"42\",\"\",\"\",\"\",\"bar\",\"\",,,%n"
                    + "\"2\",\":User\",\"12\",\"\",\"\",\"\",\"\",\"\",,,%n"
                    + "\"3\",\":Address:Address1\",\"\",\"Milano\",\"\",\"\",\"Andrea\",\"Via Garibaldi, 7\",,,%n"
                    + "\"4\",\":Address\",\"\",\"\",\"\",\"\",\"Bar Sport\",\"\",,,%n"
                    + "\"5\",\":Address\",\"\",\"\",\"\",\"\",\"\",\"via Benni\",,,%n"
                    + ",,,,,,,,\"0\",\"1\",\"KNOWS\"%n"
                    + ",,,,,,,,\"3\",\"4\",\"NEXT_DELIVERY\"%n");

    private static final String EXPECTED_NONE_QUOTES =
            String.format("_id,_labels,age,city,kids,male,name,street,_start,_end,_type%n"
                    + "0,:User:User1,42,,[\"a\",\"b\",\"c\"],true,foo,,,,%n"
                    + "1,:User,42,,,,bar,,,,%n"
                    + "2,:User,12,,,,,,,,%n"
                    + "3,:Address:Address1,,Milano,,,Andrea,Via Garibaldi, 7,,,%n"
                    + "4,:Address,,,,,Bar Sport,,,,%n"
                    + "5,:Address,,,,,,via Benni,,,%n"
                    + ",,,,,,,,0,1,KNOWS%n"
                    + ",,,,,,,,3,4,NEXT_DELIVERY%n");
    private static final String EXPECTED_NEEDED_QUOTES =
            String.format("_id,_labels,age,city,kids,male,name,street,_start,_end,_type%n"
                    + "0,:User:User1,42,,\"[\"a\",\"b\",\"c\"]\",true,foo,,,,%n"
                    + "1,:User,42,,,,bar,,,,%n"
                    + "2,:User,12,,,,,,,,%n"
                    + "3,:Address:Address1,,Milano,,,Andrea,\"Via Garibaldi, 7\",,,%n"
                    + "4,:Address,,,,,Bar Sport,,,,%n"
                    + "5,:Address,,,,,,via Benni,,,%n"
                    + ",,,,,,,,0,1,KNOWS%n"
                    + ",,,,,,,,3,4,NEXT_DELIVERY%n");

    @Test
    public void testExportAllCsvS3() {
        String fileName = "all.csv";
        String s3Url = s3Container.getUrl(fileName);
        TestUtil.testCall(
                db, "CALL apoc.export.csv.all($s3,null)", map("s3", s3Url), (r) -> assertResults(s3Url, r, "database"));
        assertStringFileEquals(EXPECTED, s3Url);
    }

    @Test
    public void testExportAllCsvS3WithQuotes() {
        String fileName = "all.csv";
        String s3Url = s3Container.getUrl(fileName);
        TestUtil.testCall(
                db,
                "CALL apoc.export.csv.all($s3,{quotes: true})",
                map("s3", s3Url),
                (r) -> assertResults(s3Url, r, "database"));
        assertStringFileEquals(EXPECTED, s3Url);
    }

    @Test
    public void testExportAllCsvS3WithoutQuotes() {
        String fileName = "all1.csv";
        String s3Url = s3Container.getUrl(fileName);
        TestUtil.testCall(
                db,
                "CALL apoc.export.csv.all($s3,{quotes: 'none'})",
                map("s3", s3Url),
                (r) -> assertResults(s3Url, r, "database"));
        assertStringFileEquals(EXPECTED_NONE_QUOTES, s3Url);
    }

    @Test
    public void testExportAllCsvS3NeededQuotes() {
        String fileName = "all2.csv";
        String s3Url = s3Container.getUrl(fileName);
        TestUtil.testCall(
                db,
                "CALL apoc.export.csv.all($s3,{quotes: 'ifNeeded'})",
                map("s3", s3Url),
                (r) -> assertResults(s3Url, r, "database"));
        assertStringFileEquals(EXPECTED_NEEDED_QUOTES, s3Url);
    }

    @Test
    public void testExportGraphCsv() {
        String fileName = "graph.csv";
        String s3Url = s3Container.getUrl(fileName);
        TestUtil.testCall(
                db,
                "CALL apoc.graph.fromDB('test',{}) yield graph "
                        + "CALL apoc.export.csv.graph(graph, $s3,{quotes: 'none'}) "
                        + "YIELD nodes, relationships, properties, file, source,format, time "
                        + "RETURN *",
                map("s3", s3Url),
                (r) -> assertResults(s3Url, r, "graph"));
        assertStringFileEquals(EXPECTED_NONE_QUOTES, s3Url);
    }

    @Test
    public void testExportGraphCsvWithoutQuotes() {
        String fileName = "graph1.csv";
        String s3Url = s3Container.getUrl(fileName);
        TestUtil.testCall(
                db,
                "CALL apoc.graph.fromDB('test',{}) yield graph " + "CALL apoc.export.csv.graph(graph, $s3,null) "
                        + "YIELD nodes, relationships, properties, file, source,format, time "
                        + "RETURN *",
                map("s3", s3Url),
                (r) -> assertResults(s3Url, r, "graph"));
        assertStringFileEquals(EXPECTED, s3Url);
    }

    @Test
    public void testExportQueryCsv() {
        String fileName = "query.csv";
        String s3Url = s3Container.getUrl(fileName);
        String query = "MATCH (u:User) return u.age, u.name, u.male, u.kids, labels(u)";
        TestUtil.testCall(db, "CALL apoc.export.csv.query($query,$s3,null)", map("s3", s3Url, "query", query), (r) -> {
            assertTrue("Should get statement", r.get("source").toString().contains("statement: cols(5)"));
            assertEquals(s3Url, r.get("file"));
            assertEquals("csv", r.get("format"));
        });
        assertStringFileEquals(EXPECTED_QUERY, s3Url);
    }

    @Test
    public void testExportQueryCsvWithoutQuotes() {
        String fileName = "query1.csv";
        String s3Url = s3Container.getUrl(fileName);
        String query = "MATCH (u:User) return u.age, u.name, u.male, u.kids, labels(u)";
        TestUtil.testCall(
                db, "CALL apoc.export.csv.query($query,$s3,{quotes: false})", map("s3", s3Url, "query", query), (r) -> {
                    assertTrue(
                            "Should get statement", r.get("source").toString().contains("statement: cols(5)"));
                    assertEquals(s3Url, r.get("file"));
                    assertEquals("csv", r.get("format"));
                });
        assertStringFileEquals(EXPECTED_QUERY_WITHOUT_QUOTES, s3Url);
    }

    @Test
    public void testExportQueryNodesCsv() {
        String fileName = "query_nodes.csv";
        String s3Url = s3Container.getUrl(fileName);
        String query = "MATCH (u:User) return u";
        TestUtil.testCall(db, "CALL apoc.export.csv.query($query,$s3,null)", map("s3", s3Url, "query", query), (r) -> {
            assertTrue("Should get statement", r.get("source").toString().contains("statement: cols(1)"));
            assertEquals(s3Url, r.get("file"));
            assertEquals("csv", r.get("format"));
        });
        assertStringFileEquals(EXPECTED_QUERY_NODES, s3Url);
    }

    @Test
    public void testExportQueryNodesCsvParams() {
        String fileName = "query_nodes1.csv";
        String s3Url = s3Container.getUrl(fileName);
        String query = "MATCH (u:User) WHERE u.age > $age return u";
        TestUtil.testCall(
                db,
                "CALL apoc.export.csv.query($query,$s3,{params:{age:10}})",
                map("s3", s3Url, "query", query),
                (r) -> {
                    assertTrue("Should s3 statement", r.get("source").toString().contains("statement: cols(1)"));
                    assertEquals(s3Url, r.get("file"));
                    assertEquals("csv", r.get("format"));
                });
        assertStringFileEquals(EXPECTED_QUERY_NODES, s3Url);
    }

    private void assertResults(String fileName, Map<String, Object> r, final String source) {
        assertEquals(6L, r.get("nodes"));
        assertEquals(2L, r.get("relationships"));
        assertEquals(12L, r.get("properties"));
        assertEquals(source + ": nodes(6), rels(2)", r.get("source"));
        assertEquals(fileName, r.get("file"));
        assertEquals("csv", r.get("format"));
        assertTrue("Should get time greater than 0", ((long) r.get("time")) >= 0);
    }
}
