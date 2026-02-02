import { AddModeratorOutlined, InventoryOutlined } from '@mui/icons-material';
import { Chip, IconButton, Tooltip, Typography } from '@mui/material';
import { useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import ButtonPopover from '../../../../../components/common/ButtonPopover';
import Paper from '../../../../../components/common/Paper';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import type { InjectResultOverviewOutput, InjectTarget } from '../../../../../utils/api-types';
import { AbilityContext } from '../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, INHERITED_CONTEXT, SUBJECTS } from '../../../../../utils/permissions/types';
import { computeInjectExpectationLabel } from '../../../../../utils/statusUtils';
import { emptyFilled } from '../../../../../utils/String';
import { isAssets } from '../../../../../utils/target/TargetUtils';
import { PermissionsContext } from '../../../common/Context';
import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import { isManualExpectation, isTechnicalExpectation } from '../../../common/injects/expectations/ExpectationUtils';
import { isAgentExpectation, isAssetExpectation, isAssetGroupExpectation, isPlayerExpectation, useIsManuallyUpdatable } from '../../../simulations/simulation/validation/expectations/ExpectationUtils';
import InjectExpectationContext from '../context/InjectExpectationContext';
import ExpirationChip from '../ExpirationChip';
import InjectExpectationAggregatedAgentsView from './InjectExpectationAggregatedAgentsView';
import InjectExpectationResultList from './InjectExpectationResultList';

interface Props {
  inject: InjectResultOverviewOutput;
  injectExpectation: InjectExpectationsStore;
  isAgentless: boolean;
  target: InjectTarget;
}

const useStyles = makeStyles()(theme => ({
  score: {
    fontSize: '0.75rem',
    height: '20px',
    padding: '0 4px',
  },
  lineContainer: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
    minHeight: '50px',
  },
}));

const InjectExpectationCard = ({ inject, injectExpectation, isAgentless, target }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const ability = useContext(AbilityContext);
  const { permissions, inherited_context } = useContext(PermissionsContext);

  const { onOpenDeleteInjectExpectationResult, onOpenEditInjectExpectationResultResult } = useContext(InjectExpectationContext);

  // Hooks must be called at top level - not in JSX or conditionally
  const isManuallyUpdatable = useIsManuallyUpdatable(injectExpectation);

  const statusResult = computeInjectExpectationLabel(injectExpectation.inject_expectation_status, injectExpectation.inject_expectation_type);

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
      <div className={classes.lineContainer}>
        <Typography style={{ marginRight: 'auto' }} variant="h5">{injectExpectation.inject_expectation_name}</Typography>
        {injectExpectation.inject_expectation_score !== null && (
          <>
            <ItemStatus label={t(`${statusResult}`)} status={injectExpectation.inject_expectation_status} />
            <Tooltip title={t('Score')}>
              <Chip
                classes={{ root: classes.score }}
                label={injectExpectation.inject_expectation_score}
              />
            </Tooltip>
          </>
        )}
        {injectExpectation.inject_expectation_score === null && injectExpectation.inject_expectation_created_at && (
          <ExpirationChip
            expirationTime={injectExpectation.inject_expiration_time}
            startDate={injectExpectation.inject_expectation_created_at}
          />
        )}

        {/* Create expectation result */}
        {isManuallyUpdatable && canManage && (
          <Tooltip title={t('Add a result')}>
            <IconButton
              aria-label="Add"
              onClick={() => onOpenEditInjectExpectationResultResult(null, injectExpectation)}
            >
              {['DETECTION', 'PREVENTION'].includes(injectExpectation.inject_expectation_type)
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
          <div className={classes.lineContainer}>
            <Typography gutterBottom variant="h4">{t('Validation rule:')}</Typography>
            <Typography gutterBottom>{emptyFilled(getLabelOfValidationType())}</Typography>
          </div>
        )}

      {
        // If endpoint with agents, show the injects expectations aggregated for each agent of the endpoint
        // Else show the injects expectations for the selected target (agents, endpoints agentless,...)
        (isAssets(target) && !isAgentless) ? (
          <InjectExpectationAggregatedAgentsView
            inject={inject}
            expectationType={injectExpectation.inject_expectation_type}
            target={target}
          />
        ) : (
          (!isAssetGroupExpectation(injectExpectation) && ['DETECTION', 'PREVENTION'].includes(injectExpectation.inject_expectation_type) && (injectExpectation.inject_expectation_results?.length ?? 0) > 0)
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
