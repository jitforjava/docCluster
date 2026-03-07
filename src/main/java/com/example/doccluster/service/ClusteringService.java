package com.example.doccluster.service;

import com.example.doccluster.config.SolrProperties;
import com.example.doccluster.dto.ClusterMetrics;
import com.example.doccluster.dto.ClusterResponse;
import com.example.doccluster.dto.ClusterResult;
import com.example.doccluster.dto.IndexedDocumentInfo;
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
import java.util.List;

@Service
public class ClusteringService {

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
        query.set("fl", "id,file_name,content");

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
}
