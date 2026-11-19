import { Link } from 'react-router-dom';
import { BedDouble, LogIn } from 'lucide-react';
import { buttonClasses } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';

// Synthetic demo content, labelled as such on the page.
const BEDS = [
  { id: 'ICU1-A', patient: 'Tharindu Abeysekera', mrn: 'HMS-2025-000233' },
  { id: 'ICU1-B', patient: 'Kumari Mendis', mrn: 'HMS-2025-000234', arriving: true },
  { id: 'ICU1-C' },
  { id: 'ICU1-D', patient: 'Sanduni Dissanayake', mrn: 'HMS-2022-000005' },
];

const JOURNEY = [
  { step: 'Book', who: 'Receptionist', text: 'Picks a free slot in the doctor’s schedule, or adds a walk-in.' },
  { step: 'Check in', who: 'Receptionist', text: 'The patient joins the doctor’s waiting list.' },
  { step: 'Consult', who: 'Doctor', text: 'Notes, diagnosis, vitals and a prescription checked against allergies.' },
  { step: 'Test', who: 'Lab staff', text: 'Samples get a barcode; results are flagged against reference ranges.' },
  { step: 'Dispense', who: 'Pharmacist', text: 'Medicines leave from the batch that expires first.' },
  { step: 'Pay', who: 'Accountant', text: 'Every charge from the visit is already on the invoice.' },
];

const ROLES = [
  ['Super Admin', 'Users, roles and the audit log'],
  ['Hospital Admin', 'Departments, doctors, wards and reports'],
  ['Doctor', 'Own appointments, consultations and records'],
  ['Nurse', 'Bed board, admissions and vitals'],
  ['Receptionist', 'Registration, booking and check-in'],
  ['Lab Staff', 'Worklist, samples and results'],
  ['Pharmacist', 'Dispensing, batches and stock alerts'],
  ['Accountant', 'Invoices, payments and refunds'],
  ['HR Manager', 'Staff, attendance and leave'],
];

function Mark() {
  return (
    <svg viewBox="0 0 32 32" className="h-8 w-8 shrink-0" aria-hidden="true">
      <rect width="32" height="32" rx="8" className="fill-sand-light" />
      <path d="M13 7h6v6h6v6h-6v6h-6v-6H7v-6h6z" className="fill-primary" />
    </svg>
  );
}

function WardBoard() {
  return (
    <figure className="landing-board rounded-xl bg-background p-5 text-foreground shadow-lg sm:p-6">
      <figcaption className="flex items-baseline justify-between gap-4">
        <span className="font-heading text-lg font-semibold">Intensive Care Unit</span>
        <span className="text-sm text-foreground-muted">
          <span className="landing-count tabular-nums" /> of 4 occupied
        </span>
      </figcaption>
      <p className="mt-0.5 text-xs text-foreground-subtle">Room ICU1 · Floor 1</p>
      <ul className="mt-4 grid grid-cols-2 gap-3">
        {BEDS.map((bed) => (
          <li
            key={bed.id}
            className={`rounded-lg border border-border bg-surface p-3 ${bed.arriving ? 'landing-arriving' : ''}`}
          >
            <span className="flex items-center gap-1.5 text-sm font-medium">
              <BedDouble className="h-4 w-4 text-foreground-subtle" aria-hidden="true" />
              {bed.id}
            </span>
            {bed.patient ? (
              <>
                <span className="landing-status mt-2 block">
                  <Badge tone="primary">Occupied</Badge>
                </span>
                <span className="landing-patient mt-2 block">
                  <span className="block truncate text-sm">{bed.patient}</span>
                  <span className="block text-xs tabular-nums text-foreground-subtle">{bed.mrn}</span>
                </span>
              </>
            ) : (
              <>
                <span className="mt-2 block">
                  <Badge tone="success">Available</Badge>
                </span>
                <span className="mt-2 block text-xs text-foreground-subtle">No patient</span>
              </>
            )}
          </li>
        ))}
      </ul>
      <p className="mt-4 text-xs text-foreground-subtle">Demo data. Patients shown here are invented.</p>
    </figure>
  );
}

