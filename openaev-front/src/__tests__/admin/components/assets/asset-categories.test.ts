import { describe, expect, it } from 'vitest';

import { ASSET_CATEGORY_DEFS, humanizeEnum } from '../../../../admin/components/assets/asset-categories';
import en from '../../../../utils/lang/en.json';

const catalog = en as Record<string, string>;

describe('humanizeEnum on the Source code category', () => {
  it('given_sourceCodeCategoryAndSubcategories_should_resolveToKeysOfTheEnglishCatalog', () => {
    // Arrange
    const def = ASSET_CATEGORY_DEFS.SOURCE_CODE;
    const values = [def.value, ...def.subcategories];

    // Act
    const missing = values.map(humanizeEnum).filter(label => catalog[label] === undefined);

    // Assert
    expect(missing).toEqual([]);
  });

  it('given_sourceCodeCategory_should_matchTheLabelOfItsTile', () => {
    // Arrange
    const def = ASSET_CATEGORY_DEFS.SOURCE_CODE;

    // Act
    const label = humanizeEnum(def.value);

    // Assert
    expect(label).toBe(def.label);
  });
});
