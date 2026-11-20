import {
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Spinner,
  Text,
} from '@filigran/design-system';
import { ArrowForwardOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { fdsGroupRowStyle, fdsTableFrameStyle, fdsTableSectionStyle } from '../../../../components/common/fdsTableSpec';
import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import { type OlderVersionInUse, scenarioCount, totalUsageCount, usageCount } from './usages/usageCounts';
import useNewVersionMove from './useNewVersionMove';
import useSkipNewVersionDialog from './useSkipNewVersionDialog';

interface Props {
  action: ThreatArsenalAction;
  olderVersions: OlderVersionInUse[];
  onClose: () => void;
}

const PayloadNewVersionDialog: FunctionComponent<Props> = ({ action, olderVersions, onClose }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const counted = useCountLabel();
  const [skipsDialog, setSkipsDialog] = useSkipNewVersionDialog();
  const selection = useNewVersionMove(action, olderVersions);
  const version = action.action_payload_version ?? '';
  const total = totalUsageCount(olderVersions);

  const moveLabel = selection.selectedCount === 0
    ? t('Move to v{version}', { version })
    : t('Move {count} to v{version}', {
        count: String(selection.selectedCount),
        version,
      });

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="lg">
        <DialogTitle>{t('New version v{version}', { version })}</DialogTitle>
        <DialogBody>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: theme.spacing(1),
          }}
          >
            <Text variant="content-base">
              {counted(total, '1 inject still uses an older version.', '{count} injects still use older versions.')}
            </Text>
            <div style={fdsTableFrameStyle(theme)}>
              <div style={{
                ...fdsTableSectionStyle(theme),
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(1),
              }}
              >
                <Text variant="content-base">
                  {t('{selected} of {count} selected', {
                    selected: String(selection.selectedCount),
                    count: String(selection.totalCount),
                  })}
                </Text>
                {selection.allSelected
                  ? <Button priority="tertiary" size="sm" onClick={selection.clear}>{t('Clear selection')}</Button>
                  : <Button priority="tertiary" size="sm" onClick={selection.selectAll}>{t('Select all {count}', { count: String(selection.totalCount) })}</Button>}
              </div>
              {olderVersions.map((older) => {
                const count = older.usages.payload_usages.length;
                const chosen = selection.selectedIn(older);
                return (
                  <div
                    key={older.version.payload_id}
                    style={{
                      ...fdsGroupRowStyle(theme),
                      cursor: 'default',
                    }}
                  >
                    <Checkbox
                      aria-label={t('Every inject on v{version}', { version: older.version.payload_version })}
                      checked={chosen === count || (chosen > 0 ? 'indeterminate' : false)}
                      disabled={count === 0}
                      onCheckedChange={() => selection.toggleVersion(older)}
                    />
                    <Text variant="content-base-bold">{`v${older.version.payload_version}`}</Text>
                    {older.version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
                    <Text variant="content-base" className="text-default-secondary">
                      {`(${t('{injects} in {scenarios}', {
                        injects: counted(usageCount(older.usages), '1 inject', '{count} injects'),
                        scenarios: counted(scenarioCount(older.usages), '1 scenario', '{count} scenarios'),
                      })})`}
                    </Text>
                    {chosen > 0 && chosen < count && (
                      <Text variant="content-compact" className="text-default-secondary" style={{ marginLeft: 'auto' }}>
                        {t('{count} selected', { count: String(chosen) })}
                      </Text>
                    )}
                  </div>
                );
              })}
            </div>
            <div style={{ marginTop: theme.spacing(1) }}>
              <Checkbox label={t('Don\'t ask me again')} checked={skipsDialog} onCheckedChange={checked => setSkipsDialog(checked === true)} />
            </div>
          </div>
        </DialogBody>
        <DialogFooter>
          <Button asChild priority="tertiary" style={{ marginRight: 'auto' }}>
            <Link to={`/admin/threat-arsenal?action=${encodeURIComponent(action.injector_contract_id)}&tab=versions&focus=move`} onClick={() => onClose()}>
              {t('Choose in the Versions tab')}
              <ArrowForwardOutlined fontSize="small" style={{ marginLeft: theme.spacing(0.75) }} />
            </Link>
          </Button>
          <Button priority="secondary" onClick={() => onClose()}>{t('Later')}</Button>
          <Button disabled={selection.moving || selection.selectedCount === 0} onClick={() => selection.move().then(moved => moved && onClose())}>
            {selection.moving ? <Spinner size="sm" /> : moveLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadNewVersionDialog;
