package com.example.doccluster.service;

import com.example.doccluster.config.SolrProperties;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Path must exist and be a folder: " + folderPath);
        }

        List<SolrInputDocument> docsToSend = new ArrayList<>();

        // keeping it simple, just walk everything and check one by one
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> allPaths = stream.toList();
            for (Path currentPath : allPaths) {
                if (Files.isRegularFile(currentPath) && isSupported(currentPath)) {
                    SolrInputDocument d = buildDocument(currentPath);
                    docsToSend.add(d);
                }
            }
        }

        if (docsToSend.isEmpty()) {
            return 0;
        }

        solrClient.add(solrProperties.collection(), docsToSend);
        solrClient.commit(solrProperties.collection());
        return docsToSend.size();
    }

    private SolrInputDocument buildDocument(Path path) {
        SolrInputDocument doc = new SolrInputDocument();
        String id = System.currentTimeMillis() + "-" + UUID.randomUUID();
        doc.addField("id", id);
        doc.addField("file_name", path.getFileName().toString());
        doc.addField("file_path", path.toAbsolutePath().toString());
        try {
            String rawText = tika.parseToString(path);
            doc.addField("content", rawText);
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
