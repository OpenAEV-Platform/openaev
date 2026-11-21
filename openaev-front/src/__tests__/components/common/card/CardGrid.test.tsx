import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import CardGrid from '../../../../components/common/card/CardGrid';

const CARD = 'card';

describe('CardGrid', () => {
  afterEach(cleanup);

  it('should lay its children out in a grid, with the given style on top', () => {
    // Arrange + Act
    const { container } = render(
      <CardGrid style={{ marginTop: '8px' }}>
        <span>{CARD}</span>
      </CardGrid>,
    );

    // Assert
    expect(screen.getByText(CARD)).toBeInTheDocument();
    expect(container.firstElementChild).toHaveStyle({
      display: 'grid',
      marginTop: '8px',
    });
    expect(container.querySelector('.MuiSkeleton-root')).toBeNull();
  });

  it('should render placeholders instead of its children while loading', () => {
    // Arrange + Act
    const { container } = render(
      <CardGrid loading>
        <span>{CARD}</span>
      </CardGrid>,
    );

    // Assert
    expect(screen.queryByText(CARD)).toBeNull();
    expect(container.querySelectorAll('.MuiSkeleton-root')).toHaveLength(8);
  });
});
