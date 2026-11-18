import { Link } from 'react-router-dom';
import { EmptyState } from '../components/ui/EmptyState';

export function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center p-4">
      <EmptyState
        title="Page not found"
        description="The page you are looking for does not exist."
        action={
          <Link to="/" className="text-sm font-medium text-primary underline">
            Go to home
          </Link>
        }
      />
    </div>
  );
}
