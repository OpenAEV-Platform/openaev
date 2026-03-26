import {CircularProgress, Grid, Typography} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { type AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import { fetchPayload } from '../../../actions/payloads/payload-actions';
import AttackPatternChip from '../../../components/AttackPatternChip';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import ItemDomains from '../../../components/ItemDomains';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import {
  type AttackPattern,
  type InjectorContractActionOutput,
  type Payload,
} from '../../../utils/api-types';
import InjectIcon from '../common/injects/InjectIcon';
import PayloadComponent from '../payloads/PayloadComponent';
import type {DocumentHelper} from "../../../actions/helper";
import ItemTags from "../../../components/ItemTags";

interface Props {
  open: boolean;
  onClose: () => void;
  injectorContract: InjectorContractActionOutput | null;
}

const ThreatArsenalInformationDrawer: FunctionComponent<Props> = ({
  open,
  onClose,
  injectorContract,
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
    if (!open || !injectorContract) {
      return;
    }

    setSelectedPayload(null);

    if (!injectorContract.injector_contract_payload){
      return;
    }
    setLoading(true);
    fetchPayload(injectorContract!.injector_contract_payload!.payload_id!).then((result) => {
      setSelectedPayload((result.data?? result) as Payload);
      setLoading(false);
    })
  }, [open, injectorContract]);

  const attackPatterns = useMemo(() => {
    const ids = injectorContract?.injector_contract_attack_patterns ?? [];
    return ids
      .map((id:string) => attackPatternsMap[id])
      .filter(Boolean) as AttackPattern[];
  }, [attackPatternsMap, injectorContract]);

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Threat arsenal information')}
    >
      {loading || injectorContract == null? (
        <CircularProgress size={28} />
      ) : injectorContract.injector_contract_payload ? (
        <PayloadComponent
          selectedPayload={selectedPayload}
          documentsMap={documentsMap}
          attackPatternIds={injectorContract?.injector_contract_attack_patterns ?? []}
          domains={injectorContract?.injector_contract_domains ?? []}
          tagIds={injectorContract?.injector_contract_tags ?? []}
        />
      ) : (
        <Grid container display="grid" gridTemplateColumns="1fr 1fr" gap={2}>
          <Typography style={{gridColumn: 'span 2'}} variant="h2" gutterBottom>{tPick(injectorContract?.injector_contract_labels) || '-'}</Typography>


          <div>
            <Typography variant="h3" gutterBottom>{t('Platforms')}</Typography>
          {(injectorContract?.injector_contract_platforms ?? []).length > 0 ? injectorContract!.injector_contract_platforms!.map(platform => (
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
            <ItemDomains domains={injectorContract?.injector_contract_domains ?? []} variant="list" />
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
            tags={injectorContract?.injector_contract_tags}
          />
          </div>


          <div>
            <Typography variant="h3" gutterBottom>{t('Injector type')}</Typography>
            {injectorContract?.injector_contract_injector_type ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: theme.spacing(1) }}>
                <InjectIcon
                  variant="list"
                  type={injectorContract?.injector_contract_injector_type}
                  isPayload={false}
                />
                <Typography variant="body2">{injectorContract?.injector_contract_injector_type}</Typography>
              </div>
            ) : (
              <Typography variant="body2">-</Typography>
            )}
          </div>
        </Grid>
      )}
    </Drawer>
  );
};

export default ThreatArsenalInformationDrawer;

