export function parseSubtitleOffset(text) {
    const normalized = text.trim().replace(/s$/i, '').trim().replace(',', '.');
    if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(normalized)) return null;
    const value = Number(normalized);
    return Number.isFinite(value) && Math.abs(value) <= 600 ? Math.round(value * 1000) / 1000 : null;
}

export function stepSubtitleOffset(current, step) {
    return Math.max(-600, Math.min(600, Math.round((current + step) * 1000) / 1000));
}
