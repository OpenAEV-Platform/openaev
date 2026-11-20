import '@testing-library/jest-dom/vitest';

import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import FindingCardView from '../../../../../admin/components/findings/components/FindingCardView';
import { aFinding, renderInApp } from '../findingTestUtils';

// src/components holds an empty, tracked Empty.jsx that Vite resolves before Empty.tsx: stand in for the empty state.
vi.mock('../../../../../components/Empty', async () => {
  const { createElement } = await import('react');
  return { default: ({ message }: { message: string }) => createElement('p', null, message) };
});

describe('FindingCardView', () => {
  afterEach(cleanup);

  it('should render one card per finding', () => {
    // Arrange + Act
    renderInApp(<FindingCardView loading={false} findings={[aFinding({ finding_id: 'f-1' }), aFinding({ finding_id: 'f-2' })]} />);

    // Assert
    expect(screen.getAllByTestId('finding-card')).toHaveLength(2);
    expect(screen.queryByText('No finding found.')).toBeNull();
  });

  it('should render placeholders instead of the cards, and no empty message, while loading', () => {
    // Arrange + Act
    const { container } = renderInApp(<FindingCardView loading findings={[]} />);

    // Assert
    expect(container.querySelectorAll('.MuiSkeleton-root').length).toBeGreaterThan(0);
    expect(screen.queryAllByTestId('finding-card')).toHaveLength(0);
    expect(screen.queryByText('No finding found.')).toBeNull();
  });

  it('should say so when there is no finding', () => {
    // Arrange + Act
    renderInApp(<FindingCardView loading={false} findings={[]} />);

    // Assert
    expect(screen.getByText('No finding found.')).toBeInTheDocument();
  });
});
