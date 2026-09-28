import { expect } from '@playwright/test';

import DocumentApiHelpers from '../../api-helpers/DocumentApiHelpers';
import TenantApiHelpers from '../../api-helpers/TenantApiHelpers';
import { test } from '../../fixtures';
import { tenantUrl } from '../../utils/url';

test.describe('Multi-tenancy — document isolation', () => {
  const skipInCiWithoutLicense = Boolean(process.env.CI) && !process.env.OPENAEV_APPLICATION_LICENSE;
  if (skipInCiWithoutLicense) {
    return;
  }

  let tenantAId: string | null = null;
  let tenantBId: string | null = null;
  let documentId: string | null = null;

  test.beforeEach(async ({ request, page }) => {
    // ─────────────────────────────────────────────────
    // Pre-requisite — create Tenant A and Tenant B
    // ─────────────────────────────────────────────────
    const tenantApiHelpers = new TenantApiHelpers(request);
    const tenantA = await tenantApiHelpers.createTenant(`Tenant A E2E ${Date.now()}`);
    const tenantB = await tenantApiHelpers.createTenant(`Tenant B E2E ${Date.now()}`);
    tenantAId = tenantA.tenant_id;
    tenantBId = tenantB.tenant_id;

    // ─────────────────────────────────────────────────
    // Pre-requisite — switch to Tenant A
    // ─────────────────────────────────────────────────
    await page.goto(tenantUrl('/admin/components/documents', tenantAId));
    await page.waitForURL(url => url.toString().includes(tenantAId!));
    await page.waitForLoadState('domcontentloaded');

    // ─────────────────────────────────────────────────
    // Pre-requisite — upload a document in Tenant A
    // ─────────────────────────────────────────────────
    const documentApiHelpers = new DocumentApiHelpers(request);
    const uploaded = await documentApiHelpers.uploadDocument(tenantAId);
    documentId = uploaded.document_id;
  });

  test.afterEach(async ({ request }) => {
    const documentApiHelpers = new DocumentApiHelpers(request);
    const tenantApiHelpers = new TenantApiHelpers(request);

    if (documentId && tenantAId) {
      await documentApiHelpers.deleteDocument(tenantAId, documentId);
      documentId = null;
    }
    if (tenantAId) {
      await tenantApiHelpers.softDeleteTenant(tenantAId);
      tenantAId = null;
    }
    if (tenantBId) {
      await tenantApiHelpers.softDeleteTenant(tenantBId);
      tenantBId = null;
    }
  });

  test('a document uploaded in Tenant A is only visible to Tenant A on the bare route', async ({ request }) => {
    expect(tenantAId).not.toBeNull();
    expect(tenantBId).not.toBeNull();
    expect(documentId).not.toBeNull();

    const documentApiHelpers = new DocumentApiHelpers(request);

    // ─────────────────────────────────────────────────
    // Assert — Tenant A can read the document by id and by file bytes
    // ─────────────────────────────────────────────────
    const getByIdAsA = await documentApiHelpers.getDocument(documentId!, tenantAId!);
    expect(getByIdAsA.status(), 'GET /api/documents/{id} as Tenant A').toBe(200);

    const getFileAsA = await documentApiHelpers.getDocumentFile(documentId!, tenantAId!);
    expect(getFileAsA.status(), 'GET /api/documents/{id}/file as Tenant A').toBe(200);

    // ─────────────────────────────────────────────────
    // Assert — Tenant B cannot read the document by id or by file bytes
    // ─────────────────────────────────────────────────
    const getByIdAsB = await documentApiHelpers.getDocument(documentId!, tenantBId!);
    expect(getByIdAsB.status(), 'GET /api/documents/{id} as Tenant B').toBe(404);

    const getFileAsB = await documentApiHelpers.getDocumentFile(documentId!, tenantBId!);
    expect(getFileAsB.status(), 'GET /api/documents/{id}/file as Tenant B').toBe(404);
  });
});
