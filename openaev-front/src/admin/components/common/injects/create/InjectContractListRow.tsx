import { KeyboardArrowRight } from '@mui/icons-material';
import { Box, Checkbox, Chip, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, useMemo } from 'react';

import type { DomainHelper } from '../../../../../actions/domains/domain-helper';
import { useFormatter } from '../../../../../components/i18n';
import ItemDomains from '../../../../../components/ItemDomains';
import PlatformIcon from '../../../../../components/PlatformIcon';
import { useHelper } from '../../../../../store';
import { type AttackPattern, type Domain, type InjectorContractFullOutput } from '../../../../../utils/api-types';
import { TO_CLASSIFY } from '../../../../../utils/domains/domainUtils';
import { isNotEmptyField } from '../../../../../utils/utils';
import InjectIcon from '../InjectIcon';

export const LIST_GRID_COLUMNS = (selectable: boolean) =>
  `${selectable ? '40px ' : ''}44px minmax(0, 2fr) minmax(0, 1.3fr) 110px minmax(0, 1fr) 150px 32px`;

interface Props {
  contract: InjectorContractFullOutput;
  attackPatterns: AttackPattern[];
  killChainPhaseName?: string;
  checked: boolean;
  selectable: boolean;
  onSelect: () => void;
  onToggle: (event: MouseEvent<HTMLElement>) => void;
}

// Compact list-view row of the inject-contract picker, mirroring
// ThreatArsenalListRow (CSS grid, accent left border, hover highlight).
const InjectContractListRow: FunctionComponent<Props> = ({
  contract,
  attackPatterns,
  killChainPhaseName,
  checked,
  selectable,
  onSelect,
  onToggle,
}) => {
  const { tPick } = useFormatter();
  const theme = useTheme();

  const allDomains: Domain[] = useHelper(
    (helper: DomainHelper) => helper.getDomains(),
  );
  const domains = useMemo(() => {
    if (!contract.injector_contract_domains) return [] as Domain[];
    return allDomains.filter(
      d => contract.injector_contract_domains?.includes(d.domain_id) && d.domain_name !== TO_CLASSIFY,
    );
  }, [contract.injector_contract_domains, allDomains]);

  const primaryDomain = domains[0];
  const accent = primaryDomain?.domain_color ?? theme.palette.primary.main;
  const name = tPick(contract.injector_contract_labels);

  const externalIds = useMemo(() => [...new Set(
    attackPatterns
      .map(attackPattern => attackPattern.attack_pattern_external_id)
      .filter(Boolean),
  )] as string[], [attackPatterns]);

  return (
    <Box
      role="row"
      tabIndex={0}
      aria-selected={checked}
      onClick={onSelect}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onSelect();
        }
      }}
      sx={{
        'display': 'grid',
        'gridTemplateColumns': LIST_GRID_COLUMNS(selectable),
        'alignItems': 'center',
        'gap': 1.5,
        'paddingBlock': 0.75,
        'paddingInline': 1.5,
        'borderRadius': 1,
        'borderLeft': '3px solid',
        'borderLeftColor': checked ? accent : 'transparent',
        'cursor': 'pointer',
        'backgroundColor': checked ? alpha(accent, 0.08) : 'transparent',
        'transition': theme.transitions.create(['background-color', 'border-color']),
        '&:hover': { backgroundColor: alpha(theme.palette.text.primary, 0.04) },
        '&:focus-visible': {
          outline: `2px solid ${theme.palette.primary.main}`,
          outlineOffset: -2,
        },
      }}
    >
      {selectable && (
        <Box
          onClick={(event) => {
            event.stopPropagation();
            onToggle(event);
          }}
        >
          <Checkbox
            edge="start"
            checked={checked}
            disableRipple
            size="small"
            slotProps={{ input: { 'aria-label': name } }}
          />
        </Box>
      )}

      <Box
        sx={{
          width: 36,
          height: 36,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: alpha(accent, 0.12),
          border: `1px solid ${alpha(accent, 0.25)}`,
        }}
      >
        <InjectIcon
          type={contract.injector_contract_payload_type ?? contract.injector_contract_injector_type}
          isPayload={isNotEmptyField(contract.injector_contract_payload_type)}
          variant="list"
        />
      </Box>

      <Tooltip title={name} enterDelay={500}>
        <Typography
          variant="body2"
          sx={{
            fontWeight: 600,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            minWidth: 0,
          }}
        >
          {name}
        </Typography>
      </Tooltip>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        overflow: 'hidden',
      }}
      >
        <ItemDomains domains={contract.injector_contract_domains ?? []} variant="reduced-view" />
      </Box>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.75,
      }}
      >
        {(contract.injector_contract_platforms ?? []).slice(0, 4).map(platform => (
          <PlatformIcon
            key={platform}
            width={18}
            platform={platform}
            tooltip
          />
        ))}
        {(contract.injector_contract_platforms?.length ?? 0) > 4 && (
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            +
            {(contract.injector_contract_platforms?.length ?? 0) - 4}
          </Typography>
        )}
      </Box>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.5,
        overflow: 'hidden',
      }}
      >
        {externalIds.slice(0, 2).map(externalId => (
          <Chip
            key={externalId}
            size="small"
            variant="outlined"
            color="primary"
            label={externalId}
            sx={{
              height: 20,
              fontSize: 10.5,
              borderRadius: 0.75,
            }}
          />
        ))}
        {externalIds.length > 2 && (
          <Tooltip title={externalIds.slice(2).join(', ')}>
            <Chip
              size="small"
              variant="outlined"
              label={`+${externalIds.length - 2}`}
              sx={{
                height: 20,
                fontSize: 10.5,
                borderRadius: 0.75,
              }}
            />
          </Tooltip>
        )}
      </Box>

      <Typography
        variant="caption"
        sx={{
          color: 'text.secondary',
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
      >
        {killChainPhaseName ?? '-'}
      </Typography>

      <Box sx={{
        display: 'flex',
        justifyContent: 'flex-end',
        color: 'text.secondary',
      }}
      >
        <KeyboardArrowRight fontSize="small" />
      </Box>
    </Box>
  );
};

export default InjectContractListRow;
