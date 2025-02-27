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
package apoc.nodes;

import static apoc.util.TestUtil.testResult;
import static apoc.util.Util.map;
import static org.junit.Assert.*;

import apoc.util.TestUtil;
import apoc.util.collection.Iterators;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.internal.util.Iterables;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.test.rule.DbmsRule;
import org.neo4j.test.rule.ImpermanentDbmsRule;

/**
 * @author mh
 * @since 22.06.17
 */
public class GroupingTest {

    @Rule
    public DbmsRule db = new ImpermanentDbmsRule();

    @Before
    public void setUp() {
        TestUtil.registerProcedure(db, Grouping.class);
    }

    @After
    public void teardown() {
        db.shutdown();
    }

    public void createGraph() {
        db.executeTransactionally("CREATE " + "(alice:Person {name:'Alice', gender:'female', age:32, kids:1}),"
                + "(bob:Person   {name:'Bob',   gender:'male',   age:42, kids:3}),"
                + "(eve:Person   {name:'Eve',   gender:'female', age:28, kids:2}),"
                + "(graphs:Forum {name:'Graphs',    members:23}),"
                + "(dbs:Forum    {name:'Databases', members:42}),"
                + "(alice)-[:KNOWS {since:2017}]->(bob),"
                + "(eve)-[:KNOWS   {since:2018}]->(bob),"
                + "(alice)-[:MEMBER_OF]->(graphs),"
                + "(alice)-[:MEMBER_OF]->(dbs),"
                + "(bob)-[:MEMBER_OF]->(dbs),"
                + "(eve)-[:MEMBER_OF]->(graphs)");
    }

    @Test
    public void testGroupAllNodes() {
        createGraph();
        Map<String, Object> female = map("gender", "female", "count_*", 2L, "min_age", 28L);
        Map<String, Object> male = map("gender", "male", "count_*", 1L, "min_age", 42L);
        Map<String, Object> other = map("gender", null, "count_*", 2L);

        testResult(
                db,
                "CALL apoc.nodes.group(" + "['*'],['gender'],[" + "{`*`:'count', age:'min'}," + "{`*`:'count'}" + "])",
                (result) -> {
                    assertTrue(result.hasNext());

                    String[] keys = {"count_*", "gender", "min_age"};
                    while (result.hasNext()) {
                        Map<String, Object> row = result.next();

                        Node node = (Node) row.get("node");
                        Object value = node.getProperty("gender");

                        Relationship rel = (Relationship) row.get("relationship");
                        if (value == null) {
                            assertEquals(other, node.getProperties(keys));
                            assertNull(rel);
                        } else if (value.equals("female")) {
                            assertEquals(female, node.getProperties(keys));
                            //              assertEquals(2L, rels.size());
                            Object count = rel.getProperty("count_*");
                            if (count.equals(3L)) { // MEMBER_OF
                                assertEquals(other, rel.getEndNode().getProperties(keys));
                            } else if (count.equals(2L)) { // KNOWS
                                assertEquals(male, rel.getEndNode().getProperties(keys));
                            } else {
                                assertTrue("Unexpected count value: " + count, false);
                            }
                        } else if (value.equals("male")) {
                            assertEquals(male, node.getProperties(keys));
                            assertEquals(1L, rel.getProperty("count_*"));
                            assertEquals(other, rel.getEndNode().getProperties(keys));
                        } else {
                            assertTrue("Unexpected value: " + value, false);
                        }
                    }
                });
    }

