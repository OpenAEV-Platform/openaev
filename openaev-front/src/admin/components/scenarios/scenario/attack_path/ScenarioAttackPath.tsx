/**
 * ScenarioAttackPath — Attack path tab for a Scenario page.
 *
 * Shows a compact simulation selector dropdown at the top.
 * Auto-selects the latest simulation. Attack path below is identical
 * to the SimulationAttackPath page (shared AttackPathContent component).
 */
import { type FunctionComponent, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { MenuItem, Select, Typography } from '@mui/material';

import { isFeatureEnabled } from '../../../../../utils/utils';
import { useFormatter } from '../../../../../components/i18n';
import {
  IS_ATTACK_PATH_POC,
  SCENARIO_SIMULATIONS_MAP,
} from '../../../simulations/simulation/attack_path/mockAttackPathData';
import AttackPathContent from '../../../simulations/simulation/attack_path/AttackPathContent';

const FALLBACK_SIMS = [
  { id: 'e65260ad-4685-4489-8f0d-8b316db695c9', name: 'Finance Dept — Run #1', date: '2026-05-01T09:00:00Z' },
  { id: 'f4e195cd-920f-4882-89f3-9b56aa63329b', name: 'APT Mid-Enterprise — Run #1', date: '2026-04-01T09:00:00Z' },
  { id: '2a4648ff-14e7-422e-bf0f-d533368bdaf5', name: 'Large Enterprise — Run #1', date: '2026-03-01T09:00:00Z' },
];

const TOPBAR_H = 44;

const ScenarioAttackPath: FunctionComponent = () => {
  const { fldt } = useFormatter();
  const { scenarioId } = useParams<{ scenarioId: string }>();
  const navigate = useNavigate();

  if (!IS_ATTACK_PATH_POC) return null;

  // Sort newest first, auto-select the latest
  const rawList = SCENARIO_SIMULATIONS_MAP[scenarioId ?? ''] ?? FALLBACK_SIMS;
  const simList = [...rawList].sort(
    (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime(),
  );

  const [selectedSimId, setSelectedSimId] = useState<string>(() => simList[0]?.id ?? '');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 260px)', overflow: 'hidden' }}>

      {/* Compact simulation filter bar */}
      <div style={{
        flexShrink: 0,
        height: TOPBAR_H,
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '0 16px',
        borderBottom: '1px solid rgba(255,255,255,0.08)',
        backgroundColor: 'rgba(0,0,0,0.12)',
      }}>
        <Typography variant="caption" sx={{ opacity: 0.45, whiteSpace: 'nowrap', fontSize: 11 }}>
          Simulation
        </Typography>
        <Select
          size="small"
          value={selectedSimId}
          onChange={(e) => setSelectedSimId(e.target.value as string)}
          sx={{
            fontSize: 12,
            height: 30,
            minWidth: 320,
            '& .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.15)' },
            '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255,255,255,0.3)' },
          }}
        >
          {simList.map((sim, idx) => (
            <MenuItem key={sim.id} value={sim.id} sx={{ fontSize: 12 }}>
              <span style={{ color: '#90caf9', fontWeight: 600, marginRight: 8 }}>
                {fldt(sim.date)}
              </span>
              {idx === 0 && (
                <span style={{
                  fontSize: 9,
                  fontWeight: 700,
                  letterSpacing: 0.8,
                  color: '#64b5f6',
                  textTransform: 'uppercase',
                  marginRight: 8,
                  border: '1px solid #64b5f644',
                  borderRadius: 3,
                  padding: '1px 4px',
                }}>
                  latest
                </span>
              )}
              <span style={{ opacity: 0.65 }}>{sim.name}</span>
            </MenuItem>
          ))}
        </Select>
      </div>

      <AttackPathContent
        key={selectedSimId}
        exerciseId={selectedSimId}
        onStatClick={(filter) => navigate(`/admin/scenarios/${scenarioId}/findings?filter=${filter}`)}
        height={`calc(100vh - 260px - ${TOPBAR_H}px)`}
      />
    </div>
  );
};

export default ScenarioAttackPath;
