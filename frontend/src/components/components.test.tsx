import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { StatusBadge } from './StatusBadge';
import { MoneyText } from './MoneyText';
import { DateTimeText } from './DateTimeText';
import { PatientBanner } from './PatientBanner';
import { EmptyState } from './EmptyState';
import { DataTable } from './DataTable';
import { FormField } from './FormField';
import { ConfirmDialog } from './ConfirmDialog';
import { type ColumnDef } from '@tanstack/react-table';

describe('Reusable Clinical Components', () => {
  it('StatusBadge renders semantic status text and formatting', () => {
    const { rerender } = render(<StatusBadge status={true} />);
    expect(screen.getByText('Active')).toBeInTheDocument();

    rerender(<StatusBadge status={false} />);
    expect(screen.getByText('Inactive')).toBeInTheDocument();

    rerender(<StatusBadge status="RATE_LIMITED" />);
    expect(screen.getByText('RATE LIMITED')).toBeInTheDocument();
  });

  it('MoneyText renders LKR formatted currency with tabular numbers', () => {
    render(<MoneyText value={25000.5} />);
    const el = screen.getByText(/LKR/);
    expect(el).toHaveClass('tabular-nums');
    expect(el.textContent).toMatch(/25,000\.50/);
  });

  it('DateTimeText renders time element with ISO value and formatted text', () => {
    const isoString = '2026-10-31T05:30:00Z';
    render(<DateTimeText value={isoString} />);
    const el = screen.getByRole('time');
    expect(el).toHaveAttribute('dateTime', isoString);
    expect(el).toHaveClass('tabular-nums');
  });

  it('PatientBanner displays patient demographics and highlights allergies', () => {
    render(
      <PatientBanner
        name="Sunil Perera"
        ageSex="45y / Male"
        mrn="MRN-2026-001"
        allergies={['Penicillin', 'Sulfa']}
      />
    );

    expect(screen.getByText('Sunil Perera')).toBeInTheDocument();
    expect(screen.getByText('45y / Male')).toBeInTheDocument();
    expect(screen.getByText('MRN MRN-2026-001')).toBeInTheDocument();
    expect(screen.getByText(/Penicillin, Sulfa/)).toBeInTheDocument();
  });

  it('EmptyState renders title, detail text, and action button', () => {
    render(
      <EmptyState
        title="No appointments today"
        detail="The doctor has completed all consultations for this shift."
        action={<button>Add patient</button>}
      />
    );

    expect(screen.getByText('No appointments today')).toBeInTheDocument();
    expect(screen.getByText('The doctor has completed all consultations for this shift.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add patient' })).toBeInTheDocument();
  });

  it('DataTable renders table rows when data is provided and EmptyState when empty', () => {
    interface TestRow {
      id: string;
      name: string;
    }

    const columns: ColumnDef<TestRow>[] = [
      { accessorKey: 'id', header: 'ID' },
      { accessorKey: 'name', header: 'Name' },
    ];

    const data: TestRow[] = [
      { id: '1', name: 'Dr. Nimal' },
      { id: '2', name: 'Dr. Silva' },
    ];

    const { rerender } = render(<DataTable data={data} columns={columns} />);
    expect(screen.getByText('Dr. Nimal')).toBeInTheDocument();
    expect(screen.getByText('Dr. Silva')).toBeInTheDocument();

    rerender(
      <DataTable
        data={[]}
        columns={columns}
        emptyTitle="No physicians"
        emptyDetail="No doctors registered."
      />
    );
    expect(screen.getByText('No physicians')).toBeInTheDocument();
  });

  it('FormField renders label, input, and validation error', () => {
    render(
      <FormField label="National Identity Card (NIC)" error="NIC format is invalid">
        <input id="nic" placeholder="199012345678" />
      </FormField>
    );

    expect(screen.getByText('National Identity Card (NIC)')).toBeInTheDocument();
    expect(screen.getByText('NIC format is invalid')).toBeInTheDocument();
  });

  it('ConfirmDialog opens and triggers onConfirm on button click', () => {
    const handleConfirm = vi.fn();
    const handleOpenChange = vi.fn();

    render(
      <ConfirmDialog
        open={true}
        onOpenChange={handleOpenChange}
        title="Void Invoice #1024?"
        detail="This action is permanent and cannot be reversed."
        onConfirm={handleConfirm}
      />
    );

    expect(screen.getByText('Void Invoice #1024?')).toBeInTheDocument();
    expect(screen.getByText('This action is permanent and cannot be reversed.')).toBeInTheDocument();

    const confirmButton = screen.getByRole('button', { name: 'Confirm' });
    fireEvent.click(confirmButton);
    expect(handleConfirm).toHaveBeenCalledTimes(1);

    const cancelButton = screen.getByRole('button', { name: 'Cancel' });
    fireEvent.click(cancelButton);
    expect(handleOpenChange).toHaveBeenCalledWith(false);
  });
});
