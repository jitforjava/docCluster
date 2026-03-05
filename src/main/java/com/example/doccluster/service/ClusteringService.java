package com.example.doccluster.service;

import com.example.doccluster.config.SolrProperties;
import com.example.doccluster.dto.ClusterResponse;
import com.example.doccluster.dto.ClusterResult;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
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
        SolrQuery q = new SolrQuery(queryText);
        q.setRows(200);
        q.set("clustering", true);
        q.set("clustering.engine", "lingo");
        q.set("clustering.results", true);
        q.set("clustering.collection", false);
        q.set("fl", "id,file_name,content");

        QueryResponse response = solrClient.query(solrProperties.collection(), q);
        NamedList<Object> dataFromSolr = response.getResponse();
        Object clustersObj = dataFromSolr.get("clusters");

        List<ClusterResult> clusters = clustersObj instanceof List<?> rawClusters
                ? parseClusters(rawClusters)
                : Collections.emptyList();

        return new ClusterResponse(
                queryText,
                response.getResults().getNumFound(),
                clusters
        );
    }

    @SuppressWarnings("unchecked")
    private List<ClusterResult> parseClusters(List<?> rawClusters) {
        List<ClusterResult> out = new ArrayList<>();
        for (Object one : rawClusters) {
            if (!(one instanceof NamedList<?> namedCluster)) {
                continue;
            }

            List<String> labels = getStringList(namedCluster.get("labels"));
            List<String> docIds = getStringList(namedCluster.get("docs"));
            Integer size = namedCluster.get("size") instanceof Number number ? number.intValue() : null;

            List<ClusterResult> subClusters = Collections.emptyList();
            Object nested = namedCluster.get("clusters");
            if (nested instanceof List<?> nestedRaw) {
                subClusters = parseClusters(nestedRaw);
            }

            out.add(new ClusterResult(labels, docIds, size, subClusters));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return Collections.emptyList();
        }

        List<String> ans = new ArrayList<>();
        for (Object item : listValue) {
            ans.add(String.valueOf(item));
        }
        return ans;
    }
}
