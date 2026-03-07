package com.example.doccluster.dto;

import java.util.List;

public record SearchResponse(
        String query,
        List<String> tokens,
        long totalDocumentsFound,
        int returnedDocuments,
        List<SearchDocumentResult> documents,
        List<String> backendStages
) {
}
