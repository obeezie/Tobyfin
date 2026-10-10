import dialogHelper from '../dialogHelper/dialogHelper';
import './sleepTimer.scss';

export function openSleepTimer(timer, player, allowEnd = true) {
    const dialog = dialogHelper.createDialog({ removeOnClose: true, lockScroll: false, modal: false });
    dialog.classList.add('sleepTimerDialog');
    dialog.setAttribute('aria-label', 'Sleep timer');
    dialog.innerHTML = '<h2>Sleep timer</h2><p role="status"></p><div class="sleepTimerPresets"></div>'
        + '<form><label>Custom minutes<input type="number" min="1" max="1440" step="1" required placeholder="1–1440" /></label><button type="submit">Set</button></form>'
        + '<button type="button" class="sleepTimerEnd">Stop after this episode / movie</button>'
        + '<div class="sleepTimerActions"><button type="button" data-action="extend">+15 min</button><button type="button" data-action="cancel">Cancel timer</button><button type="button" data-action="done">Done</button></div>';
    const close = () => {
        if (dialog.isConnected) dialogHelper.close(dialog);
    };
    for (const minutes of [15, 30, 60, 90]) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = minutes + ' min';
        button.addEventListener('click', () => {
            timer.start(minutes, player);
            close();
        });
        dialog.querySelector('.sleepTimerPresets').append(button);
    }
    const update = () => {
        const seconds = Math.ceil((timer.remaining || 0) / 1000);
        let text = 'Off · keeps playing until you set a timer';
        if (timer.expired) text = 'Timer ended · playback paused';
        else if (timer.afterItem) text = 'Stops at the end of this video';
        else if (timer.remaining !== null) text = 'Pauses in ' + Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0');
        dialog.querySelector('[role="status"]').textContent = text;
    };
    dialog.querySelector('form').addEventListener('submit', event => {
        event.preventDefault();
        const input = dialog.querySelector('input');
        if (input.reportValidity()) {
            timer.start(Number(input.value), player);
            close();
        }
    });
    const end = dialog.querySelector('.sleepTimerEnd');
    end.hidden = !allowEnd;
    end.addEventListener('click', () => {
        timer.endOfItem(player);
        close();
    });
    dialog.querySelector('[data-action="extend"]').addEventListener('click', () => {
        timer.extend(player);
        update();
    });
    dialog.querySelector('[data-action="cancel"]').addEventListener('click', () => {
        timer.cancel();
        update();
    });
    dialog.querySelector('[data-action="done"]').addEventListener('click', close);
    const interval = setInterval(update, 1000);
    dialog.addEventListener('close', () => clearInterval(interval), { once: true });
    dialog.addEventListener('open', () => dialog.backdrop?.classList.add('sleepTimerBackdrop'), { once: true });
    update();
    dialogHelper.open(dialog);
    return close;
}
