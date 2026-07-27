import { useEffect, useState } from 'react';

import { searchAssetGroupByIdAsOption } from '../../../../../../../../actions/asset_groups/assetgroup-action';
import { searchAssetsByIdAsOption } from '../../../../../../../../actions/assets/endpoint-actions';
import { searchTeamByIdAsOption } from '../../../../../../../../actions/teams/team-actions';
import { searchPlayerByIdAsOption } from '../../../../../../../../actions/users/User';
import { type Option } from '../../../../../../../../utils/Option';

// The ES inject expectation document only carries the target id (asset /
// asset group / team / player side): resolve the display name client-side so
// the Source column shows the actual target instead of a generic kind chip.

export type TargetKind = 'ASSETS' | 'ASSETS_GROUPS' | 'TEAMS' | 'PLAYERS';

type OptionsResponse = { data: Option[] };

const FETCHERS: Record<TargetKind, (ids: string[]) => Promise<OptionsResponse>> = {
  // Expectations can target any asset category, so resolve through the whole
  // asset inventory options, not the endpoint-only ones.
  ASSETS: searchAssetsByIdAsOption,
  ASSETS_GROUPS: searchAssetGroupByIdAsOption,
  TEAMS: searchTeamByIdAsOption,
  PLAYERS: ids => searchPlayerByIdAsOption(ids) as Promise<OptionsResponse>,
};

const cacheKey = (kind: TargetKind, id: string) => `${kind}:${id}`;

// id -> resolved label (null = unresolvable: deleted target or no read
// permission on the inventory). Cached for the whole session so paginating
// back and forth doesn't re-fetch.
const labelCache = new Map<string, string | null>();
const pendingBatches = new Map<TargetKind, Map<string, ((label: string | null) => void)[]>>();

const flushBatch = (kind: TargetKind) => {
  const batch = pendingBatches.get(kind);
  if (!batch || batch.size === 0) return;
  pendingBatches.delete(kind);
  const ids = [...batch.keys()];
  FETCHERS[kind](ids)
    .then((response) => {
      const byId = new Map(response.data.map(option => [option.id, option.label]));
      batch.forEach((callbacks, id) => {
        const label = byId.get(id) ?? null;
        labelCache.set(cacheKey(kind, id), label);
        callbacks.forEach(callback => callback(label));
      });
    })
    .catch(() => {
      // Cache the miss (e.g. restricted inventory) so the API isn't hammered;
      // callers fall back to the generic kind label.
      batch.forEach((callbacks, id) => {
        labelCache.set(cacheKey(kind, id), null);
        callbacks.forEach(callback => callback(null));
      });
    });
};

const resolveTargetLabel = (kind: TargetKind, id: string): Promise<string | null> => {
  const key = cacheKey(kind, id);
  if (labelCache.has(key)) return Promise.resolve(labelCache.get(key) ?? null);
  return new Promise((resolve) => {
    let batch = pendingBatches.get(kind);
    if (!batch) {
      batch = new Map();
      pendingBatches.set(kind, batch);
      // All rows of a render pass share one API call per target kind.
      setTimeout(() => flushBatch(kind), 0);
    }
    const callbacks = batch.get(id);
    if (callbacks) {
      callbacks.push(resolve);
    } else {
      batch.set(id, [resolve]);
    }
  });
};

/** Resolves the display name of an inject expectation target, or null while loading / unresolvable. */
const useInjectExpectationTargetLabel = (kind?: string, id?: string): string | null => {
  const validKind = (kind && kind in FETCHERS ? kind : undefined) as TargetKind | undefined;
  const [label, setLabel] = useState<string | null>(
    () => (validKind && id ? labelCache.get(cacheKey(validKind, id)) ?? null : null),
  );
  useEffect(() => {
    if (!validKind || !id) return undefined;
    let mounted = true;
    resolveTargetLabel(validKind, id).then((resolved) => {
      if (mounted) setLabel(resolved);
    });
    return () => {
      mounted = false;
    };
  }, [validKind, id]);
  return label;
};

export default useInjectExpectationTargetLabel;
