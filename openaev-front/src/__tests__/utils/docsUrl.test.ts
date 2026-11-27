import { describe, expect, it } from 'vitest';

import docsUrl, { docsBase, docsLinkKeys } from '../../utils/docsUrl';

describe('docsUrl', () => {
  describe.each(docsLinkKeys)('for the key %s', (key) => {
    // Act
    const url = docsUrl(key);
    const path = url.split('#')[0];

    it('should build a valid URL', () => {
      expect(URL.canParse(url)).toBe(true);
    });

    it('should start with the documentation base', () => {
      expect(url.startsWith(docsBase)).toBe(true);
    });

    it('should not have a query string', () => {
      expect(url).not.toContain('?');
    });

    it('should not have an empty path segment', () => {
      expect(new URL(url).pathname).not.toContain('//');
    });

    it('should end its path with a slash', () => {
      expect(path.endsWith('/')).toBe(true);
    });
  });

  it('should only accept the keys of docs/links.json', () => {
    // `yarn check-ts` makes the assertion: it fails if an unknown key stops being a type error
    // @ts-expect-error unknown key
    const linkToUnknownKey = () => docsUrl('missing_key');

    expect(linkToUnknownKey).toBeTypeOf('function');
  });
});
