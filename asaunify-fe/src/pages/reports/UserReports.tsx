import { useCallback, useEffect, useState } from 'react';
import { reportsApi, type UserActivityDto } from '../../api/reports.api';
import DataTable, { type Column } from '../../components/ui/DataTable';
import SearchFilterBar from '../../components/ui/SearchFilterBar';

export default function UserReports() {
  const [activity, setActivity] = useState<UserActivityDto[]>([]);
  const [search, setSearch] = useState('');
  const [isLoading, setIsLoading] = useState(true);

  const loadActivity = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await reportsApi.getUserActivity();
      setActivity(data);
    } catch (error) {
      console.error('Failed to load user activity:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadActivity();
  }, [loadActivity]);

  const filtered = activity.filter(
    (u) =>
      u.fullName.toLowerCase().includes(search.toLowerCase()) ||
      u.email.toLowerCase().includes(search.toLowerCase())
  );

  const columns: Column<UserActivityDto>[] = [
    { header: 'Name', accessor: (u) => u.fullName },
    { header: 'Email', accessor: (u) => u.email },
    { header: 'Role', accessor: (u) => formatLabel(u.role) },
    { header: 'Department', accessor: (u) => u.departmentName ?? '—' },
    { header: 'Cases Initiated', accessor: (u) => u.casesInitiated },
    { header: 'Tasks Completed', accessor: (u) => u.tasksCompleted },
    {
      header: 'Avg. Task Time',
      accessor: (u) => `${u.averageTaskMinutes.toFixed(1)} min`,
    },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-bold text-gray-900">User Activity Report</h1>

      <SearchFilterBar value={search} onChange={setSearch} placeholder="Search by name or email" />

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
      ) : (
        <DataTable
          columns={columns}
          data={filtered}
          keyExtractor={(u) => u.userId}
          emptyMessage="No user activity found"
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