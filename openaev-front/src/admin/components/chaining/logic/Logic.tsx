import {
  type Node,
  useNodesState,
} from '@xyflow/react';
import { useState } from 'react';

import AddActionList from './AddActionList';
import AddComponentButton, { type LogicContext } from './AddComponentButton';
import AddComponentDrawer from './AddComponentDrawer';

interface LogicProps {
  workflowId: string | undefined;
  context: LogicContext;
}

type DrawerView = 'closed' | 'choose' | 'action';

const Logic = ({ workflowId: _workflowId, context }: LogicProps) => {
  const [nodes] = useNodesState<Node>([]);
  const [drawerView, setDrawerView] = useState<DrawerView>('closed');

  const handleOpenDrawer = () => setDrawerView('choose');
  const handleCloseAll = () => setDrawerView('closed');

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
      />
    </div>
  );
};

export default Logic;