export default function LandingPage() {
  const signIn = (variant, size = 'lg') => (
    <Link to="/login" className={buttonClasses({ variant, size })}>
      <LogIn className="h-4 w-4" aria-hidden="true" />
      Sign in
    </Link>
  );

  return (
    <div className="landing min-h-screen bg-background text-foreground">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-10 focus:rounded-md focus:bg-surface focus:px-3 focus:py-2">
        Skip to content
      </a>

      <div className="bg-sidebar text-sidebar-foreground">
        <header className="mx-auto flex max-w-6xl items-center justify-between px-4 py-5 sm:px-8">
          <span className="flex items-center gap-2.5">
            <Mark />
            <span className="font-heading text-base font-semibold text-white">Prabodha Hospital</span>
          </span>
          <Link to="/login" className={buttonClasses({ variant: 'inverse', size: 'md' })}>
            Sign in
          </Link>
        </header>

        <section className="mx-auto grid max-w-6xl items-center gap-12 px-4 pb-20 pt-10 sm:px-8 lg:grid-cols-[1.1fr_1fr] lg:pb-28 lg:pt-16">
          <div>
            <h1 className="max-w-xl text-balance font-heading text-4xl font-semibold leading-[1.1] tracking-tight text-white sm:text-5xl">
              Every bed, booking and bill at Prabodha Hospital, in one system.
            </h1>
            <p className="mt-6 max-w-lg text-lg leading-relaxed">
              Front desk, clinic, wards, laboratory, pharmacy, billing and staff share the same records, so a patient
              is registered once and every department sees the same visit.
            </p>
            <div className="mt-9 flex flex-wrap items-center gap-x-6 gap-y-4">
              {signIn('inverse')}
              <a href="#journey" className="text-sm font-medium text-white underline decoration-accent underline-offset-4 hover:decoration-2">
                Follow one patient’s visit
              </a>
            </div>
          </div>
          <WardBoard />
        </section>
      </div>

      <main id="main">
        <section id="journey" className="mx-auto max-w-6xl scroll-mt-6 px-4 py-20 sm:px-8 lg:py-28">
          <h2 className="max-w-2xl font-heading text-3xl font-semibold tracking-tight">One visit, six desks, no paper between them</h2>
          <p className="mt-4 max-w-2xl text-foreground-muted">
            Each step opens where the last one finished. Nobody types the patient in twice, and the bill builds itself as
            the visit goes.
          </p>
          <ol className="mt-12 grid gap-x-8 gap-y-10 sm:grid-cols-2 lg:grid-cols-3">
            {JOURNEY.map(({ step, who, text }, i) => (
              <li key={step} className="border-t-2 border-border-strong pt-5">
                <span className="font-heading text-sm font-semibold tabular-nums text-primary">Step {i + 1}</span>
                <h3 className="mt-1 font-heading text-xl font-semibold">{step}</h3>
                <p className="mt-1 text-sm font-medium text-foreground-subtle">{who}</p>
                <p className="mt-3 max-w-xs text-foreground-muted">{text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="bg-surface-muted">
          <div className="mx-auto grid max-w-6xl gap-12 px-4 py-20 sm:px-8 lg:grid-cols-2 lg:py-28">
            <div>
              <h2 className="font-heading text-3xl font-semibold tracking-tight">Two receptionists, one free slot, one booking</h2>
              <p className="mt-4 max-w-lg text-foreground-muted">
                The database itself refuses a second booking for the same doctor and time, and a second patient in the
                same bed. It holds even when two people press the button at the same moment.
              </p>
              <ul className="mt-8 flex max-w-lg flex-col gap-4 text-foreground-muted">
                <li><span className="font-medium text-foreground">Stock always adds up.</span> Every dispense, return and adjustment is a ledger line.</li>
                <li><span className="font-medium text-foreground">Records stay private.</span> Each role sees only its own work; opening another record asks for a reason, and it is logged.</li>
                <li><span className="font-medium text-foreground">Every change is audited.</span> Who, what and when, for every write.</li>
              </ul>
            </div>
            <figure className="self-center rounded-xl bg-surface p-5 shadow-md sm:p-6">
              <figcaption className="text-sm text-foreground-muted">Dr. Rizwan Abeysekera · Tue 10:30</figcaption>
              <ul className="mt-4 flex flex-col gap-3">
                <li className="flex items-center justify-between gap-3 rounded-lg border border-border p-3">
                  <span><span className="block text-sm font-medium">Front desk 1</span><span className="block text-xs text-foreground-subtle">10:30:04.112</span></span>
                  <Badge tone="success">Booked</Badge>
                </li>
                <li className="flex items-center justify-between gap-3 rounded-lg border border-border p-3">
                  <span><span className="block text-sm font-medium">Front desk 2</span><span className="block text-xs text-foreground-subtle">10:30:04.118</span></span>
                  <Badge tone="warning">Slot already taken</Badge>
                </li>
              </ul>
              <p className="mt-4 text-xs text-foreground-subtle">Illustration. The second desk is offered the next free slot.</p>
            </figure>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-4 py-20 sm:px-8 lg:py-28">
          <h2 className="font-heading text-3xl font-semibold tracking-tight">Nine roles, each with its own view</h2>
          <p className="mt-4 max-w-2xl text-foreground-muted">
            The server checks every permission, so a screen a role can’t use isn’t just hidden, it’s refused.
          </p>
          <dl className="mt-10 grid gap-x-10 sm:grid-cols-2 lg:grid-cols-3">
            {ROLES.map(([role, what]) => (
              <div key={role} className="border-b border-border py-4">
                <dt className="font-heading font-semibold">{role}</dt>
                <dd className="mt-0.5 text-sm text-foreground-muted">{what}</dd>
              </div>
            ))}
          </dl>
        </section>
      </main>

      <footer className="bg-sand">
        <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-6 px-4 py-14 sm:flex-row sm:items-center sm:px-8">
          <div>
            <p className="font-heading text-2xl font-semibold text-primary-soft-foreground">See it with real screens</p>
            <p className="mt-1 max-w-md text-sm text-neutral">
              A demonstration system. Every patient, invoice and record in it is invented.
            </p>
          </div>
          {signIn('primary')}
        </div>
      </footer>
    </div>
  );
}
