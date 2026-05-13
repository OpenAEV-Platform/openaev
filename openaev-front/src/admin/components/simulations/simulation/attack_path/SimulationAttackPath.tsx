import { Alert } from '@mui/material';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import type { Exercise } from '../../../../../utils/api-types';
import { simpleCall } from '../../../../../utils/Action';
import { isFeatureEnabled } from '../../../../../utils/utils';
import type { AttackPathData } from './attackPathUtils';
import AttackPathGraph from './AttackPathGraph';
import AttackPathFeed from './AttackPathFeed';
import AttackPathStatsComponent from './AttackPathStats';

const SimulationAttackPath: FunctionComponent = () => {
  const { t } = useFormatter();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const attackPathEnabled = isFeatureEnabled('CHAINING_ATTACK_PATH');

  const [data, setData] = useState<AttackPathData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  // Single fetch from aggregate endpoint
  const fetchAttackPath = useCallback(async () => {
    try {
      const response = await simpleCall(`/api/exercises/${exerciseId}/attack-path`);
      setData(response.data);
      setError(null);
    } catch (e) {
      setError('Failed to load attack path data');
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [exerciseId]);

  useEffect(() => {
    if (!attackPathEnabled) {
      setLoading(false);
      return;
    }
    fetchAttackPath();
  }, [attackPathEnabled, fetchAttackPath]);

  // Poll for live updates every 10s
  useEffect(() => {
    if (!attackPathEnabled || !data) return undefined;
    const interval = setInterval(fetchAttackPath, 10_000);
    return () => clearInterval(interval);
  }, [attackPathEnabled, data, fetchAttackPath]);

  const handleSelectNode = useCallback((nodeId: string | null) => {
    setSelectedNodeId(nodeId);
  }, []);

  if (!attackPathEnabled) return null;
  if (loading) return <Loader />;

  if (error) {
    return (
      <Alert severity="error" sx={{ mt: 2 }}>
        {t(error)}
      </Alert>
    );
  }

  if (!data || data.attack_path_nodes.length === 0) {
    return (
      <Alert severity="info" sx={{ mt: 2 }}>
        {t('No chaining workflow configured for this simulation. Configure the attack chain in the scenario logic tab first.')}
      </Alert>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 260px)', overflow: 'hidden' }}>
      {/* Stats banner */}
      <AttackPathStatsComponent stats={data.attack_path_stats} />

      {/* Main content: Feed (left) + Graph (center) */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <AttackPathFeed
          nodes={data.attack_path_nodes}
          selectedNodeId={selectedNodeId}
          onSelectNode={handleSelectNode}
        />
        <AttackPathGraph
          nodes={data.attack_path_nodes}
          edges={data.attack_path_edges}
          selectedNodeId={selectedNodeId}
          onSelectNode={handleSelectNode}
        />
      </div>
    </div>
  );
};

export default SimulationAttackPath;

