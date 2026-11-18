import { useAuth } from '../auth/useAuth';
import { Badge } from '../components/ui/Badge';
import { Card } from '../components/ui/Card';
import { usePublicSettings } from '../lib/usePublicSettings';
import { roleLabel } from '../layout/TopBar';

export function HomePage() {
  const { user } = useAuth();
  const { data: settings } = usePublicSettings();
  if (!user) return null;
  return (
    <Card className="max-w-xl">
      <h1 className="text-2xl font-semibold text-text">Welcome, {user.fullName}</h1>
      <p className="mt-2 flex items-center gap-2 text-sm text-muted">
        <Badge tone="info">{roleLabel(user.role)}</Badge>
        <span>{settings?.hospitalName ?? ''}</span>
      </p>
    </Card>
  );
}
