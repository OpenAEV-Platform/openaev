import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import {
  type CatalogInstallAllOutput,
  type CatalogInstallOutput,
  type CatalogPayloadOutput,
  type CatalogSettingsInput,
  type CatalogSettingsOutput,
  type PayloadUpsertInput,
} from '../../utils/api-types';

const CATALOG_URI = '/api/catalog';

const payloadUri = (externalId: string) => `${CATALOG_URI}/payloads/${encodeURIComponent(externalId)}`;
const versionUri = (externalId: string, version: string) => `${payloadUri(externalId)}/versions/${encodeURIComponent(version)}`;

export const fetchCatalogPayloads = (): Promise<CatalogPayloadOutput[]> => simpleCall(`${CATALOG_URI}/payloads`)
  .then(({ data }: { data: CatalogPayloadOutput[] }) => data);

export const fetchCatalogPayloadVersions = (externalId: string): Promise<CatalogPayloadOutput[]> => simpleCall(`${payloadUri(externalId)}/versions`)
  .then(({ data }: { data: CatalogPayloadOutput[] }) => data);

export const fetchCatalogPayloadVersionContent = (externalId: string, version: string): Promise<PayloadUpsertInput> => simpleCall(`${versionUri(externalId, version)}/content`)
  .then(({ data }: { data: PayloadUpsertInput }) => data);

export const installCatalogPayload = (externalId: string, version: string): Promise<CatalogInstallOutput> => simplePostCall(`${versionUri(externalId, version)}/install`)
  .then(({ data }: { data: CatalogInstallOutput }) => data);

export const installAllCatalogPayloads = (): Promise<CatalogInstallAllOutput> => simplePostCall(`${CATALOG_URI}/payloads/install-all`)
  .then(({ data }: { data: CatalogInstallAllOutput }) => data);

export const fetchCatalogSettings = (): Promise<CatalogSettingsOutput> => simpleCall(`${CATALOG_URI}/settings`)
  .then(({ data }: { data: CatalogSettingsOutput }) => data);

export const updateCatalogSettings = (input: CatalogSettingsInput): Promise<CatalogSettingsOutput> => simplePutCall(`${CATALOG_URI}/settings`, input)
  .then(({ data }: { data: CatalogSettingsOutput }) => data);
