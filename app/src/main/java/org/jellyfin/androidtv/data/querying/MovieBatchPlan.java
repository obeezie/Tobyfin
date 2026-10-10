package org.jellyfin.androidtv.data.querying;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Visits shuffled slices of four disjoint ranges, mixing the alphabet in every batch. */
public final class MovieBatchPlan {
    public static final class Slice {
        public final int startIndex;
        public final int limit;

        Slice(int startIndex, int limit) {
            this.startIndex = startIndex;
            this.limit = limit;
        }
    }

    private final List<ArrayDeque<Slice>> ranges = new ArrayList<>();

    public MovieBatchPlan(int total, Random random) {
        int rangeCount = Math.min(4, (total + 9) / 10);
        for (int range = 0; range < rangeCount; range++) {
            int start = total * range / rangeCount;
            int end = total * (range + 1) / rangeCount;
            List<Slice> slices = new ArrayList<>();
            for (int offset = start; offset < end; offset += 10) {
                slices.add(new Slice(offset, Math.min(10, end - offset)));
            }
            Collections.shuffle(slices, random);
            ranges.add(new ArrayDeque<>(slices));
        }
    }

    /** Reading a batch does not advance it; failed requests retry the same slices. */
    public List<Slice> nextBatch() {
        List<Slice> batch = new ArrayList<>();
        for (ArrayDeque<Slice> range : ranges) {
            if (!range.isEmpty()) batch.add(range.peek());
        }
        return batch;
    }

    public void commitBatch() {
        for (ArrayDeque<Slice> range : ranges) {
            if (!range.isEmpty()) range.remove();
        }
    }

    public boolean hasMore() {
        for (ArrayDeque<Slice> range : ranges) {
            if (!range.isEmpty()) return true;
        }
        return false;
    }
}
