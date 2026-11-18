import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { appointmentsApi } from '../../api/clinic';
import { toApiError } from '../../api/client';
import { DataTable } from '../../components/ui/DataTable';
import { StatusBadge } from '../../components/ui/Badge';
import { formatDate } from '../../lib/format';
import { AppointmentActions } from './shared';

/** Appointment history of one patient (newest first) with the same actions as the day view. */
export function PatientAppointments({ patientId }) {
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['appointments', 'patient', patientId, page],
    queryFn: () => appointmentsApi.search({ patientId, page, size: 20, sort: 'slotStart,desc' }),
    placeholderData: keepPreviousData,
  });
  const columns = [
    { key: 'when', header: 'Date', render: (a) => <span className="tabular whitespace-nowrap">{formatDate(a.localDate)} · {a.localTime.slice(0, 5)}</span> },
    { key: 'no', header: 'Number', render: (a) => <span className="font-mono text-xs">{a.appointmentNo}</span> },
    { key: 'doctor', header: 'Doctor', render: (a) => <div><p>{a.doctorName}</p><p className="text-xs text-foreground-subtle">{a.departmentName}</p></div> },
    { key: 'status', header: 'Status', render: (a) => (
      <div>
        <StatusBadge status={a.status} />
        {a.cancelReason && <p className="mt-1 text-xs text-foreground-subtle">{a.cancelReason}</p>}
      </div>
    ) },
    { key: 'actions', header: <span className="sr-only">Actions</span>, align: 'right', render: (a) => <AppointmentActions appointment={a} /> },
  ];
  return (
    <DataTable
      caption="Appointment history"
      columns={columns}
      page={query.data}
      loading={query.isFetching}
      error={query.isError ? toApiError(query.error).message : null}
      onPageChange={setPage}
      emptyTitle="No appointments yet"
    />
  );
}
