import {type FunctionComponent, useCallback} from 'react';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import Drawer from '../../../../../components/common/Drawer';
import useDialog from '../../../../../components/common/dialog/useDialog';
import {useFormatter} from '../../../../../components/i18n';
import type {UserInput, UserOutput} from '../../../../../utils/api-types';
import {useAppDispatch} from '../../../../../utils/hooks';
import {addUser} from "../../../../../actions/platform/users/user-action";
import {PLATFORM_USER_SCHEMA_KEY} from "../../../../../actions/platform/users/user-schema";
import PlatformUserForm from "./PlatformUserForm";

interface Props {
    onCreate: (result: UserOutput) => void
}

const PlatformUserCreate: FunctionComponent<Props> = ({onCreate}) => {
    const {t} = useFormatter();
    const dispatch = useAppDispatch();
    const {open, handleOpen, handleClose} = useDialog();

    const handleSubmit = useCallback(
        async (data: UserInput) => {
            const result = await dispatch(addUser(data));

            if (!result?.result) {
                return result;
            }
            const createdPlatformUser = result.entities[PLATFORM_USER_SCHEMA_KEY][result.result];
            onCreate(createdPlatformUser);
            handleClose();

            return result;
        },
        [dispatch, onCreate, handleClose],
    );

    return (
        <>
            <ButtonCreate onClick={handleOpen} variant={"rightMenu"}/>
            <Drawer
                open={open}
                handleClose={handleClose}
                title={t('Create a platform user')}
            >
                <PlatformUserForm
                    onSubmit={handleSubmit}
                    onCancel={handleClose}
                />
            </Drawer>
        </>
    );
};

export default PlatformUserCreate;

