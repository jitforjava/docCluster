import { ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ApiService } from './api.service';
import {
  ClusterResponse,
  FolderFileInfo,
  FolderFilesResponse,
  IndexedDocumentInfo,
  SearchMatchLocation,
  SearchDocumentResult,
  SearchResponse
} from './models';

interface EndpointHit {
  endpoint: string;
  status: 'OK' | 'ERROR';
  time: string;
  message: string;
}

interface ClusterBar {
  label: string;
  size: number;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnDestroy {
  folderPath = 'D:/docs';
  queryText = '*:*';
  searchText = '';
  searchRows = 25;

  loadingFiles = false;
  indexing = false;
  loadingClusters = false;
  searching = false;

  filesResponse: FolderFilesResponse | null = null;
  files: FolderFileInfo[] = [];
  clusterResponse: ClusterResponse | null = null;
  clusterBars: ClusterBar[] = [];
  matchedDocuments: IndexedDocumentInfo[] = [];
  searchResponse: SearchResponse | null = null;
  searchDocuments: SearchDocumentResult[] = [];
  searchTokens: string[] = [];
  searchBackendStages: string[] = [];
  searchStatusMessage = '';
  indexedDocumentCount: number | null = null;

  endpointHits: EndpointHit[] = [];
  errorText = '';
  private searchStageTimer: ReturnType<typeof setInterval> | null = null;

  constructor(private readonly api: ApiService, private readonly cdr: ChangeDetectorRef) {}

  ngOnDestroy(): void {
    this.stopSearchProgress();
  }

  loadFiles(): void {
    if (!this.folderPath.trim()) {
      this.errorText = 'Folder path is required.';
      return;
    }

    this.errorText = '';
    this.loadingFiles = true;
    this.api
      .listFiles({ folderPath: this.folderPath.trim() })
      .pipe(finalize(() => {
        this.loadingFiles = false;
        this.refreshUi();
      }))
      .subscribe({
        next: (response) => {
          this.loadingFiles = false;
          const files = Array.isArray(response?.files) ? response.files : [];
          const supportedFileCount =
            typeof response?.supportedFileCount === 'number' ? response.supportedFileCount : files.length;

          this.filesResponse = {
            folderPath: response?.folderPath ?? this.folderPath.trim(),
            supportedFileCount,
            files
          };
          this.files = files;
          this.hit('POST /api/documents/folder-files', 'OK', `Found ${supportedFileCount} supported files`);
          this.refreshUi();
        },
        error: (error) => {
          this.loadingFiles = false;
          const message = this.extractError(error);
          this.errorText = message;
          this.hit('POST /api/documents/folder-files', 'ERROR', message);
          this.refreshUi();
        }
      });
  }

  runIndexing(): void {
    if (!this.folderPath.trim()) {
      this.errorText = 'Folder path is required.';
      return;
    }

    this.errorText = '';
    this.indexing = true;
    this.api
      .indexFolder({ folderPath: this.folderPath.trim() })
      .pipe(finalize(() => {
        this.indexing = false;
        this.refreshUi();
      }))
      .subscribe({
        next: (response) => {
          this.indexing = false;
          const indexedDocumentCount =
            typeof response?.indexedDocumentCount === 'number' ? response.indexedDocumentCount : 0;
          this.indexedDocumentCount = indexedDocumentCount;
          this.hit('POST /api/documents/index-folder', 'OK', `Indexed ${indexedDocumentCount} documents`);
          this.refreshUi();
        },
        error: (error) => {
          this.indexing = false;
          const message = this.extractError(error);
          this.errorText = message;
          this.hit('POST /api/documents/index-folder', 'ERROR', message);
          this.refreshUi();
        }
      });
  }

  loadClusters(): void {
    this.errorText = '';
    this.loadingClusters = true;
    this.matchedDocuments = [];
    this.api
      .getClusters(this.queryText.trim())
      .pipe(finalize(() => {
        this.loadingClusters = false;
        this.refreshUi();
      }))
      .subscribe({
        next: (response) => {
          this.loadingClusters = false;
          const clusters = Array.isArray(response?.clusters) ? response.clusters : [];
          const documents = Array.isArray(response?.documents) ? response.documents : [];
          const totalDocuments = typeof response?.totalDocuments === 'number' ? response.totalDocuments : 0;
          const metrics = {
            totalClusters: response?.metrics?.totalClusters ?? clusters.length,
            leafClusters: response?.metrics?.leafClusters ?? clusters.length,
            largestClusterSize: response?.metrics?.largestClusterSize ?? 0
          };

          this.clusterResponse = {
            query: response?.query ?? (this.queryText.trim() || '*:*'),
            totalDocuments,
            metrics,
            clusters,
            documents
          };
          this.clusterBars = this.topClusters(clusters, 8);
          this.matchedDocuments = documents;
          this.hit('GET /api/documents/clusters', 'OK', `Found ${totalDocuments} docs and ${metrics.totalClusters} clusters`);
          this.refreshUi();
        },
        error: (error) => {
          this.loadingClusters = false;
          this.matchedDocuments = [];
          const message = this.extractError(error);
          this.errorText = message;
          this.hit('GET /api/documents/clusters', 'ERROR', message);
          this.refreshUi();
        }
      });
  }

  runSearch(): void {
    const query = this.searchText.trim() || this.queryText.trim() || '*:*';

    this.errorText = '';
    this.searching = true;
    this.searchResponse = null;
    this.searchDocuments = [];
    this.searchTokens = [];
    this.searchBackendStages = [];
    this.startSearchProgress();

    this.api
      .searchDocuments(query, this.searchRows)
      .pipe(
        finalize(() => {
          this.searching = false;
          this.stopSearchProgress();
          this.refreshUi();
        })
      )
      .subscribe({
        next: (response) => {
          const documents = Array.isArray(response?.documents)
            ? response.documents.map((doc) => ({
                ...doc,
                clusterLabels: Array.isArray(doc?.clusterLabels) ? doc.clusterLabels : [],
                matchLocations: Array.isArray(doc?.matchLocations) ? doc.matchLocations : []
              }))
            : [];
          const tokens = Array.isArray(response?.tokens) ? response.tokens : [];
          const backendStages = Array.isArray(response?.backendStages) ? response.backendStages : [];

          this.searchResponse = {
            query: response?.query ?? query,
            tokens,
            totalDocumentsFound: response?.totalDocumentsFound ?? documents.length,
            returnedDocuments: response?.returnedDocuments ?? documents.length,
            documents,
            backendStages
          };
          this.searchDocuments = documents;
          this.searchTokens = tokens;
          this.searchBackendStages = backendStages;
          this.searchStatusMessage = backendStages.length
            ? backendStages[backendStages.length - 1]
            : 'Search completed';
          this.hit(
            'GET /api/documents/search',
            'OK',
            `Found ${this.searchResponse.totalDocumentsFound} docs; returned ${this.searchResponse.returnedDocuments}`
          );
          this.refreshUi();
        },
        error: (error) => {
          const message = this.extractError(error);
          this.errorText = message;
          this.searchStatusMessage = 'Search failed';
          this.hit('GET /api/documents/search', 'ERROR', message);
          this.refreshUi();
        }
      });
  }

  trackByPath(_: number, file: FolderFileInfo): string {
    return file.filePath;
  }

  barWidth(size: number): string {
    if (!this.clusterBars.length) {
      return '0%';
    }
    const max = Math.max(...this.clusterBars.map((item) => item.size), 1);
    return `${Math.max(12, Math.round((size / max) * 100))}%`;
  }

  trackBySearchId(_: number, doc: SearchDocumentResult): string {
    return doc.id;
  }

  trackByMatchIndex(index: number, _: SearchMatchLocation): number {
    return index;
  }

  tokenPositionLabel(position: number): string {
    return position >= 0 ? `Approx token char index: ${position}` : 'Position unavailable';
  }

  private topClusters(
    clusters: { labels: string[]; size: number | null; documentIds: string[]; clusters: any[] }[] | null | undefined,
    limit: number
  ): ClusterBar[] {
    const flat: ClusterBar[] = [];
    const walk = (items: typeof clusters): void => {
      if (!Array.isArray(items)) {
        return;
      }
      for (const item of items) {
        const label = item.labels?.[0] || 'Unlabeled cluster';
        const size = item.size ?? item.documentIds?.length ?? 0;
        flat.push({ label, size });
        walk(item.clusters || []);
      }
    };
    walk(clusters);

    return flat
      .sort((a, b) => b.size - a.size)
      .slice(0, limit);
  }

  private hit(endpoint: string, status: 'OK' | 'ERROR', message: string): void {
    this.endpointHits.unshift({
      endpoint,
      status,
      message,
      time: new Date().toLocaleTimeString()
    });
    this.endpointHits = this.endpointHits.slice(0, 8);
  }

  private startSearchProgress(): void {
    this.stopSearchProgress();
    const stages = [
      'Sending search request to backend...',
      'Backend: tokenizing your query...',
      'Backend: querying Solr index...',
      'Backend: mapping documents to clusters...',
      'Preparing response for UI...'
    ];
    let index = 0;
    this.searchStatusMessage = stages[index];
    this.searchStageTimer = setInterval(() => {
      index = (index + 1) % stages.length;
      this.searchStatusMessage = stages[index];
    }, 1200);
  }

  private stopSearchProgress(): void {
    if (this.searchStageTimer) {
      clearInterval(this.searchStageTimer);
      this.searchStageTimer = null;
    }
  }

  private refreshUi(): void {
    this.cdr.markForCheck();
  }

  private extractError(error: any): string {
    if (error?.name === 'TimeoutError') {
      return 'Request timed out after 30s. Please check backend logs and folder size.';
    }
    if (error?.error?.error) {
      return error.error.error;
    }
    if (error?.error?.message) {
      return error.error.message;
    }
    if (error?.message) {
      return error.message;
    }
    return 'Request failed.';
  }
}
