import { createTheme, ThemeProvider } from '@mui/material/styles';
import { type Theme } from '@mui/material/styles';
import { cleanup, renderHook, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it } from 'vitest';

import useThemeModeClass from '../../../utils/hooks/useThemeModeClass';

// -- TEST DATA --

const darkTheme = createTheme({ palette: { mode: 'dark' } });
const lightTheme = createTheme({ palette: { mode: 'light' } });

// -- HELPERS --

/**
 * Wrapper factory. `getTheme` is read at every render (including on
 * `rerender`), so mutating the value it returns between renders lets tests
 * simulate the app toggling theme mode.
 */
const createWrapper = (getTheme: () => Theme) =>
  function Wrapper({ children }: { children: ReactNode }) {
    return <ThemeProvider theme={getTheme()}>{children}</ThemeProvider>;
  };

// -- TESTS --

describe('useThemeModeClass', () => {
  afterEach(() => {
    cleanup();
  });

  describe('Applying the class', () => {
    it('given_darkTheme_should_applyDarkClassAndReturnDark', () => {
      // Arrange
      const container = document.createElement('div');

      // Act
      const { result } = renderHook(() => useThemeModeClass(container), { wrapper: createWrapper(() => darkTheme) });

      // Assert
      expect(result.current).toBe('dark');
      expect(container.classList.contains('dark')).toBe(true);
      expect(container.classList.contains('light')).toBe(false);
    });

    it('given_lightTheme_should_applyLightClassAndReturnLight', () => {
      // Arrange
      const container = document.createElement('div');

      // Act
      const { result } = renderHook(() => useThemeModeClass(container), { wrapper: createWrapper(() => lightTheme) });

      // Assert
      expect(result.current).toBe('light');
      expect(container.classList.contains('light')).toBe(true);
      expect(container.classList.contains('dark')).toBe(false);
    });

    it('given_nullContainer_should_notThrowAndStillReturnMode', () => {
      // Act
      const { result } = renderHook(() => useThemeModeClass(null), { wrapper: createWrapper(() => darkTheme) });

      // Assert — no container to apply a class to, but the mode is still resolved
      expect(result.current).toBe('dark');
    });
  });

  describe('Reactivity to mode changes', () => {
    it('given_themeModeChangesFromDarkToLight_should_swapClassesAndReturnValue', async () => {
      // Arrange — starts in dark mode
      const container = document.createElement('div');
      let currentTheme = darkTheme;
      const { result, rerender } = renderHook(() => useThemeModeClass(container), { wrapper: createWrapper(() => currentTheme) });
      expect(result.current).toBe('dark');
      expect(container.classList.contains('dark')).toBe(true);

      // Act — theme switches to light
      currentTheme = lightTheme;
      rerender();

      // Assert
      await waitFor(() => {
        expect(result.current).toBe('light');
      });
      expect(container.classList.contains('light')).toBe(true);
      expect(container.classList.contains('dark')).toBe(false);
    });

    it('given_themeModeChangesFromLightToDark_should_swapClassesAndReturnValue', async () => {
      // Arrange — starts in light mode
      const container = document.createElement('div');
      let currentTheme = lightTheme;
      const { result, rerender } = renderHook(() => useThemeModeClass(container), { wrapper: createWrapper(() => currentTheme) });
      expect(result.current).toBe('light');

      // Act — theme switches to dark
      currentTheme = darkTheme;
      rerender();

      // Assert
      await waitFor(() => {
        expect(result.current).toBe('dark');
      });
      expect(container.classList.contains('dark')).toBe(true);
      expect(container.classList.contains('light')).toBe(false);
    });

    it('given_containerChanges_should_removeClassFromPreviousContainerAndApplyToNewOne', () => {
      // Arrange
      const firstContainer = document.createElement('div');
      let currentContainer: HTMLElement | null = firstContainer;
      const { rerender } = renderHook(() => useThemeModeClass(currentContainer), { wrapper: createWrapper(() => darkTheme) });
      expect(firstContainer.classList.contains('dark')).toBe(true);

      // Act — hook is now given a different container
      const secondContainer = document.createElement('div');
      currentContainer = secondContainer;
      rerender();

      // Assert — previous container is cleaned up, new one carries the class
      expect(firstContainer.classList.contains('dark')).toBe(false);
      expect(secondContainer.classList.contains('dark')).toBe(true);
    });
  });

  describe('Non-clobbering and scoping', () => {
    it('given_containerHasPreexistingClasses_should_preserveThemAndOnlyToggleModeClass', () => {
      // Arrange — container already used by other code (e.g. layout classes)
      const container = document.createElement('div');
      container.classList.add('some-other-widget-class', 'another-class');

      // Act
      renderHook(() => useThemeModeClass(container), { wrapper: createWrapper(() => darkTheme) });

      // Assert — pre-existing classes survive, only the mode class was added
      expect(container.classList.contains('some-other-widget-class')).toBe(true);
      expect(container.classList.contains('another-class')).toBe(true);
      expect(container.classList.contains('dark')).toBe(true);
    });

    it('given_hookApplied_should_neverTouchDocumentBodyOrDocumentElement', () => {
      // Arrange
      const container = document.createElement('div');
      const bodyClassesBefore = document.body.className;
      const htmlClassesBefore = document.documentElement.className;

      // Act
      renderHook(() => useThemeModeClass(container), { wrapper: createWrapper(() => darkTheme) });

      // Assert — strictly scoped to the given container
      expect(document.body.className).toBe(bodyClassesBefore);
      expect(document.documentElement.className).toBe(htmlClassesBefore);
    });

    it('given_hookUnmounts_should_removeModeClassFromContainer', () => {
      // Arrange
      const container = document.createElement('div');
      const { unmount } = renderHook(() => useThemeModeClass(container), { wrapper: createWrapper(() => darkTheme) });
      expect(container.classList.contains('dark')).toBe(true);

      // Act
      unmount();

      // Assert — no dangling class left on a container that outlives the hook
      expect(container.classList.contains('dark')).toBe(false);
      expect(container.classList.contains('light')).toBe(false);
    });
  });
});
