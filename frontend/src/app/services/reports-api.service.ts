import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  PlayerReportListResponse,
  ReportRequestPayload,
  ReportResponseDto,
} from '../models/report';

@Injectable({ providedIn: 'root' })
export class ReportsApiService {
  private readonly baseUrl = environment.apiBaseUrl.replace(/\/$/, '');

  constructor(private http: HttpClient) {}

  sendReport(payload: ReportRequestPayload): Observable<ReportResponseDto> {
    const reportId = new Date().toISOString();
    const headers = new HttpHeaders({ reportId });
    return this.http.post<ReportResponseDto>(`${this.baseUrl}/coach/reports`, payload, {
      headers,
    });
  }

  listPlayerReports(
    playerId: string,
    options?: { cursor?: string | null; limit?: number },
  ): Observable<PlayerReportListResponse> {
    let params = new HttpParams();
    if (options?.limit != null) {
      params = params.set('limit', options.limit);
    }
    if (options?.cursor) {
      params = params.set('cursor', options.cursor);
    }
    return this.http.get<PlayerReportListResponse>(
      `${this.baseUrl}/players/${encodeURIComponent(playerId)}/reports`,
      { params },
    );
  }
}
