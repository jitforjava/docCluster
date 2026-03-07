package com.example.doccluster.service;

import com.example.doccluster.config.SolrProperties;
import com.example.doccluster.dto.FolderFileInfo;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class DocumentIndexService {

    private final SolrClient solrClient;
    private final SolrProperties solrProperties;
    private final Tika tika = new Tika();

    public DocumentIndexService(SolrClient solrClient, SolrProperties solrProperties) {
        this.solrClient = solrClient;
        this.solrProperties = solrProperties;
    }

    public int indexFolder(String folderPath) throws IOException, SolrServerException {
        Path root = Path.of(folderPath);
        validateFolder(root, folderPath);

        List<SolrInputDocument> documents = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isSupported)
                    .forEach(path -> documents.add(buildDocument(path)));
        }

        if (documents.isEmpty()) {
            return 0;
        }

        solrClient.add(solrProperties.collection(), documents);
        solrClient.commit(solrProperties.collection());
        return documents.size();
    }

    public List<FolderFileInfo> listSupportedFiles(String folderPath) throws IOException {
        Path root = Path.of(folderPath);
        validateFolder(root, folderPath);

        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isSupported)
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .map(path -> new FolderFileInfo(
                            path.getFileName().toString(),
                            path.toAbsolutePath().toString(),
                            safeSize(path)
                    ))
                    .toList();
        }
    }

    private void validateFolder(Path root, String folderPath) {
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Path must exist and be a folder: " + folderPath);
        }
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return 0L;
        }
    }

    private SolrInputDocument buildDocument(Path path) {
        SolrInputDocument doc = new SolrInputDocument();
        String id = UUID.randomUUID().toString();
        doc.addField("id", id);
        doc.addField("file_name", path.getFileName().toString());
        doc.addField("file_path", path.toAbsolutePath().toString());
        try {
            doc.addField("content", tika.parseToString(path));
        } catch (Exception ex) {
            doc.addField("content", "");
        }
        return doc;
    }

    private boolean isSupported(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".pdf") || fileName.endsWith(".doc") || fileName.endsWith(".docx");
    }
}
