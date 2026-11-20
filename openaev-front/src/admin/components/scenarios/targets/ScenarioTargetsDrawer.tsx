import { Button, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { NO_TARGETS, type ScenarioTargets } from './scenarioTargets';
import ScenarioTargetsFields from './ScenarioTargetsFields';

interface Props {
  title: string;
  description: string;
  // A chained scenario acts inside its scope: it takes no targets.
  withTargets: boolean;
  submitLabel: string;
  onSubmit: (targets: ScenarioTargets) => Promise<unknown>;
  onClose: () => void;
}

const ScenarioTargetsDrawer: FunctionComponent<Props> = ({ title, description, withTargets, submitLabel, onSubmit, onClose }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [targets, setTargets] = useState<ScenarioTargets>(NO_TARGETS);
  const [submitting, setSubmitting] = useState(false);

  const submit = () => {
    setSubmitting(true);
    onSubmit(targets)
      .catch(() => undefined)
      .finally(() => setSubmitting(false));
  };

  return (
    <Drawer open handleClose={onClose} title={title}>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(3),
      }}
      >
        <Text variant="content-base">{description}</Text>
        {withTargets && <ScenarioTargetsFields value={targets} onChange={setTargets} />}
        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: theme.spacing(1),
        }}
        >
          <Button type="button" priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
          <Button type="button" loading={submitting} onClick={submit}>{submitLabel}</Button>
        </div>
      </div>
    </Drawer>
  );
};

export default ScenarioTargetsDrawer;
