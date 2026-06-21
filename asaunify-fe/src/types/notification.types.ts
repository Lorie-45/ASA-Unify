import type { ActionType } from './enums';

export interface NotificationDto {
  id: string;
  title: string;
  body: string;
  actionType: ActionType;
  isRead: boolean;
  createdAt: string;
  requestId: string | null;
  requestReferenceNumber: string | null;
  memoId: string | null;
  memoReferenceNumber: string | null;
}

export interface NotificationSummaryDto {
  unreadCount: number;
  notifications: NotificationDto[];
}