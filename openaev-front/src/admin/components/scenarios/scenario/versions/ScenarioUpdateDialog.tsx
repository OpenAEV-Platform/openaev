import {
  Alert,
  Button,
  Chip,
  type ChipSeverity,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Spinner,
  Text,
} from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import ConflictChoice from '../../../../../components/common/update/ConflictChoice';
import { useFormatter } from '../../../../../components/i18n';
import { type ScenarioChangeOutput } from '../../../../../utils/api-types';
import { changeLabel, valueText } from './scenarioChanges';
import { type ScenarioUpdateTarget } from './scenarioUpdateTargets';
import useScenarioUpdate from './useScenarioUpdate';

interface Props {
  target: ScenarioUpdateTarget;
  onClose: () => void;
  onApplied: () => void;
}

const STATUSES: Record<ScenarioChangeOutput['scenario_change_status'], {
  label: string;
  severity: ChipSeverity;
}> = {
  TAKE_APPLIED: {
    label: 'Taken',
    severity: 'info',
  },
  KEEP_MINE: {
    label: 'Your change, kept',
    severity: 'neutral',
  },
  CONFLICT: {
    label: 'Conflict',
    severity: 'medium',
  },
  ADDED: {
    label: 'Added',
    severity: 'low',
  },
  REMOVED: {
    label: 'Removed',
    severity: 'medium',
  },
};

// What an update or a rollback changes, the side to keep for each conflict, then applying it.
const ScenarioUpdateDialog: FunctionComponent<Props> = ({ target, onClose, onApplied }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const column = {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: theme.spacing(1.5),
  };
  const { loading, conflicts, others, choices, choose, ready, applying, apply } = useScenarioUpdate(target, onApplied);

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="lg">
        <DialogTitle>{target.title}</DialogTitle>
        <DialogBody>
          {loading ? <Spinner /> : (
            <div style={column}>
              <Alert severity="info" title={t('Your targets stay as they are.')} />
              {conflicts.length > 0 && (
                <div style={column}>
                  <Text variant="title-xs">{t('Choose the value to keep for each conflict')}</Text>
                  {conflicts.map((conflict) => {
                    const choice = choices[conflict.scenario_change_id];
                    return (
                      <ConflictChoice
                        key={conflict.scenario_change_id}
                        title={changeLabel(conflict, t)}
                        theirsLabel={target.theirsLabel}
                        mineLabel={t('Yours')}
                        theirs={valueText(conflict.scenario_change_applied)}
                        mine={valueText(conflict.scenario_change_local)}
                        selected={choice && (choice === 'TAKE_APPLIED' ? 'theirs' : 'mine')}
                        onSelect={side => choose(conflict.scenario_change_id, side === 'theirs' ? 'TAKE_APPLIED' : 'KEEP_MINE')}
                      />
                    );
                  })}
                </div>
              )}
              {others.length > 0 && (
                <div style={column}>
                  <Text variant="title-xs">{t('Changes')}</Text>
                  {others.map(change => (
                    <div
                      key={change.scenario_change_id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: theme.spacing(1),
                      }}
                    >
                      <Chip label={t(STATUSES[change.scenario_change_status].label)} severity={STATUSES[change.scenario_change_status].severity} />
                      <Text variant="content-base">{changeLabel(change, t)}</Text>
                    </div>
                  ))}
                </div>
              )}
              {conflicts.length === 0 && others.length === 0 && (
                <Text variant="content-base">{t('Nothing changes in the content.')}</Text>
              )}
            </div>
          )}
        </DialogBody>
        <DialogFooter>
          <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
          <Button onClick={apply} disabled={!ready || applying}>{target.applyLabel}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default ScenarioUpdateDialog;
