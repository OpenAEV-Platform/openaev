import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import {
  type AvailableActionOutput,
  type CatalogInstallAllOutput,
  type CatalogInstallOutput,
  type CatalogPayloadOutput,
  type CatalogSettingsInput,
  type CatalogSettingsOutput,
  type PayloadUpsertInput,
} from '../../utils/api-types';
import { CATALOG_URI, catalogItemUri, catalogVersionUri } from './catalog-uri';

export const fetchAvailableActions = (): Promise<AvailableActionOutput[]> => simpleCall(`${CATALOG_URI}/actions`)
  .then(({ data }: { data: AvailableActionOutput[] }) => data);

export const fetchCatalogPayloadVersions = (externalId: string): Promise<CatalogPayloadOutput[]> => simpleCall(`${catalogItemUri('payloads', externalId)}/versions`)
  .then(({ data }: { data: CatalogPayloadOutput[] }) => data);

export const fetchCatalogPayloadVersionContent = (externalId: string, version: string): Promise<PayloadUpsertInput> => simpleCall(`${catalogVersionUri('payloads', externalId, version)}/content`)
  .then(({ data }: { data: PayloadUpsertInput }) => data);

export const installCatalogPayload = (externalId: string, version: string): Promise<CatalogInstallOutput> => simplePostCall(`${catalogVersionUri('payloads', externalId, version)}/install`)
  .then(({ data }: { data: CatalogInstallOutput }) => data);

export const installAllCatalogPayloads = (): Promise<CatalogInstallAllOutput> => simplePostCall(`${CATALOG_URI}/payloads/install-all`)
  .then(({ data }: { data: CatalogInstallAllOutput }) => data);

export const fetchCatalogSettings = (): Promise<CatalogSettingsOutput> => simpleCall(`${CATALOG_URI}/settings`)
  .then(({ data }: { data: CatalogSettingsOutput }) => data);

export const updateCatalogSettings = (input: CatalogSettingsInput): Promise<CatalogSettingsOutput> => simplePutCall(`${CATALOG_URI}/settings`, input)
  .then(({ data }: { data: CatalogSettingsOutput }) => data);
