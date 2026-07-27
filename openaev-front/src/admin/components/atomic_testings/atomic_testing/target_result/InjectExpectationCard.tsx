import { AddModeratorOutlined, InventoryOutlined } from '@mui/icons-material';
import { Box, IconButton, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useContext } from 'react';

import ButtonPopover from '../../../../../components/common/ButtonPopover';
import Paper from '../../../../../components/common/Paper';
import { useFormatter } from '../../../../../components/i18n';
import type { InjectResultOverviewOutput, InjectTarget } from '../../../../../utils/api-types';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, INHERITED_CONTEXT, SUBJECTS } from '../../../../../utils/permissions/types';
import { computeInjectExpectationLabel, computeStatusStyle } from '../../../../../utils/statusUtils';
import { emptyFilled } from '../../../../../utils/String';
import { isAssets } from '../../../../../utils/target/TargetUtils';
import { PermissionsContext } from '../../../common/Context';
import { expectationTypeIcon } from '../../../common/ExpectationIconByType';
import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import { isAgentExpectation, isAssetExpectation, isAssetGroupExpectation, isManualExpectation, isPlayerExpectation, isTechnicalExpectation, useIsManuallyUpdatable } from '../../../common/injects/expectations/ExpectationUtils';
import InjectExpectationContext from '../context/InjectExpectationContext';
import ExpirationChip from '../ExpirationChip';
import InjectExpectationAggregatedAgentsView from './InjectExpectationAggregatedAgentsView';
import InjectExpectationResultList from './InjectExpectationResultList';
import StatusPill from './StatusPill';

interface Props {
  inject: InjectResultOverviewOutput;
  injectExpectation: InjectExpectationsStore;
  isAgentless: boolean;
  target: InjectTarget;
}

