// Order-independent: a marking picker's Autocomplete can re-emit the same selection in a
// different order without the assigned set actually changing, and treating that as "changed"
// would fire the markings PUT (and its own success/failure notification) on every save even when
// markings never moved. Shared by every form that hosts a markings picker (AssetForm,
// AiTargetForm, ...) so they all draw the same "did markings actually change?" line.
const markingIdSetsEqual = (a: string[], b: string[]): boolean => {
  const setA = new Set(a);
  const setB = new Set(b);
  return setA.size === setB.size && [...setA].every(id => setB.has(id));
};

export default markingIdSetsEqual;
