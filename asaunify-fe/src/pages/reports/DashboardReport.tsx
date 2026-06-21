import { useCallback, useEffect, useState } from 'react';
import { reportsApi, type DashboardSummaryDto } from '../../api/reports.api';
import StatCard from '../../components/ui/StatCard';

export default function DashboardReport() {
  const [summary, setSummary] = useState<DashboardSummaryDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadSummary = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await reportsApi.getDashboard();
      setSummary(data);
    } catch (error) {
      console.error('Failed to load dashboard report:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadSummary();
  }, [loadSummary]);

  if (isLoading) {
    return <p className="text-sm text-gray-400 text-center py-12">Loading...</p>;
  }

  if (!summary) {
    return <p className="text-sm text-gray-400 text-center py-12">No data available.</p>;
  }

  return (
    <div className="space-y-8">
      <h1 className="text-xl font-bold text-gray-900">Performance Dashboard</h1>

      {/* Top KPIs */}
      <div className="flex flex-wrap gap-4">
        <StatCard
          title="Total Requests"
          value={summary.totalRequests}
          subtitle="All time"
          variant="filled"
        />
        <StatCard title="Pending" value={summary.totalPending} subtitle="Awaiting action" />
        <StatCard title="Completed" value={summary.totalCompleted} subtitle="Fully processed" />
        <StatCard title="Rejected" value={summary.totalRejected} subtitle="Declined requests" />
        <StatCard
          title="On-Time Rate"
          value={`${summary.onTimeRatePercent}%`}
          subtitle="Completed before due date"
        />
      </div>

      {/* Pending by type */}
      <section>
        <h2 className="text-lg font-bold text-teal mb-4">Pending by Request Type</h2>
        <div className="grid grid-cols-3 gap-4">
          {Object.entries(summary.pendingByType).map(([type, count]) => (
            <div key={type} className="border border-gray-200 rounded-xl p-5">
              <p className="text-sm text-gray-500 mb-1">{formatLabel(type)}</p>
              <p className="text-3xl font-bold text-gray-900">{count}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Bottleneck detection */}
      <section>
        <h2 className="text-lg font-bold text-teal mb-1">
          Approval Bottlenecks
        </h2>
        <p className="text-sm text-gray-500 mb-4">
          Average time each role takes to act on a pending stage
        </p>
        <div className="border border-gray-200 rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-5 py-3 font-medium text-gray-500">
                  Role
                </th>
                <th className="text-left px-5 py-3 font-medium text-gray-500">
                  Avg. Time to Act
                </th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(summary.averageCompletionMinutesByRole)
                .sort(([, a], [, b]) => b - a)
                .map(([role, minutes]) => (
                  <tr key={role} className="border-t border-gray-100">
                    <td className="px-5 py-4 text-gray-800">
                      {formatLabel(role)}
                    </td>
                    <td className="px-5 py-4 text-gray-800">
                      {formatMinutes(minutes)}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function formatLabel(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(' ');
}

function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${Math.round(minutes)} min`;
  const hours = Math.floor(minutes / 60);
  const remaining = Math.round(minutes % 60);
  return `${hours}h ${remaining}m`;
}