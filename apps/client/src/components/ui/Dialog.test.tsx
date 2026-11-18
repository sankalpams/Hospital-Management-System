import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Dialog } from './Dialog';

function Host() {
  const [text, setText] = useState('');
  // A fresh onClose identity on every render, as real callers produce.
  return (
    <Dialog open title="Edit" onClose={() => setText('')}>
      <input aria-label="First" />
      <input aria-label="Second" value={text} onChange={(e) => setText(e.target.value)} />
    </Dialog>
  );
}

describe('Dialog', () => {
  it('keeps focus on the field being typed in when the parent re-renders', async () => {
    render(<Host />);
    const second = screen.getByLabelText('Second');
    await userEvent.click(second);
    await userEvent.type(second, 'abc');
    expect(second).toHaveFocus();
    expect(second).toHaveValue('abc');
  });
});
