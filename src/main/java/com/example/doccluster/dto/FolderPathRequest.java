package com.example.doccluster.dto;

import jakarta.validation.constraints.NotBlank;

public record FolderPathRequest(
        @NotBlank String folderPath
) {
}
