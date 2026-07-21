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
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 120,
  },
  chipLarge: {
    fontSize: 12,
    lineHeight: '12px',
    height: 25,
    marginRight: 7,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 150,
  },
  chipXLarge: {
    fontSize: 12,
    lineHeight: '12px',
    height: 25,
    marginRight: 7,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 250,
  },
  chipInList: {
    fontSize: 12,
    lineHeight: '12px',
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 140,
  },
  chipFit: {
    fontSize: 12,
    lineHeight: '12px',
    height: 25,
    textTransform: 'none',
    borderRadius: 4,
  },
});

const computeInlineStyles = theme => ({
  green: {
    backgroundColor: 'rgba(76, 175, 80, 0.08)',
    color: '#4caf50',
  },
  red: {
    backgroundColor: 'rgba(244, 67, 54, 0.08)',
    color: '#f44336',
  },
  blue: {
    backgroundColor: 'rgba(92, 123, 245, 0.08)',
    color: '#5c7bf5',
  },
  ee: {
    backgroundColor: theme.palette.ee.lightBackground,
    color: theme.palette.ee.main,
  },
  accent: {
    backgroundColor: theme.palette.background.accent,
    color: theme.palette.common.white,
    fontWeight: theme.typography.fontWeightBold,
  },
});

const RenderChip = (props) => {
  const { classes, label, neutralLabel, status, variant, t, reverse, styleOverride } = props;
  const theme = useTheme();
  let style = classes.chip;
  if (variant === 'inList') {
    style = classes.chipInList;
  } else if (variant === 'large') {
    style = classes.chipLarge;
  } else if (variant === 'xlarge') {
    style = classes.chipXLarge;
  } else if (variant === 'fit') {
    style = classes.chipFit;
  }
  const inlineStyles = computeInlineStyles(theme);
  if (status === true) {
    return (
      <Chip
        classes={{ root: style }}
        style={{
          ...(reverse ? inlineStyles.red : inlineStyles.green),
          ...styleOverride,
        }}
        label={label}
      />
    );
  }
  if (status === null) {
    return (
      <Chip
        classes={{ root: style }}
        style={{
          ...inlineStyles.blue,
          ...styleOverride,
        }}
        label={neutralLabel || t('Not applicable')}
      />
    );
  }
  if (status === 'accent') {
    return (
      <Chip
        classes={{ root: style }}
        style={{
          ...inlineStyles.accent,
          ...styleOverride,
        }}
        label={neutralLabel || t('Not applicable')}
      />
    );
  }
  if (status === 'ee') {
    return (
      <Chip
        classes={{ root: style }}
        style={{
          ...inlineStyles.ee,
          ...styleOverride,
        }}
        label={neutralLabel || t('EE')}
      />
    );
  }
  if (status === undefined) {
    return (
      <Chip
        classes={{ root: style }}
        style={{
          ...inlineStyles.blue,
          ...styleOverride,
        }}
        label={<CircularProgress size={10} color="primary" />}
      />
    );
  }
  return (
    <Chip
      classes={{ root: style }}
      style={{
        ...(reverse ? inlineStyles.green : inlineStyles.red),
        ...styleOverride,
      }}
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
  styleOverride: PropTypes.object,
};

const ItemBoolean = R.compose(
  inject18n,
  Component => withStyles(Component, styles),
)(ItemBooleanComponent);

export default ItemBoolean;
