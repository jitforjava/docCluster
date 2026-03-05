package com.example.doccluster.dto;

import java.util.List;

public record ClusterResult(
        List<String> labels,
        List<String> documentIds,
        Integer size,
        List<ClusterResult> clusters
) {
}
