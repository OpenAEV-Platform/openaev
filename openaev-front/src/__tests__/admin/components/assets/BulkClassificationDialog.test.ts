import { describe, expect, it } from 'vitest';

import { ASSET_EDITABLE_CATEGORIES, subcategoryBelongsTo } from '../../../../admin/components/assets/asset-categories';
import { toBulkClassificationInput } from '../../../../admin/components/assets/endpoints/BulkClassificationDialog';

describe('subcategoryBelongsTo', () => {
  it('accepts a subcategory of its own category', () => {
    expect(subcategoryBelongsTo('WEBSITE', 'WEB_APPLICATION')).toBe(true);
  });

  it('refuses a subcategory of another category or without a category', () => {
    expect(subcategoryBelongsTo('SERVER', 'WEB_APPLICATION')).toBe(false);
    expect(subcategoryBelongsTo('WEBSITE', null)).toBe(false);
  });
});

describe('ASSET_EDITABLE_CATEGORIES', () => {
  it('leaves AI targets to their own form', () => {
    expect(ASSET_EDITABLE_CATEGORIES).not.toContain('AI_TARGET');
    expect(ASSET_EDITABLE_CATEGORIES).toContain('WEB_APPLICATION');
  });
});

describe('toBulkClassificationInput', () => {
  it('sends only the fields that were filled', () => {
    expect(toBulkClassificationInput({
      asset_category: 'WEB_APPLICATION',
      asset_subcategory: '',
      asset_url: '  ',
      asset_criticality: '',
      asset_tags_to_add: [],
    })).toEqual({
      asset_category: 'WEB_APPLICATION',
      asset_subcategory: undefined,
      asset_url: undefined,
      asset_criticality: undefined,
      asset_tags_to_add: undefined,
    });
  });

  it('trims the URL and keeps the tags to add', () => {
    const input = toBulkClassificationInput({
      asset_category: '',
      asset_subcategory: '',
      asset_url: ' https://app.example.com ',
      asset_criticality: 'HIGH',
      asset_tags_to_add: ['tag-1'],
    });
    expect(input.asset_url).toBe('https://app.example.com');
    expect(input.asset_criticality).toBe('HIGH');
    expect(input.asset_tags_to_add).toEqual(['tag-1']);
  });
});
