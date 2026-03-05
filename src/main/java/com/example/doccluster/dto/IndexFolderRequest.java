package com.example.doccluster.dto;

import jakarta.validation.constraints.NotBlank;

public record IndexFolderRequest(
        @NotBlank String folderPath
) {
}
