import type { RequestStatus, RequestType, StageActionType } from './enums';

export interface ApprovalStageDto {
  id: string;
  stageIndex: number;
  assignedRole: string;
  actedByName: string | null;
  status: string;
  comment: string | null;
  isParallel: boolean;
  assignedAt: string | null;
  actedAt: string | null;
  durationMinutes: number | null;
}

export interface AttachmentDto {
  id: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  uploadedAt: string;
  uploadedByName: string;
}

export interface RequestResponseDto {
  id: string;
  referenceNumber: string;
  type: RequestType;
  status: RequestStatus;
  title: string;
  details: string;
  notes: string | null;
  initiatorId: string;
  initiatorName: string;
  departmentId: string;
  departmentName: string;
  extraFields: Record<string, unknown> | null;
  approvalStages: ApprovalStageDto[];
  attachments: AttachmentDto[];
  rejectionReason: string | null;
  rejectedByName: string | null;
  rejectedAt: string | null;
  parentReferenceNumber: string | null;
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
  isOverdue: boolean;
}

export interface CreateRequestDto {
  type: RequestType;
  title: string;
  details: string;
  notes?: string;
  dueDate?: string;
  extraFields?: Record<string, unknown>;
  parentRequestId?: string;
}

export interface ApprovalActionDto {
  action: StageActionType;
  comment?: string;
  inStock?: boolean;
}

export interface AssignDriverDto {
  driverId: string;
  note?: string;
}

// ─── Type-specific extra field shapes ───────────────────────

export interface EquipmentExtraFields {
  item_name: string;
  quantity: number;
}

export interface VehicleExtraFields {
  destination: string;
  trip_date: string;
  purpose: string;
}

export interface LoanExtraFields {
  amount: number;
  loan_type?: string;
  is_top_up?: boolean;
  in_stock?: boolean; // only set by logistics, lives on same extraFields bag
}