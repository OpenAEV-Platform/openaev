import { Alert, Button, Chip, Skeleton, Spinner, Text } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useReducer, useState } from 'react';

import { fdsGroupRowStyle, fdsTableFrameStyle, fdsTableSectionStyle } from '../../../../components/common/fdsTableSpec';
import { LIB_SURFACE_LAYER } from '../../../../components/common/libSurfaceBorder';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadUsageMoveOutput, type PayloadVersionOutput } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import moveUsages from './moveUsages';
import PayloadVersionSelect from './PayloadVersionSelect';
import PayloadUsagesTable from './usages/PayloadUsagesTable';
import { usageCount } from './usages/usageCounts';
import useVersionUsages from './usages/useVersionUsages';

interface VersionProps {
  version: PayloadVersionOutput;
  // The versions its usages can move to; none when the user cannot move them.
  targets: PayloadVersionOutput[];
  canMove: boolean;
  reloadKey: number;
  onMoved: () => void;
}

const PayloadVersion: FunctionComponent<VersionProps> = ({ version, targets, canMove, reloadKey, onMoved }) => {
  const { t } = useFormatter();
  const counted = useCountLabel();
  const theme = useTheme();
  const usages = useVersionUsages(version.payload_action_id, reloadKey);
  const [expanded, setExpanded] = useState(version.payload_is_latest);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  // The latest version is the usual target.
  const defaultTarget = (targets.find(v => v.payload_is_latest) ?? targets[0])?.payload_id ?? '';
  const [target, setTarget] = useState(defaultTarget);
  const [moving, setMoving] = useState(false);
  const [result, setResult] = useState<PayloadUsageMoveOutput | null>(null);
  // The moved usages leave the list: their names are kept for the refusal messages.
  const [names, setNames] = useState<Record<string, string>>({});
  useEffect(() => setSelected(new Set()), [usages]);
  useEffect(() => setTarget(defaultTarget), [defaultTarget]);

  const rows = usages?.payload_usages ?? [];

  const move = () => {
    const chosen = rows.filter(usage => selected.has(usage.usage_id));
    setNames(Object.fromEntries(chosen.map(usage => [usage.usage_id, usage.usage_name ?? usage.usage_id])));
    setMoving(true);
    moveUsages(version.payload_action_id, target, chosen)
      .then((moves) => {
        setResult(moves);
        onMoved();
      })
      .finally(() => setMoving(false));
  };

  return (
    <div>
      <button type="button" style={fdsGroupRowStyle(theme)} aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
        <Text variant="content-base-bold">{`v${version.payload_version}`}</Text>
        {usages && (
          <Text variant="content-base" className="text-default-secondary">
            {`(${counted(usageCount(usages), '1 inject', '{count} injects')})`}
          </Text>
        )}
        {version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
        <span style={{ flex: 1 }} />
        {expanded ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
      </button>
      {expanded && (
        <>
          {version.payload_revoked && <Alert severity="warning" title={t('These injects will fail. Move them to another version.')} />}
          {result && (
            <Alert
              severity={result.refused.length > 0 ? 'warning' : 'success'}
              title={`${t('Moved')}: ${result.moved_ids.length}`}
              description={result.refused.map(refusal => (
                <div key={refusal.usage_id}>{`${t('Not moved')}: ${names[refusal.usage_id] ?? refusal.usage_id} (${refusal.reason})`}</div>
              ))}
              action={<Button priority="secondary" size="sm" onClick={() => setResult(null)}>{t('Close')}</Button>}
            />
          )}
          {usages
            ? <PayloadUsagesTable usages={usages} selected={canMove ? selected : undefined} onSelect={canMove ? setSelected : undefined} />
            : <Skeleton className="h-16" />}
          {canMove && rows.length > 0 && (
            <div style={{
              ...fdsTableSectionStyle(theme),
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end',
              gap: theme.spacing(1),
            }}
            >
              {targets.length === 0
                ? <Text variant="content-compact" className="text-default-secondary">{t('No other version to move to')}</Text>
                : (
                    <>
                      <Text variant="content-compact" className="text-default-secondary" style={{ marginRight: 'auto' }}>
                        {t('{count} selected', { count: String(selected.size) })}
                      </Text>
                      <Text variant="content-compact">{t('Move to')}</Text>
                      <PayloadVersionSelect value={target} versions={targets} onChange={setTarget} />
                      <Button disabled={selected.size === 0 || target === '' || moving} onClick={move}>
                        {moving ? <Spinner size="sm" /> : t('Move')}
                      </Button>
                    </>
                  )}
            </div>
          )}
        </>
      )}
    </div>
  );
};

interface Props { versions: PayloadVersionOutput[] | null }

const PayloadVersionsTab: FunctionComponent<Props> = ({ versions }) => {
  const theme = useTheme();
  const canMove = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  // A move changes the usages of two versions: each version loads its own again.
  const [reloadKey, reload] = useReducer((key: number) => key + 1, 0);

  if (versions === null) {
    return <Skeleton className="h-24" />;
  }
  return (
    <div className={LIB_SURFACE_LAYER} style={fdsTableFrameStyle(theme)}>
      {versions.map(version => (
        <PayloadVersion
          key={version.payload_id}
          version={version}
          targets={versions.filter(v => v.payload_id !== version.payload_id && !v.payload_revoked)}
          canMove={canMove}
          reloadKey={reloadKey}
          onMoved={reload}
        />
      ))}
    </div>
  );
};

export default PayloadVersionsTab;