    @Test
    public void testGroupNode() {
        createGraph();
        Map<String, Object> female =
                map("gender", "female", "count_*", 2L, "sum_kids", 3L, "min_age", 28L, "max_age", 32L, "avg_age", 30D);
        Map<String, Object> male =
                map("gender", "male", "count_*", 1L, "sum_kids", 3L, "min_age", 42L, "max_age", 42L, "avg_age", 42D);
        testResult(
                db,
                "CALL apoc.nodes.group(" + "['Person'],['gender'],["
                        + "{`*`:'count', kids:'sum', age:['min', 'max', 'avg'], gender:'collect'},"
                        + "{`*`:'count', since:['min', 'max']}"
                        + "])",
                (result) -> {
                    assertTrue(result.hasNext());
                    Map<String, Object> row = result.next();
                    Node node = (Node) row.get("node");
                    String[] keys = {"count_*", "gender", "sum_kids", "min_age", "max_age", "avg_age"};
                    assertEquals(node.getProperty("gender").equals("female") ? female : male, node.getProperties(keys));
                    Relationship rel = (Relationship) row.get("relationship");
                    assertEquals(2L, rel.getProperty("count_*"));
                    assertEquals(2017L, rel.getProperty("min_since"));
                    assertEquals(2018L, rel.getProperty("max_since"));
                    assertEquals("KNOWS", rel.getType().name());
                    node = rel.getOtherNode(node);
                    assertEquals(node.getProperty("gender").equals("female") ? female : male, node.getProperties(keys));

                    assertTrue(result.hasNext());
                    row = result.next();

                    node = (Node) row.get("node");
                    assertEquals(node.getProperty("gender").equals("female") ? female : male, node.getProperties(keys));
                    rel = (Relationship) row.get("relationship");
                    assertEquals(null, rel);
                });
    }

    @Test
    public void testRemoveOrphans() {
        db.executeTransactionally("CREATE (u:User {gender:'male'})");
        TestUtil.testCallCount(db, "CALL apoc.nodes.group(['User'],['gender'],null,{orphans:false})", 0);
        TestUtil.testCallCount(db, "CALL apoc.nodes.group(['User'],['gender'],null,{orphans:true})", 1);
    }

    @Test
    public void testSelfRels() {
        db.executeTransactionally("CREATE (u:User {gender:'male'})-[:REL]->(u)");

        Relationship rel = TestUtil.singleResultFirstColumn(
                db,
                "CALL apoc.nodes.group(['User'],['gender'],null,{selfRels:true}) yield relationship return relationship");
        assertNotNull(rel);

        rel = TestUtil.singleResultFirstColumn(
                db,
                "CALL apoc.nodes.group(['User'],['gender'],null,{selfRels:false}) yield relationship return relationship");
        assertNull(rel);
    }

    @Test
    public void testFilterMin() {
        db.executeTransactionally(
                "CREATE (:User {name:'Joe',gender:'male'}), (:User {gender:'female',name:'Jane'}), (:User {gender:'female',name:'Jenny'})");
        TestUtil.testResult(
                db, "CALL apoc.nodes.group(['User'],['gender'],null,{filter:{`User.count_*.min`:2}})", result -> {
                    Node node = Iterators.single(result.columnAs("node"));
                    assertEquals("female", node.getProperty("gender"));
                });
        TestUtil.testCallCount(
                db, "CALL apoc.nodes.group(['User'],['gender'],null,{filter:{`User.count_*.min`:3}})", 0);
    }

    @Test
    public void testFilterMax() {
        db.executeTransactionally(
                "CREATE (:User {name:'Joe',gender:'male'}), (:User {gender:'female',name:'Jane'}), (:User {gender:'female',name:'Jenny'})");
        TestUtil.testResult(
                db, "CALL apoc.nodes.group(['User'],['gender'],null,{filter:{`User.count_*.max`:1}})", result -> {
                    Node node = Iterators.single(result.columnAs("node"));
                    assertEquals("male", node.getProperty("gender"));
                });
        TestUtil.testCallCount(
                db, "CALL apoc.nodes.group(['User'],['gender'],null,{filter:{`User.count_*.max`:0}})", 0);
    }

