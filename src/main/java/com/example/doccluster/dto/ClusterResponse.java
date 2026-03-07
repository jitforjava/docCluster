package com.example.doccluster.dto;

import java.util.List;

public record ClusterResponse(
        String query,
        long totalDocuments,
        ClusterMetrics metrics,
        List<ClusterResult> clusters,
        List<IndexedDocumentInfo> documents
) {
}
