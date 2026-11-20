import { useEffect, useState } from 'react';

import { fetchCatalogPayloadVersions } from '../../../../actions/catalog/payload-catalog-actions';
import { type CatalogPayloadOutput } from '../../../../utils/api-types';

/** The versions the catalogue offers of a payload, newest first; none until loaded or without payload. */
const useCatalogPayloadVersions = (externalId: string | undefined) => {
  const [versions, setVersions] = useState<CatalogPayloadOutput[]>([]);

  useEffect(() => {
    setVersions([]);
    if (!externalId) return undefined;
    let cancelled = false;
    fetchCatalogPayloadVersions(externalId)
      .then((loaded) => {
        if (!cancelled) setVersions(loaded);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [externalId]);

  return versions;
};

export default useCatalogPayloadVersions;