const InjectExpectationCard = ({ inject, injectExpectation, isAgentless, target }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useContext(AbilityContext);
  const { permissions, inherited_context } = useContext(PermissionsContext);

  const { onOpenDeleteInjectExpectationResult, onOpenEditInjectExpectationResultResult } = useContext(InjectExpectationContext);

  // Hooks must be called at top level - not in JSX or conditionally
  const isManuallyUpdatable = useIsManuallyUpdatable(injectExpectation);

  const statusResult = computeInjectExpectationLabel(injectExpectation.inject_expectation_status, injectExpectation.inject_expectation_type);
  const statusColor = computeStatusStyle(injectExpectation.inject_expectation_status).color;

  // Neutral brand accent for the expectation type icon tile. The expectation
  // TYPE must never be colored with status hues (green/orange/red), which read
  // as pass/fail and are misleading here - the icon shape already conveys the
  // type; the status/score chips are the only place status colors belong.
  const accent = theme.palette.primary.main;
  const TypeIcon = expectationTypeIcon(injectExpectation.inject_expectation_type);

  const getLabelOfValidationType = (): string => {
    if (isTechnicalExpectation(injectExpectation.inject_expectation_type)) {
      let entityName;
      if (isAgentExpectation(injectExpectation)) {
        entityName = 'agent';
      } else if (isAssetExpectation(injectExpectation)) {
        entityName = 'agent';
      } else if (isAssetGroupExpectation(injectExpectation)) {
        entityName = 'asset';
      }
      return injectExpectation.inject_expectation_group
        ? t(`At least one ${entityName} (per group) must validate the expectation`)
        : t(`All ${entityName}s (per group) must validate the expectation`);
    } else {
      return injectExpectation.inject_expectation_group
        ? t('At least one player (per team) must validate the expectation')
        : t('All players (per team) must validate the expectation');
    }
  };

  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT)
    || (inherited_context === INHERITED_CONTEXT.NONE && ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, inject.inject_id))
    || permissions.canManage;

  const entries = [{
    label: t('Update'),
    action: () => onOpenEditInjectExpectationResultResult((injectExpectation?.inject_expectation_results || [])[0], injectExpectation),
    disabled: false,
    userRight: canManage,
  },
  {
    label: t('Delete'),
    action: () => onOpenDeleteInjectExpectationResult((injectExpectation?.inject_expectation_results || [])[0], injectExpectation),
    disabled: false,
    userRight: canManage,
  }];

  return (
    <Paper>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1.5),
      }}
      >
        {/* Type icon tile */}
        <Box
          aria-hidden
          sx={{
            width: 32,
            height: 32,
            flexShrink: 0,
            borderRadius: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: alpha(accent, 0.12),
          }}
        >
          <TypeIcon sx={{
            fontSize: 18,
            color: accent,
          }}
          />
        </Box>

        {/* Name + type label */}
        <div style={{
          flex: 1,
          minWidth: 0,
        }}
        >
          <Typography
            sx={{
              fontSize: 13,
              fontWeight: 600,
              minWidth: 0,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {injectExpectation.inject_expectation_name}
          </Typography>
          <Typography
            sx={{
              fontSize: 11,
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              color: 'text.secondary',
              fontFamily: theme.typography.h1.fontFamily,
              whiteSpace: 'nowrap',
            }}
          >
            {t(`TYPE_${injectExpectation.inject_expectation_type}`)}
          </Typography>
        </div>

        {/* Status + score, aligned as one unit */}
        <div style={{
          marginLeft: 'auto',
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
        >
          {statusResult && (
            <StatusPill
              label={t(statusResult)}
              status={injectExpectation.inject_expectation_status}
            />
          )}
          {injectExpectation.inject_expectation_score !== null && (
            <Tooltip title={t('Score')}>
              <Box
                component="span"
                sx={{
                  minWidth: 34,
                  height: 22,
                  borderRadius: 1,
                  paddingInline: 0.75,
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  backgroundColor: alpha(statusColor, 0.12),
                  color: statusColor,
                  fontSize: 12,
                  fontWeight: 700,
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                {injectExpectation.inject_expectation_score}
              </Box>
            </Tooltip>
          )}
          {injectExpectation.inject_expectation_score === null && injectExpectation.inject_expectation_created_at && (
            <ExpirationChip
              expirationTime={injectExpectation.inject_expiration_time}
              startDate={injectExpectation.inject_expectation_created_at}
            />
          )}
        </div>

        {/* Create expectation result */}
        {isManuallyUpdatable && canManage && (
          <Tooltip title={t('Add a result')}>
            <IconButton
              aria-label="Add"
              size="small"
              onClick={() => onOpenEditInjectExpectationResultResult(null, injectExpectation)}
            >
              {['DETECTION', 'PREVENTION', 'VULNERABILITY'].includes(injectExpectation.inject_expectation_type)
                ? <AddModeratorOutlined color="primary" fontSize="medium" />
                : <InventoryOutlined color="primary" fontSize="medium" />}
            </IconButton>
          </Tooltip>
        )}

        {/* Update expectation result */}
        {isManualExpectation(injectExpectation.inject_expectation_type)
          && (injectExpectation.inject_expectation_results?.length ?? 0) > 0 && (
          <ButtonPopover entries={entries} variant="icon" />
        )}
      </div>
      {(!isAgentExpectation(injectExpectation) && !isAssetExpectation(injectExpectation) && !isPlayerExpectation(injectExpectation))
        && (
          <div style={{
            display: 'flex',
            alignItems: 'baseline',
            gap: theme.spacing(1),
            marginTop: theme.spacing(1),
          }}
          >
            <Typography
              sx={{
                fontSize: 11,
                textTransform: 'uppercase',
                letterSpacing: '0.08em',
                color: 'text.secondary',
                fontFamily: theme.typography.h1.fontFamily,
                whiteSpace: 'nowrap',
              }}
            >
              {t('Validation rule:')}
            </Typography>
            <Typography sx={{ fontSize: 13 }}>{emptyFilled(getLabelOfValidationType())}</Typography>
          </div>
        )}

      {
        // If endpoint with agents, show the aggregated security-platform results of the endpoint
        // (union of the agents' results, answered results preferred); the per-agent detail is
        // surfaced in a per-line "i" tooltip rather than a heavy expandable table. Else show the
        // injects expectations for the selected target (agents, endpoints agentless,...)
        (isAssets(target) && !isAgentless) ? (
          ['DETECTION', 'PREVENTION', 'VULNERABILITY'].includes(injectExpectation.inject_expectation_type)
          && (injectExpectation.inject_expectation_results?.length ?? 0) > 0 && (
            <InjectExpectationAggregatedAgentsView
              inject={inject}
              injectExpectation={injectExpectation}
              expectationType={injectExpectation.inject_expectation_type}
              target={target}
            />
          )
        ) : (
          (!isAssetGroupExpectation(injectExpectation) && ['DETECTION', 'PREVENTION', 'VULNERABILITY'].includes(injectExpectation.inject_expectation_type) && (injectExpectation.inject_expectation_results?.length ?? 0) > 0)
          && (
            <InjectExpectationResultList
              injectExpectation={injectExpectation}
              injectExpectationResults={injectExpectation.inject_expectation_results ?? []}
              injectExpectationAgent={injectExpectation.inject_expectation_agent}
              injectorContractPayload={inject.inject_injector_contract?.injector_contract_payload}
              injectType={inject.inject_type}
            />
          )
        )
      }
    </Paper>
  );
};

export default InjectExpectationCard;
