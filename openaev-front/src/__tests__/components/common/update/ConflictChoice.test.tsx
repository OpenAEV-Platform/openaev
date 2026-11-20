import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ConflictChoice from '../../../../components/common/update/ConflictChoice';
import { diffRuns } from '../../../../components/common/update/textDiffRuns';

describe('ConflictChoice', () => {
  afterEach(cleanup);

  it('offers both values and reports the side picked', () => {
    // Arrange
    const onSelect = vi.fn();
    render(
      <ConflictChoice
        title="Scan · Title"
        theirsLabel="Version v1.1.0"
        mineLabel="Yours"
        theirs="nmap -sV"
        mine="nmap -A"
        onSelect={onSelect}
      />,
    );

    // Act
    fireEvent.click(screen.getByRole('radio', { name: 'Yours' }));

    // Assert
    expect(screen.getByRole('radiogroup')).toHaveAccessibleName('Scan · Title');
    expect(screen.getByRole('radio', { name: 'Version v1.1.0' })).toBeInTheDocument();
    expect(onSelect).toHaveBeenCalledWith('mine');
  });

  it('shows the side already picked as checked', () => {
    // Arrange & Act
    render(
      <ConflictChoice
        title="Scan · Title"
        theirsLabel="Version v1.1.0"
        mineLabel="Yours"
        theirs="nmap -sV"
        mine="nmap -A"
        selected="theirs"
        onSelect={vi.fn()}
      />,
    );

    // Assert
    expect(screen.getByRole('radio', { name: 'Version v1.1.0' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Yours' })).not.toBeChecked();
  });
});

describe('diffRuns', () => {
  it('marks the words missing from the other text as changed', () => {
    // Act
    const runs = diffRuns('nmap -sV target', 'nmap -A target');

    // Assert
    expect(runs.filter(run => run.changed).map(run => run.token)).toEqual(['-sV']);
    expect(runs.map(run => run.token).join('')).toBe('nmap -sV target');
  });
});
