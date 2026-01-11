import { AddModeratorOutlined, InventoryOutlined, MoreVertOutlined } from '@mui/icons-material';
import { Chip, IconButton, Menu, MenuItem, Tooltip, Typography } from '@mui/material';
import { useContext, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchInjectResultOverviewOutput } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { deleteInjectExpectationResult } from '../../../../../actions/Exercise';
import DialogDelete from '../../../../../components/common/DialogDelete';
import Paper from '../../../../../components/common/Paper';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import type { InjectExpectationResult, InjectResultOverviewOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { AbilityContext } from '../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, INHERITED_CONTEXT, SUBJECTS } from '../../../../../utils/permissions/types';
import { computeInjectExpectationLabel } from '../../../../../utils/statusUtils';
import { emptyFilled } from '../../../../../utils/String';
import { PermissionsContext } from '../../../common/Context';
import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import { isManualExpectation, isTechnicalExpectation } from '../../../common/injects/expectations/ExpectationUtils';
import { isAgentExpectation, isAssetExpectation, isAssetGroupExpectation, isPlayerExpectation, useIsManuallyUpdatable } from '../../../simulations/simulation/validation/expectations/ExpectationUtils';
import ExpirationChip from '../ExpirationChip';
import TargetResultsSecurityPlatform from '../TargetResultsSecurityPlatform';
import EditInjectExpectationResultDialog from './EditInjectExpectationResultDialog';
import InjectExpectationResultList from './InjectExpectationResultList';

interface Props {
  inject: InjectResultOverviewOutput;
  injectExpectation: InjectExpectationsStore;
  onUpdateInjectExpectationResult: (result: InjectResultOverviewOutput) => void;
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

const InjectExpectationCard = ({ inject, injectExpectation, onUpdateInjectExpectationResult }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { permissions, inherited_context } = useContext(PermissionsContext);

  const [anchorEditButton, setAnchorEditButton] = useState<null | HTMLElement>(null);
  const openEditButtonMenu = Boolean(anchorEditButton);
  const [openEditResult, setOpenEditResult] = useState<boolean>(false);
  const [selectedResult, setSelectedResult] = useState<InjectExpectationResult | null>(null);
  const [openDeleteResult, setOpenDeleteResult] = useState<boolean>(false);
  const [openSecurityPlatform, setOpenSecurityPlatform] = useState<boolean>(false);

  // Hooks must be called at top level - not in JSX or conditionally
  const isManuallyUpdatable = useIsManuallyUpdatable(injectExpectation);

  const statusResult = computeInjectExpectationLabel(injectExpectation.inject_expectation_status, injectExpectation.inject_expectation_type);

  const onCloseEditResultMenu = () => {
    setAnchorEditButton(null);
  };

  // -- Delete Inject Expectation Result
  const onOpenDeleteInjectExpectationResult = (result: InjectExpectationResult | null = null) => {
    setSelectedResult(result ?? (injectExpectation?.inject_expectation_results || [])[0]);
    setOpenDeleteResult(true);
    onCloseEditResultMenu();
  };
  const onCloseDeleteInjectExpectationResult = () => {
    setSelectedResult(null);
    setOpenDeleteResult(false);
  };
  const onDelete = () => {
    dispatch(deleteInjectExpectationResult(injectExpectation.inject_expectation_id, selectedResult?.sourceId ?? '')).then(() => {
      fetchInjectResultOverviewOutput(inject.inject_id).then((result: { data: InjectResultOverviewOutput }) => {
        onUpdateInjectExpectationResult(result.data);
        onCloseDeleteInjectExpectationResult();
      });
    });
  };

  // -- Create or Update Inject Expectation Result
  const onOpenEditInjectExpectationResultResult = (result: InjectExpectationResult | null = null) => {
    setSelectedResult(result);
    setOpenEditResult(true);
    onCloseEditResultMenu();
  };
  const onCloseEditInjectExpectationResultResult = () => {
    setSelectedResult(null);
    setOpenEditResult(false);
  };
  const onUpdateValidation = () => {
    fetchInjectResultOverviewOutput(inject.inject_id).then((result: { data: InjectResultOverviewOutput }) => {
      onUpdateInjectExpectationResult(result.data);
      onCloseEditInjectExpectationResultResult();
    });
  };

  const onOpenSecurityPlatform = (result: InjectExpectationResult | null = null) => {
    setSelectedResult(result);
    setOpenSecurityPlatform(true);
  };

  const onCloseSecurityPlatformResult = () => {
    setSelectedResult(null);
    setOpenSecurityPlatform(false);
  };

  const computeExistingSourceIds = (results: InjectExpectationResult[]) => {
    const sourceIds: string[] = [];
    results.forEach((result) => {
      if (result.sourceId) {
        sourceIds.push(result.sourceId);
      }
    });
    return sourceIds;
  };

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

  return (
    <>
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
                onClick={() => onOpenEditInjectExpectationResultResult(null)}
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
            <>
              <IconButton
                color="primary"
                onClick={(ev) => {
                  ev.stopPropagation();
                  setAnchorEditButton(ev.currentTarget);
                }}
                aria-haspopup="true"
                size="large"
              >
                <MoreVertOutlined />
              </IconButton>
              <Menu
                anchorEl={anchorEditButton}
                open={openEditButtonMenu}
                onClose={onCloseEditResultMenu}
              >
                <MenuItem onClick={() => onOpenEditInjectExpectationResultResult(null)}>
                  {t('Update')}
                </MenuItem>
                <MenuItem onClick={() => onOpenDeleteInjectExpectationResult(null)}>
                  {t('Delete')}
                </MenuItem>
              </Menu>
            </>
          )}
        </div>
        {(!isAgentExpectation(injectExpectation) && !isPlayerExpectation(injectExpectation))
          && (
            <div className={classes.lineContainer}>
              <Typography gutterBottom variant="h4">{t('Validation rule:')}</Typography>
              <Typography gutterBottom>{emptyFilled(getLabelOfValidationType())}</Typography>
            </div>
          )}

        {(['DETECTION', 'PREVENTION'].includes(injectExpectation.inject_expectation_type) && (injectExpectation.inject_expectation_results?.length ?? 0) > 0)
          && (
            <InjectExpectationResultList
              injectExpectationId={injectExpectation.inject_expectation_id}
              injectExpectationResults={injectExpectation.inject_expectation_results ?? []}
              injectExpectationAgent={injectExpectation.inject_expectation_agent}
              injectorContractPayload={inject.inject_injector_contract?.injector_contract_payload}
              injectType={inject.inject_type}
              handleOpenEditResult={onOpenEditInjectExpectationResultResult}
              handleOpenDeleteResult={onOpenDeleteInjectExpectationResult}
              handleOpenSecurityPlatform={onOpenSecurityPlatform}
            />
          )}
      </Paper>
      <EditInjectExpectationResultDialog
        open={openEditResult}
        injectExpectation={injectExpectation}
        sourceIds={computeExistingSourceIds(injectExpectation.inject_expectation_results ?? [])}
        onClose={onCloseEditInjectExpectationResultResult}
        onUpdate={onUpdateValidation}
        resultToEdit={selectedResult}
      />
      <DialogDelete
        open={openDeleteResult}
        handleClose={onCloseDeleteInjectExpectationResult}
        text={t('Do you want to delete this expectation result?')}
        handleSubmit={onDelete}
      />
      <TargetResultsSecurityPlatform
        injectExpectation={injectExpectation}
        sourceId={selectedResult?.sourceId ?? ''}
        expectationResult={selectedResult}
        open={openSecurityPlatform}
        handleClose={onCloseSecurityPlatformResult}
      />

    </>
  );
};

export default InjectExpectationCard;
