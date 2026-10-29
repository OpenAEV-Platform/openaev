import { Checkbox, Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, useMemo } from 'react';

import type { DomainHelper } from '../../../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackPattern, type Domain, type InjectorContractFullOutput } from '../../../../../utils/api-types';
import { TO_CLASSIFY } from '../../../../../utils/domains/domainUtils';
import { isNotEmptyField } from '../../../../../utils/utils';
import PayloadOriginChip from '../../../threat_arsenal/PayloadOriginChip';
import InjectIcon from '../InjectIcon';

interface Props {
  contract: InjectorContractFullOutput;
  attackPatterns: AttackPattern[];
  killChainPhaseName?: string;
  checked: boolean;
  anySelected: boolean;
  /** Atomic testing creation is single-select: no basket checkbox. */
  selectable: boolean;
  onSelect: () => void;
  onToggle: (event: MouseEvent<HTMLElement>) => void;
}

// The Threat Arsenal card, so the inject creation picker reads exactly like the library.
const InjectContractCard: FunctionComponent<Props> = ({
  contract,
  attackPatterns,
  killChainPhaseName,
  checked,
  anySelected,
  selectable,
  onSelect,
  onToggle,
}) => {
  const { t, tPick } = useFormatter();
  const theme = useTheme();

  const allDomains: Domain[] = useHelper(
    (helper: DomainHelper) => helper.getDomains(),
  );

  const domains = useMemo(() => allDomains
    .filter(d => contract.injector_contract_domains?.includes(d.domain_id) && d.domain_name !== TO_CLASSIFY)
    .sort((a, b) => a.domain_name.localeCompare(b.domain_name)), [contract.injector_contract_domains, allDomains]);

  const primaryDomain = domains[0];
  const name = tPick(contract.injector_contract_labels);

  const techniques = useMemo(() => [...new Set(
    attackPatterns
      .map(attackPattern => attackPattern.attack_pattern_external_id)
      .filter(Boolean),
  )].sort() as string[], [attackPatterns]);

  // Prefer the kill chain phase; when none is mapped, show the executing injector(s).
  const injectorNames = useMemo(() => [...new Set(
    Object.values(contract.injector_contract_injector_names ?? {}).filter(Boolean),
  )], [contract.injector_contract_injector_names]);
  const context = killChainPhaseName ?? (injectorNames.length > 0 ? injectorNames.join(', ') : null);

  return (
    <MarketplaceCard
      accent={primaryDomain?.domain_color ?? theme.palette.primary.main}
      testId="inject-contract-card"
      icon={(
        <InjectIcon
          type={contract.injector_contract_payload_type ?? contract.injector_contract_injector_type}
          isPayload={isNotEmptyField(contract.injector_contract_payload_type)}
          variant="list"
        />
      )}
      title={name}
      overline={primaryDomain ? t(primaryDomain.domain_name) : undefined}
      selected={checked}
      onClick={onSelect}
      topLeft={selectable && (
        <span onClick={event => onToggle(event)}>
          <Checkbox checked={checked} aria-label={name} />
        </span>
      )}
      topLeftVisible={selectable && (anySelected || checked)}
      chips={(
        <>
          <PayloadOriginChip origin={contract.injector_contract_payload_origin} />
          {techniques.length > 0 && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="inline-flex">
                  <Chip label={techniques.length > 1 ? `${techniques[0]} +${techniques.length - 1}` : techniques[0]} />
                </span>
              </TooltipTrigger>
              <TooltipContent>{techniques.join(', ')}</TooltipContent>
            </Tooltip>
          )}
        </>
      )}
      platforms={contract.injector_contract_platforms ?? []}
      caption={[
        contract.injector_contract_payload_version ? `v${contract.injector_contract_payload_version}` : null,
        context,
      ].filter(Boolean).join(' · ')}
    />
  );
};

export default InjectContractCard;
