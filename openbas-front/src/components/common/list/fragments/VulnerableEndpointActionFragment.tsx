import { LabelColorDict } from '../../../Theme';
import LabelChip from '../../chips/LabelChip';

type Props = { action?: string };

const VulnerableEndpointActionFragment = ({ action = 'OK' }: Props) => {
  let color;
  if (action == 'REPLACE') {
    color = LabelColorDict.Red;
  } else if (action == 'UPDATE') {
    color = LabelColorDict.Orange;
  } else {
    color = LabelColorDict.Green;
  }
  return (
    <LabelChip
      label={action}
      color={color}
    />
  );
};

export default VulnerableEndpointActionFragment;
