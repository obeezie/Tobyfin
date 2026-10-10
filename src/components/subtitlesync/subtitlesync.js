import { playbackManager } from '../playback/playbackmanager';
import template from './subtitlesync.template.html';
import './subtitlesync.scss';
import { parseSubtitleOffset, stepSubtitleOffset } from './subtitleOffset';

class SubtitleSync {
    constructor(player) {
        this.player = player;
        this.delay = 0;
        this.isOpen = false;
        this.element = document.createElement('div');
        this.element.innerHTML = template;
        document.body.appendChild(this.element);
        this.container = this.element.querySelector('.subtitleSyncContainer');
        this.value = this.element.querySelector('.subtitleSync-value');
        this.input = this.element.querySelector('.subtitleSyncTextField');
        this.more = this.element.querySelector('.subtitleSync-more');
        this.advanced = this.element.querySelector('.subtitleSync-advanced');
        this.element.querySelectorAll('[data-subtitle-step]').forEach(button => {
            button.addEventListener('click', () => this.update(Number(button.dataset.subtitleStep)));
        });
        this.more.addEventListener('click', () => {
            this.advanced.hidden = !this.advanced.hidden;
            this.more.setAttribute('aria-expanded', String(!this.advanced.hidden));
        });
        this.element.querySelector('.subtitleSync-reset').addEventListener('click', () => this.apply(0));
        const applyEntry = () => {
            const delay = parseSubtitleOffset(this.input.value);
            this.input.setAttribute('aria-invalid', String(delay === null));
            if (delay !== null) {
                this.apply(delay);
                this.input.blur();
            }
        };
        this.element.querySelector('.subtitleSync-apply').addEventListener('click', applyEntry);
        this.input.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                event.preventDefault();
                applyEntry();
            }
        });
        this.element.querySelector('.subtitleSync-closeButton').addEventListener('click', () => this.close());
        this.element.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                event.preventDefault();
                this.close();
            }
            event.stopPropagation();
        });
        ['click', 'pointermove', 'mousemove', 'touchstart'].forEach(name => {
            this.element.addEventListener(name, event => event.stopPropagation());
        });
        this.renderValue();
    }

    close() {
        playbackManager.disableShowingSubtitleOffset(this.player);
        this.toggle('forceToHide');
    }

    destroy() {
        this.close();
        playbackManager.setSubtitleOffset(0, this.player);
        this.element.remove();
    }

    canAdjust() {
        return playbackManager.supportSubtitleOffset(this.player)
            && playbackManager.canHandleOffsetOnCurrentSubtitle(this.player);
    }

    renderValue() {
        this.value.textContent = (this.delay > 0 ? '+' : '') + this.delay + ' s';
        this.input.value = String(this.delay);
    }

    apply(delay) {
        if (!this.canAdjust()) return;
        this.delay = stepSubtitleOffset(delay, 0);
        this.renderValue();
        // The web player uses the inverse sign: this UI consistently means positive = later.
        playbackManager.setSubtitleOffset(-this.delay, this.player);
    }

    toggle(action) {
        if (action === 'hide' && this.isOpen) return;
        const show = !action && this.canAdjust()
            && playbackManager.isShowingSubtitleOffsetEnabled(this.player);
        if (show && !this.isOpen) {
            this.delay = -playbackManager.getPlayerSubtitleOffset(this.player) || 0;
            this.renderValue();
        }
        this.isOpen = !!show;
        this.container.classList.toggle('hide', !show);
        if (!show) {
            this.advanced.hidden = true;
            this.more.setAttribute('aria-expanded', 'false');
        }
    }

    update(offset) {
        this.toggle();
        this.apply(stepSubtitleOffset(this.delay, offset));
    }

    incrementOffset() { this.update(0.1); }
    decrementOffset() { this.update(-0.1); }
}

export default SubtitleSync;
