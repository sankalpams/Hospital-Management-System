import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, apiMessage } from '../api/client';
import { Dialog, DialogContent } from '../components/ui/dialog';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { FormField } from '../components/FormField';
import type { Appointment } from '../types/api';
import { AlertTriangle, AlertCircle } from 'lucide-react';

interface CancelModalProps {
  appointment: Appointment | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CancelModal({ appointment, open, onOpenChange }: CancelModalProps) {
  const client = useQueryClient();
  const [reason, setReason] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const cancelMutation = useMutation({
    mutationFn: async () => {
      if (!appointment) return;
      if (!reason.trim()) {
        throw new Error('A cancellation reason is strictly required.');
      }
      return (
        await api.post(`/appointments/${appointment.id}/cancel`, {
          reason: reason.trim(),
        })
      ).data;
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['appointments'] });
      void client.invalidateQueries({ queryKey: ['appointments-calendar'] });
      onOpenChange(false);
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
      <DialogContent title="Cancel Appointment">
        <p className="text-xs text-muted-foreground -mt-2 mb-4">
          Cancelling {appointment.appointmentNumber} for {appointment.patientName}.
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
              <span className="font-semibold">Doctor:</span> {appointment.doctorName}
            </div>
            <div>
              <span className="font-semibold">Slot:</span> {appointment.appointmentDate} at{' '}
              {appointment.startTime.substring(0, 5)} (Token #{appointment.tokenNumber})
            </div>
          </div>

          <FormField label="Mandatory Cancellation Reason *">
            <Input
              placeholder="e.g. Patient called to cancel, Doctor urgent surgery, Travel difficulty…"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              autoFocus
            />
          </FormField>
        </div>

        <div className="mt-5 flex items-center justify-end gap-2 border-t pt-3">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            Keep Appointment
          </Button>
          <Button
            type="button"
            variant="danger"
            disabled={!reason.trim() || cancelMutation.isPending}
            onClick={() => cancelMutation.mutate()}
          >
            {cancelMutation.isPending ? 'Cancelling…' : 'Confirm Cancellation'}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

