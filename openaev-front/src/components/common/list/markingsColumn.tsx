import ItemMarkings from '../../ItemMarkings';

const markingsColumn = <T,>(field: string, markingIds: (row: T) => string[] | undefined) => ({
  field,
  label: 'Markings',
  isSortable: false,
  value: (row: T) => <ItemMarkings markingIds={markingIds(row)} limit={1} />,
});

export default markingsColumn;
