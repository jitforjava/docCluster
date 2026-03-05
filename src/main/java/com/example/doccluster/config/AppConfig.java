package com.example.doccluster.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SolrProperties.class)
public class AppConfig {

    @Bean
    public SolrClient solrClient(SolrProperties solrProperties) {
        return new Http2SolrClient.Builder(solrProperties.baseUrl()).build();
    }
}
