import { Step, StepButton, Stepper } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { addAiTarget } from '../../../../actions/assets/aiTarget-actions';
import { addEndpointAgentless } from '../../../../actions/assets/endpoint-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { AiTargetInput, Endpoint, EndpointInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import AiTargetForm from '../ai_targets/AiTargetForm';
import { type AssetCategory, getCategoryDef } from '../asset-categories';
import AssetCategoryPicker from '../AssetCategoryPicker';
import AssetForm from '../AssetForm';

interface Props {
  agentless?: boolean;
  onCreate?: (result: Endpoint) => void;
}

const EndpointCreation: FunctionComponent<Props> = ({
  agentless = true,
  onCreate,
}) => {
  const [open, setOpen] = useState(false);
  const [category, setCategory] = useState<AssetCategory | null>(null);
  const { t } = useFormatter();

  const dispatch = useAppDispatch();

  const handleClose = () => {
    setOpen(false);
    setCategory(null);
  };

  const onSubmit = (data: EndpointInput) => {
    dispatch(addEndpointAgentless(data)).then(
      (result: {
        result: string;
        entities: { endpoints: Record<string, Endpoint> };
      }) => {
        if (result.entities) {
          if (onCreate) {
            const endpointCreated = result.entities.endpoints[result.result];
            onCreate(endpointCreated);
          }
          handleClose();
        }
        return result;
      },
    );
  };

  const onSubmitAiTarget = (data: AiTargetInput) => {
    // AI targets are base Assets, not Endpoints, so they are not prepended to the
    // (endpoint-shaped) onCreate list here; they surface in the unified asset inventory.
    dispatch(addAiTarget(data)).then((result: { entities?: unknown }) => {
      if (result.entities) {
        handleClose();
      }
      return result;
    });
  };

  const renderStepContent = () => {
    if (!category) {
      return <AssetCategoryPicker onSelect={setCategory} />;
    }
    if (category === 'AI_TARGET') {
      return <AiTargetForm onSubmit={onSubmitAiTarget} handleClose={handleClose} />;
    }
    return (
      <AssetForm
        category={category}
        agentless={agentless}
        onSubmit={onSubmit}
        handleClose={handleClose}
      />
    );
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a new asset')}
      >
        <>
          {/* Two-step flow: pick a category, then fill the form. The stepper lets
              the user jump back to step 1 to reselect the category (replacing the
              old "Change category" text button). */}
          <Stepper nonLinear activeStep={category ? 1 : 0} sx={{ marginBottom: 3 }}>
            <Step completed={!!category}>
              <StepButton onClick={() => setCategory(null)}>
                {t('Category')}
              </StepButton>
            </Step>
            <Step>
              <StepButton disabled={!category}>
                {category ? t(getCategoryDef(category).label) : t('Details')}
              </StepButton>
            </Step>
          </Stepper>
          {renderStepContent()}
        </>
      </Drawer>
    </>
  );
};

export default EndpointCreation;
