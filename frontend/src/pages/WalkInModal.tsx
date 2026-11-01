import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, apiMessage } from '../api/client';
import { Dialog, DialogContent } from '../components/ui/dialog';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { FormField } from '../components/FormField';
import type { Doctor, PatientSummary } from '../types/api';
import { UserCheck, AlertCircle, Zap } from 'lucide-react';

interface WalkInModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  defaultDoctorId?: string;
}

export function WalkInModal({ open, onOpenChange, defaultDoctorId }: WalkInModalProps) {
  const client = useQueryClient();

  const [doctorId, setDoctorId] = useState<string>(defaultDoctorId || '');
  const [patientSearch, setPatientSearch] = useState('');
  const [selectedPatient, setSelectedPatient] = useState<PatientSummary | null>(null);
  const [reason, setReason] = useState('Walk-in triage consultation');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Query doctors
  const { data: doctorsData } = useQuery({
    queryKey: ['doctors-walkin'],
    queryFn: async () => {
      const res = await api.get<{ content: Doctor[] }>('/doctors', { params: { size: 50 } });
      return res.data.content;
    },
    enabled: open,
  });
  const doctors = doctorsData || [];

  // Patient autocomplete
  const { data: patientResults = [] } = useQuery({
    queryKey: ['patient-search-walkin', patientSearch],
    queryFn: async () => {
      const res = await api.get<PatientSummary[]>('/patients/quick-search', {
        params: { q: patientSearch },
      });
      return res.data;
    },
    enabled: patientSearch.length >= 2,
  });

  const walkInMutation = useMutation({
    mutationFn: async () => {
      if (!doctorId || !selectedPatient) {
        throw new Error('Please select doctor and patient.');
      }
      return (
        await api.post('/appointments/walk-in', {
          doctorId,
          patientId: selectedPatient.id,
          reason,
        })
      ).data;
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['appointments'] });
      void client.invalidateQueries({ queryKey: ['appointments-today-queue'] });
      onOpenChange(false);
      resetForm();
    },
    onError: (err) => {
      setErrorMessage(apiMessage(err));
    },
  });

  const resetForm = () => {
    setSelectedPatient(null);
    setPatientSearch('');
    setReason('Walk-in triage consultation');
    setErrorMessage(null);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="Walk-In Registration & Token Issue">
        <p className="text-xs text-muted-foreground -mt-2 mb-4">
          Instantly assign the next available token number and check in for today's clinic.
        </p>

        {errorMessage && (
          <div className="mb-4 rounded-md border border-danger/30 bg-danger/10 p-3 text-xs text-danger flex items-start gap-2">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <div>{errorMessage}</div>
          </div>
        )}

        <div className="space-y-4 text-xs">
          <FormField label="Consulting Doctor *">
            <select
              value={doctorId}
              onChange={(e) => setDoctorId(e.target.value)}
              className="w-full rounded-md border bg-background px-3 py-2 text-xs"
            >
              <option value="">Select On-Duty Doctor</option>
              {doctors.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.doctorName} — {d.specialization} ({d.departmentName})
                </option>
              ))}
            </select>
          </FormField>

          <FormField label="Search Registered Patient *">
            {selectedPatient ? (
              <div className="flex items-center justify-between rounded-md border bg-muted/40 p-2.5">
                <div>
                  <span className="font-semibold text-foreground">{selectedPatient.fullName}</span> •{' '}
                  <span className="text-muted-foreground">MRN: {selectedPatient.mrn}</span>
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
                  placeholder="Search patient by MRN, Name, or +94 phone…"
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
                          <span className="text-muted-foreground">({p.mrn})</span>
                        </div>
                        <div className="text-muted-foreground text-[11px]">{p.phone}</div>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
          </FormField>

          <FormField label="Reason for Visit">
            <Input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Acute fever, Chest discomfort, Dressing change…"
            />
          </FormField>
        </div>

        <div className="mt-5 flex items-center justify-end gap-2 border-t pt-3">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            type="button"
            variant="primary"
            disabled={!doctorId || !selectedPatient || walkInMutation.isPending}
            onClick={() => walkInMutation.mutate()}
          >
            {walkInMutation.isPending ? 'Issuing Token…' : 'Issue Next Token & Check In'}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

