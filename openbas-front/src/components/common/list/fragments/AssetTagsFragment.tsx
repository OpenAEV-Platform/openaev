import ItemTags from '../../../ItemTags';

type Props = { tags?: string[] };

const AssetTagsFragment = (props: Props) => {
  return (<ItemTags variant="list" tags={props.tags ?? []} />);
};

export default AssetTagsFragment;
