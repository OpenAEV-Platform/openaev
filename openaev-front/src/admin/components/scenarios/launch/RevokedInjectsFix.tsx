import { Button, Spinner, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadUsageOutput, type RevokedInjectOutput } from '../../../../utils/api-types';
import PayloadVersionSelect from '../../threat_arsenal/versions/PayloadVersionSelect';
import { moveTargets, moveUsages } from '../../threat_arsenal/versions/usages/usageMove';
import usePayloadVersions from '../../threat_arsenal/versions/usePayloadVersions';

interface Props {
  actionId: string;
  injects: RevokedInjectOutput[];
  onMoved: () => void;
}

// Moves this scenario's injects off a revoked payload version, to another version of the action.
const RevokedInjectsFix: FunctionComponent<Props> = ({ actionId, injects, onMoved }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const versions = usePayloadVersions(actionId, true);
  const source = versions?.find(version => version.payload_action_id === actionId);
  const { targets, defaultTarget } = moveTargets(versions ?? [], source?.payload_id ?? '');
  const [target, setTarget] = useState(defaultTarget);
  const [moving, setMoving] = useState(false);
  useEffect(() => setTarget(defaultTarget), [defaultTarget]);

  const usages: PayloadUsageOutput[] = injects.map(inject => ({
    usage_id: inject.inject_id,
    usage_name: inject.inject_title,
    usage_type: 'inject',
  }));
  const move = () => {
    setMoving(true);
    moveUsages(actionId, target, usages)
      .then(onMoved)
      .catch(() => undefined)
      .finally(() => setMoving(false));
  };
  let fix: ReactNode = (
    <>
      <PayloadVersionSelect value={target} versions={targets} onChange={setTarget} />
      <Button type="button" size="sm" priority="secondary" disabled={target === ''} loading={moving} onClick={move}>
        {t('Move to this version')}
      </Button>
    </>
  );
  if (versions === null) {
    fix = <Spinner size="sm" />;
  } else if (targets.length === 0) {
    fix = <Text variant="content-caption">{t('No other version to move to')}</Text>;
  }
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      flexWrap: 'wrap',
      gap: theme.spacing(1),
    }}
    >
      <Text variant="content-base-bold">{injects[0].inject_action_name}</Text>
      {fix}
    </div>
  );
};

export default RevokedInjectsFix;
