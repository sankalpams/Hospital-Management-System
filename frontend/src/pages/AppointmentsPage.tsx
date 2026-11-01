import { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, apiMessage } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { DataTable } from '../components/DataTable';
import { StatusBadge } from '../components/StatusBadge';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { MoneyText } from '../components/MoneyText';
import { BookingModal } from './BookingModal';
import { WalkInModal } from './WalkInModal';
import { RescheduleModal } from './RescheduleModal';
import { CancelModal } from './CancelModal';
import { AppointmentDetailModal } from './AppointmentDetailModal';
import { AppointmentCalendar } from './AppointmentCalendar';
import { useAuth } from '../hooks/use-auth';
import type { ColumnDef } from '@tanstack/react-table';
import type {
  Appointment,
  CalendarEvent,
  Department,
  Doctor,
  PageResponse,
} from '../types/api';
import {
  CalendarDays,
  List,
  Plus,
  Search,
  Zap,
  RotateCcw,
  XCircle,
  Eye,
  Calendar,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';

export function AppointmentsPage() {
  const { has } = useAuth();
  const client = useQueryClient();

  const [viewMode, setViewMode] = useState<'list' | 'calendar'>('list');
  const [doctorId, setDoctorId] = useState<string>('');
  const [departmentId, setDepartmentId] = useState<string>('');
  const [status, setStatus] = useState<string>('');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [page, setPage] = useState(0);

  // Calendar dates
  const [calendarStart, setCalendarStart] = useState<string>(
    new Date(Date.now() - 7 * 86400000).toISOString().split('T')[0]
  );
  const [calendarEnd, setCalendarEnd] = useState<string>(
    new Date(Date.now() + 14 * 86400000).toISOString().split('T')[0]
  );

  // Modals state
  const [bookingOpen, setBookingOpen] = useState(false);
  const [walkInOpen, setWalkInOpen] = useState(false);
  const [selectedAppointment, setSelectedAppointment] = useState<Appointment | null>(null);
  const [detailAppointmentId, setDetailAppointmentId] = useState<string | null>(null);
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);

  // Keyboard shortcut: N for new appointment
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (
        e.key.toLowerCase() === 'n' &&
        document.activeElement?.tagName !== 'INPUT' &&
        document.activeElement?.tagName !== 'TEXTAREA' &&
        document.activeElement?.tagName !== 'SELECT'
      ) {
        e.preventDefault();
        setBookingOpen(true);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Fetch departments & doctors for filters
  const { data: departments = [] } = useQuery({
    queryKey: ['departments-filter'],
    queryFn: async () => (await api.get<Department[]>('/departments')).data,
  });

  const { data: doctorsData } = useQuery({
    queryKey: ['doctors-filter', departmentId],
    queryFn: async () => {
      const res = await api.get<{ content: Doctor[] }>('/doctors', {
        params: { departmentId: departmentId || undefined, size: 50 },
      });
      return res.data.content;
    },
  });
  const doctors = doctorsData || [];

  // Fetch appointments for List View
  const { data: pageData } = useQuery({
    queryKey: ['appointments', doctorId, departmentId, status, startDate, endDate, searchQuery, page],
    queryFn: async () => {
      const res = await api.get<PageResponse<Appointment>>('/appointments', {
        params: {
          doctorId: doctorId || undefined,
          departmentId: departmentId || undefined,
          status: status || undefined,
          startDate: startDate || undefined,
          endDate: endDate || undefined,
          q: searchQuery || undefined,
          page,
          size: 15,
        },
      });
      return res.data;
    },
    enabled: viewMode === 'list',
  });

  // Fetch calendar events
  const { data: calendarEvents = [] } = useQuery({
    queryKey: ['appointments-calendar', doctorId, departmentId, calendarStart, calendarEnd],
    queryFn: async () => {
      const res = await api.get<CalendarEvent[]>('/appointments/calendar', {
        params: {
          doctorId: doctorId || undefined,
          departmentId: departmentId || undefined,
          start: calendarStart,
          end: calendarEnd,
        },
      });
      return res.data;
    },
    enabled: viewMode === 'calendar',
  });

  // Fast action mutations
  const checkInMutation = useMutation({
    mutationFn: (id: string) => api.post(`/appointments/${id}/check-in`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['appointments'] });
      void client.invalidateQueries({ queryKey: ['appointments-calendar'] });
      void client.invalidateQueries({ queryKey: ['appointments-today-queue'] });
    },
    onError: (err) => alert(apiMessage(err)),
  });

  const startConsultMutation = useMutation({
    mutationFn: (id: string) => api.post(`/appointments/${id}/start-consultation`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['appointments'] });
      void client.invalidateQueries({ queryKey: ['appointments-calendar'] });
      void client.invalidateQueries({ queryKey: ['appointments-today-queue'] });
    },
    onError: (err) => alert(apiMessage(err)),
  });

  const completeConsultMutation = useMutation({
    mutationFn: (id: string) => api.post(`/appointments/${id}/complete`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['appointments'] });
      void client.invalidateQueries({ queryKey: ['appointments-calendar'] });
      void client.invalidateQueries({ queryKey: ['appointments-today-queue'] });
    },
    onError: (err) => alert(apiMessage(err)),
  });

  const columns = useMemo<ColumnDef<Appointment>[]>(
    () => [
      {
        header: 'Token / Appt #',
        cell: ({ row }) => {
          const apt = row.original;
          return (
            <div>
              <span className="font-bold text-primary mr-1.5">#{apt.tokenNumber}</span>
              <span className="font-medium text-foreground">{apt.appointmentNumber}</span>
              <span className="block text-[10px] text-muted-foreground">{apt.type}</span>
            </div>
          );
        },
      },
      {
        header: 'Patient Details',
        cell: ({ row }) => {
          const apt = row.original;
          return (
            <div>
              <span className="font-semibold text-foreground">{apt.patientName}</span>
              <span className="block text-[11px] text-muted-foreground">
                MRN: {apt.patientMrn} • {apt.patientPhone}
              </span>
            </div>
          );
        },
      },
      {
        header: 'Doctor & Dept',
        cell: ({ row }) => {
          const apt = row.original;
          return (
            <div>
              <span className="font-medium text-foreground">{apt.doctorName}</span>
              <span className="block text-[11px] text-muted-foreground">
                {apt.departmentName} • {apt.doctorSpecialization}
              </span>
            </div>
          );
        },
      },
      {
        header: 'Date & Slot',
        cell: ({ row }) => {
          const apt = row.original;
          return (
            <div>
              <span className="font-medium">{apt.appointmentDate}</span>
              <span className="block text-[11px] text-muted-foreground">
                {apt.startTime.substring(0, 5)} - {apt.endTime.substring(0, 5)}
              </span>
            </div>
          );
        },
      },
      {
        header: 'Status',
        cell: ({ row }) => <StatusBadge status={row.original.status} />,
      },
      {
        header: 'Fee',
        cell: ({ row }) => <MoneyText value={row.original.consultationFee} />,
      },
      {
        header: 'Actions',
        cell: ({ row }) => {
          const apt = row.original;
          return (
            <div className="flex items-center gap-1">
              <Button
                variant="ghost"
                size="sm"
                className="h-7 w-7 p-0"
                title="View details & audit timeline"
                onClick={() => setDetailAppointmentId(apt.id)}
              >
                <Eye className="h-3.5 w-3.5" />
              </Button>

              {(apt.status === 'SCHEDULED' || apt.status === 'CONFIRMED') && (
                <Button
                  variant="secondary"
                  size="sm"
                  className="h-7 px-2 text-[11px] bg-blue-50 text-blue-700 hover:bg-blue-100 dark:bg-blue-950/40 dark:text-blue-300"
                  title="Patient arrived — Check-in"
                  onClick={() => checkInMutation.mutate(apt.id)}
                >
                  Check-in
                </Button>
              )}

              {apt.status === 'CHECKED_IN' && (
                <Button
                  variant="secondary"
                  size="sm"
                  className="h-7 px-2 text-[11px] bg-amber-50 text-amber-700 hover:bg-amber-100 dark:bg-amber-950/40 dark:text-amber-300"
                  title="Doctor start consultation"
                  onClick={() => startConsultMutation.mutate(apt.id)}
                >
                  Start
                </Button>
              )}

              {apt.status === 'IN_CONSULTATION' && (
                <>
                  <Link to={`/consultation/${apt.id}`}>
                    <Button
                      size="sm"
                      className="h-7 px-2 text-[11px]"
                      title="Open consultation workspace"
                    >
                      EMR
                    </Button>
                  </Link>
                  <Button
                    variant="secondary"
                    size="sm"
                    className="h-7 px-2 text-[11px] bg-green-50 text-green-700 hover:bg-green-100 dark:bg-green-950/40 dark:text-green-300"
                    title="Complete consultation"
                    onClick={() => completeConsultMutation.mutate(apt.id)}
                  >
                    Complete
                  </Button>
                </>
              )}

              {(apt.status === 'SCHEDULED' || apt.status === 'CONFIRMED') && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-7 w-7 p-0"
                  title="Reschedule slot"
                  onClick={() => {
                    setSelectedAppointment(apt);
                    setRescheduleOpen(true);
                  }}
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                </Button>
              )}

              {(apt.status === 'SCHEDULED' ||
                apt.status === 'CONFIRMED' ||
                apt.status === 'CHECKED_IN') && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-7 w-7 p-0 text-danger hover:text-danger"
                  title="Cancel appointment"
                  onClick={() => {
                    setSelectedAppointment(apt);
                    setCancelOpen(true);
                  }}
                >
                  <XCircle className="h-3.5 w-3.5" />
                </Button>
              )}
            </div>
          );
        },
      },
    ],
    []
  );

  return (
    <div className="space-y-4">
      <PageHeader
        title="Clinical Appointments & Scheduling"
        description="Outpatient scheduling, token queues, calendar views, and double-booking protection."
        action={
          <div className="flex items-center gap-2">
            <div className="inline-flex rounded-md border bg-muted p-0.5 text-xs">
              <button
                type="button"
                onClick={() => setViewMode('list')}
                className={`flex items-center gap-1.5 rounded px-2.5 py-1 font-medium transition-colors ${
                  viewMode === 'list'
                    ? 'bg-surface text-foreground shadow-sm'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                <List className="h-3.5 w-3.5" /> List
              </button>
              <button
                type="button"
                onClick={() => setViewMode('calendar')}
                className={`flex items-center gap-1.5 rounded px-2.5 py-1 font-medium transition-colors ${
                  viewMode === 'calendar'
                    ? 'bg-surface text-foreground shadow-sm'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                <Calendar className="h-3.5 w-3.5" /> Calendar
              </button>
            </div>

            {has('APPOINTMENT_WRITE') && (
              <>
                <Button variant="secondary" size="sm" onClick={() => setWalkInOpen(true)}>
                  <Zap className="h-3.5 w-3.5 text-amber-500" /> Walk-In
                </Button>
                <Button size="sm" onClick={() => setBookingOpen(true)}>
                  <Plus className="h-3.5 w-3.5" /> Book Appointment <span className="opacity-60 text-[10px] ml-1">[N]</span>
                </Button>
              </>
            )}
          </div>
        }
      />

      {/* Filter Bar */}
      <div className="rounded-md border bg-surface p-3 shadow-panel">
        <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 lg:grid-cols-6 text-xs">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              placeholder="Search patient, MRN, phone…"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setPage(0);
              }}
              className="pl-8 text-xs"
            />
          </div>

          <select
            value={departmentId}
            onChange={(e) => {
              setDepartmentId(e.target.value);
              setDoctorId('');
              setPage(0);
            }}
            className="rounded-md border bg-background px-2.5 py-1.5 text-xs"
          >
            <option value="">All Departments</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>

          <select
            value={doctorId}
            onChange={(e) => {
              setDoctorId(e.target.value);
              setPage(0);
            }}
            className="rounded-md border bg-background px-2.5 py-1.5 text-xs"
          >
            <option value="">All Doctors</option>
            {doctors.map((d) => (
              <option key={d.id} value={d.id}>
                {d.doctorName}
              </option>
            ))}
          </select>

          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
            className="rounded-md border bg-background px-2.5 py-1.5 text-xs"
          >
            <option value="">All Statuses</option>
            <option value="SCHEDULED">Scheduled</option>
            <option value="CONFIRMED">Confirmed</option>
            <option value="CHECKED_IN">Checked In</option>
            <option value="IN_CONSULTATION">In Consultation</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
            <option value="NO_SHOW">No Show</option>
            <option value="RESCHEDULED">Rescheduled</option>
          </select>

          <Input
            type="date"
            value={startDate}
            onChange={(e) => {
              setStartDate(e.target.value);
              setPage(0);
            }}
            title="Start date"
            className="text-xs"
          />

          <Input
            type="date"
            value={endDate}
            onChange={(e) => {
              setEndDate(e.target.value);
              setPage(0);
            }}
            title="End date"
            className="text-xs"
          />
        </div>
      </div>

      {/* Content: List or Calendar View */}
      {viewMode === 'calendar' ? (
        <AppointmentCalendar
          events={calendarEvents}
          onSelectEvent={(id) => setDetailAppointmentId(id)}
          onDatesSet={(start, end) => {
            setCalendarStart(start);
            setCalendarEnd(end);
          }}
        />
      ) : (
        <>
          <DataTable
            data={pageData?.content ?? []}
            columns={columns}
            emptyTitle="No appointments found"
            emptyDetail="Try adjusting filters or book a new clinical consultation."
          />

          {/* Pagination Bar */}
          {pageData && pageData.totalPages > 1 && (
            <div className="flex items-center justify-between border-t pt-3 text-xs text-muted-foreground">
              <div>
                Showing page {pageData.page + 1} of {pageData.totalPages} ({pageData.totalElements} records)
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={pageData.first}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  <ChevronLeft className="h-3.5 w-3.5 mr-1" /> Previous
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={pageData.last}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next <ChevronRight className="h-3.5 w-3.5 ml-1" />
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      {/* Booking Dialog */}
      <BookingModal
        open={bookingOpen}
        onOpenChange={setBookingOpen}
        defaultDoctorId={doctorId}
        defaultDepartmentId={departmentId}
      />

      {/* Walk-in Dialog */}
      <WalkInModal
        open={walkInOpen}
        onOpenChange={setWalkInOpen}
        defaultDoctorId={doctorId}
      />

      {/* Reschedule Dialog */}
      <RescheduleModal
        appointment={selectedAppointment}
        open={rescheduleOpen}
        onOpenChange={setRescheduleOpen}
      />

      {/* Cancel Dialog */}
      <CancelModal
        appointment={selectedAppointment}
        open={cancelOpen}
        onOpenChange={setCancelOpen}
      />

      {/* Details & History Dialog */}
      <AppointmentDetailModal
        appointmentId={detailAppointmentId}
        open={!!detailAppointmentId}
        onOpenChange={(open) => !open && setDetailAppointmentId(null)}
      />
    </div>
  );
}
