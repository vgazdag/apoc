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
package apoc.path;

import static org.neo4j.graphdb.traversal.Evaluation.*;

import java.util.HashSet;
import java.util.Set;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.traversal.Evaluation;

/**
 * A matcher for evaluating whether or not a node is accepted by a group of matchers comprised of a denylist, allowlist, endNode and termination node matchers.
 * Unlike a LabelMatcher, LabelMatcherGroups interpret context for labels according to filter symbols provided.
 * Labels can be added that are prefixed with filter symbols (+, -, /, &gt;) (for allowlist, denylist, terminator, and end node respectively).
 * Lack of a symbol is interpreted as allowlisted.
 * If no labels are set as allowlisted, then all labels are considered allowlisted (if not otherwise disallowed by the denylist).
 * The node will not be included if denylisted, or not matched via the allowlist, end node, or termination node matchers.
 * If end nodes only, then the node will only be included if matched via the end node and termination node matchers.
 * The path will be pruned if matching the denylist, the termination node matchers, or otherwise not included by any of the other matchers.
 */
public class LabelMatcherGroup {
    private boolean endNodesOnly;
    private LabelMatcher allowlistMatcher = new LabelMatcher();
    private LabelMatcher denylistMatcher = new LabelMatcher();
    private LabelMatcher endNodeMatcher = new LabelMatcher();
    private LabelMatcher terminatorNodeMatcher = new LabelMatcher();

    public LabelMatcherGroup addLabels(String fullFilterString) {
        if (fullFilterString != null && !fullFilterString.isEmpty()) {
            String[] elements = fullFilterString.split("\\|");

            for (String filterString : elements) {
                addLabel(filterString);
            }
        }

        return this;
    }

    public LabelMatcherGroup addLabel(String filterString) {
        if (filterString != null && !filterString.isEmpty()) {
            LabelMatcher matcher;

            char operator = filterString.charAt(0);

            switch (operator) {
                case '>':
                    endNodesOnly = true;
                    matcher = endNodeMatcher;
                    filterString = filterString.substring(1);
                    break;
                case '/':
                    endNodesOnly = true;
                    matcher = terminatorNodeMatcher;
                    filterString = filterString.substring(1);
                    break;
                case '-':
                    matcher = denylistMatcher;
                    filterString = filterString.substring(1);
                    break;
                case '+':
                    filterString = filterString.substring(1);
                default:
                    matcher = allowlistMatcher;
            }

            matcher.addLabel(filterString);
        }

        return this;
    }

    public Evaluation evaluate(Node node, boolean belowMinLevel) {
        Set<String> nodeLabels = new HashSet<>();
        node.getLabels().forEach(label -> nodeLabels.add(label.name()));

        if (denylistMatcher.matchesLabels(nodeLabels)) {
            return EXCLUDE_AND_PRUNE;
        }

        if (terminatorNodeMatcher.matchesLabels(nodeLabels)) {
            return belowMinLevel ? EXCLUDE_AND_CONTINUE : INCLUDE_AND_PRUNE;
        }

        if (endNodeMatcher.matchesLabels(nodeLabels)) {
            return belowMinLevel ? EXCLUDE_AND_CONTINUE : INCLUDE_AND_CONTINUE;
        }

        if (allowlistMatcher.isEmpty() || allowlistMatcher.matchesLabels(nodeLabels)) {
            return endNodesOnly || belowMinLevel ? EXCLUDE_AND_CONTINUE : INCLUDE_AND_CONTINUE;
        }

        return EXCLUDE_AND_PRUNE;
    }

    public boolean isEndNodesOnly() {
        return endNodesOnly;
    }

    public void setEndNodesOnly(boolean endNodesOnly) {
        this.endNodesOnly = endNodesOnly;
    }
}
