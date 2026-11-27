export const CATALOG_URI = '/api/catalog';

type CatalogKind = 'payloads' | 'scenarios';

export const catalogItemUri = (kind: CatalogKind, externalId: string) => `${CATALOG_URI}/${kind}/${encodeURIComponent(externalId)}`;

export const catalogVersionUri = (kind: CatalogKind, externalId: string, version: string) => `${catalogItemUri(kind, externalId)}/versions/${encodeURIComponent(version)}`;
