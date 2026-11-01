import { Button, Spinner, Text } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { changeThreatArsenalVersion, fetchThreatArsenalVersions } from '../../../../actions/threat_arsenals/threatArsenal-version-actions';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import PayloadVersionSelect from '../../threat_arsenal/PayloadVersionSelect';
import { type RevokedInject } from './scenarioLaunchBlockers';

interface Props {
  contractId: string;
  injects: RevokedInject[];
  onMoved: () => void;
}

// F18 in place: move this scenario's injects off a revoked version, to another installed one.
const RevokedPayloadFix: FunctionComponent<Props> = ({ contractId, injects, onMoved }) => {
  const { t } = useFormatter();
  const [targets, setTargets] = useState<PayloadVersionOutput[] | null>(null);
  const [target, setTarget] = useState('');
  const [moving, setMoving] = useState(false);

  useEffect(() => {
    fetchThreatArsenalVersions(contractId)
      .then((response: { data: PayloadVersionOutput[] }) => {
        const usable = response.data.filter(v => v.payload_installed && !v.payload_revoked && v.payload_action_id !== contractId);
        setTargets(usable);
        setTarget((usable.find(v => v.payload_is_latest) ?? usable[0])?.payload_version ?? '');
      })
      .catch(() => setTargets([]));
  }, [contractId]);

  if (targets === null) {
    return <Spinner size="sm" />;
  }
  if (targets.length === 0) {
    return (
      <Text variant="content-caption">
        {t('No other version is installed: install one from the Threat Arsenal, or remove these injects.')}
      </Text>
    );
  }
  const move = () => {
    const chosen = targets.find(v => v.payload_version === target);
    if (!chosen?.payload_id) return;
    setMoving(true);
    changeThreatArsenalVersion(contractId, {
      target_payload_id: chosen.payload_id,
      inject_ids: injects.map(inject => inject.id),
    })
      .then(onMoved)
      .finally(() => setMoving(false));
  };
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      flexWrap: 'wrap',
      gap: 8,
    }}
    >
      <PayloadVersionSelect
        value={target}
        options={targets.map(v => ({
          version: v.payload_version ?? '',
          latest: v.payload_is_latest,
          installed: true,
        }))}
        onChange={setTarget}
      />
      <Button size="sm" priority="secondary" disabled={!target || moving} onClick={move}>
        {moving ? <Spinner size="sm" /> : t('Move to this version')}
      </Button>
    </div>
  );
};

export default RevokedPayloadFix;
