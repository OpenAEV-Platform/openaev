import '@testing-library/jest-dom/vitest';

import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ComponentProps } from 'react';
import { afterEach, describe, expect, it } from 'vitest';

import CardGrid from '../../../../components/common/card/CardGrid';

const theme = createTheme();

const renderGrid = (props: ComponentProps<typeof CardGrid> = {}) => render(
  <ThemeProvider theme={theme}>
    <CardGrid {...props} />
  </ThemeProvider>,
);

describe('CardGrid', () => {
  afterEach(cleanup);

  it('given_loading_should_renderPlaceholdersInsteadOfChildren', () => {
    // Arrange / Act
    const { container } = renderGrid({
      loading: true,
      children: <div data-testid="card" />,
    });

    // Assert
    expect(screen.queryByTestId('card')).not.toBeInTheDocument();
    expect(container.querySelectorAll('.MuiSkeleton-root')).toHaveLength(8);
  });

  it('given_notLoading_should_renderChildren', () => {
    // Arrange / Act
    const { container } = renderGrid({ children: [<div key="1" data-testid="card" />, <div key="2" data-testid="card" />] });

    // Assert
    expect(screen.getAllByTestId('card')).toHaveLength(2);
    expect(container.querySelectorAll('.MuiSkeleton-root')).toHaveLength(0);
  });

  // jsdom does no layout: the guard is on the declared track sizing, which is
  // what caps the grid at 4 columns of at least 280px.
  it('given_anyContent_should_declareAtMostFourColumnsOfAtLeast280px', () => {
    // Arrange / Act
    const { container } = renderGrid();

    // Assert
    const grid = container.firstElementChild as HTMLElement;
    expect(grid.style.gridTemplateColumns).toContain('280px');
    expect(grid.style.gridTemplateColumns).toContain('/ 4');
  });

  it('given_style_should_mergeItIntoTheGrid', () => {
    // Arrange / Act
    const { container } = renderGrid({ style: { marginTop: 16 } });

    // Assert
    const grid = container.firstElementChild as HTMLElement;
    expect(grid.style.marginTop).toBe('16px');
    expect(grid.style.display).toBe('grid');
  });
});
