import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { Dialog, DialogContent } from '../components/ui/dialog';
import { Button } from '../components/ui/button';
import { StatusBadge } from '../components/StatusBadge';
import { DateTimeText } from '../components/DateTimeText';
import { MoneyText } from '../components/MoneyText';
import type { AppointmentDetail } from '../types/api';
import { CalendarDays, Clock, History, User } from 'lucide-react';

interface AppointmentDetailModalProps {
  appointmentId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function AppointmentDetailModal({
  appointmentId,
  open,
  onOpenChange,
}: AppointmentDetailModalProps) {
  const { data: detail, isLoading } = useQuery({
    queryKey: ['appointment-detail', appointmentId],
    queryFn: async () => {
      if (!appointmentId) return null;
      const res = await api.get<AppointmentDetail>(`/appointments/${appointmentId}`);
      return res.data;
    },
    enabled: !!appointmentId && open,
  });

  if (!open) return null;

  const apt = detail?.appointment;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title={apt?.appointmentNumber || 'Appointment Details'}>
        {isLoading ? (
          <div className="p-8 text-center text-xs text-muted-foreground">Loading appointment…</div>
        ) : apt ? (
          <div className="space-y-4 text-xs">
            {/* Demographic and Session Summary */}
            <div className="grid grid-cols-2 gap-3 rounded-md bg-muted/30 p-3 border">
              <div>
                <span className="text-muted-foreground block text-[11px]">Patient</span>
                <span className="font-semibold text-foreground text-sm">{apt.patientName}</span>
                <span className="block text-muted-foreground text-[11px]">
                  MRN: {apt.patientMrn} • {apt.patientPhone}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground block text-[11px]">Doctor & Dept</span>
                <span className="font-semibold text-foreground text-sm">{apt.doctorName}</span>
                <span className="block text-muted-foreground text-[11px]">
                  {apt.doctorSpecialization} • {apt.departmentName}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground block text-[11px]">Schedule & Token</span>
                <span className="font-medium text-foreground">
                  {apt.appointmentDate} ({apt.startTime.substring(0, 5)} - {apt.endTime.substring(0, 5)})
                </span>
                <span className="block font-bold text-primary">Token #{apt.tokenNumber}</span>
              </div>
              <div>
                <span className="text-muted-foreground block text-[11px]">Consultation Fee & Type</span>
                <span className="font-medium text-foreground">
                  <MoneyText value={apt.consultationFee} />
                </span>
                <span className="block text-muted-foreground text-[11px]">Type: {apt.type}</span>
              </div>
            </div>

            {apt.reason && (
              <div className="rounded-md border p-2.5">
                <span className="font-semibold block mb-0.5 text-muted-foreground text-[11px]">
                  Clinical Reason / Chief Complaint
                </span>
                <p className="text-xs text-foreground">{apt.reason}</p>
              </div>
            )}

            {apt.cancellationReason && (
              <div className="rounded-md border border-danger/30 bg-danger/5 p-2.5">
                <span className="font-semibold block mb-0.5 text-danger text-[11px]">
                  Cancellation / Reschedule Reason
                </span>
                <p className="text-xs text-danger">{apt.cancellationReason}</p>
              </div>
            )}

            {/* Audit History Timeline */}
            <div className="space-y-2 border-t pt-3">
              <h3 className="font-semibold text-xs flex items-center gap-1.5 text-foreground">
                <History className="h-4 w-4 text-primary" /> Status Transition History ({detail.statusHistory.length})
              </h3>
              <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
                {detail.statusHistory.map((h) => (
                  <div
                    key={h.id}
                    className="flex items-start justify-between rounded border p-2 text-xs bg-surface"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        {h.fromStatus && (
                          <>
                            <StatusBadge status={h.fromStatus} />
                            <span className="text-muted-foreground">→</span>
                          </>
                        )}
                        <StatusBadge status={h.toStatus} />
                      </div>
                      {h.reason && <p className="mt-1 text-muted-foreground">{h.reason}</p>}
                    </div>
                    <div className="text-right text-[11px] text-muted-foreground shrink-0 ml-3">
                      <div>By {h.changedBy}</div>
                      <DateTimeText value={h.changedAt} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : null}

        <div className="mt-4 flex justify-end border-t pt-3">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            Close
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

