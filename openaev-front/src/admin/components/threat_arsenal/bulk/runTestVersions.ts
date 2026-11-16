import { fetchThreatArsenalVersions } from '../../../../actions/threat_arsenals/threatArsenal-version-actions';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { type InjectorContractSearchPaginationInput, type PayloadVersionOutput, type ThreatArsenalAction } from '../../../../utils/api-types';

/**
 * Payload approval: a test only runs an approved version that is installed and not revoked.
 * Anything else (pending, rejected, superseded, revoked) is refused by the server at launch, so it
 * is never offered.
 */
export const isRunnableVersion = (version: PayloadVersionOutput) => !!version.payload_action_id
  && !!version.payload_installed
  && !version.payload_revoked
  && version.payload_approval_status === 'APPROVED';

/** The installed versions a test could be asked to run, newest first (the API's order). */
export const installedVersions = (versions: PayloadVersionOutput[]) => versions
  .filter(version => version.payload_installed && version.payload_action_id);

/** The row's own version can run as it is: no payload (nothing to approve) or an approved, live one. */
export const rowRunsAsIs = (action: ThreatArsenalAction) => !action.action_payload
  || (action.action_payload.payload_approval_status === 'APPROVED' && !action.action_payload.payload_revoked);

/** The action of one version: each version is its own action (injector contract). */
export const actionOfVersion = (action: ThreatArsenalAction, version: PayloadVersionOutput): ThreatArsenalAction => ({
  ...action,
  injector_contract_id: version.payload_action_id ?? action.injector_contract_id,
  action_payload: action.action_payload && {
    ...action.action_payload,
    payload_id: version.payload_id,
    payload_version: version.payload_version,
    payload_status: version.payload_status,
    payload_revoked: version.payload_revoked,
    payload_approval_status: version.payload_approval_status,
  },
});

/**
 * The search the run drawer sends with the chosen actions. The list hides older versions, so a
 * chosen older approved version would not be found: every version is searched, restricted to the
 * chosen ids. The list's own filters are left out, they do not apply to an explicit choice.
 */
export const runTestSearchInput = (): InjectorContractSearchPaginationInput => buildSearchPagination({
  filterGroup: {
    mode: 'and',
    filters: [{
      id: generateFilterId(),
      key: 'action_all_versions',
      mode: 'or',
      operator: 'eq',
      values: ['true'],
    }],
  },
}) as InjectorContractSearchPaginationInput;

export interface ResolvedRunSelection {
  /** What runs, by action id: the row's version, or its latest approved version instead. */
  runnable: Record<string, ThreatArsenalAction>;
  /** Actions with no approved version: they cannot run. */
  blocked: ThreatArsenalAction[];
}

/**
 * A selection of actions to run: each runs its own version when approved, otherwise its latest
 * approved version, like the pickers; an action with no approved version is blocked.
 */
export const resolveRunSelection = async (selected: ThreatArsenalAction[]): Promise<ResolvedRunSelection> => {
  const resolved = await Promise.all(selected.map(async (action) => {
    if (rowRunsAsIs(action)) return action;
    const versions: PayloadVersionOutput[] = await fetchThreatArsenalVersions(action.injector_contract_id)
      .then((response: { data: PayloadVersionOutput[] }) => response.data)
      .catch(() => []);
    const latestApproved = versions.find(isRunnableVersion);
    return latestApproved ? actionOfVersion(action, latestApproved) : null;
  }));
  const runnable: Record<string, ThreatArsenalAction> = {};
  const blocked: ThreatArsenalAction[] = [];
  resolved.forEach((action, index) => {
    if (action) {
      runnable[action.injector_contract_id] = action;
    } else {
      blocked.push(selected[index]);
    }
  });
  return {
    runnable,
    blocked,
  };
};
