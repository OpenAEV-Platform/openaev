import { LabelColorDict } from '../../../Theme';
import LabelChip from '../../chips/LabelChip';

/*
  Colours GREEN if FALSE (= NO is OK), RED if TRUE (= YES is NOK)
 */
type Props = { bool?: boolean };

const InverseBooleanFragment = ({ bool }: Props) => {
  return (
    <LabelChip
      label={bool ? 'Yes' : 'No'}
      color={bool ? LabelColorDict.Red : LabelColorDict.Green}
    />
  );
};

export default InverseBooleanFragment;
