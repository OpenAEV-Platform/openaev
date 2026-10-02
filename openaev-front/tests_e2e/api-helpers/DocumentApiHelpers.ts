import { type APIRequestContext, type APIResponse } from '@playwright/test';

import { tenantApiPath } from '../utils/url';

class DocumentApiHelpers {
  readonly bareDocumentUri = '/api/documents';

  constructor(private request: APIRequestContext) {}

  /** Uploads a small dummy text file as the given tenant, via the tenant-prefixed route. */
  async uploadDocument(tenantId: string, fileName?: string): Promise<{ document_id: string }> {
    const name = fileName ?? `e2e-document-${Date.now()}.txt`;
    const response = await this.request.post(tenantApiPath('/api/documents', tenantId), {
      multipart: {
        input: {
          name: 'input.json',
          mimeType: 'application/json',
          buffer: Buffer.from(JSON.stringify({ document_description: 'e2e tenant isolation test' })),
        },
        file: {
          name,
          mimeType: 'text/plain',
          buffer: Buffer.from(`e2e tenant isolation dummy content ${new Date().toISOString()}`),
        },
      },
    });

    if (!response.ok()) {
      throw new Error(
        `Failed to upload document (POST ${tenantApiPath('/api/documents', tenantId)}): `
        + `${response.status()} ${response.statusText()} - ${await response.text()}`,
      );
    }

    return response.json();
  }

  /** GETs a document by id on the bare route, scoped by X-Tenant-Ids. */
  async getDocument(documentId: string, tenantId: string): Promise<APIResponse> {
    return this.request.get(`${this.bareDocumentUri}/${documentId}`, { headers: { 'X-Tenant-Ids': tenantId } });
  }

  /** GETs a document's file bytes on the bare route, scoped by X-Tenant-Ids. */
  async getDocumentFile(documentId: string, tenantId: string): Promise<APIResponse> {
    return this.request.get(`${this.bareDocumentUri}/${documentId}/file`, { headers: { 'X-Tenant-Ids': tenantId } });
  }

  /** Deletes a document as the owning tenant, via the tenant-prefixed route. */
  async deleteDocument(tenantId: string, documentId: string): Promise<void> {
    await this.request.delete(`${tenantApiPath('/api/documents', tenantId)}/${documentId}`);
  }
}

export default DocumentApiHelpers;
