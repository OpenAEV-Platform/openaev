import { type FunctionComponent, useMemo } from 'react';

import { fetchAttackPatterns } from '../../../../actions/AttackPattern';
import { fetchDomains } from '../../../../actions/domains/domain-actions';
import {
  type Document,
  type InjectorContract,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { resolveUserName } from '../../../../utils/String';
import ThreatArsenalActionOverview from '../../threat_arsenal/ThreatArsenalActionOverview';

interface Props {
  injectorContract: Omit<InjectorContract, 'convertedContent'>;
  documentsMap?: Record<string, Document> | null;
}

/**
 * "Action info" panel of the inject update drawer: renders the exact same
 * overview as the Threat Arsenal "Action information" drawer, fed from the
 * inject's injector contract. Expectations are intentionally omitted - they
 * are already editable in the "Inject details" tab.
 */
const InjectContractOverview: FunctionComponent<Props> = ({ injectorContract, documentsMap }) => {
  const dispatch = useAppDispatch();

  // Attack pattern and domain chips resolve through the Redux maps.
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
    dispatch(fetchDomains());
  });

  const payload = injectorContract.injector_contract_payload ?? null;

  const action: ThreatArsenalAction = useMemo(() => {
    const authorUser = injectorContract.injector_contract_payload_author_user;
    const authorTeam = injectorContract.injector_contract_payload_author_team;
    const authorOrganization = injectorContract.injector_contract_payload_author_organization;
    let authorName: string | undefined;
    let authorType: string | undefined;
    if (authorUser) {
      authorName = resolveUserName(authorUser);
      authorType = 'user';
    } else if (authorTeam) {
      authorName = authorTeam.team_name;
      authorType = 'team';
    } else if (authorOrganization) {
      authorName = authorOrganization.organization_name;
      authorType = 'organization';
    }
    return {
      injector_contract_id: injectorContract.injector_contract_id,
      injector_contract_updated_at: injectorContract.injector_contract_updated_at,
      action_attack_patterns_ids: injectorContract.injector_contract_attack_patterns ?? [],
      action_domains_ids: injectorContract.injector_contract_domains ?? [],
      action_tags_ids: injectorContract.injector_contract_tags ?? [],
      action_labels: injectorContract.injector_contract_labels,
      action_injector_type: injectorContract.injector_contract_injector_type,
      action_platforms: injectorContract.injector_contract_platforms,
      action_payload: payload
        ? {
            payload_id: payload.payload_id,
            payload_type: payload.payload_type,
            payload_collector_type: payload.payload_collector_type,
            payload_status: payload.payload_status,
          }
        : undefined,
      action_author_name: authorName,
      action_author_type: authorType,
    };
  }, [injectorContract, payload]);

  return (
    <ThreatArsenalActionOverview
      action={action}
      payload={payload}
      // Hide the Expectations section: expectations are already part of the
      // inject edition details.
      expectations={[]}
      documentsMap={documentsMap ?? undefined}
      loading={false}
    />
  );
};

export default InjectContractOverview;
