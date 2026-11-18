import { Link } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';

export default function ForbiddenPage() {
  return (
    <div className="flex flex-col items-center py-20 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-warning-soft text-warning">
        <ShieldAlert className="h-6 w-6" aria-hidden="true" />
      </span>
      <h1 className="mt-4 text-xl">You don’t have access to this page</h1>
      <p className="mt-1 max-w-md text-sm text-foreground-muted">
        Your role does not include the permission this page needs. If you need it for your work, ask a hospital administrator.
      </p>
      <Link to="/" className="mt-6 text-sm font-medium text-primary hover:underline">Back to dashboard</Link>
    </div>
  );
}
