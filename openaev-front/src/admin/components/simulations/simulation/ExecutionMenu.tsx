import { FactCheckOutlined, MailOutlined, NoteAltOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { type FunctionComponent, useContext } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../../components/common/menu/RightMenu';
import { type Exercise } from '../../../../utils/api-types';
import { AutonomousContext } from '../../autonomous/AutonomousContext';

interface Props { exerciseId: Exercise['exercise_id'] }

// Permanent right-hand menu of the simulation Execution area (overview, mails,
// validations, logs). The simulation Index pads the content area by the menu
// width whenever the location is under /execution.
const ExecutionMenu: FunctionComponent<Props> = ({ exerciseId }) => {
  // Autonomous (AI-driven) runs reserve the right column for the reasoning
  // panel, so the legacy execution right menu is suppressed to avoid overlap.
  const { isAutonomous } = useContext(AutonomousContext);
  if (isAutonomous) {
    return null;
  }
  const base = `/admin/simulations/${exerciseId}/execution`;
  const entries: RightMenuEntry[] = [
    {
      path: `${base}/timeline`,
      icon: () => (<TrackChangesOutlined />),
      label: 'Overview',
    },
    {
      path: `${base}/mails`,
      icon: () => (<MailOutlined />),
      label: 'Mails',
    },
    {
      path: `${base}/validations`,
      icon: () => (<FactCheckOutlined />),
      label: 'Validations',
    },
    {
      path: `${base}/logs`,
      icon: () => (<NoteAltOutlined />),
      label: 'Simulation logs',
    },
  ];

  return (
    <RightMenu entries={entries} />
  );
};

export default ExecutionMenu;
