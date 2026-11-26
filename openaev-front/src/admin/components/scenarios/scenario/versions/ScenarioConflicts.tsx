import { Button, Text, Textarea } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import ConflictChoice, { type ConflictSide } from '../../../../../components/common/update/ConflictChoice';
import TextDiff from '../../../../../components/common/update/TextDiff';
import { useFormatter } from '../../../../../components/i18n';
import { type ScenarioChangeOutput } from '../../../../../utils/api-types';
import { changeLabel, valueText } from './scenarioChanges';
import { type ScenarioUpdateTarget } from './scenarioUpdateTargets';
import { type ScenarioUpdateChoice } from './useScenarioUpdate';

const SIDES: Partial<Record<ScenarioUpdateChoice, ConflictSide>> = {
  TAKE_APPLIED: 'theirs',
  KEEP_MINE: 'mine',
};

interface Props {
  target: ScenarioUpdateTarget;
  conflicts: ScenarioChangeOutput[];
  choices: Record<string, ScenarioUpdateChoice>;
  edits: Record<string, string>;
  choose: (changeId: string, choice: ScenarioUpdateChoice) => void;
  chooseAll: (choice: ScenarioUpdateChoice) => void;
  startEdit: (conflict: ScenarioChangeOutput) => void;
  edit: (changeId: string, value: string) => void;
}

// The conflicts of an update or a rollback: a side for each, or a value typed for a text.
const ScenarioConflicts: FunctionComponent<Props> = ({ target, conflicts, choices, edits, choose, chooseAll, startEdit, edit }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const column = {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: theme.spacing(1.5),
  };

  // An inject the applied content removes but the client changed: its own, or deleted.
  const removedConflict = (conflict: ScenarioChangeOutput) => (
    <ConflictChoice
      key={conflict.scenario_change_id}
      title={`${changeLabel(conflict, t)} · ${target.removedConflictLabel}`}
      theirsLabel={t('Delete it')}
      mineLabel={t('Keep it as my own inject')}
      theirs={null}
      mine={null}
      selected={SIDES[choices[conflict.scenario_change_id]]}
      onSelect={side => choose(conflict.scenario_change_id, side === 'theirs' ? 'TAKE_APPLIED' : 'KEEP_MINE')}
    />
  );

  const fieldConflict = (conflict: ScenarioChangeOutput) => {
    const id = conflict.scenario_change_id;
    const editing = choices[id] === 'EDIT';
    const theirs = valueText(conflict, conflict.scenario_change_applied);
    const mine = valueText(conflict, conflict.scenario_change_local);
    return (
      <div key={id} style={column}>
        <ConflictChoice
          title={changeLabel(conflict, t)}
          theirsLabel={target.theirsLabel}
          mineLabel={t('Yours')}
          theirs={<TextDiff text={theirs} other={mine} />}
          mine={<TextDiff text={mine} other={theirs} />}
          selected={SIDES[choices[id]]}
          onSelect={side => choose(id, side === 'theirs' ? 'TAKE_APPLIED' : 'KEEP_MINE')}
        />
        {conflict.scenario_change_editable && (
          <div>
            <Button size="sm" priority={editing ? 'primary' : 'tertiary'} aria-pressed={editing} onClick={() => startEdit(conflict)}>
              {t('Edit')}
            </Button>
          </div>
        )}
        {editing && (
          <Textarea
            label={t('Value')}
            value={edits[id] ?? ''}
            onChange={event => edit(id, event.target.value)}
            rows={4}
            resize="none"
          />
        )}
      </div>
    );
  };

  return (
    <div style={column}>
      <Text variant="content-base">{target.conflictsIntro}</Text>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: theme.spacing(1),
      }}
      >
        <Text variant="title-xs">{t('Conflicts ({count})', { count: String(conflicts.length) })}</Text>
        <div style={{
          display: 'flex',
          gap: theme.spacing(1),
        }}
        >
          <Button size="sm" priority="secondary" onClick={() => chooseAll('KEEP_MINE')}>{t('Keep all mine')}</Button>
          <Button size="sm" priority="secondary" onClick={() => chooseAll('TAKE_APPLIED')}>{t('Take all')}</Button>
        </div>
      </div>
      {conflicts.map(conflict => (conflict.scenario_change_field_kind ? fieldConflict(conflict) : removedConflict(conflict)))}
    </div>
  );
};

export default ScenarioConflicts;
