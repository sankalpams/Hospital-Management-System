import { Link } from 'react-router-dom';
import { EmptyState } from '../components/ui/EmptyState';

export function ForbiddenPage() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center p-4">
      <EmptyState
        title="You do not have access to this page"
        description="Ask an administrator if you need access."
        action={
          <Link to="/" className="text-sm font-medium text-primary underline">
            Go to home
          </Link>
        }
      />
    </div>
  );
}
