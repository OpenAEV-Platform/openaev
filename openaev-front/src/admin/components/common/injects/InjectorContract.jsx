import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { withStyles } from 'tss-react/mui';

import inject18n from '../../../../components/i18n';

const styles = () => ({
  chip: {
    fontSize: 15,
    height: 30,
    margin: '0 7px 7px 0',
    borderRadius: 4,
    width: 160,
  },
  chipInList: {
    fontSize: 12,
    height: 20,
    float: 'left',
    marginRight: 7,
    borderRadius: 4,
    width: 140,
  },
});

class InjectorContractComponent extends Component {
  render() {
    const { label, deleted } = this.props;
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <Chip severity={deleted ? 'neutral' : 'info'} label={label} />
        </TooltipTrigger>
        {label && <TooltipContent>{label}</TooltipContent>}
      </Tooltip>
    );
  }
}

InjectorContractComponent.propTypes = {
  classes: PropTypes.object.isRequired,
  variant: PropTypes.string,
  label: PropTypes.string,
  deleted: PropTypes.bool,
};

const InjectorContract = R.compose(
  inject18n,
  Component => withStyles(Component, styles),
)(InjectorContractComponent);

export default InjectorContract;
