import { Box, CircularProgress, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { withStyles } from 'tss-react/mui';

import Tag from './common/tag/Tag';
import inject18n from './i18n';

const styles = () => ({});

const RenderChip = (props) => {
  const { label, neutralLabel, status, variant, t, reverse } = props;
  const theme = useTheme();

  const getTagProps = () => {
    if (status === true) {
      return {
        label: label,
        color: reverse ? theme.palette.error.main : theme.palette.success.main,
      };
    }
    if (status === false) {
      return {
        label: label,
        color: reverse ? theme.palette.success.main : theme.palette.error.main,
      };
    }
    if (status === 'ee') {
      return {
        label: neutralLabel || t('EE'),
        color: theme.palette.ee.main,
      };
    }
    if (status === null) {
      return {
        label: neutralLabel || t('Not applicable'),
        color: null,
      };
    }
    if (status === undefined) {
      return {
        label: <CircularProgress size={10} color="primary" />,
        color: null,
      };
    }
    return {
      label: label,
      color: null,
    };
  };

  const tagProps = getTagProps();
  let maxWidth = 120;
  if (variant === 'xlarge') maxWidth = 250;
  else if (variant === 'large') maxWidth = 150;
  else if (variant === 'inList') maxWidth = 140;

  return (
    <Tag
      label={tagProps.label}
      color={tagProps.color}
      maxWidth={maxWidth}
      disableTooltip
    />
  );
};

const ItemBooleanComponent = (props) => {
  const { tooltip } = props;
  if (tooltip) {
    return (
      <Tooltip title={tooltip}>
        <Box component="span" sx={{ display: 'inline-block' }}>
          <RenderChip {...props} />
        </Box>
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
