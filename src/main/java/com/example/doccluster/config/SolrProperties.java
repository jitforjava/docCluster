package com.example.doccluster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.solr")
public record SolrProperties(
        String baseUrl,
        String collection
) {
}
