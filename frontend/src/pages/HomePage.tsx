import { CalendarDays, ClipboardList, FlaskConical, Pill, ReceiptText, Stethoscope, UsersRound } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { useAuth } from '../hooks/use-auth';
const copy: Record<string, { title: string; text: string; icon: typeof CalendarDays }> = {
  ADMIN: { title: 'Operations overview', text: 'Manage staff access, review accountability records, and keep the system ready for the day.', icon: UsersRound },
  DOCTOR: { title: 'Clinical workspace', text: 'No appointments left today. Patient schedules and records will appear here in the next phase.', icon: Stethoscope },
  NURSE: { title: 'Care desk', text: 'Clinical observations and assigned work will appear here in the next phase.', icon: ClipboardList },
  RECEPTIONIST: { title: 'Front desk', text: 'No appointments left today. Patient registration and booking will appear here in the next phase.', icon: CalendarDays },
  LAB_STAFF: { title: 'Laboratory queue', text: 'No specimens are waiting right now. Results workflows will arrive in a later phase.', icon: FlaskConical },
  PHARMACIST: { title: 'Dispensary', text: 'No prescriptions are waiting right now. Dispensing workflows will arrive in a later phase.', icon: Pill },
  ACCOUNTANT: { title: 'Accounts', text: 'No invoices need attention today. Billing workflows will arrive in a later phase.', icon: ReceiptText },
};
export function HomePage() { const { user } = useAuth(); const content = copy[user?.role ?? 'RECEPTIONIST']; const Icon = content.icon; return <><PageHeader title={`Good day, ${user?.firstName}`} description="Your HMS workspace is ready." /><section className="grid max-w-2xl gap-5 border bg-surface p-6 shadow-panel sm:grid-cols-[auto_1fr]"><div className="grid h-12 w-12 place-items-center rounded-md bg-muted"><Icon className="h-6 w-6 text-primary" /></div><div><h2 className="font-semibold">{content.title}</h2><p className="mt-1 text-sm leading-6 text-muted-foreground">{content.text}</p></div></section></>; }
