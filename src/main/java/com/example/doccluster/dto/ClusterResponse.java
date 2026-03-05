package com.example.doccluster.dto;

import java.util.List;

public record ClusterResponse(
        String query,
        long totalDocuments,
        List<ClusterResult> clusters
) {
}
