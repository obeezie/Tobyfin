import { buildCustomColorScheme } from 'themes/utils';

/** The "Tobyfin" color scheme: dark navy with purple-to-cyan accents. */
const theme = buildCustomColorScheme({
    palette: {
        mode: 'dark',
        background: {
            default: '#080c1d',
            paper: '#121a35'
        },
        primary: {
            main: '#3cc4f5',
            contrastText: '#0b1022'
        },
        secondary: {
            main: '#b27dff',
            contrastText: '#0b1022'
        },
        error: {
            main: '#ff8a8a'
        },
        text: {
            primary: '#eef0fa',
            secondary: '#a2a8c6'
        },
        action: {
            hover: 'rgba(60, 196, 245, 0.08)',
            focus: '#26305e',
            selected: 'rgba(60, 196, 245, 0.14)'
        },
        divider: '#263056',
        AppBar: {
            defaultBg: '#080c1d'
        },
        Button: {
            inheritContainedBg: '#18213f',
            inheritContainedHoverBg: '#26305e'
        },
        FilledInput: {
            bg: '#0c1229',
            hoverBg: '#0c1229'
        },
        SnackbarContent: {
            bg: '#2b3157',
            color: '#eef0fa'
        }
    }
});

export default theme;
