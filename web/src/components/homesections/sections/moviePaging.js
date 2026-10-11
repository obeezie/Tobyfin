export function shuffleItems(items, random = Math.random) {
    const shuffled = [...items];
    for (let index = shuffled.length - 1; index > 0; index--) {
        const other = Math.floor(random() * (index + 1));
        [shuffled[index], shuffled[other]] = [shuffled[other], shuffled[index]];
    }
    return shuffled;
}

// Each discovery batch combines small slices from four parts of the library.
// Stable, disjoint slices preserve coverage without alphabetical batch clusters.
export function createMoviePager(fetchMovies, query, initial = [], accept = movie => !!movie.Id, compare, { random = Math.random } = {}) {
    const seen = new Set(initial.map(movie => movie.Id));
    const randomized = !query.SortBy || query.SortBy === 'Random';
    const base = { ...query, EnableTotalRecordCount: true, SortBy: randomized ? 'SortName' : query.SortBy, SortOrder: query.SortOrder || 'Ascending' };
    let offset = 0;
    let hasMore = true;
    let pending;
    let ranges;
    let prepared = !randomized;
    const loadMore = () => {
        if (pending) return pending;
        pending = (async () => {
            if (hasMore && !prepared) {
                const count = await fetchMovies({ ...base, StartIndex: 0, Limit: 1 });
                if (typeof count.TotalRecordCount === 'number') {
                    const total = count.TotalRecordCount;
                    const rangeCount = Math.min(4, Math.ceil(total / 10));
                    ranges = Array.from({ length: rangeCount }, (_, index) => {
                        const start = Math.floor(total * index / rangeCount);
                        const end = Math.floor(total * (index + 1) / rangeCount);
                        return shuffleItems(Array.from({ length: Math.ceil((end - start) / 10) }, (unused, slice) => ({
                            StartIndex: start + slice * 10,
                            Limit: Math.min(10, end - start - slice * 10)
                        })), random);
                    });
                    hasMore = ranges.length > 0;
                }
                prepared = true;
            }
            let items = [];
            while (hasMore && !items.length) {
                let page;
                if (ranges) {
                    const active = ranges.filter(range => range.length);
                    const results = await Promise.allSettled(active.map(range => fetchMovies({ ...base, ...range[0], EnableTotalRecordCount: false })));
                    const failure = results.find(result => result.status === 'rejected');
                    // Commit the complete batch together. A partial failure
                    // retries these same slices without losing successful titles.
                    if (failure) throw failure.reason;
                    page = results.flatMap(result => result.value.Items || []);
                    active.forEach(range => {
                        range.shift();
                    });
                    hasMore = ranges.some(range => range.length);
                } else {
                    const result = await fetchMovies({ ...base, StartIndex: offset, Limit: 40 });
                    page = result.Items || [];
                    offset += page.length;
                    hasMore = page.length > 0 && (typeof result.TotalRecordCount === 'number' && result.TotalRecordCount > 0 ? offset < result.TotalRecordCount : page.length === 40);
                }
                items = page.filter(movie => {
                    if (!movie.Id || seen.has(movie.Id) || !accept(movie)) return false;
                    seen.add(movie.Id);
                    return true;
                });
                if (randomized) items = shuffleItems(items, random);
                if (compare) items.sort(compare);
            }
            return { Items: items, HasMore: hasMore };
        })().finally(() => { pending = undefined; });
        return pending;
    };
    return loadMore;
}

export function getDiscoveryGenres() {
    return [
        { title: 'Action', filter: 'Action' },
        { title: 'Comedy', filter: 'Comedy' },
        { title: 'Horror', filter: 'Horror' },
        { title: 'Suspense', filter: 'Suspense|Thriller' },
        { title: 'Drama', filter: 'Drama' },
        { title: 'Sci-Fi', filter: 'Science Fiction|Sci-Fi' },
        { title: 'Fantasy', filter: 'Fantasy' },
        { title: 'Adventure', filter: 'Adventure' },
        { title: 'Crime & Mystery', filter: 'Crime|Mystery' },
        { title: 'Animation', filter: 'Animation' },
        { title: 'Family', filter: 'Family' },
        { title: 'Romance', filter: 'Romance' },
        { title: 'Documentaries', filter: 'Documentary|Documentaries' }
    ];
}
