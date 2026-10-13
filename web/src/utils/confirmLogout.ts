import confirm from 'components/confirm/confirm';
import globalize from 'lib/globalize';
import Dashboard from 'utils/dashboard';

/** Tobyfin: ask before signing out, from the Settings page and the account menu. */
export function confirmLogout() {
    confirm({
        title: 'Sign out?',
        text: 'You’ll need to sign in again to use Tobyfin on this device.',
        confirmText: globalize.translate('ButtonSignOut'),
        primary: 'delete'
    }).then(() => {
        Dashboard.logout();
    }).catch(() => {
        // Cancelled
    });
}
