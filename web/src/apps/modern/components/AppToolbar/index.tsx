import Stack from '@mui/material/Stack';
import { useTheme } from '@mui/material/styles';

import lightWordmark from 'assets/branding/banner-light.png';
import darkWordmark from 'assets/branding/banner-dark.png';
import React, { type FC } from 'react';
import { useLocation } from 'react-router-dom';

import { appRouter, PUBLIC_PATHS } from 'components/router/appRouter';
import BaseToolbar from 'components/toolbar/AppToolbar';
import ServerButton from 'components/toolbar/ServerButton';

import RemotePlayButton from './RemotePlayButton';
import SyncPlayButton from './SyncPlayButton';
import SearchButton from './SearchButton';
import UserViewNav from './userViews/UserViewNav';

import './homeBranding.scss';

interface AppToolbarProps {
    isDrawerAvailable: boolean
    isDrawerOpen: boolean
    onDrawerButtonClick: (event: React.MouseEvent<HTMLElement>) => void
}

const AppToolbar: FC<AppToolbarProps> = ({
    isDrawerAvailable,
    isDrawerOpen,
    onDrawerButtonClick
}) => {
    const location = useLocation();
    const theme = useTheme();
    const isHome = location.pathname === '/home' || location.pathname === '/';

    // The video osd does not show the standard toolbar
    if (location.pathname === '/video') return null;

    // Only show the back button in apps when appropriate
    const isBackButtonAvailable = window.NativeShell && appRouter.canGoBack(location.pathname);

    // Check if the current path is a public path to hide user content
    const isPublicPath = PUBLIC_PATHS.includes(location.pathname);

    return (
        <BaseToolbar
            buttons={!isPublicPath && (
                <>
                    <SyncPlayButton />
                    <RemotePlayButton />
                    <SearchButton />
                </>
            )}
            isDrawerAvailable={isDrawerAvailable}
            isDrawerOpen={isDrawerOpen}
            onDrawerButtonClick={onDrawerButtonClick}
            isBackButtonAvailable={isBackButtonAvailable}
            isUserMenuAvailable={!isPublicPath}
            className='padded-left padded-right tobyfinHomeToolbar'
        >
            {(
                <img
                    className='tobyfinHomeLogo'
                    src={theme.palette.mode === 'dark' ? lightWordmark : darkWordmark}
                    alt='Tobyfin'
                />
            )}
            {!isDrawerAvailable && (
                <Stack
                    direction='row'
                    spacing={0.5}
                >
                    {!isHome && <ServerButton />}

                    {!isPublicPath && (
                        <UserViewNav />
                    )}
                </Stack>
            )}
        </BaseToolbar>
    );
};

export default AppToolbar;
