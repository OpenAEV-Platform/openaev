import { CircularProgress, Grid, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { type AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import type { DocumentHelper } from '../../../actions/helper';
import { fetchThreatArsenalAction } from '../../../actions/threat_arsenals/threatArsenal-actions';
import AttackPatternChip from '../../../components/AttackPatternChip';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import ItemDomains from '../../../components/ItemDomains';
import ItemTags from '../../../components/ItemTags';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import {
  type AttackPattern,
  type Payload,
  type ThreatArsenalAction,
  type ThreatArsenalActionFullOutput,
} from '../../../utils/api-types';
import InjectIcon from '../common/injects/InjectIcon';
import PayloadComponent from '../payloads/PayloadComponent';

const toPayload = (action: ThreatArsenalActionFullOutput): Payload => {
  return {
    payload_id: action.action_id,
    payload_name: action.action_labels?.en ?? Object.values(action.action_labels ?? {})[0] ?? '',
    payload_created_at: action.action_created_at,
    payload_updated_at: action.action_updated_at,
    payload_description: action.action_description,
    payload_execution_arch: action.action_execution_arch,
    payload_platforms: action.action_platforms ?? [],
    payload_source: action.action_source,
    payload_status: action.action_status,
    payload_type: action.action_type,
    payload_external_id: action.action_external_id,
    payload_arguments: action.action_arguments,
    payload_cleanup_command: action.action_cleanup_command,
    payload_cleanup_executor: action.action_cleanup_executor,
    payload_collector_type: action.action_collector_type,
    payload_detection_remediations: action.action_detection_remediations,
    payload_expectations: action.action_expectations,
    payload_output_parsers: action.action_output_parsers,
    payload_prerequisites: action.action_prerequisites,
    command_content: action.command_content,
    command_executor: action.command_executor,
    dns_resolution_hostname: action.dns_resolution_hostname,
    executable_file: action.executable_file,
    file_drop_file: action.file_drop_file,
  } as Payload;
};

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
    fetchThreatArsenalAction(threatArsenalAction.injector_contract_id).then((result) => {
      setSelectedPayload(toPayload(result.data as ThreatArsenalActionFullOutput));
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
      title={t('Threat Arsenal information')}
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
