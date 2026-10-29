import { type FunctionComponent } from 'react';

import SearchFilter from '../../../SearchFilter';
import { type TextSearchHelpers } from './TextSearchHelpers';

interface Props {
  textSearch?: string;
  textSearchHelpers: TextSearchHelpers;
}

const TextSearchComponent: FunctionComponent<Props> = ({
  textSearch,
  textSearchHelpers,
}) => {
  const handleTextSearch = (value?: string) => textSearchHelpers.handleTextSearch(value?.trim());

  return (
    // 192px when space allows, shrinkable down to 120px in a tight toolbar: the
    // search compresses before the controls next to it (#7340).
    <div style={{
      flex: '0 1 192px',
      minWidth: 120,
    }}
    >
      <SearchFilter
        variant="small"
        fullWidth
        onChange={handleTextSearch}
        keyword={textSearch}
      />
    </div>
  );
};

export default TextSearchComponent;
