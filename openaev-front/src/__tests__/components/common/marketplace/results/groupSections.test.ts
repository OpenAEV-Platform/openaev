import { describe, expect, it } from 'vitest';

import groupSections from '../../../../../components/common/marketplace/results/groupSections';

interface Item {
  name: string;
  domain?: string;
}

const byDomain = (item: Item) => item.domain;

describe('groupSections', () => {
  it('given_noItem_should_returnNoSection', () => {
    // Act
    const sections = groupSections<Item>([], byDomain);

    // Assert
    expect(sections).toEqual([]);
  });

  it('given_itemsSortedByKey_should_makeOneSectionPerRun', () => {
    // Arrange
    const items: Item[] = [
      {
        name: 'a',
        domain: 'Endpoint',
      },
      {
        name: 'b',
        domain: 'Endpoint',
      },
      {
        name: 'c',
        domain: 'Network',
      },
      { name: 'd' },
      {
        name: 'e',
        domain: '',
      },
    ];

    // Act
    const sections = groupSections(items, byDomain);

    // Assert
    expect(sections.map(section => [section.key, section.items.map(item => item.name)])).toEqual([
      ['Endpoint', ['a', 'b']],
      ['Network', ['c']],
      [undefined, ['d', 'e']],
    ]);
  });

  it('given_aKeyThatComesBack_should_openANewSection', () => {
    // Arrange
    const items: Item[] = [
      {
        name: 'a',
        domain: 'Endpoint',
      },
      {
        name: 'b',
        domain: 'Network',
      },
      {
        name: 'c',
        domain: 'Endpoint',
      },
    ];

    // Act
    const sections = groupSections(items, byDomain);

    // Assert
    expect(sections.map(section => section.key)).toEqual(['Endpoint', 'Network', 'Endpoint']);
  });
});
