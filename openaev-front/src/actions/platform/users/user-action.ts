import type {Dispatch} from 'redux';

import {delReferential, getReferential, postReferential, putReferential, simplePostCall} from '../../../utils/Action';
import {type SearchPaginationInput, UserInput, UserOutput} from '../../../utils/api-types';
import {arrayOfUsers, user} from './user-schema';

export const USER_URI = '/api/users';

// -- CREATE --

export const addUser = (data: UserInput) => (dispatch: Dispatch) => {
    return postReferential(user, USER_URI, data)(dispatch);
};

// -- READ --

export const fetchUser = (userId: UserOutput['user_id']) => (dispatch: Dispatch) => {
    const uri = `${USER_URI}/${userId}`;
    return getReferential(arrayOfUsers, uri)(dispatch);
};

// -- SEARCH --

export const searchUsers = (paginationInput: SearchPaginationInput) => {
    const uri = `${USER_URI}/search`;
    return simplePostCall(uri, paginationInput);
};

// -- UPDATE --

export const updateUser
    = (userId: UserOutput['user_id'], data: UserInput) =>
    (dispatch: Dispatch) => {
        const uri = `${USER_URI}/${userId}`;
        return putReferential(user, uri, data)(dispatch);
    };

// -- DELETE --

export const deleteUser
    = (userId: UserOutput['user_id']) =>
    (dispatch: Dispatch) => {
        const uri = `${USER_URI}/${userId}`;
        return delReferential(uri, 'users', userId)(dispatch);
    };
