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
import TargetResultsSecurityPlatform from '../TargetResultsSecurityPlatform';
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
  const [openSecurityPlatform, setOpenSecurityPlatform] = useState<boolean>(false);

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

  const onOpenSecurityPlatform = (result: InjectExpectationResult | null = null, injectExpectationStore: InjectExpectationsStore | null = null) => {
    setSelectedResult(result);
    setSelectedInjectExpectation(injectExpectationStore);
    setOpenSecurityPlatform(true);
  };

  const onCloseSecurityPlatformResult = () => {
    setSelectedResult(null);
    setSelectedInjectExpectation(null);
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

  const contextValue = useMemo(() => ({
    onOpenDeleteInjectExpectationResult,
    onOpenEditInjectExpectationResultResult,
    onOpenSecurityPlatform,
  }),
  [onOpenDeleteInjectExpectationResult,
    onOpenEditInjectExpectationResultResult,
    onOpenSecurityPlatform]);

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
          handleSubmit={onDelete}
        />
      ) }
      {selectedInjectExpectation && (
        <TargetResultsSecurityPlatform
          injectExpectation={selectedInjectExpectation}
          sourceId={selectedResult?.sourceId ?? ''}
          expectationResult={selectedResult}
          open={openSecurityPlatform}
          handleClose={onCloseSecurityPlatformResult}
        />
      )}
    </InjectExpectationContext.Provider>
  );
};

export default InjectExpectationProvider;
