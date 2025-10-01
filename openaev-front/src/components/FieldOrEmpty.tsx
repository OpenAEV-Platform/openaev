import { type ReactNode } from 'react';

import { isNotEmptyField } from '../utils/utils';

interface FieldOrEmptyProps<T> {
  source: T;
  children: ReactNode;
}

const FieldOrEmpty = <T = never>({ source, children }: FieldOrEmptyProps<T>) => {
  return <>{isNotEmptyField(source) ? children : '-'}</>; // render the children if source is defined
};
export default FieldOrEmpty;
