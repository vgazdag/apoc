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
package apoc.export.csv;

import apoc.ApocConfig;
import apoc.Pools;
import apoc.export.cypher.ExportFileManager;
import apoc.export.cypher.FileManagerFactory;
import apoc.export.util.ExportConfig;
import apoc.export.util.ExportUtils;
import apoc.export.util.NodesAndRelsSubGraph;
import apoc.export.util.ProgressReporter;
import apoc.result.ProgressInfo;
import apoc.util.Util;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.neo4j.cypher.export.DatabaseSubGraph;
import org.neo4j.cypher.export.SubGraph;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.NotThreadSafe;
import org.neo4j.procedure.Procedure;
import org.neo4j.procedure.TerminationGuard;

/**
 * @author mh
 * @since 22.05.16
 */
public class ExportCSV {
    @Context
    public Transaction tx;

    @Context
    public GraphDatabaseService db;

    @Context
    public TerminationGuard terminationGuard;

    @Context
    public ApocConfig apocConfig;

    @Context
    public Pools pools;

    public ExportCSV() {}

    @NotThreadSafe
    @Procedure("apoc.export.csv.all")
    @Description("Exports the full database to the provided CSV file.")
    public Stream<ProgressInfo> all(@Name("file") String fileName, @Name("config") Map<String, Object> config) {
        String source = String.format("database: nodes(%d), rels(%d)", Util.nodeCount(tx), Util.relCount(tx));
        return exportCsv(fileName, source, new DatabaseSubGraph(tx), new ExportConfig(config));
    }

    @NotThreadSafe
    @Procedure("apoc.export.csv.data")
    @Description("Exports the given `NODE` and `RELATIONSHIP` values to the provided CSV file.")
    public Stream<ProgressInfo> data(
            @Name("nodes") List<Node> nodes,
            @Name("rels") List<Relationship> rels,
            @Name("file") String fileName,
            @Name("config") Map<String, Object> config) {
        ExportConfig exportConfig = new ExportConfig(config);
        preventBulkImport(exportConfig);
        String source = String.format("data: nodes(%d), rels(%d)", nodes.size(), rels.size());
        return exportCsv(fileName, source, new NodesAndRelsSubGraph(tx, nodes, rels), exportConfig);
    }

    @NotThreadSafe
    @Procedure("apoc.export.csv.graph")
    @Description("Exports the given graph to the provided CSV file.")
    public Stream<ProgressInfo> graph(
            @Name("graph") Map<String, Object> graph,
            @Name("file") String fileName,
            @Name("config") Map<String, Object> config) {
        Collection<Node> nodes = (Collection<Node>) graph.get("nodes");
        Collection<Relationship> rels = (Collection<Relationship>) graph.get("relationships");
        String source = String.format("graph: nodes(%d), rels(%d)", nodes.size(), rels.size());
        return exportCsv(fileName, source, new NodesAndRelsSubGraph(tx, nodes, rels), new ExportConfig(config));
    }

    @NotThreadSafe
    @Procedure("apoc.export.csv.query")
    @Description("Exports the results from running the given Cypher query to the provided CSV file.")
    public Stream<ProgressInfo> query(
            @Name("query") String query, @Name("file") String fileName, @Name("config") Map<String, Object> config) {
        ExportConfig exportConfig = new ExportConfig(config);
        preventBulkImport(exportConfig);
        Map<String, Object> params = config == null
                ? Collections.emptyMap()
                : (Map<String, Object>) config.getOrDefault("params", Collections.emptyMap());
        Result result = tx.execute(query, params);

        String source = String.format("statement: cols(%d)", result.columns().size());
        return exportCsv(fileName, source, result, exportConfig);
    }

    private void preventBulkImport(ExportConfig config) {
        if (config.isBulkImport()) {
            throw new RuntimeException(
                    "You can use the `bulkImport` only with apoc.export.all and apoc.export.csv.graph");
        }
    }

    private Stream<ProgressInfo> exportCsv(
            @Name("file") String fileName, String source, Object data, ExportConfig exportConfig) {
        apocConfig.checkWriteAllowed(exportConfig, fileName);
        final String format = "csv";
        ProgressInfo progressInfo = new ProgressInfo(fileName, source, format);
        progressInfo.batchSize = exportConfig.getBatchSize();
        ProgressReporter reporter = new ProgressReporter(null, null, progressInfo);
        CsvFormat exporter = new CsvFormat(db, (InternalTransaction) tx);

        ExportFileManager cypherFileManager =
                FileManagerFactory.createFileManager(fileName, exportConfig.isBulkImport(), exportConfig);

        if (exportConfig.streamStatements()) {
            return ExportUtils.getProgressInfoStream(
                    db,
                    pools.getDefaultExecutorService(),
                    terminationGuard,
                    format,
                    exportConfig,
                    reporter,
                    cypherFileManager,
                    (reporterWithConsumer) ->
                            dump(data, exportConfig, reporterWithConsumer, cypherFileManager, exporter));
        } else {
            dump(data, exportConfig, reporter, cypherFileManager, exporter);
            return reporter.stream();
        }
    }

    private void dump(
            Object data, ExportConfig c, ProgressReporter reporter, ExportFileManager printWriter, CsvFormat exporter) {
        if (data instanceof SubGraph) exporter.dump((SubGraph) data, printWriter, reporter, c);
        if (data instanceof Result) exporter.dump((Result) data, printWriter, reporter, c);
    }
}
