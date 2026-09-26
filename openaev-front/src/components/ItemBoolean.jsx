import { Chip, Spinner, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { withStyles } from 'tss-react/mui';

import EEChip from '../admin/components/common/entreprise_edition/EEChip';
import inject18n from './i18n';

const styles = () => ({
  chip: {
    fontSize: 12,
    height: 25,
    marginRight: 7,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 120,
  },
  chipLarge: {
    fontSize: 12,
    height: 25,
    marginRight: 7,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 150,
  },
  chipXLarge: {
    fontSize: 12,
    height: 25,
    marginRight: 7,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 250,
  },
  chipInList: {
    fontSize: 12,
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 140,
  },
});

const RenderChip = (props) => {
  const { label, neutralLabel, status, t, reverse } = props;
  if (status === true) {
    return (
      <Chip label={label} severity={reverse ? 'critical' : 'low'} />
    );
  }
  if (status === null) {
    return (
      <Chip label={neutralLabel || t('Not applicable')} severity="info" />
    );
  }
  if (status === 'ee') {
    return (
      <EEChip />
    );
  }
  if (status === undefined) {
    return (
      <Chip label={t('Loading')} severity="info" startIcon={<Spinner size="sm" />} />
    );
  }
  return (
    <Chip label={label} severity={reverse ? 'low' : 'critical'} />
  );
};
const ItemBooleanComponent = (props) => {
  const { tooltip } = props;
  if (tooltip) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <Box component="span" sx={{ display: 'inline-block' }}>
            <RenderChip {...props} />
          </Box>
        </TooltipTrigger>
        {tooltip && <TooltipContent>{tooltip}</TooltipContent>}
      </Tooltip>
    );
  }
  return <RenderChip {...props} />;
};

ItemBooleanComponent.propTypes = {
  classes: PropTypes.object.isRequired,
  status: PropTypes.oneOfType([PropTypes.bool, PropTypes.string]),
  label: PropTypes.string,
  neutralLabel: PropTypes.string,
  variant: PropTypes.string,
  reverse: PropTypes.bool,
  tooltip: PropTypes.string,
};

const ItemBoolean = R.compose(
  inject18n,
  Component => withStyles(Component, styles),
)(ItemBooleanComponent);

export default ItemBoolean;
