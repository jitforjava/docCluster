import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, timeout } from 'rxjs';
import { environment } from '../environments/environment';
import {
  ClusterResponse,
  FolderFilesResponse,
  FolderPathRequest,
  IndexFolderResponse
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/documents`;
  private readonly requestTimeoutMs = 30000;

  constructor(private readonly http: HttpClient) {}

  listFiles(request: FolderPathRequest): Observable<FolderFilesResponse> {
    return this.http
      .post<FolderFilesResponse>(`${this.baseUrl}/folder-files`, request)
      .pipe(timeout(this.requestTimeoutMs));
  }

  indexFolder(request: FolderPathRequest): Observable<IndexFolderResponse> {
    return this.http
      .post<IndexFolderResponse>(`${this.baseUrl}/index-folder`, request)
      .pipe(timeout(this.requestTimeoutMs));
  }

  getClusters(query: string): Observable<ClusterResponse> {
    return this.http
      .get<ClusterResponse>(`${this.baseUrl}/clusters`, {
        params: { q: query || '*:*' }
      })
      .pipe(timeout(this.requestTimeoutMs));
  }
}
