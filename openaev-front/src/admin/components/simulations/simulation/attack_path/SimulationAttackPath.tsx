import { type FunctionComponent } from 'react';
import { useNavigate, useParams } from 'react-router';

import { IS_ATTACK_PATH_POC } from './mockAttackPathData';
import AttackPathContent from './AttackPathContent';

const SimulationAttackPath: FunctionComponent = () => {
  const { exerciseId } = useParams<{ exerciseId: string }>();
  const navigate = useNavigate();

  if (!IS_ATTACK_PATH_POC) return null;

  return (
    <AttackPathContent
      exerciseId={exerciseId ?? ''}
      onStatClick={(filter) => navigate(`/admin/simulations/${exerciseId}/findings?filter=${filter}`)}
    />
  );
};

export default SimulationAttackPath;
