import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import * as PropTypes from 'prop-types';
import { Component } from 'react';

import inject18n from '../../../../components/i18n';

class InjectorContractComponent extends Component {
  render() {
    const { label, deleted } = this.props;
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          {/* The contract label is free text and the list cell is narrow: cap the
              chip at its cell so the library truncates the label and opens its own
              tooltip, instead of the cell cutting the chip mid-word. */}
          <Chip severity={deleted ? 'neutral' : 'info'} label={label} style={{ maxWidth: '100%' }} />
        </TooltipTrigger>
        {label && <TooltipContent>{label}</TooltipContent>}
      </Tooltip>
    );
  }
}

InjectorContractComponent.propTypes = {
  variant: PropTypes.string,
  label: PropTypes.string,
  deleted: PropTypes.bool,
};

const InjectorContract = inject18n(InjectorContractComponent);

export default InjectorContract;
