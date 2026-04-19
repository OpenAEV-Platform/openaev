import { CircularProgress, Grid, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { type AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import type { DocumentHelper } from '../../../actions/helper';
import { fetchPayload } from '../../../actions/payloads/payload-actions';
import AttackPatternChip from '../../../components/AttackPatternChip';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import ItemDomains from '../../../components/ItemDomains';
import ItemTags from '../../../components/ItemTags';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import {
  type AttackPattern,
  type Payload, type ThreatArsenalAction,
} from '../../../utils/api-types';
import InjectIcon from '../common/injects/InjectIcon';
import PayloadComponent from '../payloads/PayloadComponent';

interface Props {
  open: boolean;
  onClose: () => void;
  threatArsenalAction: ThreatArsenalAction | null;
}

const ThreatArsenalInformationDrawer: FunctionComponent<Props> = ({
  open,
  onClose,
  threatArsenalAction,
}) => {
  const theme = useTheme();
  const { t, tPick } = useFormatter();

  const { attackPatternsMap, documentsMap } = useHelper((helper: AttackPatternHelper & DocumentHelper) => ({
    attackPatternsMap: helper.getAttackPatternsMap(),
    documentsMap: helper.getDocumentsMap(),
  }));

  const [loading, setLoading] = useState(false);
  const [selectedPayload, setSelectedPayload] = useState<Payload | null>(null);

  useEffect(() => {
    if (!open || !threatArsenalAction) {
      return;
    }

    setSelectedPayload(null);

    if (!threatArsenalAction.action_payload) {
      return;
    }
    setLoading(true);
    fetchPayload(threatArsenalAction!.action_payload!.payload_id!).then((result) => {
      setSelectedPayload((result.data ?? result) as Payload);
      setLoading(false);
    });
  }, [open, threatArsenalAction]);

  const attackPatterns = useMemo(() => {
    return (threatArsenalAction?.action_attack_patterns_ids ?? [])
      .map((id: string) => attackPatternsMap[id])
      .filter(Boolean) as AttackPattern[];
  }, [attackPatternsMap, threatArsenalAction]);

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Threat arsenal information')}
    >
      <>
        {(loading || threatArsenalAction == null) && <CircularProgress size={28} />}

        {!loading && threatArsenalAction != null && threatArsenalAction.action_payload && (
          <PayloadComponent
            selectedPayload={selectedPayload}
            documentsMap={documentsMap}
            attackPatternIds={threatArsenalAction?.action_attack_patterns_ids ?? []}
            domains={threatArsenalAction?.action_domains_ids ?? []}
            tagIds={threatArsenalAction?.action_tags_ids ?? []}
          />
        )}

        {!loading && threatArsenalAction != null && threatArsenalAction.action_payload == null && (
          <Grid container display="grid" gridTemplateColumns="1fr 1fr" gap={2}>
            <Typography style={{ gridColumn: 'span 2' }} variant="h2" gutterBottom>{tPick(threatArsenalAction?.action_labels) || '-'}</Typography>

            <div>
              <Typography variant="h3" gutterBottom>{t('Platforms')}</Typography>
              {(threatArsenalAction?.action_platforms ?? []).length > 0 ? threatArsenalAction!.action_platforms!.map(platform => (
                <PlatformIcon
                  key={platform}
                  platform={platform}
                  width={24}
                  marginRight={theme.spacing(2)}
                />
              )) : (
                <Typography variant="body2">-</Typography>
              )}
            </div>

            <div>
              <Typography variant="h3" gutterBottom>{t('Attack patterns')}</Typography>
              {attackPatterns.length > 0 ? attackPatterns.map(attackPattern => (
                <AttackPatternChip
                  key={attackPattern.attack_pattern_id}
                  attackPattern={attackPattern}
                />
              )) : (
                <Typography variant="body2">-</Typography>
              )}

            </div>

            <div>
              <Typography variant="h3" gutterBottom>{t('Domains')}</Typography>
              <ItemDomains domains={threatArsenalAction?.action_domains_ids ?? []} variant="list" />
            </div>

            <div>
              <Typography
                variant="h3"
                gutterBottom
              >
                {t('Tags')}
              </Typography>
              <ItemTags
                variant="reduced-view"
                tags={threatArsenalAction?.action_tags_ids}
              />
            </div>

            <div>
              <Typography variant="h3" gutterBottom>{t('Injector type')}</Typography>
              {threatArsenalAction?.action_injector_type ? (
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: theme.spacing(1),
                }}
                >
                  <InjectIcon
                    variant="list"
                    type={threatArsenalAction?.action_injector_type}
                    isPayload={false}
                  />
                  <Typography variant="body2">{threatArsenalAction?.action_injector_type}</Typography>
                </div>
              ) : (
                <Typography variant="body2">-</Typography>
              )}
            </div>
          </Grid>
        )}
      </>
    </Drawer>
  );
};

export default ThreatArsenalInformationDrawer;
