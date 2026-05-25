import {
  type Node,
  useNodesState,
} from '@xyflow/react';
import { useMemo, useState } from 'react';

import { fetchWorkflowConfiguration } from '../../../../actions/chaining/workflow-actions';
import type { WorkflowConfigurationHelper } from '../../../../actions/chaining/workflow-helper';
import { useHelper } from '../../../../store';
import type { ThreatArsenalAction, WorkflowScopeRuleOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import AddActionDetail, { type ActionDetailData } from './AddActionDetail';
import AddActionList from './AddActionList';
import AddComponentButton, { type LogicContext } from './AddComponentButton';
import AddComponentDrawer from './AddComponentDrawer';

interface LogicProps {
  workflowId: string | undefined;
  context: LogicContext;
}

type DrawerView = 'closed' | 'choose' | 'action' | 'actionDetail';

const Logic = ({ workflowId, context }: LogicProps) => {
  const dispatch = useAppDispatch();

  // Fetch workflow configuration to get allowlisted assets
  useDataLoader(() => {
    if (workflowId) {
      dispatch(fetchWorkflowConfiguration(workflowId));
    }
  });

  const { workflowConfiguration } = useHelper(
    (helper: WorkflowConfigurationHelper) => ({
      workflowConfiguration: workflowId
        ? helper.getWorkflowConfiguration(workflowId)
        : undefined,
    }),
  );

  const allowListedAssetIds = useMemo(() => {
    const rules: WorkflowScopeRuleOutput[] = workflowConfiguration?.workflow_scope_rules ?? [];
    return rules
      .filter((r: WorkflowScopeRuleOutput) => r.workflow_scope_rule_selected_mode === 'ALLOWLIST' && r.workflow_scope_rule_source === 'ASSET')
      .map((r: WorkflowScopeRuleOutput) => r.workflow_scope_rule_value ?? '')
      .filter(Boolean);
  }, [workflowConfiguration]);
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

  const handleAddActions = (_selectedIds: string[]) => {
    // TODO: create steps from selected action IDs
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

  const handleSaveActionDetail = (_data: ActionDetailData) => {
    // TODO: create step from action detail data
    setDrawerView('closed');
    setSelectedAction(null);
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
      <AddActionDetail
        open={drawerView === 'actionDetail'}
        action={selectedAction}
        allowListedAssetIds={allowListedAssetIds}
        onClose={handleCloseAll}
        onBack={handleBackToActionList}
        onBackToRoot={handleBackToChoose}
        onSave={handleSaveActionDetail}
      />
    </div>
  );
};

export default Logic;
