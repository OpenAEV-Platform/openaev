import {useCallback, useState} from 'react';

import type {SearchPaginationInput, UserOutput} from '../../../../../../utils/api-types';
import {searchUsers} from "../../../../../../actions/platform/users/user-action";

const usePlatformUsers = () => {
    const [platformUsers, setPlatformUsers] = useState<UserOutput[]>([]);
    const [loading, setLoading] = useState(true);

    const setPlatformUserList = useCallback((users: UserOutput[]) => {
        setPlatformUsers(users);
    }, []);

    const fetchPlatformUsers = useCallback(
        async (input: SearchPaginationInput) => {
            setLoading(true);
            try {
                return await searchUsers(input);
            } finally {
                setLoading(false);
            }
        },
        [],
    );

    const addPlatformUser = useCallback((user: UserOutput) => {
        setPlatformUsers(prev => [user, ...prev]);
    }, []);

    const updatePlatformUserInList = useCallback((user: UserOutput) => {
        setPlatformUsers(prev =>
            prev.map(u =>
                u.user_id === user.user_id ? user : u,
            ),
        );
    }, []);

    const removePlatformUser = useCallback((userId: string) => {
        setPlatformUsers(prev => prev.filter(u => u.user_id !== userId));
    }, []);

    return {
        platformUsers,
        setPlatformUserList,
        loading,
        fetchPlatformUsers,
        addPlatformUser,
        updatePlatformUserInList,
        removePlatformUser,
    };
};

export default usePlatformUsers;

