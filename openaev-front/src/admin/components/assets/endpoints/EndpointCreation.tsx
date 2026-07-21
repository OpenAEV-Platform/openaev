import { Button } from '@mui/material';
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

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={category ? t(getCategoryDef(category).label) : t('Create a new asset')}
      >
        {!category
          ? (<AssetCategoryPicker onSelect={setCategory} />)
          : (
              <>
                <Button size="small" onClick={() => setCategory(null)} style={{ alignSelf: 'flex-start' }}>
                  {t('Change category')}
                </Button>
                {category === 'AI_TARGET'
                  ? (
                      <AiTargetForm
                        onSubmit={onSubmitAiTarget}
                        handleClose={handleClose}
                      />
                    )
                  : (
                      <AssetForm
                        category={category}
                        agentless={agentless}
                        onSubmit={onSubmit}
                        handleClose={handleClose}
                      />
                    )}
              </>
            )}
      </Drawer>
    </>
  );
};

export default EndpointCreation;
