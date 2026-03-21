import { Box, Chip, CircularProgress, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { withStyles } from 'tss-react/mui';

import inject18n from './i18n';

const styles = () => ({
  chip: {
    fontSize: 12,
    lineHeight: '12px',
    height: 25,
    marginRight: 7,
    borderRadius: 4,
    width: 120,
  },
  chipLarge: {
    fontSize: 12,
    lineHeight: '12px',
    height: 25,
    marginRight: 7,
    borderRadius: 4,
    width: 150,
  },
  chipXLarge: {
    fontSize: 12,
    lineHeight: '12px',
    height: 25,
    marginRight: 7,
    borderRadius: 4,
    width: 250,
  },
  chipInList: {
    fontSize: 12,
    lineHeight: '12px',
    height: 20,
    float: 'left',
    borderRadius: 4,
    width: 140,
  },
});

const computeInlineStyles = theme => ({
  green: {
    backgroundColor: 'rgba(23, 171, 31, 0.2)',
    color: theme.palette.success?.main ?? '#17AB1F',
  },
  red: {
    backgroundColor: 'rgba(241, 67, 55, 0.2)',
    color: theme.palette.error?.main ?? '#F14337',
  },
  blue: {
    backgroundColor: 'rgba(15, 188, 255, 0.2)',
    color: theme.palette.primary?.main ?? '#0FBCFF',
  },
  ee: {
    backgroundColor: theme.palette.ee.lightBackground,
    color: theme.palette.ee.main,
  },
});

const RenderChip = (props) => {
  const { classes, label, neutralLabel, status, variant, t, reverse } = props;
  const theme = useTheme();
  let style = classes.chip;
  if (variant === 'inList') {
    style = classes.chipInList;
  } else if (variant === 'large') {
    style = classes.chipLarge;
  } else if (variant === 'xlarge') {
    style = classes.chipXLarge;
  }
  const inlineStyles = computeInlineStyles(theme);
  if (status === true) {
    return (
      <Chip
        classes={{ root: style }}
        style={reverse ? inlineStyles.red : inlineStyles.green}
        label={label}
      />
    );
  }
  if (status === null) {
    return (
      <Chip
        classes={{ root: style }}
        style={inlineStyles.blue}
        label={neutralLabel || t('Not applicable')}
      />
    );
  }
  if (status === 'ee') {
    return (
      <Chip
        classes={{ root: style }}
        style={inlineStyles.ee}
        label={neutralLabel || t('EE')}
      />
    );
  }
  if (status === undefined) {
    return (
      <Chip
        classes={{ root: style }}
        style={inlineStyles.blue}
        label={<CircularProgress size={10} color="primary" />}
      />
    );
  }
  return (
    <Chip
      classes={{ root: style }}
      style={reverse ? inlineStyles.green : inlineStyles.red}
      label={label}
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
