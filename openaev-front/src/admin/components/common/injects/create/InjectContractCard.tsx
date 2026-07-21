import { CheckCircleOutlined, RadioButtonUncheckedOutlined } from '@mui/icons-material';
import { Box, Card, CardActionArea, Checkbox, Chip, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, useMemo } from 'react';

import type { DomainHelper } from '../../../../../actions/domains/domain-helper';
import { useFormatter } from '../../../../../components/i18n';
import PlatformIcon from '../../../../../components/PlatformIcon';
import { useHelper } from '../../../../../store';
import { type AttackPattern, type Domain, type InjectorContractFullOutput } from '../../../../../utils/api-types';
import { getIconByDomain } from '../../../../../utils/domains/domainIcons';
import { TO_CLASSIFY } from '../../../../../utils/domains/domainUtils';
import { isNotEmptyField } from '../../../../../utils/utils';
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

// Marketplace-style injector-contract card, mirroring ThreatArsenalCard so the
// inject creation picker reads exactly like the Threat Arsenal library.
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

  const showCheckbox = selectable && (anySelected || checked);

  return (
    <Card
      variant="outlined"
      data-testid="inject-contract-card"
      sx={{
        'position': 'relative',
        'display': 'flex',
        'flexDirection': 'column',
        'height': '100%',
        'borderRadius': 1,
        'overflow': 'hidden',
        'borderColor': checked ? accent : theme.palette.divider,
        'backgroundColor': checked
          ? alpha(accent, 0.06)
          : theme.palette.background.paper,
        'transition': theme.transitions.create(
          ['border-color', 'box-shadow', 'transform'],
          { duration: theme.transitions.duration.shorter },
        ),
        '&:hover': {
          borderColor: alpha(accent, 0.3),
          boxShadow: `0 0 30px ${alpha(accent, 0.12)}`,
          transform: 'translateY(-2px)',
        },
        '&:hover .inject-contract-checkbox': { opacity: 1 },
      }}
    >
      <Box
        aria-hidden
        sx={{
          height: 56,
          background: `linear-gradient(135deg, ${alpha(accent, 0.22)} 0%, ${alpha(accent, 0.04)} 100%)`,
          borderBottom: `1px solid ${alpha(accent, 0.18)}`,
          position: 'relative',
        }}
      >
        <Box
          sx={{
            position: 'absolute',
            left: 16,
            bottom: -20,
            width: 40,
            height: 40,
            borderRadius: 1.5,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: theme.palette.background.paper,
            border: `1px solid ${alpha(accent, 0.4)}`,
            boxShadow: `0 4px 12px -4px ${alpha(accent, 0.4)}`,
          }}
        >
          <InjectIcon
            type={contract.injector_contract_payload_type ?? contract.injector_contract_injector_type}
            isPayload={isNotEmptyField(contract.injector_contract_payload_type)}
            variant="list"
          />
        </Box>
      </Box>

      {selectable && (
        <Box
          className="inject-contract-checkbox"
          sx={{
            'position': 'absolute',
            'top': 8,
            'left': 10,
            'zIndex': 2,
            'opacity': showCheckbox ? 1 : 0,
            'transition': theme.transitions.create(['opacity', 'transform']),
            '& .MuiCheckbox-root': {
              'padding': 0.5,
              'color': alpha('#fff', 0.85),
              '&.Mui-checked': { color: theme.palette.primary.main },
              '&:hover': { backgroundColor: alpha('#fff', 0.08) },
            },
            '& .MuiSvgIcon-root': {
              fontSize: 22,
              filter: `drop-shadow(0 1px 2px ${alpha('#000', 0.5)})`,
            },
          }}
          onClick={(event) => {
            event.stopPropagation();
            onToggle(event);
          }}
        >
          <Checkbox
            checked={checked}
            disableRipple
            size="small"
            icon={<RadioButtonUncheckedOutlined />}
            checkedIcon={<CheckCircleOutlined />}
            slotProps={{ input: { 'aria-label': name } }}
          />
        </Box>
      )}

      <CardActionArea
        onClick={onSelect}
        sx={{
          'flex': 1,
          'paddingTop': 3.5,
          'paddingInline': 2,
          'paddingBottom': 1.5,
          'display': 'flex',
          'flexDirection': 'column',
          'gap': 1,
          'alignItems': 'stretch',
          'justifyContent': 'flex-start',
          '& .MuiCardActionArea-focusHighlight': { background: 'transparent' },
        }}
      >
        <Tooltip title={name} enterDelay={500}>
          <Typography
            sx={{
              fontSize: 13.5,
              fontWeight: 600,
              lineHeight: 1.35,
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
              wordBreak: 'break-word',
              minHeight: 36,
            }}
          >
            {name}
          </Typography>
        </Tooltip>

        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          flexWrap: 'wrap',
          minHeight: 22,
        }}
        >
          {primaryDomain && (
            <Chip
              size="small"
              icon={getIconByDomain(primaryDomain.domain_name, {
                fontSize: 13,
                color: accent,
              })}
              label={primaryDomain.domain_name}
              variant="outlined"
              sx={{
                'height': 20,
                'fontSize': 10.5,
                'fontWeight': 600,
                'letterSpacing': '0.02em',
                'textTransform': 'uppercase',
                'borderColor': alpha(accent, 0.5),
                'color': accent,
                'backgroundColor': alpha(accent, 0.08),
                'borderRadius': 0.75,
                '& .MuiChip-icon': { marginLeft: 0.5 },
              }}
            />
          )}
          {domains.length > 1 && (
            <Tooltip title={domains.slice(1).map(d => d.domain_name).join(', ')}>
              <Chip
                size="small"
                label={`+${domains.length - 1}`}
                variant="outlined"
                sx={{
                  height: 20,
                  fontSize: 10.5,
                  borderRadius: 0.75,
                }}
              />
            </Tooltip>
          )}
        </Box>

        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            marginTop: 'auto',
            paddingTop: 1,
            minHeight: 28,
            borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
          }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
            flexShrink: 0,
          }}
          >
            {(contract.injector_contract_platforms ?? []).slice(0, 4).map(platform => (
              <PlatformIcon
                key={platform}
                width={16}
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
            flex: 1,
            display: 'flex',
            justifyContent: 'flex-end',
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
        </Box>

        <Typography
          variant="caption"
          sx={{
            color: 'text.disabled',
            fontSize: 11,
            marginTop: 0.25,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {killChainPhaseName ?? t('Unknown kill chain phase')}
        </Typography>
      </CardActionArea>
    </Card>
  );
};

export default InjectContractCard;
