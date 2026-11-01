import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, apiMessage } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Dialog, DialogContent } from '../components/ui/dialog';
import { FormField } from '../components/FormField';
import { DateTimeText } from '../components/DateTimeText';
import type { Doctor, DoctorSchedule, ScheduleException } from '../types/api';
import {
  Clock,
  Calendar,
  Plus,
  Trash2,
  AlertTriangle,
} from 'lucide-react';

const DAYS_OF_WEEK = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

export function DoctorSchedulesPage() {
  const client = useQueryClient();

  const [selectedDoctorId, setSelectedDoctorId] = useState<string>('');
  const [activeTab, setActiveTab] = useState<'weekly' | 'exceptions'>('weekly');

  // Modals state
  const [scheduleModalOpen, setScheduleModalOpen] = useState(false);
  const [exceptionModalOpen, setExceptionModalOpen] = useState(false);

  // New schedule form
  const [dayOfWeek, setDayOfWeek] = useState('MONDAY');
  const [startTime, setStartTime] = useState('08:30');
  const [endTime, setEndTime] = useState('12:30');
  const [slotDuration, setSlotDuration] = useState('20');
  const [maxPatients, setMaxPatients] = useState('12');
  const [roomNumber, setRoomNumber] = useState('Consultation Room 101');

  // New exception form
  const [exceptionType, setExceptionType] = useState<'LEAVE' | 'BLOCKED_SLOT' | 'EXTRA_SESSION'>('LEAVE');
  const [startDateTime, setStartDateTime] = useState(
    new Date(Date.now() + 86400000).toISOString().slice(0, 16)
  );
  const [endDateTime, setEndDateTime] = useState(
    new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 16)
  );
  const [exceptionReason, setExceptionReason] = useState('');
  const [extraDuration, setExtraDuration] = useState('20');
  const [extraMaxPatients, setExtraMaxPatients] = useState('10');

  // Fetch doctors
  const { data: doctorsData } = useQuery({
    queryKey: ['doctors-schedule-page'],
    queryFn: async () => {
      const res = await api.get<{ content: Doctor[] }>('/doctors', { params: { size: 50 } });
      return res.data.content;
    },
  });
  const doctors = doctorsData || [];

  // Automatically select first doctor if none selected
  const activeDocId = selectedDoctorId || (doctors.length > 0 ? doctors[0].id : '');

  // Fetch weekly schedules
  const { data: weeklySchedules = [] } = useQuery({
    queryKey: ['doctor-schedules', activeDocId],
    queryFn: async () => {
      if (!activeDocId) return [];
      const res = await api.get<DoctorSchedule[]>(`/schedules/doctors/${activeDocId}`);
      return res.data;
    },
    enabled: !!activeDocId,
  });

  // Fetch exceptions
  const { data: exceptions = [] } = useQuery({
    queryKey: ['doctor-exceptions', activeDocId],
    queryFn: async () => {
      if (!activeDocId) return [];
      const res = await api.get<ScheduleException[]>(`/schedules/doctors/${activeDocId}/exceptions`);
      return res.data;
    },
    enabled: !!activeDocId,
  });

  // Schedule mutation
  const saveScheduleMutation = useMutation({
    mutationFn: () =>
      api.post(`/schedules/doctors/${activeDocId}`, {
        dayOfWeek,
        startTime,
        endTime,
        slotDurationMinutes: parseInt(slotDuration, 10),
        maxPatients: parseInt(maxPatients, 10),
        roomNumber,
      }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['doctor-schedules', activeDocId] });
      setScheduleModalOpen(false);
    },
    onError: (err) => alert('Failed to save schedule: ' + apiMessage(err)),
  });

  const deleteScheduleMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/schedules/${id}`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['doctor-schedules', activeDocId] });
    },
    onError: (err) => alert('Failed to remove schedule: ' + apiMessage(err)),
  });

  // Exception mutation
  const saveExceptionMutation = useMutation({
    mutationFn: () =>
      api.post(`/schedules/doctors/${activeDocId}/exceptions`, {
        exceptionType,
        startDateTime: new Date(startDateTime).toISOString(),
        endDateTime: new Date(endDateTime).toISOString(),
        reason: exceptionReason,
        slotDurationMinutes: exceptionType === 'EXTRA_SESSION' ? parseInt(extraDuration, 10) : undefined,
        maxPatients: exceptionType === 'EXTRA_SESSION' ? parseInt(extraMaxPatients, 10) : undefined,
      }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['doctor-exceptions', activeDocId] });
      setExceptionModalOpen(false);
      setExceptionReason('');
    },
    onError: (err) => alert('Failed to save exception: ' + apiMessage(err)),
  });

  const deleteExceptionMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/schedules/exceptions/${id}`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['doctor-exceptions', activeDocId] });
    },
    onError: (err) => alert('Failed to delete exception: ' + apiMessage(err)),
  });

  const selectedDoctor = doctors.find((d) => d.id === activeDocId);

  return (
    <div className="space-y-4">
      <PageHeader
        title="Doctor Schedules & Availability"
        description="Configure weekly clinical timetables, manage leave periods, and block specific consultation slots."
        action={
          <div className="flex items-center gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setExceptionModalOpen(true)}
              disabled={!activeDocId}
            >
              <AlertTriangle className="h-3.5 w-3.5 text-amber-500" /> Add Leave / Block
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={() => setScheduleModalOpen(true)}
              disabled={!activeDocId}
            >
              <Plus className="h-3.5 w-3.5" /> Add Weekly Session
            </Button>
          </div>
        }
      />

      {/* Doctor Selector */}
      <div className="rounded-md border bg-surface p-3 shadow-panel">
        <div className="flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-foreground">Select Doctor:</span>
            <select
              value={activeDocId}
              onChange={(e) => setSelectedDoctorId(e.target.value)}
              className="rounded-md border bg-background px-3 py-1.5 text-xs min-w-[240px]"
            >
              {doctors.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.doctorName} — {d.specialization} ({d.departmentName})
                </option>
              ))}
            </select>
          </div>

          {selectedDoctor && (
            <div className="text-muted-foreground">
              SLMC: <span className="font-medium text-foreground">{selectedDoctor.slmcRegNumber}</span> •{' '}
              Dept: <span className="font-medium text-foreground">{selectedDoctor.departmentName}</span>
            </div>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b">
        <nav className="flex space-x-6 text-sm font-medium">
          <button
            type="button"
            onClick={() => setActiveTab('weekly')}
            className={`pb-3 border-b-2 whitespace-nowrap flex items-center gap-1.5 ${
              activeTab === 'weekly'
                ? 'border-primary text-primary font-semibold'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            <Clock className="h-4 w-4" /> Weekly Timetable ({weeklySchedules.length})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('exceptions')}
            className={`pb-3 border-b-2 whitespace-nowrap flex items-center gap-1.5 ${
              activeTab === 'exceptions'
                ? 'border-primary text-primary font-semibold'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            <Calendar className="h-4 w-4" /> Leave & Exceptions ({exceptions.length})
          </button>
        </nav>
      </div>

      {/* Tab: Weekly Timetable Grid */}
      {activeTab === 'weekly' && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {DAYS_OF_WEEK.map((day) => {
            const daySchedules = weeklySchedules.filter((s) => s.dayOfWeek === day);

            return (
              <div
                key={day}
                className="rounded-md border bg-surface p-3.5 shadow-sm space-y-2.5 text-xs"
              >
                <div className="flex items-center justify-between border-b pb-2">
                  <span className="font-bold text-foreground uppercase tracking-wider text-[11px]">
                    {day}
                  </span>
                  <span className="text-[11px] text-muted-foreground">
                    {daySchedules.length} session{daySchedules.length === 1 ? '' : 's'}
                  </span>
                </div>

                {daySchedules.length === 0 ? (
                  <p className="text-muted-foreground text-center py-4 italic text-[11px]">
                    No clinics scheduled
                  </p>
                ) : (
                  daySchedules.map((sched) => (
                    <div
                      key={sched.id}
                      className="rounded border bg-muted/30 p-2.5 space-y-1 relative group"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-primary">
                          {sched.startTime.substring(0, 5)} - {sched.endTime.substring(0, 5)}
                        </span>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-6 w-6 p-0 text-muted-foreground hover:text-danger"
                          onClick={() => deleteScheduleMutation.mutate(sched.id)}
                          title="Remove session"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                      <div className="text-[11px] text-muted-foreground">
                        {sched.slotDurationMinutes} min slots • Max {sched.maxPatients} patients
                      </div>
                      {sched.roomNumber && (
                        <div className="text-[11px] text-foreground font-medium">
                          {sched.roomNumber}
                        </div>
                      )}
                    </div>
                  ))
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Tab: Exceptions & Leave */}
      {activeTab === 'exceptions' && (
        <div className="rounded-md border bg-surface p-4 shadow-sm text-xs">
          {exceptions.length === 0 ? (
            <div className="py-8 text-center text-muted-foreground">
              No leave periods or blocked slots registered for this doctor.
            </div>
          ) : (
            <div className="divide-y">
              {exceptions.map((ex) => (
                <div key={ex.id} className="py-3 flex items-start justify-between">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span
                        className={`inline-block rounded px-2 py-0.5 text-[10px] font-bold ${
                          ex.exceptionType === 'LEAVE'
                            ? 'bg-danger/10 text-danger'
                            : ex.exceptionType === 'BLOCKED_SLOT'
                            ? 'bg-amber-100 text-amber-800 dark:bg-amber-950/40 dark:text-amber-300'
                            : 'bg-green-100 text-green-800 dark:bg-green-950/40 dark:text-green-300'
                        }`}
                      >
                        {ex.exceptionType}
                      </span>
                      <span className="font-semibold text-foreground">
                        <DateTimeText value={ex.startDateTime} /> — <DateTimeText value={ex.endDateTime} />
                      </span>
                    </div>
                    {ex.reason && <p className="text-muted-foreground text-[11px]">{ex.reason}</p>}
                    {ex.exceptionType === 'EXTRA_SESSION' && (
                      <div className="text-[11px] text-muted-foreground">
                        Duration: {ex.slotDurationMinutes} min • Max: {ex.maxPatients} patients
                      </div>
                    )}
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0 text-muted-foreground hover:text-danger"
                    onClick={() => deleteExceptionMutation.mutate(ex.id)}
                    title="Remove exception"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Schedule Modal */}
      <Dialog open={scheduleModalOpen} onOpenChange={setScheduleModalOpen}>
        <DialogContent title="Add Weekly Clinic Session">
          <div className="space-y-3 text-xs">
            <p className="text-xs text-muted-foreground mb-3">
              Configure regular weekly consultation availability for {selectedDoctor?.doctorName}.
            </p>

            <FormField label="Day of Week *">
              <select
                value={dayOfWeek}
                onChange={(e) => setDayOfWeek(e.target.value)}
                className="w-full rounded-md border bg-background px-3 py-2 text-xs"
              >
                {DAYS_OF_WEEK.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </FormField>

            <div className="grid grid-cols-2 gap-2">
              <FormField label="Start Time *">
                <Input
                  type="time"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                />
              </FormField>
              <FormField label="End Time *">
                <Input
                  type="time"
                  value={endTime}
                  onChange={(e) => setEndTime(e.target.value)}
                />
              </FormField>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <FormField label="Slot Duration (Mins) *">
                <Input
                  type="number"
                  min="5"
                  step="5"
                  value={slotDuration}
                  onChange={(e) => setSlotDuration(e.target.value)}
                />
              </FormField>
              <FormField label="Max Patients *">
                <Input
                  type="number"
                  min="1"
                  value={maxPatients}
                  onChange={(e) => setMaxPatients(e.target.value)}
                />
              </FormField>
            </div>

            <FormField label="Consultation Room / Location">
              <Input
                value={roomNumber}
                onChange={(e) => setRoomNumber(e.target.value)}
                placeholder="e.g. Room 204, OPD Block B"
              />
            </FormField>

            <div className="mt-4 flex justify-end gap-2 border-t pt-3">
              <Button variant="ghost" onClick={() => setScheduleModalOpen(false)}>
                Cancel
              </Button>
              <Button
                variant="primary"
                disabled={saveScheduleMutation.isPending}
                onClick={() => saveScheduleMutation.mutate()}
              >
                {saveScheduleMutation.isPending ? 'Saving…' : 'Save Session'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Exception Modal */}
      <Dialog open={exceptionModalOpen} onOpenChange={setExceptionModalOpen}>
        <DialogContent title="Add Leave / Schedule Exception">
          <div className="space-y-3 text-xs">
            <p className="text-xs text-muted-foreground mb-3">
              Record leave, blocked consultation hours, or extra sessions for {selectedDoctor?.doctorName}.
            </p>

            <FormField label="Exception Type *">
              <select
                value={exceptionType}
                onChange={(e) => setExceptionType(e.target.value as any)}
                className="w-full rounded-md border bg-background px-3 py-2 text-xs"
              >
                <option value="LEAVE">Doctor on Leave (Full Day or Multi-day)</option>
                <option value="BLOCKED_SLOT">Blocked Slot (Ward Rounds, Meetings)</option>
                <option value="EXTRA_SESSION">Extra Clinic Session (Ad-hoc)</option>
              </select>
            </FormField>

            <div className="grid grid-cols-2 gap-2">
              <FormField label="Start Date & Time *">
                <Input
                  type="datetime-local"
                  value={startDateTime}
                  onChange={(e) => setStartDateTime(e.target.value)}
                />
              </FormField>
              <FormField label="End Date & Time *">
                <Input
                  type="datetime-local"
                  value={endDateTime}
                  onChange={(e) => setEndDateTime(e.target.value)}
                />
              </FormField>
            </div>

            <FormField label="Reason / Remarks *">
              <Input
                placeholder="e.g. CME Medical Conference, Personal Leave, Emergency Surgery…"
                value={exceptionReason}
                onChange={(e) => setExceptionReason(e.target.value)}
              />
            </FormField>

            {exceptionType === 'EXTRA_SESSION' && (
              <div className="grid grid-cols-2 gap-2">
                <FormField label="Slot Duration (Mins)">
                  <Input
                    type="number"
                    value={extraDuration}
                    onChange={(e) => setExtraDuration(e.target.value)}
                  />
                </FormField>
                <FormField label="Max Patients">
                  <Input
                    type="number"
                    value={extraMaxPatients}
                    onChange={(e) => setExtraMaxPatients(e.target.value)}
                  />
                </FormField>
              </div>
            )}

            <div className="mt-4 flex justify-end gap-2 border-t pt-3">
              <Button variant="ghost" onClick={() => setExceptionModalOpen(false)}>
                Cancel
              </Button>
              <Button
                variant="primary"
                disabled={saveExceptionMutation.isPending}
                onClick={() => saveExceptionMutation.mutate()}
              >
                {saveExceptionMutation.isPending ? 'Saving…' : 'Record Exception'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
