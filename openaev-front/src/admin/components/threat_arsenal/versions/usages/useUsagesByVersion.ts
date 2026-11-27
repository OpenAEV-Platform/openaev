import { useEffect, useRef, useState } from 'react';

import { fetchThreatArsenalUsages } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';

type Usages = Record<string, PayloadUsagesOutput>;

/**
 * The usages of the versions asked for, by payload id, once each is loaded: a version is loaded once while asked for,
 * dropped when no longer asked for, and all are loaded again when `reloadKey` changes.
 */
const useUsagesByVersion = (versions: PayloadVersionOutput[], reloadKey = 0) => {
  const [loaded, setLoaded] = useState<{
    reloadKey: number;
    usages: Usages;
  }>({
    reloadKey,
    usages: {},
  });
  const requested = useRef({
    reloadKey,
    ids: new Set<string>(),
  });
  const askedIds = versions.map(version => version.payload_id);

  useEffect(() => {
    if (requested.current.reloadKey !== reloadKey) {
      requested.current = {
        reloadKey,
        ids: new Set(),
      };
    }
    const { ids } = requested.current;
    const dropped = [...ids].filter(id => !askedIds.includes(id));
    if (dropped.length > 0) {
      dropped.forEach(id => ids.delete(id));
      setLoaded(previous => ({
        ...previous,
        usages: Object.fromEntries(Object.entries(previous.usages).filter(([id]) => !dropped.includes(id))),
      }));
    }
    versions.filter(version => !ids.has(version.payload_id)).forEach((version) => {
      ids.add(version.payload_id);
      fetchThreatArsenalUsages(version.payload_action_id)
        .then(({ data }: { data: PayloadUsagesOutput }) => data)
        // The call already tells the user it failed: the version shows no usage instead of loading forever.
        .catch((): PayloadUsagesOutput => ({ payload_usages: [] }))
        .then((versionUsages) => {
          if (!requested.current.ids.has(version.payload_id) || requested.current.reloadKey !== reloadKey) return;
          setLoaded(previous => ({
            reloadKey,
            usages: {
              ...(previous.reloadKey === reloadKey ? previous.usages : {}),
              [version.payload_id]: versionUsages,
            },
          }));
        });
    });
  }, [askedIds.join(), reloadKey]);

  return loaded.reloadKey === reloadKey ? loaded.usages : {};
};

export default useUsagesByVersion;
