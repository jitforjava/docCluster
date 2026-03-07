export interface FolderPathRequest {
  folderPath: string;
}

export interface FolderFileInfo {
  fileName: string;
  filePath: string;
  sizeBytes: number;
}

export interface FolderFilesResponse {
  folderPath: string;
  supportedFileCount: number;
  files: FolderFileInfo[];
}

export interface IndexFolderResponse {
  folderPath: string;
  indexedDocumentCount: number;
}

export interface ClusterResult {
  labels: string[];
  documentIds: string[];
  size: number | null;
  clusters: ClusterResult[];
}

export interface ClusterMetrics {
  totalClusters: number;
  leafClusters: number;
  largestClusterSize: number;
}

export interface ClusterResponse {
  query: string;
  totalDocuments: number;
  metrics: ClusterMetrics;
  clusters: ClusterResult[];
  documents: IndexedDocumentInfo[];
}

export interface IndexedDocumentInfo {
  id: string;
  fileName: string;
  filePath: string;
}

export interface SearchDocumentResult {
  id: string;
  fileName: string;
  filePath: string;
  score: number;
  clusterLabels: string[];
  matchLocations: SearchMatchLocation[];
}

export interface SearchResponse {
  query: string;
  tokens: string[];
  totalDocumentsFound: number;
  returnedDocuments: number;
  documents: SearchDocumentResult[];
  backendStages: string[];
}

export interface SearchMatchLocation {
  field: string;
  snippet: string;
  tokenPosition: number;
}
