import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Download } from 'lucide-react';
import { requestsApi } from '../../api/requests.api';
import { reportsApi } from '../../api/reports.api';
import { usePermissions } from '../../hooks/usePermissions';
import SearchFilterBar from '../../components/ui/SearchFilterBar';
import DataTable, { type Column } from '../../components/ui/DataTable';
import StatusBadge from '../../components/ui/StatusBadge';
import Button from '../../components/ui/Button';
import { formatDate } from '../../utils/formatDate';
import type { RequestResponseDto } from '../../types/request.types';

type Scope = 'my' | 'department' | 'all' | 'completed' | 'rejected' | 'overdue';

export default function CaseReports() {
  const navigate = useNavigate();
  const { isAdmin, isAuditor, isDepartmentHead } = usePermissions();

  const [scope, setScope] = useState<Scope>('my');
  const [requests, setRequests] = useState<RequestResponseDto[]>([]);
  const [search, setSearch] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isExporting, setIsExporting] = useState(false);

  const availableTabs: { value: Scope; label: string }[] = [
    { value: 'my', label: 'My Cases' },
    ...(isDepartmentHead || isAdmin || isAuditor
      ? [{ value: 'department' as Scope, label: 'Department' }]
      : []),
    ...(isAdmin || isAuditor ? [{ value: 'all' as Scope, label: 'All Cases' }] : []),
    { value: 'completed', label: 'Completed' },
    { value: 'rejected', label: 'Rejected' },
    { value: 'overdue', label: 'Overdue' },
  ];

  const loadCases = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await requestsApi.getCases({ scope });
      setRequests(data);
    } catch (error) {
      console.error('Failed to load cases:', error);
    } finally {
      setIsLoading(false);
    }
  }, [scope]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadCases();
  }, [loadCases]);

  async function handleExport(format: 'excel' | 'csv' | 'pdf') {
    setIsExporting(true);
    try {
      const blob = await reportsApi.exportCases({ format });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `requests-report.${format === 'excel' ? 'xlsx' : format}`;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Export failed:', error);
    } finally {
      setIsExporting(false);
    }
  }

  const filtered = requests.filter(
    (r) =>
      r.title.toLowerCase().includes(search.toLowerCase()) ||
      r.referenceNumber.toLowerCase().includes(search.toLowerCase())
  );

  const columns: Column<RequestResponseDto>[] = [
    { header: 'Reference', accessor: (r) => r.referenceNumber },
    { header: 'Type', accessor: (r) => formatType(r.type) },
    { header: 'Title', accessor: (r) => r.title },
    { header: 'Department', accessor: (r) => r.departmentName },
    {
      header: 'Status',
      accessor: (r) => <StatusBadge status={r.status} />,
    },
    { header: 'Created', accessor: (r) => formatDate(r.createdAt) },
    {
      header: 'Actions',
      accessor: (r) => (
        <button
          onClick={() => navigate(`/requests/${r.id}`)}
          className="text-primary font-medium underline hover:text-primary-dark"
        >
          View
        </button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">Case Reports</h1>
        <div className="flex gap-2">
          <Button
            variant="outline"
            icon={<Download size={16} />}
            disabled={isExporting}
            onClick={() => handleExport('excel')}
          >
            Excel
          </Button>
          <Button
            variant="outline"
            icon={<Download size={16} />}
            disabled={isExporting}
            onClick={() => handleExport('pdf')}
          >
            PDF
          </Button>
        </div>
      </div>

      {/* Scope tabs */}
      <div className="flex gap-2 border-b border-gray-200">
        {availableTabs.map((tab) => (
          <button
            key={tab.value}
            onClick={() => setScope(tab.value)}
            className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              scope === tab.value
                ? 'border-primary text-primary'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <SearchFilterBar value={search} onChange={setSearch} />

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
      ) : (
        <DataTable
          columns={columns}
          data={filtered}
          keyExtractor={(r) => r.id}
          emptyMessage="No cases found for this view"
        />
      )}
    </div>
  );
}

function formatType(type: string): string {
  return type.charAt(0) + type.slice(1).toLowerCase();
}