export interface ReportRequestPayload {
  playerId: string;
  playerEmail: string;
  categories: Record<string, string>;
}

export interface ReportResponseDto {
  reportId: string;
  status: string;
  at: string;
}

export interface PlayerReportListItem {
  reportId: string;
  reportTimestamp: string;
  createdAt: string;
  coachId: string;
  s3Key?: string | null;
}

export interface PlayerReportListResponse {
  items: PlayerReportListItem[];
  nextCursor?: string | null;
}
