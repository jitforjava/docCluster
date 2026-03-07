# Solr Document Clustering (Spring Boot)

This project indexes documents from a local folder (`.pdf`, `.doc`, `.docx`) into Apache Solr and returns document clusters from Solr.

## Tech Stack
- Spring Boot 3
- SolrJ
- Apache Tika (text extraction from PDF/Word)

## 1) Run Solr with a collection

```bash
docker run --name solr -p 8983:8983 -d solr:9
```

Create a collection:

```bash
docker exec -it solr solr create_collection -c documents -shards 1 -replicationFactor 1
```

## 2) Prepare Solr schema

Add these fields to your `documents` collection schema (Managed Schema API):

```bash
curl -X POST -H 'Content-type:application/json' \
  --data-binary '{"add-field":{"name":"file_name","type":"string","stored":true}}' \
  http://localhost:8983/solr/documents/schema

curl -X POST -H 'Content-type:application/json' \
  --data-binary '{"add-field":{"name":"file_path","type":"string","stored":true}}' \
  http://localhost:8983/solr/documents/schema

curl -X POST -H 'Content-type:application/json' \
  --data-binary '{"add-field":{"name":"content","type":"text_general","stored":true,"indexed":true}}' \
  http://localhost:8983/solr/documents/schema
```

## 3) Enable clustering component in `solrconfig.xml`

For clustering support, enable Solr's clustering search component in the collection configset:

```xml
<searchComponent name="clustering" class="solr.clustering.ClusteringComponent">
  <lst name="engine">
    <str name="name">lingo</str>
    <str name="optional">false</str>
    <str name="carrot.algorithm">LingoClusteringAlgorithm</str>
  </lst>
</searchComponent>

<requestHandler name="/select" class="solr.SearchHandler">
  <arr name="components">
    <str>query</str>
    <str>facet</str>
    <str>mlt</str>
    <str>highlight</str>
    <str>stats</str>
    <str>expand</str>
    <str>clustering</str>
  </arr>
</requestHandler>
```

> Note: Depending on your Solr version/configset, clustering may require additional modules/jars.

## 4) Configure and run this app

`src/main/resources/application.yml`

```yaml
app:
  solr:
    base-url: http://localhost:8983/solr
    collection: documents
```

Run:

```bash
mvn spring-boot:run
```

## 5) API usage

### List supported files from a folder

```bash
curl -X POST http://localhost:8080/api/documents/folder-files \
  -H 'Content-Type: application/json' \
  -d '{"folderPath":"/absolute/path/to/your/folder"}'
```

### Index a folder

```bash
curl -X POST http://localhost:8080/api/documents/index-folder \
  -H 'Content-Type: application/json' \
  -d '{"folderPath":"/absolute/path/to/your/folder"}'
```

### Get clusters

```bash
curl "http://localhost:8080/api/documents/clusters?q=*:*"
```

You can also query specific terms:

```bash
curl "http://localhost:8080/api/documents/clusters?q=machine learning"
```

## Notes
- Only `.pdf`, `.doc`, and `.docx` files are indexed.
- If text extraction fails for a file, the file is still indexed with empty content.
- Large folders can take time; indexing is synchronous in this starter implementation.

## Angular UI (separate folder)

The project now includes a separate Angular UI under `ui/`:
- folder file listing (`/api/documents/folder-files`)
- indexing trigger (`/api/documents/index-folder`)
- cluster summary + visualization (`/api/documents/clusters`)
- endpoint activity table for quick API diagnostics

Backend CORS is configured for `http://localhost:4200`.

Run UI:

```bash
cd ui
npm install
npm start
```

UI URL:

```text
http://localhost:4200
```
