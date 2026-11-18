import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Plus } from 'lucide-react';
import { ThemeProvider } from '../../theme/ThemeProvider';
import { Avatar } from './Avatar';
import { Button, IconButton } from './Button';
import { DropdownMenu } from './DropdownMenu';
import { Sheet } from './Sheet';
import { StatusBadge } from './StatusBadge';
import { Tabs } from './Tabs';
import { ThemeToggle } from './ThemeToggle';
import { PageHeader } from './PageHeader';

describe('Avatar', () => {
  it('uses first letters of the first and last words', () => {
    render(<Avatar name="Dr. Kumari Fernando" />);
    expect(screen.getByText('DF')).toBeInTheDocument();
  });
  it('uses one letter for a single word', () => {
    render(<Avatar name="Nimal" />);
    expect(screen.getByText('N')).toBeInTheDocument();
  });
});

describe('Button', () => {
  it('is disabled and aria-busy while loading', () => {
    render(<Button loading>Save</Button>);
    const b = screen.getByRole('button', { name: /save/i });
    expect(b).toBeDisabled();
    expect(b).toHaveAttribute('aria-busy', 'true');
  });
  it('IconButton exposes its aria-label', () => {
    render(<IconButton aria-label="Add" icon={Plus} />);
    expect(screen.getByRole('button', { name: 'Add' })).toBeInTheDocument();
  });
});

describe('StatusBadge', () => {
  it('renders children', () => {
    render(<StatusBadge tone="success" dot>Active</StatusBadge>);
    expect(screen.getByText('Active')).toBeInTheDocument();
  });
});

describe('PageHeader', () => {
  it('renders one h1 plus breadcrumbs and actions', () => {
    render(
      <PageHeader title="Users" description="Manage" actions={<button>Go</button>} breadcrumbs={[{ label: 'Admin' }, { label: 'Users' }]} />,
    );
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByRole('navigation', { name: /breadcrumb/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Go' })).toBeInTheDocument();
  });
});

describe('Tabs', () => {
  it('arrow keys move selection', async () => {
    const user = userEvent.setup();
    render(
      <Tabs.Root defaultValue="a">
        <Tabs.List aria-label="Sections">
          <Tabs.Trigger value="a">A</Tabs.Trigger>
          <Tabs.Trigger value="b">B</Tabs.Trigger>
        </Tabs.List>
        <Tabs.Content value="a">Panel A</Tabs.Content>
        <Tabs.Content value="b">Panel B</Tabs.Content>
      </Tabs.Root>,
    );
    await user.click(screen.getByRole('tab', { name: 'A' }));
    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'B' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('Panel B')).toBeInTheDocument();
  });
});

describe('DropdownMenu', () => {
  it('opens on Enter and closes on Escape returning focus to the trigger', async () => {
    const user = userEvent.setup();
    render(
      <DropdownMenu.Root>
        <DropdownMenu.Trigger asChild>
          <button>Menu</button>
        </DropdownMenu.Trigger>
        <DropdownMenu.Content>
          <DropdownMenu.Item>One</DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Root>,
    );
    const trigger = screen.getByRole('button', { name: 'Menu' });
    trigger.focus();
    await user.keyboard('{Enter}');
    expect(await screen.findByRole('menuitem', { name: 'One' })).toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menuitem', { name: 'One' })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });
});

describe('ThemeToggle', () => {
  it('choosing Dark sets data-theme="dark"', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <ThemeToggle />
      </ThemeProvider>,
    );
    await user.click(screen.getByRole('button', { name: 'Theme' }));
    await user.click(await screen.findByRole('menuitemradio', { name: 'Dark' }));
    expect(document.documentElement.dataset.theme).toBe('dark');
  });
});

describe('Sheet', () => {
  function Host() {
    const [open, setOpen] = useState(false);
    return (
      <>
        <button onClick={() => setOpen(true)}>Open</button>
        <Sheet open={open} onOpenChange={setOpen} side="left" title="Navigation">
          <button>Inside</button>
        </Sheet>
      </>
    );
  }
  it('traps focus and closes on Escape', async () => {
    const user = userEvent.setup();
    render(<Host />);
    await user.click(screen.getByRole('button', { name: 'Open' }));
    const dialog = await screen.findByRole('dialog', { name: 'Navigation' });
    expect(dialog).toContainElement(document.activeElement as HTMLElement);
    await user.tab();
    await user.tab();
    expect(dialog).toContainElement(document.activeElement as HTMLElement);
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
