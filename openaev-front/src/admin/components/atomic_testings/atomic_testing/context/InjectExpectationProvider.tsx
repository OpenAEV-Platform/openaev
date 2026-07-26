import { Alert, DialogContentText } from '@mui/material';
import { type ReactNode, useContext, useMemo, useState } from 'react';

import { fetchInjectResultOverviewOutput } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { deleteInjectExpectationResult } from '../../../../../actions/Exercise';
import DialogDelete from '../../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../../components/i18n';
import { type InjectExpectationResult, type InjectResultOverviewOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import {
  InjectResultOverviewOutputContext,
  type InjectResultOverviewOutputContextType,
} from '../../InjectResultOverviewOutputContext';
import EditInjectExpectationResultDialog from '../target_result/EditInjectExpectationResultDialog';
import TargetResultAlertsDialog from '../target_result/TargetResultAlertsDialog';
import InjectExpectationContext from './InjectExpectationContext';

const InjectExpectationProvider = ({ children, inject }: {
  children: ReactNode;
  inject: InjectResultOverviewOutput;
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [openEditResult, setOpenEditResult] = useState<boolean>(false);
  const [selectedResult, setSelectedResult] = useState<InjectExpectationResult | null>(null);
  const [selectedInjectExpectation, setSelectedInjectExpectation] = useState<InjectExpectationsStore | null>(null);
  const [openDeleteResult, setOpenDeleteResult] = useState<boolean>(false);
  const [openAlertsDialog, setOpenAlertsDialog] = useState<boolean>(false);

  const { updateInjectResultOverviewOutput } = useContext<InjectResultOverviewOutputContextType>(InjectResultOverviewOutputContext);

  // -- Delete Inject Expectation Result
  const onOpenDeleteInjectExpectationResult = (injectExpectationResult: InjectExpectationResult | null = null, injectExpectationStore: InjectExpectationsStore | null = null) => {
    setSelectedResult(injectExpectationResult);
    setSelectedInjectExpectation(injectExpectationStore);
    setOpenDeleteResult(true);
  };
  const onCloseDeleteInjectExpectationResult = () => {
    setSelectedResult(null);
    setSelectedInjectExpectation(null);
    setOpenDeleteResult(false);
  };
  const onDelete = () => {
    dispatch(deleteInjectExpectationResult(selectedInjectExpectation?.inject_expectation_id ?? '', selectedResult?.sourceId ?? '')).then(() => {
      fetchInjectResultOverviewOutput(inject.inject_id).then((result: { data: InjectResultOverviewOutput }) => {
        updateInjectResultOverviewOutput(result.data);
        onCloseDeleteInjectExpectationResult();
      });
    });
  };

  // -- Create or Update Inject Expectation Result
  const onOpenEditInjectExpectationResultResult = (result: InjectExpectationResult | null = null, injectExpectationStore: InjectExpectationsStore | null = null) => {
    setSelectedResult(result);
    setSelectedInjectExpectation(injectExpectationStore);
    setOpenEditResult(true);
  };
  const onCloseEditInjectExpectationResultResult = () => {
    setSelectedResult(null);
    setSelectedInjectExpectation(null);
    setOpenEditResult(false);
  };
  const onUpdateValidation = () => {
    fetchInjectResultOverviewOutput(inject.inject_id).then((result: { data: InjectResultOverviewOutput }) => {
      updateInjectResultOverviewOutput(result.data);
      onCloseEditInjectExpectationResultResult();
    });
  };

  const onOpenAlertsDialog = (result: InjectExpectationResult | null = null, injectExpectationStore: InjectExpectationsStore | null = null) => {
    setSelectedResult(result);
    setSelectedInjectExpectation(injectExpectationStore);
    setOpenAlertsDialog(true);
  };

  const onCloseAlertsDialog = () => {
    setSelectedResult(null);
    setSelectedInjectExpectation(null);
    setOpenAlertsDialog(false);
  };

  // Asset-level expectation (no agent): its result rows are the aggregation of the
  // agents' security platform results, so deleting one cascades to all agents.
  const isAssetLevelResultDeletion = !!selectedInjectExpectation?.inject_expectation_asset
    && !selectedInjectExpectation?.inject_expectation_agent;

  const computeExistingSourceIds = (results: InjectExpectationResult[]) => {
    const sourceIds: string[] = [];
    results.forEach((result) => {
      if (result.sourceId) {
        sourceIds.push(result.sourceId);
      }
    });
    return sourceIds;
  };

  const contextValue = useMemo(() => ({
    onOpenDeleteInjectExpectationResult,
    onOpenEditInjectExpectationResultResult,
    onOpenAlertsDialog,
  }),
  [onOpenDeleteInjectExpectationResult,
    onOpenEditInjectExpectationResultResult,
    onOpenAlertsDialog]);

  return (
    <InjectExpectationContext.Provider value={contextValue}>
      {children}
      {openEditResult && (
        <EditInjectExpectationResultDialog
          open={openEditResult}
          injectExpectation={selectedInjectExpectation}
          sourceIds={computeExistingSourceIds(selectedInjectExpectation?.inject_expectation_results ?? [])}
          onClose={onCloseEditInjectExpectationResultResult}
          onUpdate={onUpdateValidation}
          resultToEdit={selectedResult}
        />
      )}
      {openDeleteResult && (
        <DialogDelete
          open={openDeleteResult}
          handleClose={onCloseDeleteInjectExpectationResult}
          text={t('Do you want to delete this expectation result?')}
          // Asset-level rows aggregate the agents' security platform results: deleting one
          // cascades to every agent of the asset, so warn explicitly before submitting.
          richContent={isAssetLevelResultDeletion ? (
            <>
              <DialogContentText>
                {t('Do you want to delete this expectation result?')}
              </DialogContentText>
              <Alert severity="warning" style={{ marginTop: 16 }}>
                {t('This result is an aggregation: deleting it will also delete the results of this security platform for all agents of this asset.')}
              </Alert>
            </>
          ) : undefined}
          handleSubmit={onDelete}
        />
      ) }
      {selectedInjectExpectation && (
        <TargetResultAlertsDialog
          injectExpectation={selectedInjectExpectation}
          sourceId={selectedResult?.sourceId ?? ''}
          expectationResult={selectedResult}
          open={openAlertsDialog}
          handleClose={onCloseAlertsDialog}
        />
      )}
    </InjectExpectationContext.Provider>
  );
};

export default InjectExpectationProvider;
