import { Check, X, Clock } from 'lucide-react';
import type { ApprovalStageDto } from '../../types/request.types';
import { StageStatus } from '../../types/enums';
import { formatDate } from '../../utils/formatDate';

interface StageTrackerProps {
  stages: ApprovalStageDto[];
}

function formatRoleLabel(role: string): string {
  return role
    .toLowerCase()
    .split('_')
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(' ');
}

function StageIcon({ status }: { status: string }) {
  if (status === StageStatus.APPROVED) {
    return (
      <div className="w-8 h-8 rounded-full bg-status-approved flex items-center justify-center text-white shrink-0">
        <Check size={16} />
      </div>
    );
  }
  if (status === StageStatus.REJECTED) {
    return (
      <div className="w-8 h-8 rounded-full bg-status-rejected flex items-center justify-center text-white shrink-0">
        <X size={16} />
      </div>
    );
  }
  return (
    <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 shrink-0">
      <Clock size={14} />
    </div>
  );
}

export default function StageTracker({ stages }: StageTrackerProps) {
  if (stages.length === 0) {
    return <p className="text-sm text-gray-400">No approval stages yet.</p>;
  }

  // Group by stageIndex — parallel stages (loans) share an index
  const grouped = stages.reduce<Record<number, ApprovalStageDto[]>>(
    (acc, stage) => {
      (acc[stage.stageIndex] ??= []).push(stage);
      return acc;
    },
    {}
  );

  const orderedIndexes = Object.keys(grouped)
    .map(Number)
    .sort((a, b) => a - b);

  return (
    <div className="space-y-6">
      {orderedIndexes.map((index) => (
        <div key={index} className="space-y-3">
          {grouped[index].map((stage) => (
            <div key={stage.id} className="flex gap-4">
              <StageIcon status={stage.status} />
              <div className="flex-1 pb-2">
                <div className="flex items-center justify-between">
                  <p className="font-semibold text-gray-900 text-sm">
                    {formatRoleLabel(stage.assignedRole)}
                    {stage.isParallel && (
                      <span className="ml-2 text-xs font-normal text-gray-400">
                        (parallel)
                      </span>
                    )}
                  </p>
                  <span className="text-xs text-gray-400">
                    {stage.actedAt
                      ? formatDate(stage.actedAt)
                      : stage.status === StageStatus.PENDING
                      ? 'Awaiting action'
                      : ''}
                  </span>
                </div>
                {stage.actedByName && (
                  <p className="text-xs text-gray-500 mt-0.5">
                    by {stage.actedByName}
                  </p>
                )}
                {stage.comment && (
                  <p className="text-sm text-gray-600 mt-1 bg-gray-50 px-3 py-2 rounded-lg">
                    "{stage.comment}"
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}