package com.example.doccluster.dto;

public record FolderFileInfo(
        String fileName,
        String filePath,
        long sizeBytes
) {
}