    @Test
    public void testFilterRelationshipsInclude() {
        db.executeTransactionally("CREATE (u:User {name:'Joe',gender:'male'})-[:KNOWS]->(u), (u)-[:LOVES]->(u)");
        assertEquals(
                "KNOWS",
                TestUtil.singleResultFirstColumn(
                        db,
                        "CALL apoc.nodes.group(['User'],['gender'],null,{includeRels:'KNOWS'}) yield relationship return type(relationship)"));
    }

    @Test
    public void testFilterRelationshipsExclude() {
        db.executeTransactionally("CREATE (u:User {name:'Joe',gender:'male'})-[:KNOWS]->(u), (u)-[:LOVES]->(u)");
        assertEquals(
                "KNOWS",
                TestUtil.singleResultFirstColumn(
                        db,
                        "CALL apoc.nodes.group(['User'],['gender'],null,{excludeRels:'LOVES'}) yield relationship return type(relationship)"));
    }

    @Test
    public void testFilterRelationshipsBothExcludeAndInclude() {
        db.executeTransactionally("CREATE (u:User {name:'Joe',gender:'male'})-[:KNOWS]->(u), (u)-[:LOVES]->(u)");
        final Map<String, String> conf = map("includeRels", "LOVES", "excludeRels", "LOVES");

        final Object result = TestUtil.singleResultFirstColumn(
                db,
                "CALL apoc.nodes.group(['User'], ['gender'], null, $conf) yield relationship return relationship",
                map("conf", conf));
        assertNull(result);
    }

    @Test
    public void testFilterRelationshipsExcludeAsList() {
        db.executeTransactionally("CREATE (u:User {name:'Joe',gender:'male'})-[:KNOWS]->(u), (u)-[:LOVES]->(u)");
        final Map<String, Object> conf = map("excludeRels", List.of("KNOWS", "LOVES"));

        final Object result = TestUtil.singleResultFirstColumn(
                db,
                "CALL apoc.nodes.group(['User'], ['gender'], null, $conf) yield relationship return relationship",
                map("conf", conf));
        assertNull(result);
    }

    @Test
    public void testFilterRelationshipsIncludeAsList() {
        db.executeTransactionally("CREATE (u:User {name:'Joe',gender:'male'})-[:KNOWS]->(u), (u)-[:LOVES]->(u)");
        final List<String> rels = List.of("KNOWS", "LOVES");
        final Map<String, Object> conf = map("includeRels", rels);

        testResult(
                db,
                "CALL apoc.nodes.group(['User'], ['gender'], null, $conf) yield relationship with type(relationship) as rel return rel order by rel ",
                map("conf", conf),
                r -> assertEquals(rels, Iterators.asList(r.columnAs("rel"))));
    }

    @Test
    public void testGroupAllLabels() {
        db.executeTransactionally("CREATE (u:User {name:'Joe',gender:'male'})");
        TestUtil.testResult(db, "CALL apoc.nodes.group(['*'],['gender'])", result -> {
            Node node = Iterators.single(result.columnAs("node"));
            assertEquals("User", Iterables.single(node.getLabels()).name());
        });
    }

    @Test
    public void testLimitNodes() {
        db.executeTransactionally("CREATE (:User {name:'Joe',gender:'male'}), (:User {name:'Jane',gender:'female'})");
        TestUtil.testResult(db, "CALL apoc.nodes.group(['User'],['gender'],null, {limitNodes:1})", result -> {
            Node node = Iterators.single(result.columnAs("node"));
            assertEquals("User", Iterables.single(node.getLabels()).name());
        });
    }

    @Test
    public void testLimitRelsNodes() {
        db.executeTransactionally(
                "CREATE (u:User {name:'Joe',gender:'male'})-[:KNOWS]->(u), (u)-[:LOVES]->(u), (u)-[:HATES]->(u)");
        TestUtil.testResult(db, "CALL apoc.nodes.group(['User'],['gender'],null, {relsPerNode:1})", result -> {
            Node node = Iterators.single(result.columnAs("node"));
            assertEquals("User", Iterables.single(node.getLabels()).name());
        });
    }
}
