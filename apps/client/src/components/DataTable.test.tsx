import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { DataTable } from './DataTable';
import { Pagination } from './Pagination';

interface Row {
  id: string;
  name: string;
}
const columns = [{ key: 'name', header: 'Name', cell: (r: Row) => r.name }];

describe('DataTable', () => {
  it('renders a captioned table with column headers', () => {
    render(
      <DataTable caption="People" columns={columns} rows={[{ id: '1', name: 'Ann' }]} getRowKey={(r) => r.id} />,
    );
    expect(screen.getByRole('table', { name: 'People' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Name' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Ann' })).toBeInTheDocument();
  });

  it('is busy while loading and announces it', () => {
    render(<DataTable caption="People" columns={columns} rows={[]} loading getRowKey={(r: Row) => r.id} />);
    expect(screen.getByRole('table')).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('status')).toHaveTextContent('Loading');
  });

  it('shows the empty message', () => {
    render(
      <DataTable caption="People" columns={columns} rows={[]} emptyMessage="Nobody here" getRowKey={(r: Row) => r.id} />,
    );
    expect(screen.getByText('Nobody here')).toBeInTheDocument();
  });
});

describe('Pagination', () => {
  it('shows the range and pages', async () => {
    const onChange = vi.fn();
    render(<Pagination page={2} pageSize={10} total={35} onChange={onChange} />);
    expect(screen.getByText('Showing 11-20 of 35')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(onChange).toHaveBeenCalledWith(3);
    await userEvent.click(screen.getByRole('button', { name: 'Previous page' }));
    expect(onChange).toHaveBeenCalledWith(1);
  });

  it('disables the ends', () => {
    render(<Pagination page={1} pageSize={10} total={5} onChange={() => {}} />);
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
  });
});
