import { useCallback, useEffect, useState } from 'react';
import { reportsApi, type AuditLogDto } from '../../api/reports.api';
import DataTable, { type Column } from '../../components/ui/DataTable';
import SearchFilterBar from '../../components/ui/SearchFilterBar';
import { formatDate } from '../../utils/formatDate';

export default function AuditLog() {
  const [logs, setLogs] = useState<AuditLogDto[]>([]);
  const [search, setSearch] = useState('');
  const [isLoading, setIsLoading] = useState(true);

  const loadLogs = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await reportsApi.getRecentLogs(100);
      setLogs(data);
    } catch (error) {
      console.error('Failed to load audit logs:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadLogs();
  }, [loadLogs]);

  const filtered = logs.filter(
    (log) =>
      log.userFullName?.toLowerCase().includes(search.toLowerCase()) ||
      log.actionType.toLowerCase().includes(search.toLowerCase()) ||
      log.module.toLowerCase().includes(search.toLowerCase())
  );

  const columns: Column<AuditLogDto>[] = [
    { header: 'Timestamp', accessor: (log) => formatDate(log.timestamp) },
    { header: 'User', accessor: (log) => log.userFullName ?? 'System' },
    { header: 'Action', accessor: (log) => formatLabel(log.actionType) },
    { header: 'Module', accessor: (log) => formatLabel(log.module) },
    { header: 'IP Address', accessor: (log) => log.ipAddress ?? '—' },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-bold text-gray-900">Activity Logs</h1>

      <SearchFilterBar
        value={search}
        onChange={setSearch}
        placeholder="Search by user, action, module..."
      />

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
      ) : (
        <DataTable
          columns={columns}
          data={filtered}
          keyExtractor={(log) => log.id}
          emptyMessage="No activity logs found"
        />
      )}
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