import { afterEach, describe, expect, it, vi } from 'vitest';
import { SleepTimer } from './sleepTimer';

afterEach(() => vi.useRealTimers());
describe('sleep timer playback session', () => {
    it('pauses once at elapsed deadline, including during buffering, and blocks autoplay', () => {
        vi.useFakeTimers();
        let now = 0;
        const pause = vi.fn();
        const timer = new SleepTimer(pause, () => now);
        const player = {};
        timer.start(15, player);
        now = 900000;
        vi.advanceTimersByTime(500);
        expect(pause).toHaveBeenCalledExactlyOnceWith(player);
        expect(timer.finishItem(player)).toBe(true);
        vi.advanceTimersByTime(10000);
        expect(pause).toHaveBeenCalledTimes(1);
        timer.resumeManually(player);
        expect(timer.expired).toBe(false);
    });
    it('carries countdown through episode transitions, extends remaining time and isolates players', () => {
        vi.useFakeTimers();
        let now = 0;
        const timer = new SleepTimer(vi.fn(), () => now);
        const player = {};
        timer.start(30, player);
        now = 600000;
        expect(timer.finishItem(player)).toBe(false);
        timer.extend(player);
        expect(timer.remaining).toBe(2100000);
        timer.endOfItem(player);
        expect(timer.finishItem({})).toBe(false);
        expect(timer.finishItem(player)).toBe(true);
        timer.cancel();
        expect(timer.finishItem(player)).toBe(false);
    });
    it('validates custom duration and replaces or cancels timers', () => {
        vi.useFakeTimers();
        const timer = new SleepTimer(vi.fn(), () => 0);
        timer.start(15, {});
        expect(() => timer.start(0, {})).toThrow();
        expect(() => timer.start(1441, {})).toThrow();
        expect(() => timer.start(1.5, {})).toThrow();
        expect(timer.remaining).toBe(900000);
        timer.start(60, {});
        expect(timer.remaining).toBe(3600000);
        timer.cancel();
        expect(vi.getTimerCount()).toBe(0);
    });
});
