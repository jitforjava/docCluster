import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ApiService } from './api.service';
import { ClusterResponse, FolderFileInfo, FolderFilesResponse, IndexedDocumentInfo } from './models';

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
export class AppComponent {
  folderPath = '';
  queryText = '*:*';

  loadingFiles = false;
  indexing = false;
  loadingClusters = false;

  filesResponse: FolderFilesResponse | null = null;
  files: FolderFileInfo[] = [];
  clusterResponse: ClusterResponse | null = null;
  clusterBars: ClusterBar[] = [];
  matchedDocuments: IndexedDocumentInfo[] = [];
  indexedDocumentCount: number | null = null;

  endpointHits: EndpointHit[] = [];
  errorText = '';

  constructor(private readonly api: ApiService) {}

  loadFiles(): void {
    if (!this.folderPath.trim()) {
      this.errorText = 'Folder path is required.';
      return;
    }

    this.errorText = '';
    this.loadingFiles = true;
    this.api
      .listFiles({ folderPath: this.folderPath.trim() })
      .pipe(finalize(() => (this.loadingFiles = false)))
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
        },
        error: (error) => {
          this.loadingFiles = false;
          const message = this.extractError(error);
          this.errorText = message;
          this.hit('POST /api/documents/folder-files', 'ERROR', message);
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
      .pipe(finalize(() => (this.indexing = false)))
      .subscribe({
        next: (response) => {
          this.indexing = false;
          const indexedDocumentCount =
            typeof response?.indexedDocumentCount === 'number' ? response.indexedDocumentCount : 0;
          this.indexedDocumentCount = indexedDocumentCount;
          this.hit('POST /api/documents/index-folder', 'OK', `Indexed ${indexedDocumentCount} documents`);
        },
        error: (error) => {
          this.indexing = false;
          const message = this.extractError(error);
          this.errorText = message;
          this.hit('POST /api/documents/index-folder', 'ERROR', message);
        }
      });
  }

  loadClusters(): void {
    this.errorText = '';
    this.loadingClusters = true;
    this.matchedDocuments = [];
    this.api
      .getClusters(this.queryText.trim())
      .pipe(finalize(() => (this.loadingClusters = false)))
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
        },
        error: (error) => {
          this.loadingClusters = false;
          this.matchedDocuments = [];
          const message = this.extractError(error);
          this.errorText = message;
          this.hit('GET /api/documents/clusters', 'ERROR', message);
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
