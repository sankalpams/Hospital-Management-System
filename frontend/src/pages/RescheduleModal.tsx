import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, apiMessage } from '../api/client';
import { Dialog, DialogContent } from '../components/ui/dialog';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { FormField } from '../components/FormField';
import type { Appointment, TimeSlot } from '../types/api';
import { CalendarDays, AlertCircle } from 'lucide-react';

interface RescheduleModalProps {
  appointment: Appointment | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function RescheduleModal({ appointment, open, onOpenChange }: RescheduleModalProps) {
  const client = useQueryClient();

  const [newDate, setNewDate] = useState<string>(
    new Date(Date.now() + 86400000).toISOString().split('T')[0]
  );
  const [selectedSlot, setSelectedSlot] = useState<TimeSlot | null>(null);
  const [reason, setReason] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Query available slots for the doctor on the new date
  const { data: slots = [], isLoading: slotsLoading } = useQuery({
    queryKey: ['doctor-slots', appointment?.doctorId, newDate],
    queryFn: async () => {
      if (!appointment?.doctorId) return [];
      const res = await api.get<TimeSlot[]>(`/schedules/doctors/${appointment.doctorId}/slots`, {
        params: { date: newDate },
      });
      return res.data;
    },
    enabled: !!appointment?.doctorId && !!newDate && open,
  });

  const rescheduleMutation = useMutation({
    mutationFn: async () => {
      if (!appointment || !selectedSlot) {
        throw new Error('Please select an available slot.');
      }
      if (!reason.trim()) {
        throw new Error('Please state a reason for rescheduling.');
      }
      return (
        await api.post(`/appointments/${appointment.id}/reschedule`, {
          appointmentDate: newDate,
          startTime: selectedSlot.startTime,
          reason,
        })
      ).data;
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['appointments'] });
      void client.invalidateQueries({ queryKey: ['appointments-calendar'] });
      onOpenChange(false);
      setSelectedSlot(null);
      setReason('');
      setErrorMessage(null);
    },
    onError: (err) => {
      setErrorMessage(apiMessage(err));
    },
  });

  if (!appointment) return null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="Reschedule Appointment">
        <p className="text-xs text-muted-foreground -mt-2 mb-4">
          Move {appointment.appointmentNumber} ({appointment.patientName}) to a new date and time slot.
        </p>

        {errorMessage && (
          <div className="mb-4 rounded-md border border-danger/30 bg-danger/10 p-3 text-xs text-danger flex items-start gap-2">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <div>{errorMessage}</div>
          </div>
        )}

        <div className="space-y-4 text-xs">
          <div className="rounded-md bg-muted/40 p-2.5 text-xs space-y-1 border">
            <div>
              <span className="font-semibold">Current Slot:</span> {appointment.appointmentDate} at{' '}
              {appointment.startTime.substring(0, 5)} (Token #{appointment.tokenNumber})
            </div>
            <div>
              <span className="font-semibold">Doctor:</span> {appointment.doctorName} (
              {appointment.departmentName})
            </div>
          </div>

          <FormField label="New Consultation Date *">
            <Input
              type="date"
              min={new Date().toISOString().split('T')[0]}
              value={newDate}
              onChange={(e) => {
                setNewDate(e.target.value);
                setSelectedSlot(null);
              }}
            />
          </FormField>

          <div>
            <label className="block text-xs font-medium text-foreground mb-1.5">
              Select New Time Slot *
            </label>
            {slotsLoading ? (
              <div className="p-3 text-center text-muted-foreground text-xs">Loading availability…</div>
            ) : slots.length === 0 ? (
              <div className="p-3 text-center rounded-md border border-dashed text-muted-foreground text-xs">
                Doctor has no available clinic slots on this date.
              </div>
            ) : (
              <div className="grid grid-cols-3 gap-2 max-h-40 overflow-y-auto p-1">
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
                      className={`px-2 py-1.5 rounded border text-center text-xs font-medium ${
                        disabled
                          ? 'bg-muted/40 text-muted-foreground/60 border-muted line-through cursor-not-allowed'
                          : isSelected
                          ? 'bg-primary text-primary-foreground border-primary font-bold'
                          : 'bg-surface hover:bg-muted text-foreground border-border'
                      }`}
                    >
                      {slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          <FormField label="Reason for Rescheduling *">
            <Input
              placeholder="e.g. Patient requested morning slot, Doctor emergency…"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </FormField>
        </div>

        <div className="mt-5 flex items-center justify-end gap-2 border-t pt-3">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            Keep Existing Slot
          </Button>
          <Button
            type="button"
            variant="primary"
            disabled={!selectedSlot || !reason.trim() || rescheduleMutation.isPending}
            onClick={() => rescheduleMutation.mutate()}
          >
            {rescheduleMutation.isPending ? 'Rescheduling…' : 'Confirm Reschedule'}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

