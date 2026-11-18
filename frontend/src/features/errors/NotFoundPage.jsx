import { Link } from 'react-router-dom';
import { SearchX } from 'lucide-react';

export default function NotFoundPage() {
  return (
    <div className="flex flex-col items-center py-20 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-neutral-soft text-neutral">
        <SearchX className="h-6 w-6" aria-hidden="true" />
      </span>
      <h1 className="mt-4 text-xl">Page not found</h1>
      <p className="mt-1 max-w-md text-sm text-foreground-muted">The address may be mistyped, or the page may have moved.</p>
      <Link to="/" className="mt-6 text-sm font-medium text-primary hover:underline">Back to dashboard</Link>
    </div>
  );
}
