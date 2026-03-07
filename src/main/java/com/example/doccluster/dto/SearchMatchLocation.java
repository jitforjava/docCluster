package com.example.doccluster.dto;

public record SearchMatchLocation(
        String field,
        String snippet,
        int tokenPosition
) {
}
