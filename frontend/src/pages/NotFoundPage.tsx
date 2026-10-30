import { Link } from 'react-router-dom';
import { EmptyState } from '../components/EmptyState';
export function NotFoundPage() { return <main className="grid min-h-screen place-items-center p-6"><EmptyState title="This page isn’t here" detail="The link may be out of date, or you may not have access." action={<Link className="text-sm text-primary hover:underline" to="/">Go to your home page</Link>} /></main>; }
export function ForbiddenPage() { return <EmptyState title="You don’t have access to this area" detail="Ask an administrator if you think this is a mistake." action={<Link className="text-sm text-primary hover:underline" to="/">Go to your home page</Link>} />; }
