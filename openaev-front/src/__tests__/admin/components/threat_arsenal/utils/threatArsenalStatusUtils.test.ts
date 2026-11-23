import { createTheme, type Theme } from '@mui/material/styles';
import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  getActionStatus,
  getPayloadSourceLabel,
  getStatusColor,
  getStatusLabel,
  isShownRevoked,
} from '../../../../../admin/components/threat_arsenal/utils/threatArsenalStatusUtils';
import { anAction } from '../threatArsenalTestUtils';

const flags = vi.hoisted(() => ({ catalog: true }));

vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => flags.catalog }));

describe('threatArsenalStatusUtils', () => {
  afterEach(() => {
    flags.catalog = true;
  });

  describe('getStatusColor', () => {
    it.each(['light', 'dark'] as const)('given_the_%s_theme_should_returnTheShadeOfThatTheme', (mode) => {
      // Arrange
      const theme = createTheme({ palette: { mode } });

      // Act
      const colour = getStatusColor(theme, 'UNVERIFIED');

      // Assert
      expect(colour).toBe(theme.palette.warning.main);
    });

    it.each([
      ['VERIFIED', (theme: Theme) => theme.palette.success.main],
      ['UNVERIFIED', (theme: Theme) => theme.palette.warning.main],
      ['DEPRECATED', (theme: Theme) => theme.palette.text.disabled],
      ['REVOKED', (theme: Theme) => theme.palette.error.main],
    ])('given_%s_should_returnItsColour', (status, expected) => {
      // Arrange
      const theme = createTheme();

      // Act
      const colour = getStatusColor(theme, status);

      // Assert
      expect(colour).toBe(expected(theme));
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

  describe('isShownRevoked', () => {
    it('given a revoked payload should show it revoked', () => {
      expect(isShownRevoked(anAction({ action_payload_revoked: true }))).toBe(true);
    });

    it('given a payload that is not revoked should not show it revoked', () => {
      expect(isShownRevoked(anAction({ action_payload_revoked: false }))).toBe(false);
    });

    it('given the catalogue preview off should not show it revoked', () => {
      // Arrange
      flags.catalog = false;

      // Act
      const revoked = isShownRevoked(anAction({ action_payload_revoked: true }));

      // Assert
      expect(revoked).toBe(false);
    });
  });

  describe('getActionStatus', () => {
    it('given a revoked payload should read REVOKED whatever its status', () => {
      expect(getActionStatus(anAction({ action_payload_revoked: true }))).toBe('REVOKED');
    });

    it('given a payload that is not revoked should read its own status', () => {
      expect(getActionStatus(anAction())).toBe('VERIFIED');
    });

    it('given a status passed by the caller should read it', () => {
      expect(getActionStatus(anAction({ action_payload: undefined }), 'DEPRECATED')).toBe('DEPRECATED');
    });
  });

  describe('getPayloadSourceLabel', () => {
    it.each([
      [true, false, 'Filigran'],
      [false, true, 'Customized'],
      [false, false, 'Local'],
    ])('given managed %s and customized %s should read %s', (managed, customized, label) => {
      expect(getPayloadSourceLabel(anAction({
        action_payload_managed: managed,
        action_payload_customized: customized,
      }))).toBe(label);
    });
  });
});
