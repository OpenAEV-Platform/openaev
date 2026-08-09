import { Paper, Typography } from '@mui/material';

import { SECTION_LABEL_SX } from '../../../../components/common/detail/detailStyles';
import { useFormatter } from '../../../../components/i18n';

interface Props {
  /** Already-translated description (per-type fallback), or undefined when unknown. */
  description?: string;
}

/**
 * Overview content for a built-in connector. Built-ins have no catalog entry, so
 * the catalog info card is skipped and the Overview tab would otherwise render
 * empty. This shows at least the connector's description, styled like the
 * "Description" card of the catalog overview.
 */
const ConnectorBuiltinInfo = ({ description }: Props) => {
  const { t } = useFormatter();

  return (
    <section style={{
      display: 'flex',
      flexDirection: 'column',
    }}
    >
      <Typography sx={SECTION_LABEL_SX}>{t('Description')}</Typography>
      <Paper
        variant="outlined"
        sx={{
          padding: 2,
          borderRadius: 1,
        }}
      >
        <Typography
          variant="body1"
          sx={{
            whiteSpace: 'pre-line',
            color: description ? 'text.primary' : 'text.secondary',
          }}
        >
          {description || '-'}
        </Typography>
      </Paper>
    </section>
  );
};

export default ConnectorBuiltinInfo;
