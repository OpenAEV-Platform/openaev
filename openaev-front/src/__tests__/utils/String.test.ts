import { describe, expect, it } from 'vitest';

import { sanitizeHtml } from '../../utils/String';

describe('String utils', () => {
  describe('Function: sanitizeHtml()', () => {
    it('given a plain string, should return it unchanged', () => {
      expect(sanitizeHtml('Organization ABC')).toBe('Organization ABC');
    });

    it('given a string with HTML special characters, should escape them', () => {
      expect(sanitizeHtml('<img src=x onerror=alert(1)>')).toBe(
        '&lt;img src=x onerror=alert(1)&gt;',
      );
    });

    it('given a string breaking out of an attribute via a quote, should escape the quotes', () => {
      expect(sanitizeHtml('" onmouseover="alert(1)')).toBe(
        '&quot; onmouseover=&quot;alert(1)',
      );
    });

    it('given an ampersand, should escape it', () => {
      expect(sanitizeHtml('Tom & Jerry')).toBe('Tom &amp; Jerry');
    });

    it('given null or undefined, should return an empty string', () => {
      expect(sanitizeHtml(null)).toBe('');
      expect(sanitizeHtml(undefined)).toBe('');
    });

    it('given a non-string value, should coerce it to a string', () => {
      expect(sanitizeHtml(42)).toBe('42');
    });
  });
});
