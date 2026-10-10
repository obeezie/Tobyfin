import globalize from 'lib/globalize';
import imageLoader from 'components/images/imageLoader';

export function attachInfiniteMovieRow(container, loadMore, renderItems) {
    container.cleanupPaging?.();
    const button = document.createElement('button');
    button.className = 'button-flat emby-button';
    button.style.minWidth = '160px';
    button.style.alignSelf = 'center';
    button.textContent = globalize.translate('MovieDiscoveryLoadMore');
    container.appendChild(button);
    let active = true;
    let loading = false;
    const observer = typeof IntersectionObserver === 'undefined' ? null : new IntersectionObserver(entries => {
        if (entries.some(entry => entry.isIntersecting)) void load();
    }, { rootMargin: '100px', threshold: 0.1 });
    const load = async () => {
        if (!active || loading || !container.isConnected) return;
        loading = true;
        button.disabled = true;
        try {
            const result = await loadMore();
            if (!active || !container.isConnected) return;
            button.insertAdjacentHTML('beforebegin', renderItems(result.Items));
            container.closest('.emby-scroller')?.scroller?.reload();
            imageLoader.lazyChildren(container);
            if (!result.HasMore) {
                observer?.disconnect();
                button.remove();
            }
        } catch (error) {
            console.warn('[MovieDiscovery] Could not load more movies', error);
            button.textContent = globalize.translate('MovieDiscoveryRetry');
            // A failed request is retried explicitly, not in a request loop.
            observer?.disconnect();
        } finally {
            loading = false;
            button.disabled = false;
        }
    };
    button.addEventListener('click', load);
    observer?.observe(button);
    container.cleanupPaging = () => {
        active = false;
        observer?.disconnect();
        button.removeEventListener('click', load);
        button.remove();
    };
}
