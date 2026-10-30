import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  Download,
  FileText,
  Plus,
  ShieldAlert,
  Trash2,
  Upload,
  Calendar,
  FlaskConical,
  Pill,
  Receipt,
  UserCheck,
  GitMerge,
  ArrowLeft,
} from 'lucide-react';
import { api, apiMessage } from '../api/client';
import type {
  AllergySeverity,
  Consent,
  DocumentCategory,
  Patient,
  PatientAllergy,
  PatientDocument,
} from '../types/api';
import { PatientBanner } from '../components/PatientBanner';
import { PageHeader } from '../components/PageHeader';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Dialog, DialogContent } from '../components/ui/dialog';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { StatusBadge } from '../components/StatusBadge';
import { DateTimeText } from '../components/DateTimeText';
import { EmptyState } from '../components/EmptyState';
import { FormField } from '../components/FormField';
import { useAuth } from '../hooks/use-auth';

export function PatientProfilePage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { has, user } = useAuth();
  const client = useQueryClient();

  const canViewClinical = has('EMR_READ');

  const [activeTab, setActiveTab] = useState<'overview' | 'medical' | 'documents' | 'consents' | 'visits' | 'lab' | 'prescriptions' | 'billing'>('overview');
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [showAllergyModal, setShowAllergyModal] = useState(false);
  const [showConsentModal, setShowConsentModal] = useState(false);
  const [showMergeModal, setShowMergeModal] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // Patient detail query
  const { data: patient, isLoading, isError, error } = useQuery({
    queryKey: ['patient', id],
    queryFn: async () => (await api.get<Patient>(`/patients/${id}`)).data,
    enabled: !!id,
  });

  // Export handlers
  const handleExport = async (format: 'json' | 'pdf') => {
    try {
      const response = await api.get(`/patients/${id}/export`, {
        params: { format },
        responseType: 'blob',
      });
      const blob = new Blob([response.data], {
        type: format === 'pdf' ? 'application/pdf' : 'application/json',
      });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `patient-${patient?.mrn || id}.${format}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert('Failed to export patient records: ' + apiMessage(err));
    }
  };

  // Delete/Deactivate patient mutation
  const deleteMutation = useMutation({
    mutationFn: () => api.delete(`/patients/${id}`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['patients'] });
      navigate('/patients');
    },
  });

  if (isLoading) {
    return (
      <div className="p-8 text-center text-sm text-muted-foreground">
        Loading patient clinical chart…
      </div>
    );
  }

  if (isError || !patient) {
    return (
      <div className="p-8 text-center text-sm text-danger">
        {apiMessage(error) || 'Patient record not found.'}
        <div className="mt-4">
          <Link to="/patients">
            <Button variant="secondary" size="sm">
              <ArrowLeft className="h-4 w-4" /> Back to Patient Index
            </Button>
          </Link>
        </div>
      </div>
    );
  }

  const allergyNames = patient.allergies.map((a) => a.allergen);

  return (
    <>
      <div className="mb-2">
        <Link
          to="/patients"
          className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground mb-2"
        >
          <ArrowLeft className="h-3.5 w-3.5" /> Back to Patient Index
        </Link>
      </div>

      {/* Pinned Patient Clinical Banner */}
      <PatientBanner
        name={patient.fullName}
        ageSex={patient.ageSex}
        mrn={patient.mrn}
        allergies={allergyNames}
      />

      <div className="mb-5 flex flex-wrap items-center justify-between gap-3 border-b pb-4">
        <div>
          <h2 className="text-lg font-semibold tracking-tight">{patient.fullName}</h2>
          <p className="text-xs text-muted-foreground">
            Registered on <DateTimeText value={patient.createdAt} /> • District:{' '}
            <span className="font-medium">{patient.district}</span>
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {has('USER_MANAGE') && (
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => handleExport('pdf')}
                title="Export patient clinical chart to PDF"
              >
                <Download className="h-3.5 w-3.5" /> Export PDF
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => handleExport('json')}
                title="Export full patient record as JSON"
              >
                <FileText className="h-3.5 w-3.5" /> Export JSON
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setShowMergeModal(true)}
              >
                <GitMerge className="h-3.5 w-3.5" /> Merge Record
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={() => setDeleting(true)}
              >
                <Trash2 className="h-3.5 w-3.5" /> Delete
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="border-b mb-6 overflow-x-auto">
        <nav className="flex space-x-6 text-sm font-medium">
          <button
            onClick={() => setActiveTab('overview')}
            className={`pb-3 border-b-2 whitespace-nowrap ${
              activeTab === 'overview'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            Overview
          </button>

          {canViewClinical && (
            <button
              onClick={() => setActiveTab('medical')}
              className={`pb-3 border-b-2 whitespace-nowrap flex items-center gap-1.5 ${
                activeTab === 'medical'
                  ? 'border-primary text-primary'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              Medical History & Allergies
              {patient.allergies.length > 0 && (
                <span className="rounded-full bg-danger/10 px-1.5 py-0.2 text-[10px] font-bold text-danger">
                  {patient.allergies.length}
                </span>
              )}
            </button>
          )}

          <button
            onClick={() => setActiveTab('documents')}
            className={`pb-3 border-b-2 whitespace-nowrap flex items-center gap-1.5 ${
              activeTab === 'documents'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            Documents
            {patient.documents.length > 0 && (
              <span className="rounded-full bg-muted px-1.5 py-0.2 text-[10px] font-semibold text-muted-foreground">
                {patient.documents.length}
              </span>
            )}
          </button>

          <button
            onClick={() => setActiveTab('consents')}
            className={`pb-3 border-b-2 whitespace-nowrap ${
              activeTab === 'consents'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            Consents
          </button>

          <button
            onClick={() => setActiveTab('visits')}
            className={`pb-3 border-b-2 whitespace-nowrap ${
              activeTab === 'visits'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            Visits
          </button>

          {canViewClinical && (
            <>
              <button
                onClick={() => setActiveTab('lab')}
                className={`pb-3 border-b-2 whitespace-nowrap ${
                  activeTab === 'lab'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-muted-foreground hover:text-foreground'
                }`}
              >
                Lab & Diagnostics
              </button>

              <button
                onClick={() => setActiveTab('prescriptions')}
                className={`pb-3 border-b-2 whitespace-nowrap ${
                  activeTab === 'prescriptions'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-muted-foreground hover:text-foreground'
                }`}
              >
                Prescriptions
              </button>
            </>
          )}

          <button
            onClick={() => setActiveTab('billing')}
            className={`pb-3 border-b-2 whitespace-nowrap ${
              activeTab === 'billing'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            Billing
          </button>
        </nav>
      </div>

      {/* Tab: Overview */}
      {activeTab === 'overview' && (
        <div className="grid gap-6 md:grid-cols-2">
          {/* Demographics Card */}
          <div className="border bg-surface p-5 shadow-panel space-y-4">
            <h3 className="font-semibold text-sm tracking-wide text-foreground uppercase border-b pb-2">
              Identity & Demographics
            </h3>
            <div className="grid grid-cols-2 gap-y-3 text-sm">
              <span className="text-muted-foreground">National Identity (NIC):</span>
              <span className="font-mono font-medium">{patient.nic ?? 'Not on file'}</span>

              <span className="text-muted-foreground">Date of Birth:</span>
              <span className="font-medium">
                {patient.dateOfBirth} ({patient.age} years)
              </span>

              <span className="text-muted-foreground">Sex / Gender:</span>
              <span className="font-medium">{patient.sex}</span>

              <span className="text-muted-foreground">Blood Group:</span>
              <span className="font-mono font-bold text-primary">
                {patient.bloodGroup ?? 'Unknown'}
              </span>

              <span className="text-muted-foreground">Account Status:</span>
              <span>
                <StatusBadge status={patient.active} />
              </span>
            </div>
          </div>

          {/* Contact Card */}
          <div className="border bg-surface p-5 shadow-panel space-y-4">
            <h3 className="font-semibold text-sm tracking-wide text-foreground uppercase border-b pb-2">
              Contact & Residential Address
            </h3>
            <div className="grid grid-cols-2 gap-y-3 text-sm">
              <span className="text-muted-foreground">Telephone:</span>
              <span className="font-mono font-medium">{patient.phone ?? '—'}</span>

              <span className="text-muted-foreground">Email:</span>
              <span>{patient.email ?? '—'}</span>

              <span className="text-muted-foreground">District:</span>
              <span className="font-medium">{patient.district}</span>

              <span className="text-muted-foreground">City:</span>
              <span>{patient.city ?? '—'}</span>

              <span className="text-muted-foreground">Address:</span>
              <span className="col-span-1">{patient.addressLine1 ?? '—'}</span>
            </div>
          </div>

          {/* Emergency Contact */}
          <div className="border bg-surface p-5 shadow-panel space-y-4">
            <h3 className="font-semibold text-sm tracking-wide text-foreground uppercase border-b pb-2">
              Guardian / Emergency Contact
            </h3>
            <div className="grid grid-cols-2 gap-y-3 text-sm">
              <span className="text-muted-foreground">Contact Name:</span>
              <span className="font-medium">{patient.guardianName ?? '—'}</span>

              <span className="text-muted-foreground">Relationship:</span>
              <span>{patient.guardianRelationship ?? '—'}</span>

              <span className="text-muted-foreground">Emergency Phone:</span>
              <span className="font-mono font-medium">{patient.guardianPhone ?? '—'}</span>
            </div>
          </div>

          {/* Insurance & Chronic Conditions */}
          <div className="border bg-surface p-5 shadow-panel space-y-4">
            <h3 className="font-semibold text-sm tracking-wide text-foreground uppercase border-b pb-2">
              Coverage & Chronic Notes
            </h3>
            <div className="grid grid-cols-2 gap-y-3 text-sm">
              <span className="text-muted-foreground">Insurance Provider:</span>
              <span className="font-medium">{patient.insuranceProvider ?? 'None / Cash'}</span>

              <span className="text-muted-foreground">Policy Number:</span>
              <span className="font-mono">{patient.insurancePolicyNumber ?? '—'}</span>

              <span className="text-muted-foreground">Chronic Conditions:</span>
              <span className="font-medium col-span-1">
                {patient.chronicConditions || 'No chronic conditions reported.'}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Tab: Medical History & Allergies */}
      {activeTab === 'medical' && canViewClinical && (
        <div className="space-y-6">
          <div className="border bg-surface p-5 shadow-panel">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="font-semibold text-base">Clinical Allergies</h3>
                <p className="text-xs text-muted-foreground">
                  Structured adverse reactions, severity classification, and clinical notes.
                </p>
              </div>
              {has('PATIENT_WRITE') && (
                <Button size="sm" onClick={() => setShowAllergyModal(true)}>
                  <Plus className="h-3.5 w-3.5" /> Add Allergy
                </Button>
              )}
            </div>

            {patient.allergies.length === 0 ? (
              <EmptyState
                title="No known allergies"
                detail="Patient has no documented drug, environmental, or food allergies on file."
                action={
                  has('PATIENT_WRITE') ? (
                    <Button size="sm" variant="secondary" onClick={() => setShowAllergyModal(true)}>
                      Document allergy
                    </Button>
                  ) : undefined
                }
              />
            ) : (
              <div className="overflow-x-auto border">
                <table className="w-full text-left text-sm">
                  <thead className="bg-muted text-xs uppercase text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2">Allergen</th>
                      <th className="px-3 py-2">Reported Reaction</th>
                      <th className="px-3 py-2">Severity</th>
                      <th className="px-3 py-2">Clinical Notes</th>
                      {has('PATIENT_WRITE') && <th className="px-3 py-2 text-right">Action</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {patient.allergies.map((allergy) => (
                      <tr key={allergy.id} className="border-t hover:bg-muted/40">
                        <td className="px-3 py-2.5 font-bold text-danger">{allergy.allergen}</td>
                        <td className="px-3 py-2.5">{allergy.reaction}</td>
                        <td className="px-3 py-2.5">
                          <span
                            className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${
                              allergy.severity === 'SEVERE'
                                ? 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'
                                : allergy.severity === 'MODERATE'
                                ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                                : 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                            }`}
                          >
                            {allergy.severity}
                          </span>
                        </td>
                        <td className="px-3 py-2.5 text-muted-foreground">{allergy.notes ?? '—'}</td>
                        {has('PATIENT_WRITE') && (
                          <td className="px-3 py-2.5 text-right">
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-danger"
                              onClick={async () => {
                                if (confirm(`Remove allergy record for ${allergy.allergen}?`)) {
                                  await api.delete(`/patients/${id}/allergies/${allergy.id}`);
                                  void client.invalidateQueries({ queryKey: ['patient', id] });
                                }
                              }}
                            >
                              Remove
                            </Button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab: Documents */}
      {activeTab === 'documents' && (
        <div className="border bg-surface p-5 shadow-panel space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="font-semibold text-base">Attached Documents</h3>
              <p className="text-xs text-muted-foreground">
                Lab reports, radiologic scans, external referrals, and patient identification.
              </p>
            </div>
            {has('PATIENT_WRITE') && (
              <Button size="sm" onClick={() => setShowUploadModal(true)}>
                <Upload className="h-3.5 w-3.5" /> Upload Document
              </Button>
            )}
          </div>

          {patient.documents.length === 0 ? (
            <EmptyState
              title="No documents uploaded"
              detail="Upload patient referral letters, lab reports, or diagnostic scans."
              action={
                has('PATIENT_WRITE') ? (
                  <Button size="sm" variant="secondary" onClick={() => setShowUploadModal(true)}>
                    Upload first document
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <div className="overflow-x-auto border">
              <table className="w-full text-left text-sm">
                <thead className="bg-muted text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2">Filename</th>
                    <th className="px-3 py-2">Category</th>
                    <th className="px-3 py-2">Size</th>
                    <th className="px-3 py-2">Uploaded</th>
                    <th className="px-3 py-2 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {patient.documents.map((doc) => (
                    <tr key={doc.id} className="border-t hover:bg-muted/40">
                      <td className="px-3 py-2.5 font-medium">
                        <div className="flex items-center gap-2">
                          <FileText className="h-4 w-4 text-primary" />
                          <span>{doc.fileName}</span>
                        </div>
                        {doc.notes && <p className="text-xs text-muted-foreground ml-6">{doc.notes}</p>}
                      </td>
                      <td className="px-3 py-2.5">
                        <StatusBadge status={doc.category} />
                      </td>
                      <td className="px-3 py-2.5 text-xs font-mono text-muted-foreground">
                        {(doc.fileSize / 1024).toFixed(1)} KB
                      </td>
                      <td className="px-3 py-2.5 text-xs">
                        <DateTimeText value={doc.createdAt} />
                      </td>
                      <td className="px-3 py-2.5 text-right space-x-1">
                        <a
                          href={`/api/v1/patients/${id}/documents/${doc.id}/download?preview=true`}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          <Button size="sm" variant="ghost">
                            Preview
                          </Button>
                        </a>
                        <a
                          href={`/api/v1/patients/${id}/documents/${doc.id}/download`}
                          download
                        >
                          <Button size="sm" variant="ghost">
                            Download
                          </Button>
                        </a>
                        {has('PATIENT_WRITE') && (
                          <Button
                            size="sm"
                            variant="ghost"
                            className="text-danger"
                            onClick={async () => {
                              if (confirm(`Delete document "${doc.fileName}"?`)) {
                                await api.delete(`/patients/${id}/documents/${doc.id}`);
                                void client.invalidateQueries({ queryKey: ['patient', id] });
                              }
                            }}
                          >
                            Delete
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab: Consents */}
      {activeTab === 'consents' && (
        <div className="border bg-surface p-5 shadow-panel space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="font-semibold text-base">Informed Consents</h3>
              <p className="text-xs text-muted-foreground">
                Documented patient authorizations for clinical procedures, disclosures, and treatments.
              </p>
            </div>
            {has('PATIENT_WRITE') && (
              <Button size="sm" onClick={() => setShowConsentModal(true)}>
                <UserCheck className="h-3.5 w-3.5" /> Record Consent
              </Button>
            )}
          </div>

          {patient.consents.length === 0 ? (
            <EmptyState
              title="No consents recorded"
              detail="Record signed procedure consents or data disclosure authorizations."
              action={
                has('PATIENT_WRITE') ? (
                  <Button size="sm" variant="secondary" onClick={() => setShowConsentModal(true)}>
                    Record first consent
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <div className="overflow-x-auto border">
              <table className="w-full text-left text-sm">
                <thead className="bg-muted text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2">Purpose / Scope</th>
                    <th className="px-3 py-2">Date Granted</th>
                    <th className="px-3 py-2">Status</th>
                    <th className="px-3 py-2">Witness</th>
                    {has('PATIENT_WRITE') && <th className="px-3 py-2 text-right">Action</th>}
                  </tr>
                </thead>
                <tbody>
                  {patient.consents.map((c) => (
                    <tr key={c.id} className="border-t hover:bg-muted/40">
                      <td className="px-3 py-2.5 font-medium">{c.purpose}</td>
                      <td className="px-3 py-2.5 font-mono text-xs">{c.givenDate}</td>
                      <td className="px-3 py-2.5">
                        <StatusBadge status={!c.withdrawnDate} />
                      </td>
                      <td className="px-3 py-2.5 text-muted-foreground">{c.witnessName ?? '—'}</td>
                      {has('PATIENT_WRITE') && (
                        <td className="px-3 py-2.5 text-right">
                          {!c.withdrawnDate && (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-danger"
                              onClick={async () => {
                                if (confirm(`Withdraw consent for: ${c.purpose}?`)) {
                                  await api.post(`/patients/${id}/consents/${c.id}/withdraw`);
                                  void client.invalidateQueries({ queryKey: ['patient', id] });
                                }
                              }}
                            >
                              Withdraw
                            </Button>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab: Visits (Phase 3 empty state) */}
      {activeTab === 'visits' && (
        <EmptyState
          title="No appointments or encounters recorded"
          detail="Outpatient consultations, admission records, and doctor scheduling will arrive in Phase 3."
        />
      )}

      {/* Tab: Lab & Diagnostics (Phase 5 empty state) */}
      {activeTab === 'lab' && canViewClinical && (
        <EmptyState
          title="No laboratory specimens or reports"
          detail="Diagnostic test orders, specimen tracking, and laboratory validation workflows arrive in Phase 5."
        />
      )}

      {/* Tab: Prescriptions (Phase 6 empty state) */}
      {activeTab === 'prescriptions' && canViewClinical && (
        <EmptyState
          title="No pharmacy dispensations"
          detail="E-prescriptions, medication fulfillment, and pharmacy dispensing records arrive in Phase 6."
        />
      )}

      {/* Tab: Billing (Phase 7 empty state) */}
      {activeTab === 'billing' && (
        <EmptyState
          title="No billing invoices generated"
          detail="Invoicing, cash collection, panel claims, and payment receipts arrive in Phase 7."
        />
      )}

      {/* Upload Document Modal */}
      {showUploadModal && (
        <DocumentUploadModal
          patientId={id!}
          onClose={() => setShowUploadModal(false)}
          onSuccess={() => {
            setShowUploadModal(false);
            void client.invalidateQueries({ queryKey: ['patient', id] });
          }}
        />
      )}

      {/* Add Allergy Modal */}
      {showAllergyModal && (
        <AddAllergyModal
          patientId={id!}
          onClose={() => setShowAllergyModal(false)}
          onSuccess={() => {
            setShowAllergyModal(false);
            void client.invalidateQueries({ queryKey: ['patient', id] });
          }}
        />
      )}

      {/* Add Consent Modal */}
      {showConsentModal && (
        <AddConsentModal
          patientId={id!}
          onClose={() => setShowConsentModal(false)}
          onSuccess={() => {
            setShowConsentModal(false);
            void client.invalidateQueries({ queryKey: ['patient', id] });
          }}
        />
      )}

      {/* Merge Patient Modal */}
      {showMergeModal && (
        <MergePatientModal
          sourcePatient={patient}
          onClose={() => setShowMergeModal(false)}
          onSuccess={(targetId) => {
            setShowMergeModal(false);
            navigate(`/patients/${targetId}`);
          }}
        />
      )}

      {/* Confirm Delete Patient Dialog */}
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title="Deactivate and archive patient record?"
        detail={`This will soft-delete ${patient.fullName}'s clinical chart (${patient.mrn}). The action is permanent and recorded in the audit log.`}
        loading={deleteMutation.isPending}
        onConfirm={() => deleteMutation.mutate()}
      />
    </>
  );
}

function DocumentUploadModal({
  patientId,
  onClose,
  onSuccess,
}: {
  patientId: string;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [category, setCategory] = useState<DocumentCategory>('LAB_REPORT');
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setError('Please choose a file to upload.');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError('File size exceeds the 10 MB limit.');
      return;
    }

    try {
      setLoading(true);
      setError('');
      const formData = new FormData();
      formData.append('file', file);
      formData.append('category', category);
      if (notes.trim()) formData.append('notes', notes.trim());

      await api.post(`/patients/${patientId}/documents`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      onSuccess();
    } catch (err) {
      setError(apiMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent title="Upload Patient Clinical Document">
        <form onSubmit={handleSubmit} className="space-y-4">
          <FormField label="Document Category">
            <select
              className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
              value={category}
              onChange={(e) => setCategory(e.target.value as DocumentCategory)}
            >
              <option value="LAB_REPORT">Laboratory Report</option>
              <option value="SCAN">Radiology / Imaging Scan</option>
              <option value="REFERRAL">External Referral Letter</option>
              <option value="OTHER">Other Clinical Document</option>
            </select>
          </FormField>

          <FormField label="Select File (PDF, PNG, JPG up to 10MB)">
            <input
              type="file"
              accept=".pdf,.png,.jpg,.jpeg,.webp"
              className="w-full text-sm file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-semibold file:bg-primary file:text-primary-foreground hover:file:bg-primary/90 cursor-pointer"
              onChange={(e) => setFile(e.target.files?.[0] || null)}
            />
          </FormField>

          <FormField label="Clinical Description / Notes (Optional)">
            <Input
              placeholder="e.g. Chest X-Ray frontal view from Asiri Hospital"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </FormField>

          {error && <p className="text-sm text-danger">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={loading}>
              {loading ? 'Uploading & scanning…' : 'Upload file'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function AddAllergyModal({
  patientId,
  onClose,
  onSuccess,
}: {
  patientId: string;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [allergen, setAllergen] = useState('');
  const [reaction, setReaction] = useState('');
  const [severity, setSeverity] = useState<AllergySeverity>('MODERATE');
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!allergen.trim() || !reaction.trim()) {
      setError('Both allergen and reaction description are required.');
      return;
    }

    try {
      setLoading(true);
      setError('');
      await api.post(`/patients/${patientId}/allergies`, {
        allergen: allergen.trim(),
        reaction: reaction.trim(),
        severity,
        notes: notes.trim() || undefined,
      });
      onSuccess();
    } catch (err) {
      setError(apiMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent title="Document Adverse Allergy Reaction">
        <form onSubmit={handleSubmit} className="space-y-4">
          <FormField label="Allergen Name">
            <Input
              placeholder="e.g. Penicillin, Amoxicillin, Latex"
              value={allergen}
              onChange={(e) => setAllergen(e.target.value)}
            />
          </FormField>

          <FormField label="Reported Reaction">
            <Input
              placeholder="e.g. Anaphylaxis, Bronchospasm, Urticaria"
              value={reaction}
              onChange={(e) => setReaction(e.target.value)}
            />
          </FormField>

          <FormField label="Severity Classification">
            <select
              className="h-10 w-full rounded-md border bg-surface px-3 text-sm"
              value={severity}
              onChange={(e) => setSeverity(e.target.value as AllergySeverity)}
            >
              <option value="MILD">Mild (localized itching, mild erythema)</option>
              <option value="MODERATE">Moderate (diffuse rash, mild swelling)</option>
              <option value="SEVERE">Severe (anaphylaxis, airway compromise)</option>
            </select>
          </FormField>

          <FormField label="Clinical Observation Notes (Optional)">
            <Input
              placeholder="e.g. Observed during surgical premedication"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </FormField>

          {error && <p className="text-sm text-danger">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={loading}>
              {loading ? 'Recording…' : 'Save allergy'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function AddConsentModal({
  patientId,
  onClose,
  onSuccess,
}: {
  patientId: string;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [purpose, setPurpose] = useState('');
  const [givenDate, setGivenDate] = useState(new Date().toISOString().split('T')[0]);
  const [witnessName, setWitnessName] = useState('');
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!purpose.trim() || !givenDate) {
      setError('Consent purpose and date are required.');
      return;
    }

    try {
      setLoading(true);
      setError('');
      await api.post(`/patients/${patientId}/consents`, {
        purpose: purpose.trim(),
        givenDate,
        witnessName: witnessName.trim() || undefined,
        notes: notes.trim() || undefined,
      });
      onSuccess();
    } catch (err) {
      setError(apiMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent title="Record Informed Patient Consent">
        <form onSubmit={handleSubmit} className="space-y-4">
          <FormField label="Consent Purpose / Authorization Scope">
            <Input
              placeholder="e.g. General Surgical Consent for Laparoscopic Cholecystectomy"
              value={purpose}
              onChange={(e) => setPurpose(e.target.value)}
            />
          </FormField>

          <div className="grid grid-cols-2 gap-3">
            <FormField label="Date Given">
              <Input
                type="date"
                value={givenDate}
                onChange={(e) => setGivenDate(e.target.value)}
              />
            </FormField>

            <FormField label="Witness / Staff Name">
              <Input
                placeholder="e.g. Sister S. Silva"
                value={witnessName}
                onChange={(e) => setWitnessName(e.target.value)}
              />
            </FormField>
          </div>

          <FormField label="Additional Terms / Notes (Optional)">
            <Input
              placeholder="e.g. Signed paper consent filed under MRN-2026-000123"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </FormField>

          {error && <p className="text-sm text-danger">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={loading}>
              {loading ? 'Recording…' : 'Record consent'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function MergePatientModal({
  sourcePatient,
  onClose,
  onSuccess,
}: {
  sourcePatient: Patient;
  onClose: () => void;
  onSuccess: (targetId: string) => void;
}) {
  const [targetMrn, setTargetMrn] = useState('');
  const [reason, setReason] = useState('Duplicate patient registration consolidation');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [searchResults, setSearchResults] = useState<{ id: string; mrn: string; fullName: string }[]>([]);

  const handleSearch = async (queryText: string) => {
    setTargetMrn(queryText);
    if (queryText.trim().length >= 2) {
      try {
        const { data } = await api.get<{ id: string; mrn: string; fullName: string }[]>(
          '/patients/quick-search',
          { params: { q: queryText.trim(), limit: 5 } }
        );
        setSearchResults(data.filter((p) => p.id !== sourcePatient.id));
      } catch {
        setSearchResults([]);
      }
    } else {
      setSearchResults([]);
    }
  };

  const handleExecuteMerge = async (targetId: string) => {
    if (!confirm(`Are you certain you wish to merge ${sourcePatient.mrn} into this record? All allergies, documents and clinical files will be permanently transferred.`)) {
      return;
    }

    try {
      setLoading(true);
      setError('');
      await api.post(`/patients/${sourcePatient.id}/merge`, {
        targetPatientId: targetId,
        reason: reason.trim() || undefined,
      });
      onSuccess(targetId);
    } catch (err) {
      setError(apiMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent title="Merge Duplicate Patient Record (Admin)">
        <div className="space-y-4">
          <p className="text-xs text-muted-foreground">
            This will transfer all documents, allergies, and clinical history from{' '}
            <strong className="text-foreground">{sourcePatient.fullName} ({sourcePatient.mrn})</strong> into
            the selected target record. The source record will be archived.
          </p>

          <FormField label="Search Target Patient (by MRN, NIC, or Name)">
            <Input
              placeholder="Search target record…"
              value={targetMrn}
              onChange={(e) => void handleSearch(e.target.value)}
            />
          </FormField>

          {searchResults.length > 0 && (
            <div className="border rounded-md divide-y max-h-40 overflow-y-auto">
              {searchResults.map((res) => (
                <div
                  key={res.id}
                  className="p-2.5 flex items-center justify-between hover:bg-muted/40 text-xs"
                >
                  <div>
                    <span className="font-mono font-bold text-primary">{res.mrn}</span> —{' '}
                    <span className="font-medium">{res.fullName}</span>
                  </div>
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={loading}
                    onClick={() => void handleExecuteMerge(res.id)}
                  >
                    Merge into this
                  </Button>
                </div>
              ))}
            </div>
          )}

          <FormField label="Reason for Merge">
            <Input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Administrative merge justification"
            />
          </FormField>

          {error && <p className="text-sm text-danger">{error}</p>}

          <div className="flex justify-end gap-2 pt-2 border-t">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
