import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ComponentProps } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import EntityCard from '../../../../components/common/card/EntityCard';

const theme = createTheme();

const renderCard = (props: Partial<ComponentProps<typeof EntityCard>> = {}) => render(
  <ThemeProvider theme={theme}>
    <TooltipProvider>
      <MemoryRouter>
        <EntityCard
          testId="entity-card"
          icon={<span />}
          title="Card title"
          {...props}
        />
      </MemoryRouter>
    </TooltipProvider>
  </ThemeProvider>,
);

describe('EntityCard', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  describe('action area', () => {
    it('given_to_should_renderARealLink', () => {
      // Arrange / Act
      renderCard({ to: '/admin/reporting/1' });

      // Assert
      expect(screen.getByRole('link')).toHaveAttribute('href', '/admin/reporting/1');
    });

    it('given_onClick_should_callItWithoutRenderingALink', () => {
      // Arrange
      const onClick = vi.fn();
      renderCard({ onClick });

      // Act
      fireEvent.click(screen.getByRole('button'));

      // Assert
      expect(onClick).toHaveBeenCalledTimes(1);
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });

    it('given_neitherToNorOnClick_should_renderAPlainSurface', () => {
      // Arrange / Act
      renderCard();

      // Assert
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
      expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });
  });

  describe('slots', () => {
    it('given_textSlots_should_renderThem', () => {
      // Arrange / Act
      renderCard({
        overline: 'Collector',
        description: 'Card description',
        meta: {
          label: 'Updated:',
          value: 'today',
        },
      });

      // Assert
      expect(screen.getByTestId('entity-card')).toBeInTheDocument();
      expect(screen.getByText('Collector')).toBeInTheDocument();
      expect(screen.getByText('Card title')).toBeInTheDocument();
      expect(screen.getByText('Card description')).toBeInTheDocument();
      expect(screen.getByText('Updated:')).toBeInTheDocument();
      expect(screen.getByText('today')).toBeInTheDocument();
    });

    it('given_image_should_renderItInsteadOfTheIcon', () => {
      // Arrange / Act
      renderCard({
        icon: <span data-testid="icon" />,
        image: {
          src: '/logo.png',
          alt: 'Logo',
        },
      });

      // Assert
      expect(screen.getByRole('img', { name: 'Logo' })).toHaveAttribute('src', '/logo.png');
      expect(screen.queryByTestId('icon')).not.toBeInTheDocument();
    });

    // The menu opens dialogs in a portal: inside the link, React would bubble
    // their clicks up to the card and navigate.
    it('given_menu_should_renderItOutsideTheLink', () => {
      // Arrange / Act
      renderCard({
        to: '/admin/reporting/1',
        menu: <button type="button" aria-label="More actions" />,
      });

      // Assert
      const menu = screen.getByRole('button', { name: 'More actions' });
      expect(screen.getByRole('link')).not.toContainElement(menu);
    });

    it('given_footerEnd_should_renderItInTheFooter', () => {
      // Arrange / Act
      renderCard({ footerEnd: <button type="button" aria-label="Deploy" /> });

      // Assert
      expect(screen.getByRole('button', { name: 'Deploy' })).toBeInTheDocument();
    });

    it('given_noFooter_should_dropTheFooterSlots', () => {
      // Arrange / Act
      renderCard({
        footer: false,
        footerStart: <span data-testid="footer-start" />,
      });

      // Assert
      expect(screen.queryByTestId('footer-start')).not.toBeInTheDocument();
    });
  });

  describe('selection', () => {
    it('given_selection_should_toggleWithoutTriggeringTheCard', () => {
      // Arrange
      const onClick = vi.fn();
      const onToggle = vi.fn();
      renderCard({
        onClick,
        selection: {
          checked: false,
          alwaysVisible: false,
          onToggle,
          label: 'Card title',
        },
      });

      // Act
      fireEvent.click(screen.getByRole('checkbox', { name: 'Card title' }));

      // Assert
      expect(onToggle).toHaveBeenCalledTimes(1);
      expect(onClick).not.toHaveBeenCalled();
    });
  });
});
