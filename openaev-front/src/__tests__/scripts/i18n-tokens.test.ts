/* eslint-disable no-template-curly-in-string -- ${var} is the literal placeholder syntax of the email templates */
import { describe, expect, it } from 'vitest';

import tokens from '../../../scripts/i18n-tokens';

const LINK = 'Please fill this questionnaire: <a href=\'${lessons_uri}\'>${lessons_uri}</a>.';

describe('i18n tokens', () => {
  it('extracts placeholders in every supported syntax', () => {
    expect(tokens('{count} injects, {{phishing_url}} and ${source.type}')).toEqual(
      ['${source.type}', '{count}', '{{phishing_url}}'],
    );
  });

  it('extracts a tag with attributes as a single token', () => {
    expect(tokens(LINK)).toEqual(['${lessons_uri}', '</a>', '<a href=\'${lessons_uri}\'>']);
  });

  it('extracts plain, closing and self-closing tags', () => {
    expect(tokens('<b>bold</b><br/><br /><0>rich</0>')).toEqual(['</0>', '</b>', '<0>', '<b>', '<br />', '<br/>']);
  });

  // Regression: the opening tag used to be ignored, so a translation losing or corrupting it
  // while keeping ${lessons_uri} and </a> passed the placeholder check.
  it.each([
    ['dropped opening tag', 'Veuillez remplir ce questionnaire : ${lessons_uri}</a>.'],
    ['corrupted attribute', 'Veuillez remplir ce questionnaire : <a href=\'${lessons}\'>${lessons_uri}</a>.'],
    ['changed attribute name', 'Veuillez remplir ce questionnaire : <a src=\'${lessons_uri}\'>${lessons_uri}</a>.'],
  ])('detects a %s', (_, translation) => {
    expect(tokens(translation)).not.toEqual(tokens(LINK));
  });

  it('accepts a translation that keeps the link intact', () => {
    const translation = 'Veuillez remplir ce questionnaire : <a href=\'${lessons_uri}\'>${lessons_uri}</a>.';
    expect(tokens(translation)).toEqual(tokens(LINK));
  });

  it('ignores a repeated placeholder', () => {
    expect(tokens('{count} sur {count}')).toEqual(['{count}']);
  });
});
