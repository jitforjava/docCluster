package com.example.doccluster.controller;

import com.example.doccluster.dto.ClusterResponse;
import com.example.doccluster.dto.FolderFileInfo;
import com.example.doccluster.dto.FolderFilesResponse;
import com.example.doccluster.dto.FolderPathRequest;
import com.example.doccluster.dto.IndexFolderRequest;
import com.example.doccluster.service.ClusteringService;
import com.example.doccluster.service.DocumentIndexService;
import jakarta.validation.Valid;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"}, allowCredentials = "true")
public class DocumentController {

    private final DocumentIndexService indexService;
    private final ClusteringService clusteringService;

    public DocumentController(DocumentIndexService indexService, ClusteringService clusteringService) {
        this.indexService = indexService;
        this.clusteringService = clusteringService;
    }

    @PostMapping("/index-folder")
    public ResponseEntity<Map<String, Object>> indexFolder(@Valid @RequestBody IndexFolderRequest request)
            throws IOException, SolrServerException {
        int indexedCount = indexService.indexFolder(request.folderPath());
        return ResponseEntity.ok(Map.of(
                "folderPath", request.folderPath(),
                "indexedDocumentCount", indexedCount
        ));
    }

    @PostMapping("/folder-files")
    public ResponseEntity<FolderFilesResponse> getFolderFiles(@Valid @RequestBody FolderPathRequest request)
            throws IOException {
        List<FolderFileInfo> files = indexService.listSupportedFiles(request.folderPath());
        return ResponseEntity.ok(new FolderFilesResponse(
                request.folderPath(),
                files.size(),
                files
        ));
    }

    @GetMapping("/clusters")
    public ResponseEntity<ClusterResponse> getClusters(@RequestParam(defaultValue = "*:*") String q)
            throws SolrServerException, IOException {
        return ResponseEntity.ok(clusteringService.cluster(q));
    }
}
