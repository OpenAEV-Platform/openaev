import { Chip, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { makeStyles } from 'tss-react/mui';

import { type AssetCategory, humanizeEnum } from '../../../../admin/components/assets/asset-categories';
import AssetCategoryIcon from '../../../../admin/components/assets/AssetCategoryIcon';
import { useFormatter } from '../../../i18n';

type Props = {
  type?: string;
  category?: AssetCategory | null;
};

const AssetTypeFragment = (props: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const useStyles = makeStyles()(() => ({
    typeChip: {
      'height': 20,
      'borderRadius': 4,
      'textTransform': 'uppercase',
      'minWidth': 100,
      'marginBottom': theme.spacing(0),
      '& .MuiChip-icon': {
        marginLeft: 6,
        color: 'inherit',
      },
    },
  }));

  const { classes } = useStyles();
  // The asset category is the meaningful business descriptor (Host, Web application, AI target,
  // ...). The raw discriminator type ("Endpoint") is only a storage detail - agentless web
  // applications are persisted as endpoints - so it is used as a last-resort fallback only.
  const label = props.category ? t(humanizeEnum(props.category)) : props.type;
  return (
    <Tooltip title={label}>
      <Chip
        variant="outlined"
        className={classes.typeChip}
        icon={<AssetCategoryIcon category={props.category} sx={{ fontSize: 14 }} />}
        label={label}
      />
    </Tooltip>
  );
};

export default AssetTypeFragment;
