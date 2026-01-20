import { Slider as MUISlider } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Field } from 'react-final-form';

const renderSliderField = ({
  input,
  step,
  min,
  max,
  onSliderChange,
  disabled,
}) => (
  <MUISlider
    step={step}
    min={min}
    max={max}
    {...input}
    onDragStart={() => {
      /* disable the dragging propagation */
    }}
    onChange={(event, newValue) => {
      if (onSliderChange) {
        onSliderChange(event, newValue);
      }
      input.onChange(newValue);
    }}
    valueLabelDisplay="auto"
    marks={true}
    disabled={disabled}
  />
);

renderSliderField.propTypes = {
  input: PropTypes.object,
  name: PropTypes.string,
  step: PropTypes.number,
  min: PropTypes.number,
  max: PropTypes.number,
  meta: PropTypes.object,
  onSliderChange: PropTypes.func,
  disabled: PropTypes.bool,
};

/**
 * @deprecated The component use old form library react-final-form
 */
const SliderField = props => (
  <Field component={renderSliderField} {...props} />
);

export default SliderField;
