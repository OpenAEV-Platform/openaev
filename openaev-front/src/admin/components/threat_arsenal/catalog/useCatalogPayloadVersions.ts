import { fetchCatalogPayloadVersions } from '../../../../actions/catalog/payload-catalog-actions';
import { type CatalogPayloadOutput } from '../../../../utils/api-types';
import useLoadedOnKey from '../../../../utils/hooks/useLoadedOnKey';

const NONE: CatalogPayloadOutput[] = [];

/** The versions the catalogue offers of a payload, newest first; none until loaded or without payload. */
const useCatalogPayloadVersions = (externalId: string | undefined) => useLoadedOnKey(externalId, fetchCatalogPayloadVersions, NONE).value;

export default useCatalogPayloadVersions;
