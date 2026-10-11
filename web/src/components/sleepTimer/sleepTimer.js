// A single timer belongs to the local playback session, not its OSD or stream.
export class SleepTimer {
    constructor(onExpire, now = () => performance.now()) {
        this.now = now;
        this.onExpire = onExpire;
        this.cancel();
    }
    start(minutes, player) {
        if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440) throw new Error('Enter 1–1440 minutes');
        this.cancel();
        this.player = player;
        this.deadline = this.now() + minutes * 60000;
        this.interval = setInterval(() => this.check(), 500);
    }
    endOfItem(player) {
        this.cancel();
        this.player = player;
        this.afterItem = true;
    }
    extend(player) {
        const remaining = this.remaining || 0;
        this.start(15, player);
        this.deadline += remaining;
    }
    cancel() {
        clearInterval(this.interval);
        this.interval = null;
        this.deadline = null;
        this.afterItem = false;
        this.expired = false;
        this.player = null;
    }
    get remaining() { return this.deadline === null ? null : Math.max(0, this.deadline - this.now()); }
    check() {
        if (this.deadline !== null && this.now() >= this.deadline && !this.expired) {
            this.expired = true;
            this.deadline = null;
            clearInterval(this.interval);
            this.onExpire(this.player);
        }
        return this.expired;
    }
    finishItem(player) {
        if (!this.owns(player)) return false;
        this.check();
        if (this.afterItem) {
            this.afterItem = false;
            this.expired = true;
        }
        return this.expired;
    }
    owns(player) { return !!player && this.player === player; }
    resumeManually(player) { if (this.owns(player) && this.expired) this.cancel(); }
}
