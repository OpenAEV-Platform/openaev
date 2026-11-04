import { describe, expect, it } from 'vitest';

import docsUrl from '../../utils/docsUrl';

describe('docsUrl', () => {
  it('should build the URL of the page and anchor of a key', () => {
    // Act
    const url = docsUrl('teams');

    // Assert
    expect(url).toBe('https://docs.openaev.io/latest/usage/build/people/#teams');
  });

  it('should return the documentation root for the home key', () => {
    // Act
    const url = docsUrl('home');

    // Assert
    expect(url).toBe('https://docs.openaev.io/latest/');
  });

  it('should only accept the keys of docs/links.json', () => {
    // `yarn check-ts` makes the assertion: it fails if an unknown key stops being a type error
    // @ts-expect-error unknown key
    const linkToUnknownKey = () => docsUrl('missing_key');

    expect(linkToUnknownKey).toBeTypeOf('function');
  });
});
