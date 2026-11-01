import { describe, expect, it } from 'vitest';

import { resolveInjectFieldType } from '../../../../../../admin/components/common/injects/form/InjectContentFieldComponent';

// #6973: a payload argument of type "document" was rendered as a plain text field in the inject form.
describe('resolveInjectFieldType', () => {
  it('renders a document payload argument as a document picker', () => {
    expect(resolveInjectFieldType({
      type: 'text',
      argumentType: 'document',
    })).toBe('document');
  });

  it('keeps a text payload argument as a text field', () => {
    expect(resolveInjectFieldType({
      type: 'text',
      argumentType: 'text',
    })).toBe('text');
    expect(resolveInjectFieldType({ type: 'text' })).toBe('text');
  });

  it('keeps the existing rich text and typed widgets', () => {
    expect(resolveInjectFieldType({
      type: 'textarea',
      richText: true,
    })).toBe('richText');
    expect(resolveInjectFieldType({ type: 'textarea' })).toBe('textarea');
    expect(resolveInjectFieldType({
      type: 'targeted-asset',
      argumentType: 'targeted-asset',
    })).toBe('targeted-asset');
  });
});
