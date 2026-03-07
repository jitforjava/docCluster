package com.example.doccluster.dto;

public record ClusterMetrics(
        int totalClusters,
        int leafClusters,
        int largestClusterSize
) {
}
