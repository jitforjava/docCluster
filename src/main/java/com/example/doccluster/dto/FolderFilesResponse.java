package com.example.doccluster.dto;

import java.util.List;

public record FolderFilesResponse(
        String folderPath,
        int supportedFileCount,
        List<FolderFileInfo> files
) {
}
