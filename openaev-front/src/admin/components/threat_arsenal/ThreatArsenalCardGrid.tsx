import { type FunctionComponent, type MouseEvent, type ReactNode } from 'react';

import { fetchAttackPatterns } from '../../../actions/AttackPattern';
import { type DomainHelper } from '../../../actions/domains/domain-helper';
import MarketplaceGrid from '../../../components/common/marketplace/MarketplaceGrid';
import { useHelper } from '../../../store';
import { type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import computeActionFlags from './threatArsenalActionFlags';
import ThreatArsenalCard from './ThreatArsenalCard';
import ThreatArsenalDomainSectionHeader from './ThreatArsenalDomainSectionHeader';
import { groupByPrimaryDomain } from './threatArsenalDomainSections';

export interface ThreatArsenalItemsProps {
  actions: ThreatArsenalAction[];
  loading: boolean;
  emptyState: ReactNode;
  isChecked: (action: ThreatArsenalAction) => boolean;
  onSelect: (action: ThreatArsenalAction) => void;
  onToggle: (action: ThreatArsenalAction, event: MouseEvent<HTMLElement>) => void;
  onUpdate: (result: ThreatArsenalAction) => void;
  onDuplicate: (result: ThreatArsenalAction, open?: boolean) => void;
  onDelete: (actionId: string) => void;
}

interface Props extends ThreatArsenalItemsProps { selectedActionId?: string }

const ThreatArsenalCardGrid: FunctionComponent<Props> = ({
  actions,
  loading,
  emptyState,
  selectedActionId,
  onSelect,
  onUpdate,
  onDuplicate,
  onDelete,
}) => {
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
  });
  const allDomains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const card = (action: ThreatArsenalAction) => (
    <ThreatArsenalCard
      key={action.action_payload?.payload_identity_id ?? action.injector_contract_id}
      action={action}
      selected={selectedActionId === action.injector_contract_id}
      onSelect={() => onSelect(action)}
      onUpdate={onUpdate}
      onDuplicate={onDuplicate}
      onDelete={() => onDelete(action.injector_contract_id)}
      {...computeActionFlags(action)}
    />
  );
  if (loading || actions.length === 0) {
    return <MarketplaceGrid loading={loading} isEmpty={actions.length === 0} emptyState={emptyState}>{null}</MarketplaceGrid>;
  }
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      {groupByPrimaryDomain(actions, allDomains).map(section => (
        <section
          key={section.domain?.domain_id ?? 'other'}
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
          }}
        >
          <ThreatArsenalDomainSectionHeader section={section} />
          <MarketplaceGrid loading={false} isEmpty={false} emptyState={null}>
            {section.actions.map(card)}
          </MarketplaceGrid>
        </section>
      ))}
    </div>
  );
};

export default ThreatArsenalCardGrid;
