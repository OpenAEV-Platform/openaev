import { Alert, Chip, IconButton, Text } from '@filigran/design-system';
import { EditOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type InjectTargetsOutput } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import { isTargetedWith, type ScenarioTargets } from './scenarioTargets';
import TargetKindIcon from './TargetKindIcon';
import TargetsGroup from './TargetsGroup';

interface Props {
  scenarioId: string;
  injects: InjectTargetsOutput[];
  targets: ScenarioTargets;
}

// The injects that take targets, each with whether it has one once the chosen targets are added.
const InjectTargetsList: FunctionComponent<Props> = ({ scenarioId, injects, targets }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const countLabel = useCountLabel();
  const untargeted = injects.filter(inject => !isTargetedWith(inject, targets)).length;

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1),
    }}
    >
      <Text variant="content-base-bold">{`${t('Injects')} (${injects.length})`}</Text>
      <TargetsGroup>
        {injects.map(inject => (
          <div
            key={inject.inject_id}
            style={{
              display: 'grid',
              gridTemplateColumns: `${theme.spacing(2.5)} minmax(0, 1fr) auto auto`,
              alignItems: 'center',
              gap: theme.spacing(1.5),
            }}
          >
            <TargetKindIcon kind={inject.inject_target_kinds[0]} />
            <Text variant="content-base">{inject.inject_title}</Text>
            {isTargetedWith(inject, targets)
              ? <Chip label={t('Targeted')} severity="low" />
              : <Chip label={t('Missing target')} severity="high" />}
            <IconButton asChild size="sm" priority="tertiary" aria-label={t('Edit the injects')} icon={<EditOutlined fontSize="small" />}>
              <Link to={`/admin/scenarios/${scenarioId}/injects`} />
            </IconButton>
          </div>
        ))}
        {untargeted > 0 && (
          <Alert
            key="untargeted"
            elevation={1}
            severity="warning"
            title={countLabel(untargeted, '1 inject has no target', '{count} injects have no target')}
            description={t('Launch stays disabled until each targetable inject has a target.')}
          />
        )}
      </TargetsGroup>
    </div>
  );
};

export default InjectTargetsList;
