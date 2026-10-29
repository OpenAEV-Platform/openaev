import { describe, expect, it } from 'vitest';

import { type ThemeInput } from '../../utils/api-types';
import { FORM_HEX_COLOR_REGEX, sanitizeThemeColors } from '../../utils/Colors';

describe('Colors utils', () => {
  describe('Function: sanitizeThemeColors()', () => {
    it('given no theme, should return undefined', () => {
      expect(sanitizeThemeColors(undefined)).toBeUndefined();
      expect(sanitizeThemeColors(null)).toBeUndefined();
    });

    it('given valid hex colours, should keep them', () => {
      const theme: ThemeInput = {
        background_color: '#AABBCC',
        primary_color: '#fff',
        login_aside_gradient_start: '#00112233',
      };
      expect(sanitizeThemeColors(theme)).toEqual(expect.objectContaining(theme));
    });

    it('given a value that is not a colour, should drop it for every colour field', () => {
      const theme: ThemeInput = {
        background_color: 'notacolor',
        paper_color: 'red',
        navigation_color: 'rgb(1,2,3)',
        primary_color: '#12',
        secondary_color: '',
        accent_color: 'var(--x)',
        text_color: '#GGGGGG',
        login_aside_color: 'nope',
        login_aside_gradient_start: 'nope',
        login_aside_gradient_end: 'nope',
      };
      const result = sanitizeThemeColors(theme);
      Object.keys(theme).forEach(field => expect(result?.[field as keyof ThemeInput]).toBeUndefined());
    });

    it('given non-colour fields, should leave them untouched', () => {
      const theme: ThemeInput = {
        logo_url: 'notacolor',
        logo_url_collapsed: 'true',
        logo_login_url: 'https://example.com/logo.png',
        login_aside_image: 'https://example.com/aside.png',
        primary_color: 'notacolor',
      };
      const result = sanitizeThemeColors(theme);
      expect(result?.logo_url).toBe('notacolor');
      expect(result?.logo_url_collapsed).toBe('true');
      expect(result?.logo_login_url).toBe('https://example.com/logo.png');
      expect(result?.login_aside_image).toBe('https://example.com/aside.png');
    });

    it('given a theme, should not mutate it', () => {
      const theme: ThemeInput = { primary_color: 'notacolor' };
      sanitizeThemeColors(theme);
      expect(theme.primary_color).toBe('notacolor');
    });
  });

  describe('FORM_HEX_COLOR_REGEX', () => {
    it('should only accept #RRGGBB', () => {
      expect(FORM_HEX_COLOR_REGEX.test('#4CAF50')).toBe(true);
      expect(FORM_HEX_COLOR_REGEX.test('#4caf50')).toBe(true);
      expect(FORM_HEX_COLOR_REGEX.test('#fff')).toBe(false);
      expect(FORM_HEX_COLOR_REGEX.test('notacolor')).toBe(false);
      expect(FORM_HEX_COLOR_REGEX.test('')).toBe(false);
    });
  });
});
