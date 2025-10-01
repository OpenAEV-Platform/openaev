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
  const handleTextSearch = (value?: string) => textSearchHelpers.handleTextSearch(value);

  return (
    <SearchFilter
      variant="small"
      onChange={handleTextSearch}
      keyword={textSearch}
    />
  );
};

export default TextSearchComponent;
