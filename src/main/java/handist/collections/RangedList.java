package handist.collections;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import handist.collections.function.LTConsumer;

public interface RangedList<T> extends Iterable<T> {

    LongRange getRange();

    T get(long index);
    T set(long index, T value);

    long longSize();

    default public boolean isEmpty() {
        return getRange().size() == 0;
    };

    abstract boolean contains(Object o);

    default public List<RangedList<T>> splitRange(long splitPoint) {
        LongRange range = getRange();
        RangedList<T> rangedList1 = new RangedListView<T>(this, new LongRange(range.from, splitPoint));
        RangedList<T> rangedList2 = new RangedListView<T>(this, new LongRange(splitPoint, range.to));
        return Arrays.asList(rangedList1, rangedList2);
    }

    default public List<RangedList<T>> splitRange(long splitPoint1, long splitPoint2) {
        LongRange range = getRange();
        RangedList<T> rangedList1 = new RangedListView<T>(this, new LongRange(range.from, splitPoint1));
        RangedList<T> rangedList2 = new RangedListView<T>(this, new LongRange(splitPoint1, splitPoint2));
        RangedList<T> rangedList3 = new RangedListView<T>(this, new LongRange(splitPoint2, range.to));
        return Arrays.asList(rangedList1, rangedList2, rangedList3);
    }
    default public void rangeCheck(LongRange target) {
        if(!this.getRange().contains(target)) {
            throw new ArrayIndexOutOfBoundsException(
                "[Chunk] range missmatch:" + this.getRange() + " must includes " + target);
        }
    }

    default public void forEach(LongRange range, Consumer<? super T> action) {
        for (long i = range.from; i < range.to; i++) {
            action.accept(get(i));
        }   
    }

    default public void forEach(LongRange range, LTConsumer<? super T> action) {
        for (long i = range.from; i < range.to; i++) {
            action.accept(i, get(i));
        }   
    }

    default public <U> void forEach(LongRange range, BiConsumer<? super T, Consumer<? super U>> action,
            Consumer<? super U> receiver) {
        for (long i = range.from; i < range.to; i++) {
            action.accept(get(i), receiver);
        }   
    }

    default public void forEach(Consumer<? super T> action) {
        forEach(getRange(), action);
    }
    default public void forEach(LTConsumer<? super T> action) {
        forEach(getRange(), action);
    }
    default public <U> void forEach(BiConsumer<? super T, Consumer<? super U>> action, Consumer<? super U> receiver) {
        forEach(getRange(), action, receiver);
    }

    // TODO
    // forEach(pool, nthread) and map(pool, nthreads) will be implemented using
    // those of ChunkedList

    default public <U> RangedList<U> map(LongRange range, Function<? super T, ? extends U> func) {
        return this.subList(range.from, range.to).map(func);
    }

    default public <U> RangedList<U> map(Function<? super T, ? extends U> func) {
        Chunk<U> result = new Chunk<>(this.getRange());
        result.setupFrom(this, func);
        return result;
    }

    abstract public <S> void setupFrom(RangedList<S> from, Function<? super S, ? extends T> func);
    

    
    RangedList<T> cloneRange(LongRange newRange);
    Chunk<T> toChunk(LongRange newRange);
    Object[] toArray();
    Object[] toArray(LongRange newRange);

    default public RangedList<T> subList(LongRange range) {
        return subList(range.from, range.to);
    };
    public RangedList<T> subList(long begin, long end);
    public Iterator<T> iteratorFrom(long i);

}