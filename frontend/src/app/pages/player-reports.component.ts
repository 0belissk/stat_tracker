import { CommonModule, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import { PlayerReportListItem } from '../models/report';
import { ReportsApiService } from '../services/reports-api.service';

@Component({
  selector: 'app-player-reports',
  standalone: true,
  imports: [CommonModule, NgIf, NgFor, DatePipe],
  template: `
    <section>
      <h2>Player Reports</h2>
      <p class="meta">Player ID: {{ playerId }}</p>

      <p class="info" *ngIf="nextCursor === null && reports.length === 0 && !loading && !error">
        No reports found.
      </p>
      <p class="info" *ngIf="loading">Loading reports…</p>
      <p class="error" *ngIf="error">{{ error }}</p>

      <ul *ngIf="reports.length > 0" class="report-list">
        <li *ngFor="let report of reports" data-cy="player-report-row">
          <div class="row">
            <span class="timestamp">{{ report.reportTimestamp | date: 'medium' }}</span>
            <span class="coach">Coach: {{ report.coachId }}</span>
          </div>
          <div class="row">
            <span>Created: {{ report.createdAt | date: 'short' }}</span>
            <span *ngIf="report.s3Key">Storage key: {{ report.s3Key }}</span>
          </div>
        </li>
      </ul>

      <button type="button" (click)="loadMore()" *ngIf="nextCursor" [disabled]="loading">
        {{ loading ? 'Loading…' : 'Load more' }}
      </button>
    </section>
  `,
  styles: [
    `
      section {
        display: grid;
        gap: 1rem;
        max-width: 640px;
      }
      .meta {
        color: #555;
      }
      .info {
        color: #666;
        font-style: italic;
      }
      .error {
        color: #b00020;
        font-weight: 600;
      }
      .report-list {
        list-style: none;
        padding: 0;
        margin: 0;
        display: grid;
        gap: 0.75rem;
      }
      .report-list li {
        border: 1px solid #e5e5e5;
        border-radius: 8px;
        padding: 0.75rem;
        display: grid;
        gap: 0.35rem;
        background: #fafafa;
      }
      .row {
        display: flex;
        gap: 1rem;
        flex-wrap: wrap;
        font-size: 0.95rem;
      }
      .timestamp {
        font-weight: 600;
      }
      button {
        border: 1px solid #ddd;
        background: #fff;
        border-radius: 8px;
        padding: 0.4rem 0.8rem;
        cursor: pointer;
        justify-self: start;
      }
      button[disabled] {
        cursor: progress;
        opacity: 0.7;
      }
    `,
  ],
})
export class PlayerReportsComponent implements OnInit, OnDestroy {
  playerId = '';
  reports: PlayerReportListItem[] = [];
  nextCursor: string | null | undefined = undefined;
  loading = false;
  error = '';

  private readonly destroy$ = new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private reportsApi: ReportsApiService,
  ) {}

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      this.playerId = params.get('playerId') ?? '';
      this.reports = [];
      this.nextCursor = undefined;
      this.error = '';
      this.fetchReports(null);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadMore(): void {
    if (!this.nextCursor || this.loading) {
      return;
    }
    this.fetchReports(this.nextCursor);
  }

  private fetchReports(cursor: string | null): void {
    if (!this.playerId) {
      this.error = 'Missing player identifier.';
      this.nextCursor = null;
      return;
    }

    this.loading = true;
    this.reportsApi
      .listPlayerReports(this.playerId, { cursor, limit: 20 })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.loading = false;
        }),
      )
      .subscribe({
        next: (response) => {
          this.reports = [...this.reports, ...response.items];
          this.nextCursor = response.nextCursor ?? null;
          this.error = '';
        },
        error: () => {
          this.error = 'Unable to load reports right now. Please try again later.';
        },
      });
  }
}
