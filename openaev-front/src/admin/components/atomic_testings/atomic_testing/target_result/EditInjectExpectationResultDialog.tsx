import Dialog from '../../../../../components/common/dialog/Dialog';
import type { InjectExpectation, InjectExpectationResult } from '../../../../../utils/api-types';
import { isManualExpectation } from '../../../common/injects/expectations/ExpectationUtils';
import DetectionPreventionExpectationsValidationForm
  from '../../../simulations/simulation/validation/expectations/DetectionPreventionExpectationsValidationForm';
import ManualExpectationsValidationForm
  from '../../../simulations/simulation/validation/expectations/ManualExpectationsValidationForm';

interface Props {
  open: boolean;
  injectExpectation: InjectExpectation | null;
  sourceIds: string[];
  resultToEdit?: InjectExpectationResult | null;
  onClose: () => void;
  onUpdate: () => void;
}
const EditInjectExpectationResultDialog = ({ open, injectExpectation, sourceIds, resultToEdit, onClose, onUpdate }: Props) => {
  return (
    <Dialog
      open={open}
      handleClose={onClose}
    >
      {injectExpectation && (
        <>
          {isManualExpectation(injectExpectation.inject_expectation_type)
            && <ManualExpectationsValidationForm expectation={injectExpectation} onUpdate={onUpdate} />}
          {['DETECTION', 'PREVENTION'].includes(injectExpectation.inject_expectation_type)
            && (
              <DetectionPreventionExpectationsValidationForm
                expectation={injectExpectation}
                sourceIds={resultToEdit ? undefined : sourceIds}
                onUpdate={onUpdate}
                result={resultToEdit ?? undefined}
              />
            )}
        </>
      )}
    </Dialog>
  );
};

export default EditInjectExpectationResultDialog;
