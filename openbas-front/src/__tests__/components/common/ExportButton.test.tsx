// MUST be imported *before* the component
// eslint-disable-next-line simple-import-sort/imports
import mockStoreMethodWithReturn from '../../fixtures/mock';

import { faker } from '@faker-js/faker';
// @testing-library/dom is needed as well as it is a peer dependency of @testing-library/react
import { act, render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import ExportButton from '../../../components/common/ExportButton';
import { createDefaultTags, createTagMap } from '../../fixtures/api-types.fixtures';
import TestRootComponent from '../../fixtures/TestRootComponent';
/* eslint-disable  @typescript-eslint/no-explicit-any */
type testobj = { [key: string]: any };
function createObjWithDefaultKeys(objtype: string): testobj {
  const obj: testobj = {};
  ['name', 'extra_prop_1', 'extra_prop_2'].forEach((prop) => {
    obj[`${objtype}_${prop}`] = faker.lorem.sentence();
  });
  return obj;
}

describe('When tag map is defined', () => {
  const exportType: string = 'testobj';
  const exportData: testobj[] = [
    createObjWithDefaultKeys(exportType),
    createObjWithDefaultKeys(exportType),
    createObjWithDefaultKeys(exportType),
  ];
  const numberOfElements: number = exportData.length;
  const exportKeys = [
    `${exportType}_name`,
    `${exportType}_tags`,
  ];
  const tags = createDefaultTags(5);
  const tagMap = createTagMap(tags);
  for (const obj of exportData) {
    obj[`${exportType}_tags`] = tags.map(t => t.tag_id);
  }

  mockStoreMethodWithReturn('getTagsMap', tagMap);

  it('Returns the tag map for download on the href link', async () => {
    const { getByRole } = render(
      <TestRootComponent>
        <ExportButton
          totalElements={numberOfElements}
          exportProps={{
            exportType: exportType,
            exportKeys: exportKeys,
            exportData: exportData,
            exportFileName: 'export.csv',
          }}
        />
      </TestRootComponent>,
    );
    await act(async () => {
      const link = getByRole('link');
      expect(link).toBeDefined();
      expect(link.getAttribute('href')).toEqual(
        'data:text/csv;charset=utf-8,﻿' + exportKeys.map(k => '"' + k + '"').join(',') + '\n'
        + exportData.map(
          d =>
            '"' + d[`${exportType}_name`]
            + '","'
            + d[`${exportType}_tags`].map((t: string) => tagMap[t].tag_name).join(',')
            + '"').join('\n'));
    });
  });
});
