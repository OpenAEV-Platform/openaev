import {
  type Node,
  useNodesState,
} from '@xyflow/react';
import { useCallback, useEffect, useState } from 'react';

import { createStep } from '../../../../actions/chaining/chaining-actions';
import { fetchValidAssets } from '../../../../actions/chaining/workflow-actions';
import { useFormatter } from '../../../../components/i18n';
import type { ScopeAssetOutput, ThreatArsenalAction } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import AddActionList from './AddActionList';
import AddComponentButton, { type LogicContext } from './AddComponentButton';
import AddComponentDrawer from './AddComponentDrawer';
import ConfigureActionDetail from './ConfigureActionDetail';
import { type ActionDetailData } from './types';

interface LogicProps {
  workflowId: string | undefined;
  context: LogicContext;
}

type DrawerView = 'closed' | 'choose' | 'action' | 'actionDetail';

const Logic = ({ workflowId, context }: LogicProps) => {
  const { t } = useFormatter();

  // Fetch computed valid assets from backend (allowlist minus denylist)
  const [validAssets, setValidAssets] = useState<ScopeAssetOutput[]>([]);

  const loadValidAssets = useCallback(() => {
    if (workflowId) {
      fetchValidAssets(workflowId).then((assets: ScopeAssetOutput[]) => {
        setValidAssets(assets);
      });
    }
  }, [workflowId]);

  useEffect(() => {
    loadValidAssets();
  }, [loadValidAssets]);

  const [nodes] = useNodesState<Node>([]);
  const [drawerView, setDrawerView] = useState<DrawerView>('closed');
  const [selectedAction, setSelectedAction] = useState<ThreatArsenalAction | null>(null);

  const handleOpenDrawer = () => setDrawerView('choose');
  const handleCloseAll = () => {
    setDrawerView('closed');
    setSelectedAction(null);
  };

  const handleSelectComponent = (type: 'action' | 'event') => {
    if (type === 'action') {
      setDrawerView('action');
    } else {
      // TODO: handle event creation
      setDrawerView('closed');
    }
  };

  const handleBackToChoose = () => setDrawerView('choose');

  const handleAddActions = (_selectedActions: ThreatArsenalAction[]) => {
    // TODO: create steps from selected actions
    setDrawerView('closed');
  };

  const handleSelectAction = (action: ThreatArsenalAction) => {
    setSelectedAction(action);
    setDrawerView('actionDetail');
  };

  const handleBackToActionList = () => {
    setSelectedAction(null);
    setDrawerView('action');
  };

  const handleSaveActionDetail = (data: ActionDetailData) => {
    if (!workflowId) return;

    // Build step_conditions from field links (type + local/global scope)
    const stepConditions = Object.entries(data.inject_field_links).map(([fieldKey, link], i) => ({
      condition_temporary_id: String(i),
      condition_type: 'MAPPER' as const,
      condition_key_type: link.outputType as 'text',
      condition_key: fieldKey,
      condition_mapping_type: (link.localScope ? 'LOCAL' : 'GLOBAL') as 'GLOBAL',
    }));

    createStep({
      step_workflow_id: workflowId,
      step_action: 'INJECT_EXECUTION',
      step_conditions: stepConditions.length > 0 ? stepConditions : undefined,
      step_data_step: {
        inject_title: data.inject_title,
        inject_injector_contract: data.inject_injector_contract,
        inject_assets: data.inject_assets,
        inject_content: data.inject_content,
        inject_tags: [],
        inject_all_teams: false,
        inject_teams: [],
        inject_asset_groups: [],
        inject_documents: [],
        inject_depends_duration: 0,
        inject_depends_on: [],
      },
    }).then(() => {
      MESSAGING$.notifySuccess(t('The step has been successfully created'));
      setDrawerView('closed');
      setSelectedAction(null);
    });
  };

  return (
    <div style={{
      width: '100%',
      height: 'calc(100vh - 230px)',
      position: 'relative',
    }}
    >
      <AddComponentButton nodeCount={nodes.length} context={context} onClick={handleOpenDrawer} />
      <AddComponentDrawer
        open={drawerView === 'choose'}
        onClose={handleCloseAll}
        onSelect={handleSelectComponent}
      />
      <AddActionList
        open={drawerView === 'action'}
        onClose={handleCloseAll}
        onBack={handleBackToChoose}
        onAddActions={handleAddActions}
        onSelectAction={handleSelectAction}
      />
      <ConfigureActionDetail
        open={drawerView === 'actionDetail'}
        action={selectedAction}
        validAssets={validAssets}
        onClose={handleCloseAll}
        onBack={handleBackToActionList}
        onBackToRoot={handleBackToChoose}
        onSave={handleSaveActionDetail}
      />
    </div>
  );
};

export default Logic;
