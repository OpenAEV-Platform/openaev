import { Box, Paper, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import AttackPatternChip from '../../../../../components/AttackPatternChip';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import PlatformIconGroup from '../../../../../components/PlatformIconGroup';
import type { AttackPatternSimple, StatusPayloadOutput } from '../../../../../utils/api-types';
import { emptyFilled } from '../../../../../utils/String';

interface Props { payloadOutput?: StatusPayloadOutput }

const useStyles = makeStyles()(theme => ({
  paperContainer: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: theme.spacing(3),
  },
  allWidth: { gridColumn: 'span 2' },
  platformIcon: { marginRight: theme.spacing(1) },
}));

const PayloadInfoPaper = ({ payloadOutput }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  if (!payloadOutput) {
    return (
      <Paper className="paper" variant="outlined">
        <Typography variant="body1">{t('No data available')}</Typography>
      </Paper>
    );
  }

  return (
    <Paper className={`paper ${classes.paperContainer}`} variant="outlined">
      <Typography className={classes.allWidth} variant="h2" gutterBottom>
        {payloadOutput.payload_name}
      </Typography>
      <Typography className={classes.allWidth} variant="body2" gutterBottom>
        {emptyFilled(payloadOutput.payload_description)}
      </Typography>

      <Box>
        <Typography variant="h3" gutterBottom>
          {t('Platforms')}
        </Typography>
        <PlatformIconGroup platforms={payloadOutput.payload_platforms} width={25} />
      </Box>
      <Box>
        <Typography variant="h3" gutterBottom>
          {t('Attack patterns')}
        </Typography>
        {payloadOutput.payload_attack_patterns && payloadOutput.payload_attack_patterns.length === 0 ? '-' : payloadOutput.payload_attack_patterns?.map((attackPattern: AttackPatternSimple) => (
          <AttackPatternChip key={attackPattern.attack_pattern_id} attackPattern={attackPattern}></AttackPatternChip>
        ))}
      </Box>
      <Box>
        <Typography variant="h3" gutterBottom>
          {t('Tags')}
        </Typography>
        <ItemTags
          variant="reduced-view"
          tags={payloadOutput.payload_tags}
        />
      </Box>
      <Box>
        <Typography variant="h3" gutterBottom>
          {t('External ID')}
        </Typography>
        {emptyFilled(payloadOutput.payload_external_id)}
      </Box>
    </Paper>
  );
};

export default PayloadInfoPaper;
