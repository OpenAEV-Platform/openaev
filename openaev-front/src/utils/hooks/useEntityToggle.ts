import * as R from 'ramda';
import { type SyntheticEvent, useState } from 'react';

export interface UseEntityToggle<T> {
  selectedElements: Record<string, T>;
  deSelectedElements: Record<string, T>;
  selectAll: boolean;
  numberOfSelectedElements: number;
  onToggleEntity: (
    entity: T,
    _?: SyntheticEvent,
    forceRemove?: T[],
  ) => void;
  handleClearSelectedElements: () => void;
  handleToggleSelectAll: () => void;
  setSelectedElements: (selectedElements: Record<string, T>) => void;
}

// we don't know the type of every value in an object type passed here
// nor is it relevant.
/* eslint-disable-next-line @typescript-eslint/no-explicit-any */
const useEntityToggle = <T extends Record<string, any>>(
  prefix: string,
  knownElements: T[],
  totalNumberOfElements?: number,
): UseEntityToggle<T> => {
  const [selectedElements, setSelectedElements] = useState<Record<string, T>>(
    {},
  );
  const [deSelectedElements, setDeSelectedElements] = useState<
    Record<string, T>
  >({});
  const [selectAll, setSelectAll] = useState(false);
  const onToggleEntity = (
    entity: T,
    event?: SyntheticEvent,
    forceRemove: T[] = [],
  ) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    if (Array.isArray(entity)) {
      /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
      const currentIds = R.values(selectedElements).map((n: Record<string, any>) => n[`${prefix}_id`]);
      const givenIds = entity.map(n => n[`${prefix}_id`]);
      const addedIds = givenIds.filter(n => !currentIds.includes(n));
      let newSelectedElements = {
        ...selectedElements,
        ...R.indexBy(
          R.prop(`${prefix}_id`),
          entity.filter(n => addedIds.includes(n[`${prefix}_id`])),
        ),
      };
      if (forceRemove.length > 0) {
        newSelectedElements = R.omit(
          forceRemove.map(n => n[`${prefix}_id`]),
          newSelectedElements,
        );
      }
      setSelectAll(false);
      setSelectedElements(newSelectedElements);
      setDeSelectedElements({});
    } else if (entity[`${prefix}_id`] in (selectedElements || {})) {
      const newSelectedElements = R.omit([entity[`${prefix}_id`]], selectedElements);
      setSelectAll(false);
      setSelectedElements(newSelectedElements);
    } else if (selectAll && entity[`${prefix}_id`] in (deSelectedElements || {})) {
      const newDeSelectedElements = R.omit([entity[`${prefix}_id`]], deSelectedElements);
      setDeSelectedElements(newDeSelectedElements);
    } else if (selectAll) {
      const newDeSelectedElements = {
        ...deSelectedElements,
        [entity[`${prefix}_id`]]: entity,
      };
      setDeSelectedElements(newDeSelectedElements);
    } else {
      const newSelectedElements = {
        ...selectedElements,
        [entity[`${prefix}_id`]]: entity,
      };
      setSelectAll(false);
      setSelectedElements(newSelectedElements);
    }
  };
  const handleToggleSelectAll = () => {
    setSelectAll(!selectAll);
    setSelectedElements({});
    setDeSelectedElements({});
  };
  const handleClearSelectedElements = () => {
    setSelectAll(false);
    setSelectedElements({});
    setDeSelectedElements({});
  };
  let numberOfSelectedElements = Object.keys(selectedElements).length;
  if (selectAll) {
    numberOfSelectedElements = (totalNumberOfElements ?? 0) - Object.keys(deSelectedElements).length;
  }
  return {
    onToggleEntity,
    setSelectedElements,
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    numberOfSelectedElements,
  };
};

export default useEntityToggle;
