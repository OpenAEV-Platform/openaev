import { Chip, Tooltip } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { withStyles } from 'tss-react/mui';

import inject18n from '../../../../components/i18n';

const styles = () => ({
  chip: {
    fontSize: 15,
    lineHeight: '18px',
    height: 30,
    margin: '0 7px 7px 0',
    borderRadius: 4,
    width: 160,
  },
  chipInList: {
    fontSize: 12,
    lineHeight: '12px',
    height: 20,
    float: 'left',
    marginRight: 7,
    borderRadius: 4,
    width: 140,
  },
});

class InjectorContract extends Component {
  render() {
    const { classes, label, variant, deleted } = this.props;
    const style = variant === 'list' ? classes.chipInList : classes.chip;
    return (
      <Tooltip title={label}>
        <Chip
          variant="outlined"
          color={deleted ? 'default' : 'primary'}
          classes={{ root: style }}
          label={deleted ? <i>{label}</i> : label}
        />
      </Tooltip>
    );
  }
}

InjectorContract.propTypes = {
  classes: PropTypes.object.isRequired,
  variant: PropTypes.string,
  label: PropTypes.string,
  deleted: PropTypes.bool,
};

export default R.compose(inject18n, Component => withStyles(Component, styles))(InjectorContract);
