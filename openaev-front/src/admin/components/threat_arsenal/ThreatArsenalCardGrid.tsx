import { type FunctionComponent, type MouseEvent, type ReactNode } from 'react';

import MarketplaceGrid from '../../../components/common/marketplace/MarketplaceGrid';
import { type ThreatArsenalAction } from '../../../utils/api-types';
import computeActionFlags from './threatArsenalActionFlags';
import ThreatArsenalCard from './ThreatArsenalCard';

export interface ThreatArsenalItemsProps {
  actions: ThreatArsenalAction[];
  loading: boolean;
  emptyState: ReactNode;
  isChecked: (action: ThreatArsenalAction) => boolean;
  onSelect: (action: ThreatArsenalAction) => void;
  onToggle: (action: ThreatArsenalAction, event: MouseEvent<HTMLElement>) => void;
  onUpdate: (result: ThreatArsenalAction) => void;
  onDuplicate: (result: ThreatArsenalAction) => void;
  onDelete: (actionId: string) => void;
}

interface Props extends ThreatArsenalItemsProps {
  selectedActionId?: string;
  anySelected: boolean;
}

const ThreatArsenalCardGrid: FunctionComponent<Props> = ({
  actions,
  loading,
  emptyState,
  selectedActionId,
  anySelected,
  isChecked,
  onSelect,
  onToggle,
  onUpdate,
  onDuplicate,
  onDelete,
}) => (
  <MarketplaceGrid loading={loading} isEmpty={actions.length === 0} emptyState={emptyState}>
    {actions.map(action => (
      <ThreatArsenalCard
        key={action.injector_contract_id}
        action={action}
        selected={selectedActionId === action.injector_contract_id}
        checked={isChecked(action)}
        anySelected={anySelected}
        onSelect={() => onSelect(action)}
        onToggleEntity={event => onToggle(action, event)}
        onUpdate={onUpdate}
        onDuplicate={onDuplicate}
        onDelete={() => onDelete(action.injector_contract_id)}
        {...computeActionFlags(action)}
      />
    ))}
  </MarketplaceGrid>
);

export default ThreatArsenalCardGrid;
