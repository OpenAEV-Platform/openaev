import { Chip } from '@mui/material';
import * as PropTypes from 'prop-types';
import { compose } from 'ramda';
import { Component } from 'react';
import { withStyles } from 'tss-react/mui';

import inject18n from '../../../../components/i18n';
import { hexToRGB, stringToColour } from '../../../../utils/Colors';

const styles = () => ({
  chip: {
    fontSize: 12,
    lineHeight: '12px',
    height: 25,
    marginRight: 7,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 180,
  },
  chipInList: {
    fontSize: 12,
    lineHeight: '12px',
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 130,
  },
});

class DocumentType extends Component {
  render() {
    const { classes, t, type, variant, disabled } = this.props;
    const style = variant === 'list' ? classes.chipInList : classes.chip;
    const color = stringToColour(type);
    if (type) {
      return (
        <Chip
          classes={{ root: style }}
          variant="outlined"
          label={type}
          style={{
            color,
            borderColor: color,
            backgroundColor: hexToRGB(color),
          }}
        />
      );
    }
    return (
      <Chip
        classes={{ root: style }}
        variant="outlined"
        label={disabled ? t('Disabled') : t('Unknown')}
      />
    );
  }
}

DocumentType.propTypes = {
  classes: PropTypes.object.isRequired,
  type: PropTypes.string,
  variant: PropTypes.string,
  t: PropTypes.func,
  disabled: PropTypes.bool,
};

export default compose(inject18n, Component => withStyles(Component, styles))(DocumentType);
