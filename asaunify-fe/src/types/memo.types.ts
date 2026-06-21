import type { Role, RequestStatus, StageStatus, StageActionType } from './enums';

export interface MemoApprovalStageDto {
  id: string;
  assignedRole: Role;
  actedByName: string | null;
  status: StageStatus;
  comment: string | null;
  assignedAt: string;
  actedAt: string | null;
}

export interface MemoDto {
  id: string;
  referenceNumber: string;
  title: string;
  content: string;
  authorId: string;
  authorName: string;
  status: RequestStatus;
  approvalStages: MemoApprovalStageDto[];
  rejectionReason: string | null;
  rejectedByName: string | null;
  rejectedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMemoRequest {
  title: string;
  content: string;
  approverRoles: Role[];
}

export interface MemoApprovalActionDto {
  action: StageActionType;
  comment?: string;
}