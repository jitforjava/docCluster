package com.example.doccluster.service;

import com.example.doccluster.config.SolrProperties;
import com.example.doccluster.dto.ClusterMetrics;
import com.example.doccluster.dto.ClusterResponse;
import com.example.doccluster.dto.ClusterResult;
import com.example.doccluster.dto.IndexedDocumentInfo;
import com.example.doccluster.dto.SearchDocumentResult;
import com.example.doccluster.dto.SearchMatchLocation;
import com.example.doccluster.dto.SearchResponse;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.util.NamedList;
import org.springframework.stereotype.Service;

import java.io.IOException; 
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ClusteringService {
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    private final SolrClient solrClient;
    private final SolrProperties solrProperties;

    public ClusteringService(SolrClient solrClient, SolrProperties solrProperties) {
        this.solrClient = solrClient;
        this.solrProperties = solrProperties;
    }

    public ClusterResponse cluster(String queryText) throws SolrServerException, IOException {
        SolrQuery query = new SolrQuery(queryText);
        query.setRows(200);
        query.set("clustering", true);
        query.set("clustering.engine", "lingo");
        query.set("clustering.results", true);
        query.set("clustering.collection", false);
        query.set("fl", "id,file_name,file_path,content");

        QueryResponse response = solrClient.query(solrProperties.collection(), query);
        NamedList<Object> clusteringSection = response.getResponse();
        Object clustersObj = clusteringSection.get("clusters");

        List<ClusterResult> clusters = clustersObj instanceof List<?> rawClusters
                ? parseClusters(rawClusters)
                : Collections.emptyList();
        List<IndexedDocumentInfo> documents = extractDocuments(response);

        return new ClusterResponse(
                queryText,
                response.getResults().getNumFound(),
                buildMetrics(clusters),
                clusters,
                documents
        );
    }

    public SearchResponse search(String queryText, int rows) throws SolrServerException, IOException {
        String effectiveQuery = queryText == null || queryText.trim().isEmpty() ? "*:*" : queryText.trim();
        int safeRows = Math.max(1, Math.min(rows, 200));
        List<String> stages = new ArrayList<>();

        stages.add("Tokenizing query text");
        List<String> tokens = tokenize(effectiveQuery);

        stages.add("Building Solr query");
        SolrQuery query = new SolrQuery(effectiveQuery);
        query.setRows(safeRows);
        query.set("defType", "edismax");
        query.set("qf", "file_name^3 content");
        query.set("fl", "id,file_name,file_path,score");
        query.setHighlight(true);
        query.set("hl.method", "unified");
        query.set("hl.fl", "file_name,content");
        query.set("hl.snippets", 3);
        query.set("hl.fragsize", 140);
        query.setHighlightSimplePre("<em>");
        query.setHighlightSimplePost("</em>");
        query.set("clustering", true);
        query.set("clustering.engine", "lingo");
        query.set("clustering.results", true);
        query.set("clustering.collection", false);

        stages.add("Querying Solr index");
        QueryResponse response = solrClient.query(solrProperties.collection(), query);

        stages.add("Parsing cluster labels for matched docs");
        Object clustersObj = response.getResponse().get("clusters");
        List<ClusterResult> clusters = clustersObj instanceof List<?> rawClusters
                ? parseClusters(rawClusters)
                : Collections.emptyList();
        Map<String, LinkedHashSet<String>> labelsByDocId = buildLabelsByDocId(clusters);
        Map<String, Map<String, List<String>>> highlighting = response.getHighlighting() != null
                ? response.getHighlighting()
                : Collections.emptyMap();

        stages.add("Preparing search response payload");
        List<SearchDocumentResult> documents = new ArrayList<>();
        if (response.getResults() != null) {
            for (SolrDocument doc : response.getResults()) {
                String id = getString(doc.getFieldValue("id"));
                List<String> clusterLabels = labelsByDocId.containsKey(id)
                        ? new ArrayList<>(labelsByDocId.get(id))
                        : Collections.emptyList();
                List<SearchMatchLocation> matchLocations = buildMatchLocations(
                        highlighting.getOrDefault(id, Collections.emptyMap()),
                        tokens
                );
                documents.add(new SearchDocumentResult(
                        id,
                        getString(doc.getFieldValue("file_name")),
                        getString(doc.getFieldValue("file_path")),
                        getDouble(doc.getFieldValue("score")),
                        clusterLabels,
                        matchLocations
                ));
            }
        }

        stages.add("Search completed");
        return new SearchResponse(
                effectiveQuery,
                tokens,
                response.getResults() != null ? response.getResults().getNumFound() : 0L,
                documents.size(),
                documents,
                stages
        );
    }

    private ClusterMetrics buildMetrics(List<ClusterResult> clusters) {
        int totalClusters = countClusters(clusters);
        int leafClusters = countLeafClusters(clusters);
        int largestCluster = largestClusterSize(clusters);
        return new ClusterMetrics(totalClusters, leafClusters, largestCluster);
    }

    private int countClusters(List<ClusterResult> clusters) {
        int count = 0;
        for (ClusterResult cluster : clusters) {
            count++;
            count += countClusters(cluster.clusters());
        }
        return count;
    }

    private int countLeafClusters(List<ClusterResult> clusters) {
        int count = 0;
        for (ClusterResult cluster : clusters) {
            if (cluster.clusters().isEmpty()) {
                count++;
            } else {
                count += countLeafClusters(cluster.clusters());
            }
        }
        return count;
    }

    private int largestClusterSize(List<ClusterResult> clusters) {
        int max = 0;
        for (ClusterResult cluster : clusters) {
            int current = cluster.size() != null ? cluster.size() : cluster.documentIds().size();
            max = Math.max(max, current);
            max = Math.max(max, largestClusterSize(cluster.clusters()));
        }
        return max;
    }

    @SuppressWarnings("unchecked")
    private List<ClusterResult> parseClusters(List<?> rawClusters) {
        List<ClusterResult> parsed = new ArrayList<>();
        for (Object raw : rawClusters) {
            if (!(raw instanceof NamedList<?> namedCluster)) {
                continue;
            }

            List<String> labels = getStringList(namedCluster.get("labels"));
            List<String> docs = getStringList(namedCluster.get("docs"));
            Integer size = namedCluster.get("size") instanceof Number number ? number.intValue() : null;

            List<ClusterResult> subClusters = Collections.emptyList();
            Object nested = namedCluster.get("clusters");
            if (nested instanceof List<?> nestedRaw) {
                subClusters = parseClusters(nestedRaw);
            }

            parsed.add(new ClusterResult(labels, docs, size, subClusters));
        }
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        for (Object item : listValue) {
            values.add(String.valueOf(item));
        }
        return values;
    }

    private List<IndexedDocumentInfo> extractDocuments(QueryResponse response) {
        if (response.getResults() == null || response.getResults().isEmpty()) {
            return Collections.emptyList();
        }

        List<IndexedDocumentInfo> documents = new ArrayList<>();
        for (SolrDocument doc : response.getResults()) {
            documents.add(new IndexedDocumentInfo(
                    getString(doc.getFieldValue("id")),
                    getString(doc.getFieldValue("file_name")),
                    getString(doc.getFieldValue("file_path"))
            ));
        }
        return documents;
    }

    private String getString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double getDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0d;
    }

    private List<SearchMatchLocation> buildMatchLocations(Map<String, List<String>> fieldSnippets, List<String> tokens) {
        if (fieldSnippets == null || fieldSnippets.isEmpty()) {
            return Collections.emptyList();
        }

        List<SearchMatchLocation> locations = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : fieldSnippets.entrySet()) {
            String field = entry.getKey();
            List<String> snippets = entry.getValue() != null ? entry.getValue() : Collections.emptyList();
            for (String snippet : snippets) {
                int tokenPosition = findTokenPosition(snippet, tokens);
                locations.add(new SearchMatchLocation(field, snippet, tokenPosition));
            }
        }
        return locations;
    }

    private int findTokenPosition(String snippet, List<String> tokens) {
        if (snippet == null || snippet.isBlank() || tokens == null || tokens.isEmpty()) {
            return -1;
        }

        String normalizedSnippet = stripHtml(snippet).toLowerCase();
        int best = Integer.MAX_VALUE;
        for (String token : tokens) {
            if (token == null || token.isBlank() || "*:*".equals(token)) {
                continue;
            }
            int at = normalizedSnippet.indexOf(token.toLowerCase());
            if (at >= 0 && at < best) {
                best = at;
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private String stripHtml(String value) {
        return HTML_TAG_PATTERN.matcher(value).replaceAll("");
    }

    private List<String> tokenize(String queryText) {
        if ("*:*".equals(queryText)) {
            return Collections.singletonList("*:*");
        }

        return java.util.Arrays.stream(queryText.split("[^A-Za-z0-9]+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, LinkedHashSet<String>> buildLabelsByDocId(List<ClusterResult> clusters) {
        Map<String, LinkedHashSet<String>> labelsByDocId = new LinkedHashMap<>();
        collectClusterLabels(clusters, new LinkedHashSet<>(), labelsByDocId);
        return labelsByDocId;
    }

    private void collectClusterLabels(
            List<ClusterResult> clusters,
            LinkedHashSet<String> parentLabels,
            Map<String, LinkedHashSet<String>> labelsByDocId
    ) {
        for (ClusterResult cluster : clusters) {
            LinkedHashSet<String> currentLabels = new LinkedHashSet<>(parentLabels);
            for (String label : cluster.labels()) {
                if (label != null && !label.isBlank()) {
                    currentLabels.add(label);
                }
            }

            for (String docId : cluster.documentIds()) {
                if (docId == null || docId.isBlank()) {
                    continue;
                }
                labelsByDocId.computeIfAbsent(docId, ignored -> new LinkedHashSet<>()).addAll(currentLabels);
            }

            if (!cluster.clusters().isEmpty()) {
                collectClusterLabels(cluster.clusters(), currentLabels, labelsByDocId);
            }
        }
    }
}
