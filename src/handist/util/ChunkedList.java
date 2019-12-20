package handist.util;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ChunkedList<T> extends AbstractCollection<T> implements Collection<T> {

    //    private List<RangedList<T>> chunks;
    private TreeMap<LongRange, RangedList<T>> chunks;
    private long size = 0;

    public ChunkedList() {
        chunks = new TreeMap<>(Comparator.comparingLong(r -> r.begin));
    }

    public ChunkedList(TreeMap chunks) {
        this.chunks = chunks;
    }

    public void checkOverlap(LongRange range) {
	LongRange floorKey = chunks.floorKey(range);
	if (floorKey != null && floorKey.isOverlapped(range)) {
	    throw new IllegalArgumentException("ChunkedList#checkOverlap : requested range " + range + " is overlapped with " + chunks.get(floorKey));
	}

	LongRange nextKey = chunks.higherKey(range);
	if (nextKey != null && nextKey.isOverlapped(range)) {
	    throw new IllegalArgumentException("ChunkedList#checkOverlap : requested range " + range + " is overlapped with " + chunks.get(nextKey));
	}
    }

    public boolean containsIndex(long i) {
        LongRange r = new LongRange(i);
	Map.Entry<LongRange, RangedList<T>> entry = chunks.floorEntry(r);
	if (entry == null || !entry.getKey().contains(i)) {
	    return false;
	}
        return true;
    }

    public T get(long i) {
	LongRange r = new LongRange(i);
	Map.Entry<LongRange, RangedList<T>> entry = chunks.floorEntry(r);
	if (entry == null || !entry.getKey().contains(i)) {
	    throw new IndexOutOfBoundsException("ChunkedList: index " + i + " is out of range of " + chunks);
	}
        RangedList<T> chunk = entry.getValue();
        return chunk.get(i);
    }

    public T set(long i, T value) {
	LongRange r = new LongRange(i);
	Map.Entry<LongRange, RangedList<T>> entry = chunks.floorEntry(r);
	if (entry == null || !entry.getKey().contains(i)) {
	    throw new IndexOutOfBoundsException("ChunkedList: index " + i + " is out of range of " + chunks);
	}
        RangedList<T> chunk = entry.getValue();
        return chunk.set(i, value);
    }

    @Override
    public boolean isEmpty() {
	return size == 0;
    }

    /**
     * Clear the local elements
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }


    @Override
    public boolean contains(Object o) {
        for (RangedList<T> chunk : chunks.values()) {
            if (chunk.contains(o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
//        cf. https://stackoverflow.com/questions/10199772/what-is-the-cost-of-containsall-in-java
        Iterator<?> e = c.iterator();
        while (e.hasNext()) {
            if (!this.contains(e.next())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return whether this chunked list contins the given chunk.
     */

    public boolean containsChunk(RangedList<T> c) {
        return chunks.containsValue(c);
    }

    /**
     * Return the number of local elements.
     *
     * @return the number of local elements.
     */
    @Override
    public int size() {
        return (int) longSize();
    }

    public long longSize() {
	return size;
    }

    /**
     * Return a Container that has the same values in the DistCol.
     *
     * @return a Container that has the same values in the DistCol.
     */
    @Override
    protected Object clone() {
        TreeMap<LongRange, RangedList> newChunks = new TreeMap<>();
        for (RangedList c : chunks.values()) {
            newChunks.put(c.getRange(), ((Chunk) c).clone());
        }
        return new ChunkedList<T>(newChunks);
    }

    /**
     * Return the logical range assined end local.
     *
     * @return an instance of LongRange.
     */
    public Collection<LongRange> ranges() {
	return chunks.keySet();
    }

    public void addChunk(RangedList<T> c) {
        checkOverlap(c.getRange());
        chunks.put(c.getRange(), c);
        size += c.size();
    }

    public RangedList<T> removeChunk(RangedList<T> c) {
        return chunks.remove(c.getRange());
    }

    public int numChunks() {
	return chunks.size();
    }

    public void each(Consumer<T> action) {
        for (RangedList c : chunks.values()) {
            c.each(action);
        }
    }

    public <U> void each(BiConsumer<T, Receiver<U>> action, Receiver<U> receiver) {
        for (RangedList c : chunks.values()) {
            c.each(t -> action.accept((T) t, receiver));
        }
    }

    public void eachChunk(Consumer<RangedList<T>> action) {
        for (RangedList c : chunks.values()) {
            action.accept(c);
        }
    }

    public Map<LongRange, RangedList<T>> filterChunk(Predicate<RangedList<T>> filter) {
        TreeMap<LongRange, RangedList<T>> map = new TreeMap<>();
        for (RangedList c : chunks.values()) {
            if (filter.test(c)) {
                map.put(c.getRange(), c);
            }
        }
        return map;
    }

    public ChunkedList<T>[] separate(int n) {
        long totalNum = size();
        long rem = totalNum % n;
        long quo = totalNum / n;
        ChunkedList<T>[] result = new ChunkedList[n];
	RangedList<T> c = chunks.firstEntry().getValue();
        long used = 0;

        for (int i = 0; i < n; i++) {
            ChunkedList<T> r = new ChunkedList<>();
            result[i] = r;
            long rest = quo + ((i < rem) ? 1 : 0);
            while (rest > 0) {
		LongRange range = c.getRange();
                if (c.size() - used <= rest) {
                    long from = range.begin + used;
                    r.addChunk(c.subList(from, range.end));
                    rest -= c.size() - used;
                    used = 0;
		    c = chunks.higherEntry(range).getValue();
                } else {
                    long from = range.begin + used;
                    long to = from + rest;
                    r.addChunk(c.subList(from, to));
                    used += rest;
                    rest = 0;
                }

            }
        }
        return result;
    }

    private static class It<S> implements Iterator {
        public TreeMap<LongRange, RangedList<S>> chunks;
	private LongRange range;
        private Iterator<S> cIter;

        public It(TreeMap<LongRange, RangedList<S>> chunks) {
            this.chunks = chunks;
	    Map.Entry<LongRange, RangedList<S>> firstEntry = chunks.firstEntry();
	    if (firstEntry != null) {
		RangedList<S> firstChunk = firstEntry.getValue();
		range = firstChunk.getRange();
		cIter = firstChunk.iterator();
	    } else {
		range = null;
		cIter = null;
	    }
        }

        @Override
        public boolean hasNext() {
	    if (range == null) {
		return false;
	    }
	    if (cIter.hasNext()) {
		return true;
	    }
	    Map.Entry<LongRange, RangedList<S>> nextEntry = chunks.higherEntry(range);
	    if (nextEntry == null) {
		range = null;
		cIter = null;
		return false;
	    }
	    range = nextEntry.getKey();
	    cIter = nextEntry.getValue().iterator();
	    return cIter.hasNext();
	}

        @Override
        public S next() {
            if (hasNext()) {
                return cIter.next();
            }
            throw new IndexOutOfBoundsException();
        }

    }

    @Override
    public Object[] toArray() {
//        return new Object[0];
        throw new UnsupportedOperationException();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
//        return null;
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<T> iterator() {
        return new It<T>(chunks);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ChunksList(" + chunks.size() + ")");
        for (RangedList c : chunks.values()) {
            sb.append("," + c);
        }
        sb.append("]");
        return sb.toString();
    }

    public static void main(String[] args) {

	ChunkedList<String> cl5 = new ChunkedList<>();

	// Test1: Add Chunks

	System.out.println("Test1");
	for (int i = 0; i < 10; i++) {
	    if (i % 2 == 1) {
		continue;
	    }
	    long begin = i * 5;
	    long end = (i + 1) * 5;
	    Chunk<String> c = new Chunk<>(new LongRange(begin, end));
	    for (long index = begin; index < end; index++) {
		c.set(index, String.valueOf(index));
	    }
	    cl5.addChunk(c);
	}
	System.out.println(cl5.toString());

	// Test2: Iterate using each()

	System.out.println("Test2");
	StringBuilder sb2 = new StringBuilder();
	cl5.each(value -> sb2.append(value + ","));
	System.out.println(sb2.toString());

	// Test3: Iterate using iterator()

	System.out.println("Test3");
	StringBuilder sb3 = new StringBuilder();
	Iterator<String> it = cl5.iterator();
	while (it.hasNext()) {
	    sb3.append(it.next() + ",");
	}
	System.out.println(sb3.toString());

	// Test4: Raise exception

	System.out.println("Test4");
	for (int i = 0; i < 10; i++) {

	    long begin = i * 5 - 1;
	    long end = i * 5 + 1;
	    Chunk<String> c = new Chunk<>(new LongRange(begin, end));
	    try {
		cl5.addChunk(c);
		System.out.println("--- FAIL ---");
	    } catch (IllegalArgumentException e) {
		// do nothing
	    }
	}
	for (int i = 0; i < 10; i++) {

	    long begin = i * 5 - 1;
	    long end = i * 5 + 5;
	    Chunk<String> c = new Chunk<>(new LongRange(begin, end));
	    try {
		cl5.addChunk(c);
		System.out.println("--- FAIL ---");
	    } catch (IllegalArgumentException e) {
		// do nothing
	    }
	}
	for (int i = 0; i < 10; i++) {

	    long begin = i * 5 - 1;
	    long end = i * 5 + 10;
	    Chunk<String> c = new Chunk<>(new LongRange(begin, end));
	    try {
		cl5.addChunk(c);
		System.out.println("--- FAIL ---");
	    } catch (IllegalArgumentException e) {
		// do nothing
	    }
	}
	System.out.println("--- OK ---");
	// Test5: Add RangedListView

	System.out.println("Test5");
	Chunk<String> c0 = new Chunk<>(new LongRange(0, 10 * 5));
	for (long i = 0; i < 10 * 5; i++) {
	    c0.set(i, String.valueOf(i));
	}
	for (int i = 0; i < 10; i++) {
	    if (i % 2 == 0) {
		continue;
	    }
	    long begin = i * 5;
	    long end = (i + 1) * 5;
	    RangedList<String> rl = c0.subList(begin, end);
	    cl5.addChunk(rl);
	}
	System.out.println(cl5.toString());

	// Test6: Iterate combination of Chunk and RangedListView
	System.out.println("Test6");
	StringBuilder sb6 = new StringBuilder();
	Iterator<String> it6 = cl5.iterator();
	while (it6.hasNext()) {
	    sb6.append(it6.next() + ",");
	}
	System.out.println(sb6.toString());

	// Test7: Raise exception on ChunkedList with continuous range

	System.out.println("Test7");
	for (int i = 0; i < 10 * 5; i++) {
	    long begin = i - 1;
	    long end = i + 1;
	    Chunk<String> c = new Chunk<>(new LongRange(begin, end));
	    try {
		cl5.addChunk(c);
		System.out.println("--- FAIL --- " + begin + "," + end);
	    } catch (IllegalArgumentException e) {
		// do nothing
	    }
	}
	for (int i = 0; i < 10 * 5; i++) {
	    long begin = i - 1;
	    long end = i + 6;
	    Chunk<String> c = new Chunk<>(new LongRange(begin, end));
	    try {
		cl5.addChunk(c);
		System.out.println("--- FAIL --- " + begin + "," + end);
	    } catch (IllegalArgumentException e) {
		// do nothing
	    }
	}
	System.out.println("--- OK ---");

    }

}