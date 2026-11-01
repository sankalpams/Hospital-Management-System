import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, apiMessage } from '../api/client';
import { Dialog, DialogContent } from '../components/ui/dialog';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { FormField } from '../components/FormField';
import { MoneyText } from '../components/MoneyText';
import type { Department, Doctor, PatientSummary, TimeSlot } from '../types/api';
import { CalendarDays, CheckCircle2, Clock, Search, Stethoscope, UserCheck, AlertCircle } from 'lucide-react';

interface BookingModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  defaultDoctorId?: string;
  defaultDepartmentId?: string;
  defaultDate?: string;
}

export function BookingModal({
  open,
  onOpenChange,
  defaultDoctorId,
  defaultDepartmentId,
  defaultDate,
}: BookingModalProps) {
  const client = useQueryClient();

  const [departmentId, setDepartmentId] = useState<string>(defaultDepartmentId || '');
  const [doctorId, setDoctorId] = useState<string>(defaultDoctorId || '');
  const [appointmentDate, setAppointmentDate] = useState<string>(
    defaultDate || new Date().toISOString().split('T')[0]
  );
  const [selectedSlot, setSelectedSlot] = useState<TimeSlot | null>(null);

  // Patient search state
  const [patientSearch, setPatientSearch] = useState('');
  const [selectedPatient, setSelectedPatient] = useState<PatientSummary | null>(null);
  const [reason, setReason] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Query departments
  const { data: departments = [] } = useQuery({
    queryKey: ['departments-all'],
    queryFn: async () => (await api.get<Department[]>('/departments')).data,
    enabled: open,
  });

  // Query doctors
  const { data: doctorsData } = useQuery({
    queryKey: ['doctors-booking', departmentId],
    queryFn: async () => {
      const res = await api.get<{ content: Doctor[] }>('/doctors', {
        params: { departmentId: departmentId || undefined, size: 50 },
      });
      return res.data.content;
    },
    enabled: open,
  });
  const doctors = doctorsData || [];

  // Query real-time time slots for selected doctor and date
  const { data: slots = [], isLoading: slotsLoading } = useQuery({
    queryKey: ['doctor-slots', doctorId, appointmentDate],
    queryFn: async () => {
      const res = await api.get<TimeSlot[]>(`/schedules/doctors/${doctorId}/slots`, {
        params: { date: appointmentDate },
      });
      return res.data;
    },
    enabled: !!doctorId && !!appointmentDate && open,
  });

  // Query patient autocomplete
  const { data: patientResults = [] } = useQuery({
    queryKey: ['patient-search-booking', patientSearch],
    queryFn: async () => {
      const res = await api.get<PatientSummary[]>('/patients/quick-search', {
        params: { q: patientSearch },
      });
      return res.data;
    },
    enabled: patientSearch.length >= 2,
  });

  // Booking mutation
  const bookMutation = useMutation({
    mutationFn: async () => {
      if (!doctorId || !selectedPatient || !selectedSlot) {
        throw new Error('Please select doctor, patient, and an available slot.');
      }
      return (
        await api.post('/appointments', {
          doctorId,
          patientId: selectedPatient.id,
          departmentId: departmentId || undefined,
          appointmentDate,
          startTime: selectedSlot.startTime,
          reason,
          type: 'STANDARD',
        })
      ).data;
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['appointments'] });
      void client.invalidateQueries({ queryKey: ['appointments-calendar'] });
      void client.invalidateQueries({ queryKey: ['doctor-slots'] });
      void client.invalidateQueries({ queryKey: ['appointments-today-queue'] });
      onOpenChange(false);
      resetForm();
    },
    onError: (err) => {
      setErrorMessage(apiMessage(err));
    },
  });

  const resetForm = () => {
    setSelectedSlot(null);
    setSelectedPatient(null);
    setPatientSearch('');
    setReason('');
    setErrorMessage(null);
  };

  const selectedDoctorObj = doctors.find((d) => d.id === doctorId);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="Book Medical Consultation">
        <p className="text-xs text-muted-foreground -mt-2 mb-4">
          Reserve a confirmed clinical appointment slot with real-time double-booking prevention.
        </p>

        {errorMessage && (
          <div className="mb-4 rounded-md border border-danger/30 bg-danger/10 p-3 text-xs text-danger flex items-start gap-2">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">Booking Conflict</p>
              <p>{errorMessage}</p>
            </div>
          </div>
        )}

        <div className="space-y-4 text-xs">
          {/* Step 1: Department & Doctor */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <FormField label="Department (Optional)">
              <select
                value={departmentId}
                onChange={(e) => {
                  setDepartmentId(e.target.value);
                  setDoctorId('');
                  setSelectedSlot(null);
                }}
                className="w-full rounded-md border bg-background px-3 py-2 text-xs"
              >
                <option value="">All Clinical Departments</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </FormField>

            <FormField label="Doctor *">
              <select
                value={doctorId}
                onChange={(e) => {
                  setDoctorId(e.target.value);
                  setSelectedSlot(null);
                }}
                className="w-full rounded-md border bg-background px-3 py-2 text-xs"
              >
                <option value="">Select Specialist Doctor</option>
                {doctors.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.doctorName} — {d.specialization} ({d.departmentName})
                  </option>
                ))}
              </select>
            </FormField>
          </div>

          {selectedDoctorObj && (
            <div className="rounded-md bg-muted/50 p-2.5 flex items-center justify-between text-xs border">
              <div>
                <span className="font-semibold">{selectedDoctorObj.doctorName}</span> •{' '}
                <span className="text-muted-foreground">{selectedDoctorObj.specialization}</span> •{' '}
                <span className="text-muted-foreground">{selectedDoctorObj.slmcRegNumber}</span>
              </div>
              <div className="font-semibold text-primary">
                Fee: <MoneyText value={selectedDoctorObj.consultationFee} />
              </div>
            </div>
          )}

          {/* Step 2: Date & Available Slots */}
          <div className="space-y-2">
            <FormField label="Consultation Date *">
              <Input
                type="date"
                min={new Date().toISOString().split('T')[0]}
                value={appointmentDate}
                onChange={(e) => {
                  setAppointmentDate(e.target.value);
                  setSelectedSlot(null);
                }}
              />
            </FormField>

            {doctorId && (
              <div>
                <label className="block text-xs font-medium text-foreground mb-1.5 flex items-center justify-between">
                  <span>Available Time Slots *</span>
                  <span className="text-[11px] text-muted-foreground font-normal">
                    {slots.filter((s) => !s.isBooked && !s.isBlocked).length} available
                  </span>
                </label>

                {slotsLoading ? (
                  <div className="p-4 text-center text-muted-foreground text-xs">
                    Loading doctor availability…
                  </div>
                ) : slots.length === 0 ? (
                  <div className="p-4 text-center rounded-md border border-dashed text-muted-foreground text-xs">
                    No scheduled sessions or doctor is on leave on this date.
                  </div>
                ) : (
                  <div className="grid grid-cols-3 sm:grid-cols-4 gap-2 max-h-48 overflow-y-auto p-1">
                    {slots.map((slot) => {
                      const isSelected = selectedSlot?.startTime === slot.startTime;
                      const disabled = slot.isBooked || slot.isBlocked;

                      return (
                        <button
                          key={slot.startTime}
                          type="button"
                          disabled={disabled}
                          onClick={() => {
                            setSelectedSlot(slot);
                            setErrorMessage(null);
                          }}
                          className={`px-2 py-2 rounded border text-center transition-colors text-xs font-medium ${
                            disabled
                              ? 'bg-muted/40 text-muted-foreground/60 border-muted line-through cursor-not-allowed'
                              : isSelected
                              ? 'bg-primary text-primary-foreground border-primary font-bold shadow-sm'
                              : 'bg-surface hover:bg-muted/70 text-foreground border-border'
                          }`}
                        >
                          <div>
                            {slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}
                          </div>
                          {slot.isBooked && (
                            <span className="text-[10px] block font-normal">Token #{slot.tokenNumber}</span>
                          )}
                          {slot.isBlocked && <span className="text-[10px] block font-normal">Blocked</span>}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Step 3: Patient Selection */}
          <div className="space-y-2 border-t pt-3">
            <FormField label="Search Registered Patient *">
              {selectedPatient ? (
                <div className="flex items-center justify-between rounded-md border bg-muted/40 p-2.5">
                  <div>
                    <span className="font-semibold text-foreground">{selectedPatient.fullName}</span> •{' '}
                    <span className="text-muted-foreground">MRN: {selectedPatient.mrn}</span> •{' '}
                    <span className="text-muted-foreground">{selectedPatient.phone || 'No phone'}</span>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => setSelectedPatient(null)}
                  >
                    Change
                  </Button>
                </div>
              ) : (
                <div className="relative">
                  <Input
                    placeholder="Search patient by MRN, Name, NIC or +94 phone…"
                    value={patientSearch}
                    onChange={(e) => setPatientSearch(e.target.value)}
                  />
                  {patientResults.length > 0 && (
                    <div className="absolute left-0 right-0 top-full z-10 mt-1 max-h-40 overflow-y-auto rounded-md border bg-surface shadow-md">
                      {patientResults.map((p) => (
                        <button
                          key={p.id}
                          type="button"
                          onClick={() => {
                            setSelectedPatient(p);
                            setPatientSearch('');
                          }}
                          className="w-full text-left px-3 py-2 text-xs hover:bg-muted flex items-center justify-between border-b last:border-b-0"
                        >
                          <div>
                            <span className="font-semibold">{p.fullName}</span>{' '}
                            <span className="text-muted-foreground">({p.age}y / {p.sex})</span>
                          </div>
                          <div className="text-muted-foreground text-[11px]">
                            {p.mrn} • {p.district}
                          </div>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </FormField>

            <FormField label="Clinical Reason / Chief Complaint">
              <Input
                placeholder="e.g. Hypertension review, Routine diabetic check, Persistent cough…"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </FormField>
          </div>
        </div>

        <div className="mt-5 flex items-center justify-end gap-2 border-t pt-3">
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
          >
            Cancel
          </Button>
          <Button
            type="button"
            variant="primary"
            disabled={!doctorId || !selectedPatient || !selectedSlot || bookMutation.isPending}
            onClick={() => bookMutation.mutate()}
          >
            {bookMutation.isPending ? 'Reserving slot…' : 'Confirm & Book Appointment'}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

