import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import CardThumbnail from '../../../../components/common/card/CardThumbnail';

describe('CardThumbnail', () => {
  afterEach(cleanup);

  it('given_anImage_should_renderItInsteadOfTheIcon', () => {
    // Arrange / Act
    render(
      <CardThumbnail
        icon={<span data-testid="icon" />}
        image={{
          src: '/logo.png',
          alt: 'Logo',
        }}
      />,
    );

    // Assert
    expect(screen.getByRole('img', { name: 'Logo' })).toHaveAttribute('src', '/logo.png');
    expect(screen.queryByTestId('icon')).not.toBeInTheDocument();
  });

  it('given_noImage_should_renderTheIcon', () => {
    // Arrange / Act
    render(<CardThumbnail icon={<span data-testid="icon" />} />);

    // Assert
    expect(screen.getByTestId('icon')).toBeInTheDocument();
  });
});
