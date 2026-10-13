import type { UserDto } from '@jellyfin/sdk/lib/generated-client/models/user-dto';
import React, { useEffect, useMemo, useState, type FC, type MouseEvent, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';

import { appHost } from 'components/apphost';
import layoutManager from 'components/layoutManager';
import Loading from 'components/loading/LoadingComponent';
import Page from 'components/Page';
import UserAvatar from 'components/UserAvatar';
import { AppFeature } from 'constants/appFeature';
import LinkButton from 'elements/emby-button/LinkButton';
import { openDiscoveryEditor } from 'components/discoverySettings/discoveryEditor';
import { useApi } from 'hooks/useApi';
import { useQuickConnectEnabled } from 'hooks/useQuickConnect';
import { useUsers } from 'hooks/useUsers';
import globalize from 'lib/globalize';
import browser from 'scripts/browser';
import Dashboard from 'utils/dashboard';
import { confirmLogout } from 'utils/confirmLogout';
import shell from 'scripts/shell';
import keyboardNavigation from 'scripts/keyboardNavigation';

import './settings.scss';

interface SettingsItemProps {
    className: string
    icon: string
    title: string
    description?: string
    href?: string
    onClick?: (e: MouseEvent<HTMLAnchorElement>) => void
    danger?: boolean
}

// Tobyfin: one row in a settings card, with a rounded icon tile and a short description
// (prototype/tobyfin-menus-prototype.html).
const SettingsItem: FC<SettingsItemProps> = ({ className, icon, title, description, href, onClick, danger }) => (
    <LinkButton
        href={href}
        onClick={onClick}
        className={`settingsMenuItem ${className}${danger ? ' settingsMenuItem-danger' : ''}`}
    >
        <span className={`settingsMenuTile material-icons ${icon}`} aria-hidden='true' />
        <span className='settingsMenuText'>
            <span className='settingsMenuTitle'>{title}</span>
            {description && <span className='settingsMenuDescription'>{description}</span>}
        </span>
        {!danger && <span className='settingsMenuChevron material-icons chevron_right' aria-hidden='true' />}
    </LinkButton>
);

const SettingsGroup: FC<{ label?: string, className?: string, children: ReactNode }> = ({ label, className, children }) => (
    <div className={`settingsMenuGroup ${className ?? ''}`}>
        {label && <h2 className='settingsMenuLabel'>{label}</h2>}
        <div className='settingsMenuCard'>
            {children}
        </div>
    </div>
);

const UserSettingsPage: FC = () => {
    const { user: currentUser } = useApi();
    const [ searchParams ] = useSearchParams();
    const {
        data: isQuickConnectEnabled,
        isPending: isQuickConnectEnabledPending
    } = useQuickConnectEnabled();
    const { data: users } = useUsers();
    const [ user, setUser ] = useState<UserDto>();

    const userId = useMemo(() => (
        searchParams.get('userId') || currentUser?.Id
    ), [ currentUser, searchParams ]);
    const isLoggedInUser = useMemo(() => (
        userId && userId === currentUser?.Id
    ), [ currentUser, userId ]);

    useEffect(() => {
        if (userId) {
            if (userId === currentUser?.Id) setUser(currentUser);
            else setUser(users?.find(({ Id }) => userId === Id));
        }
    }, [ currentUser, userId, users ]);

    if (!userId || !user || isQuickConnectEnabledPending) {
        return (
            <Loading />
        );
    }

    // gamepad toggle unavailable on EdgeUWP, and smoothscroll unavailable on non-TV layout
    const isControlsPageEmpty = !keyboardNavigation.canEnableGamepad() && !layoutManager.tv;
    const hasDownloads = appHost.supports(AppFeature.DownloadManagement);
    const hasClientSettings = appHost.supports(AppFeature.ClientSettings);

    return (
        <Page
            id='myPreferencesMenuPage'
            className='libraryPage userPreferencesPage settingsMenuPage noSecondaryNavPage mainAnimatedPage'
            title={globalize.translate('Settings')}
            shouldAutoFocus
        >
            <div className='padded-left padded-right padded-bottom-page padded-top'>
                <div className='readOnlyContent settingsMenu'>
                    <LinkButton
                        href={`#/userprofile?userId=${userId}`}
                        className='settingsMenuMe lnkUserProfile'
                    >
                        <UserAvatar user={user} />
                        <span className='settingsMenuText'>
                            <span className='settingsMenuName headerUsername'>{user.Name}</span>
                            <span className='settingsMenuDescription'>
                                {isLoggedInUser ? 'Your picture, name and password' : globalize.translate('Profile')}
                            </span>
                        </span>
                        <span className='settingsMenuChevron material-icons chevron_right' aria-hidden='true' />
                    </LinkButton>

                    <SettingsGroup label='Your account'>
                        <SettingsItem
                            className='lnkUserProfile'
                            href={`#/userprofile?userId=${userId}`}
                            icon='person'
                            title={globalize.translate('Profile')}
                            description='Name, picture, password'
                        />
                        {isQuickConnectEnabled && (
                            <SettingsItem
                                className='lnkQuickConnectPreferences'
                                href={`#/quickconnect?userId=${userId}`}
                                icon='phonelink_lock'
                                title={globalize.translate('QuickConnect')}
                                description='Sign in on another device'
                            />
                        )}
                    </SettingsGroup>

                    <SettingsGroup label={globalize.translate('Home')}>
                        {/* Tobyfin: Customize Discovery has its own entry instead of a button inside Home. */}
                        <SettingsItem
                            className='lnkCustomizeDiscovery'
                            onClick={openDiscoveryEditor}
                            icon='dashboard_customize'
                            title='Customize Discovery'
                            description='Choose and order your Home rows'
                        />
                        <SettingsItem
                            className='lnkHomePreferences'
                            href={`#/mypreferenceshome?userId=${userId}`}
                            icon='video_library'
                            title={globalize.translate('HeaderLibraries')}
                            description='Order, My Media and Recently Added'
                        />
                    </SettingsGroup>

                    <SettingsGroup label='Watching'>
                        <SettingsItem
                            className='lnkPlaybackPreferences'
                            href={`#/mypreferencesplayback?userId=${userId}`}
                            icon='play_arrow'
                            title={globalize.translate('TitlePlayback')}
                            description='Quality, audio, skipping'
                        />
                        <SettingsItem
                            className='lnkSubtitlePreferences'
                            href={`#/mypreferencessubtitles?userId=${userId}`}
                            icon='closed_caption'
                            title={globalize.translate('Subtitles')}
                            description='Language and appearance'
                        />
                        <SettingsItem
                            className='lnkDisplayPreferences'
                            href={`#/mypreferencesdisplay?userId=${userId}`}
                            icon='palette'
                            title={globalize.translate('Display')}
                            description='Theme, language, dates'
                        />
                        {isLoggedInUser && !browser.mobile && !isControlsPageEmpty && (
                            <SettingsItem
                                className='lnkControlsPreferences'
                                href={`#/mypreferencescontrols?userId=${userId}`}
                                icon='keyboard'
                                title={globalize.translate('Controls')}
                                description='Gamepad and scrolling'
                            />
                        )}
                    </SettingsGroup>

                    {(hasDownloads || hasClientSettings) && (
                        <SettingsGroup label={layoutManager.mobile ? 'This phone' : 'This device'}>
                            {hasDownloads && (
                                <SettingsItem
                                    className='downloadManager'
                                    onClick={shell.openDownloadManager}
                                    icon='download'
                                    title={globalize.translate('DownloadManager')}
                                    description='Saved for offline'
                                />
                            )}
                            {hasClientSettings && (
                                <SettingsItem
                                    className='clientSettings'
                                    onClick={shell.openClientSettings}
                                    icon='smartphone'
                                    title={globalize.translate('ClientSettings')}
                                    description='App options for this phone'
                                />
                            )}
                        </SettingsGroup>
                    )}

                    {isLoggedInUser && user.Policy?.IsAdministrator && !layoutManager.tv && (
                        <SettingsGroup label={globalize.translate('HeaderAdmin')} className='adminSection'>
                            <SettingsItem
                                className='lnkDashboard'
                                href='#/dashboard'
                                icon='dashboard'
                                title={globalize.translate('TabDashboard')}
                                description='Manage the server'
                            />
                            <SettingsItem
                                className='lnkMetadataManager'
                                href='#/metadata'
                                icon='mode_edit'
                                title={globalize.translate('MetadataManager')}
                                description='Edit titles and artwork'
                            />
                        </SettingsGroup>
                    )}

                    {isLoggedInUser && (
                        <SettingsGroup className='userSection'>
                            {appHost.supports(AppFeature.MultiServer) && (
                                <SettingsItem
                                    className='selectServer'
                                    onClick={Dashboard.selectServer}
                                    icon='storage'
                                    title={globalize.translate('SelectServer')}
                                    description='Switch to another server'
                                />
                            )}
                            <SettingsItem
                                className='btnLogout'
                                onClick={confirmLogout}
                                icon='logout'
                                title={globalize.translate('ButtonSignOut')}
                                danger
                            />
                            {appHost.supports(AppFeature.ExitMenu) && (
                                <SettingsItem
                                    className='exitApp'
                                    onClick={appHost.exit}
                                    icon='close'
                                    title={globalize.translate('ButtonExitApp')}
                                    danger
                                />
                            )}
                        </SettingsGroup>
                    )}
                </div>
            </div>
        </Page>
    );
};

export default UserSettingsPage;
