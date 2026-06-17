import { type FunctionComponent } from 'react';
import { useNavigate, useParams } from 'react-router';

import { isFeatureEnabled } from '../../../../../utils/utils';
import AttackPathContent from './AttackPathContent';

const SimulationAttackPath: FunctionComponent = () => {
  const { exerciseId } = useParams<{ exerciseId: string }>();
  const navigate = useNavigate();

  if (!isFeatureEnabled('CHAINING_ATTACK_PATH')) return null;

  return (
    <AttackPathContent
      exerciseId={exerciseId ?? ''}
      onStatClick={(filter) => navigate(`/admin/simulations/${exerciseId}/findings?filter=${filter}`)}
    />
  );
};

export default SimulationAttackPath;

