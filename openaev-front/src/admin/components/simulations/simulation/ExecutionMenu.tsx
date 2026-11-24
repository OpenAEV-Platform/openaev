import { Icon } from '@filigran/design-system';
import { type FunctionComponent, useContext } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../../components/common/menu/RightMenu';
import { type Exercise } from '../../../../utils/api-types';
import { AutonomousContext } from '../../autonomous/AutonomousContext';

interface Props { exerciseId: Exercise['exercise_id'] }

// Navigates inside the Execution tab only, so it starts level with the tab bar.
// The simulation shell reserves its gutter.
const ExecutionMenu: FunctionComponent<Props> = ({ exerciseId }) => {
  // Autonomous runs use the right column for the reasoning panel.
  const { isAutonomous } = useContext(AutonomousContext);
  if (isAutonomous) {
    return null;
  }
  const base = `/admin/simulations/${exerciseId}/execution`;
  const entries: RightMenuEntry[] = [
    {
      path: `${base}/timeline`,
      icon: () => (<Icon name="radar" size={16} />),
      label: 'Overview',
    },
    {
      path: `${base}/mails`,
      icon: () => (<Icon name="mail" size={16} />),
      label: 'Mails',
    },
    {
      path: `${base}/validations`,
      icon: () => (<Icon name="list-checks" size={16} />),
      label: 'Validations',
    },
    {
      path: `${base}/logs`,
      icon: () => (<Icon name="square-pen" size={16} />),
      label: 'Simulation logs',
    },
  ];

  return (
    <RightMenu entries={entries} variant="sticky" />
  );
};

export default ExecutionMenu;
