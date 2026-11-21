import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import CardThumbnail from '../../../../components/common/card/CardThumbnail';

describe('CardThumbnail', () => {
  afterEach(cleanup);

  it('should show an icon when it has no image', () => {
    // Arrange + Act
    render(<CardThumbnail icon={<svg data-testid="glyph" />} />);

    // Assert
    expect(screen.getByTestId('glyph')).toBeInTheDocument();
    expect(screen.queryByRole('img')).toBeNull();
  });

  it('should show the image, rather than the icon, when it has both', () => {
    // Arrange + Act
    render(
      <CardThumbnail
        image={{
          src: '/logo.png',
          alt: 'Logo',
        }}
        icon={<svg data-testid="glyph" />}
      />,
    );

    // Assert
    expect(screen.getByRole('img', { name: 'Logo' })).toHaveAttribute('src', '/logo.png');
    expect(screen.queryByTestId('glyph')).toBeNull();
  });
});
