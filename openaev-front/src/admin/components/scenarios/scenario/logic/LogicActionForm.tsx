import { type FunctionComponent } from 'react';

import { type AtomicTestingInput, type InjectInput } from '../../../../../utils/api-types';
import type { InjectorContractFullOutput } from '../../../../../utils/api-types';
import { useFormatter } from '../../../../../components/i18n';
import CreateInject from '../../../common/injects/CreateInject';
import LogicActionPreviewPanel from '../../../common/injects/LogicActionPreviewPanel';

interface Props {
  open: boolean;
  handleClose: () => void;
  onContractSelected: (data: InjectInput | AtomicTestingInput) => Promise<void>;
}

const LogicActionForm: FunctionComponent<Props> = ({
  open,
  handleClose,
  onContractSelected,
}) => {
  const { t } = useFormatter();

  const renderLogicPreviewPanel = (
    contract: InjectorContractFullOutput,
    onAdd: (input: InjectInput) => void,
    onCancel: () => void,
  ) => (
    <LogicActionPreviewPanel
      contract={contract}
      onSubmit={(title, contractId, injectContent) => {
        const cleanContent: Record<string, unknown> = {};
        for (const [k, v] of Object.entries(injectContent)) {
          if (!k.startsWith('__link_')) cleanContent[k] = v;
        }
        const input: InjectInput = {
          inject_title: title,
          inject_injector_contract: contractId,
          inject_content: cleanContent,
          inject_type: undefined,
          inject_description: '',
          inject_depends_duration: 0,
          inject_teams: [],
          inject_assets: [],
          inject_asset_groups: [],
          inject_documents: [],
          inject_tags: [],
          inject_enabled: true,
          inject_all_teams: false,
        };
        onAdd(input);
      }}
      onCancel={onCancel}
    />
  );

  return (
    <CreateInject
      title={t('Select an action')}
      onCreateInject={onContractSelected}
      open={open}
      handleClose={handleClose}
      isAtomic={false}
      logicPreviewPanel={renderLogicPreviewPanel}
    />
  );
};

export default LogicActionForm;
