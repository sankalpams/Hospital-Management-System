import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { appointmentsApi, doctorsApi } from '../../api/clinic';
import { toApiError } from '../../api/client';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { SelectField, TextField } from '../../components/ui/Field';
import { Alert } from '../../components/ui/Alert';
import { useToast } from '../../components/ui/Toast';
import { PatientPicker } from './shared';

/** Walk-in: the server places the patient in the next free slot today and checks them in. */
export function WalkInDialog({ onClose }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [patient, setPatient] = useState(null);
  const [doctorId, setDoctorId] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const doctors = useQuery({ queryKey: ['doctor-options', ''], queryFn: () => doctorsApi.options(), staleTime: 60_000 });

  const save = useMutation({
    mutationFn: () => appointmentsApi.walkIn({ patientId: patient.id, doctorId: Number(doctorId), reason: reason || null }),
    onSuccess: (a) => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      toast.success(`${a.patientName} checked in at ${a.localTime.slice(0, 5)}, token ${a.tokenNo}.`);
      onClose();
    },
    onError: (err) => setGeneral(toApiError(err)),
  });

  const submit = () => {
    const errs = {};
    if (!patient) errs.patient = 'Select the patient.';
    if (!doctorId) errs.doctorId = 'Choose a doctor.';
    setErrors(errs);
    setGeneral(null);
    if (!Object.keys(errs).length) save.mutate();
  };

  return (
    <Modal open onClose={onClose} title="Register walk-in" size="lg"
           description="The patient is placed in the next free slot of today's session and checked in."
           footer={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button onClick={submit} loading={save.isPending}>Check in walk-in</Button></>}>
      <div className="flex flex-col gap-4">
        {general && <Alert tone="danger" requestId={general.requestId}>{general.message}</Alert>}
        <div>
          <p className="mb-1.5 text-[13px] font-medium">Patient <span className="text-danger" aria-hidden="true">*</span></p>
          <PatientPicker value={patient} onChange={setPatient} error={errors.patient} />
        </div>
        <SelectField label="Doctor" required placeholder="Select doctor" value={doctorId} onChange={(e) => setDoctorId(e.target.value)} error={errors.doctorId}
                     options={(doctors.data ?? []).map((d) => ({ value: String(d.id), label: `${d.displayName} · ${d.specialization}` }))} />
        <TextField label="Reason for visit" value={reason} maxLength={255} onChange={(e) => setReason(e.target.value)} />
      </div>
    </Modal>
  );
}
