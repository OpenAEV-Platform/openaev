import { useDispatch } from 'react-redux';
import { type Dispatch, type UnknownAction } from 'redux';

// eslint-disable-next-line import/prefer-default-export
export const useAppDispatch = useDispatch as () => <R>(
  action: (dispatch: Dispatch<UnknownAction>) => R,
) => R;
