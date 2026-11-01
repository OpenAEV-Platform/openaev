import { faker } from '@faker-js/faker';
import { beforeEach, describe, expect, it } from 'vitest';

import { downloadReportingGenerationUrl } from '../../../actions/reporting/reporting-actions';

const TENANT_ID = faker.string.uuid();

const setPathname = (pathname: string) => {
  Object.defineProperty(window, 'location', {
    writable: true,
    value: {
      ...window.location,
      pathname,
    },
  });
};

describe('reporting-actions', () => {
  beforeEach(() => {
    setPathname(`/${TENANT_ID}/admin/reportings`);
  });

  describe('downloadReportingGenerationUrl', () => {
    it('given a generation should build its download link on the tenant route of the current tenant', () => {
      // Arrange
      const generationId = faker.string.uuid();

      // Act
      const url = downloadReportingGenerationUrl(generationId);

      // Assert
      expect(url).toBe(`/api/tenants/${TENANT_ID}/reportings/generations/${generationId}/file`);
    });
  });
});
