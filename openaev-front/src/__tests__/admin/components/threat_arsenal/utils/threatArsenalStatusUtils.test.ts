import { createTheme } from '@mui/material/styles';
import { describe, expect, it } from 'vitest';

import { getStatusColor, getStatusLabel } from '../../../../../admin/components/threat_arsenal/utils/threatArsenalStatusUtils';

describe('threatArsenalStatusUtils', () => {
  describe('getStatusColor', () => {
    it.each(['light', 'dark'] as const)('given_the_%s_theme_should_returnTheShadeOfThatTheme', (mode) => {
      // Arrange
      const theme = createTheme({ palette: { mode } });

      // Act
      const colour = getStatusColor(theme, 'UNVERIFIED');

      // Assert
      expect(colour).toBe(theme.palette.warning.main);
    });

    it.each([undefined, null, 'UNKNOWN'])('given_%s_should_returnTheDisabledColour', (status) => {
      // Arrange
      const theme = createTheme();

      // Act
      const colour = getStatusColor(theme, status);

      // Assert
      expect(colour).toBe(theme.palette.text.disabled);
    });
  });

  describe('getStatusLabel', () => {
    it('given_aKnownStatus_should_returnItsLabel', () => {
      // Act
      const label = getStatusLabel('REVOKED');

      // Assert
      expect(label).toBe('Revoked');
    });

    it.each([undefined, null, '', 'UNKNOWN'])('given_%s_should_returnNull', (status) => {
      // Act
      const label = getStatusLabel(status);

      // Assert
      expect(label).toBeNull();
    });
  });
});
