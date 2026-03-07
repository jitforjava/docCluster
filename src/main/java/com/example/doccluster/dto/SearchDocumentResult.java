package com.example.doccluster.dto;

import java.util.List;

public record SearchDocumentResult(
        String id,
        String fileName,
        String filePath,
        double score,
        List<String> clusterLabels,
        List<SearchMatchLocation> matchLocations
) {
}
