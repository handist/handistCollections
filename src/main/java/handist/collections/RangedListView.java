/*******************************************************************************
 * Copyright (c) 2020 Handy Tools for Distributed Computing (HanDist) project.
 *
 * This program and the accompanying materials are made available to you under 
 * the terms of the Eclipse Public License 1.0 which accompanies this 
 * distribution, and is available at https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 *******************************************************************************/
package handist.collections;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import handist.collections.function.LongTBiConsumer;

/**
 * {@link RangedListView} provides an access to a {@link Chunk} restricted to a specific range. 
 *
 * @param <T> type handled by the {@link RangedListView} this instance provides access to
 */
public class RangedListView<T> extends RangedList<T> implements Serializable, KryoSerializable {

	/**
	 * Iterator on the elements of {@link #base} this {@link RangedListView} provides access to
	 * @param <T> the type handled by the {@link RangedList} {@link #base}
	 */
	private static class It<T> implements Iterator<T> {
		private long i;
		private LongRange range;
		private RangedListView<T> rangedListView;

		public It(RangedListView<T> view) {
			rangedListView = view;
			range = rangedListView.getRange();
			this.i = range.from - 1;
		}

		public It(RangedListView<T> view, long i0) {
			view.rangeCheck(i0);
			rangedListView = view;
			this.range = rangedListView.getRange();
			this.i = i0 - 1;
		}

		@Override
		public boolean hasNext() {
			return i + 1 < range.to;
		}

		@Override
		public T next() {
			return rangedListView.get(++i);
		}

	}
	/** Serial Version UID */
	private static final long serialVersionUID = 8258165981421352660L;

	/**
	 * Creates a new {@link RangedListView} which does not allow access to
	 * any portion of any {@link RangedList}
	 * @param <T> type handled by this instance
	 * @return a newly created {@link RangedListView} which does not grant any access
	 */
	public static <T> RangedListView<T> emptyView() {
		return new RangedListView<>((Chunk<T>)null, new LongRange(0, 0));
	}

	/** Chunk instance whose access is controlled by this instance */
	private Chunk<T>  base;

	/**
	 * The range of the {@link RangedList} which this object allows access to
	 */
	protected LongRange range;

	/**
	 * Creates a new {@link RangedListView} which grants access to the provided {@link RangedList}
	 * only on the specified range. 
	 * <p>
	 * The provided base can either be a {@link Chunk} or an existing {@link RangedListView}, in 
	 * which case the {@link Chunk} base of this {@link RangedListView} will be extracted.
	 * @param base {@link RangedList} this instance will control access to
	 * @param range the range of indices that the created instance allows access to
	 */
	public RangedListView(RangedList<T> base, LongRange range) {
		this.range = range;        
		if(base == null) {
			this.base = null;
			return;
		}
		if(base instanceof Chunk) {
			this.base = (Chunk<T>)base;
		} else if(base instanceof RangedListView) {
			this.base = ((RangedListView<T>)base).base; //base;
		} else {
			throw new UnsupportedOperationException("not supported class: "+ base.getClass());
		}
		if(!base.getRange().contains(range)) 
			throw new IndexOutOfBoundsException("[RangeListView] " + range + " is not contained in " + base.getRange());
	}
//
//	@Override
//	public void clear() {
//		throw new UnsupportedOperationException();
//	}

	/**
	 * Creates a new {@link RangedList} which contains clones of the elements this 
	 * {@link RangedListView} grants access to. 
	 */
	@Override
	public RangedList<T> clone() {
		return cloneRange(range);
	}

	/**
	 * Creates a new {@link RangedList} on the specified range containing clones
	 * of the elements this view grants access to. The specified range must be 
	 * included into this {@link RangedListView}'s range. 
	 */
	@Override
	public RangedList<T> cloneRange(LongRange range) {
		rangeCheck(range);
		if (range.equals(base.getRange())) return base.clone();
		return base.cloneRange(range);
	}

	/**
	 * Checks if the provided object is contained in the {@link RangedList} this
	 * instance provided access to on the specific indices this instance allows
	 * access to. If the underlying {@link RangedList} contains the specified 
	 * object at an index that this {@link RangedListView} does not grant access
	 * to, this method will return false
	 */
	@Override
	public boolean contains(Object o) {
		for (long i = range.from; i < range.to; i++) {
			T elem = base.get(i);
			if (o == null ? elem == null : o.equals(elem)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean equals(Object o) {
		return RangedList.equals(this, o);
	}

	@Override
	public <U> void forEach(LongRange range, BiConsumer<? super T, Consumer<? super U>> action,
			Consumer<? super U> receiver) {
		rangeCheck(range);
		base.forEach(range, action, receiver);
	}

	@Override
	public void forEach(LongRange range, Consumer<? super T> action) {
		rangeCheck(range);
		base.forEach(range, action);
	}


	@Override
	public void forEach(LongRange range, LongTBiConsumer<? super T> action) {
		rangeCheck(range);        
		base.forEach(range, action);
	}

	/**
	 * Get the element at the provided {@code index}. 
	 * 
	 * @param index index of the element to retrieve
	 * @return the value stored at the given index
	 */
	@Override
	public T get(long index) {
		rangeCheck(index);
		return base.get(index);
	}

	/**
	 * Returns the range this instance allows access to
	 */
	@Override
	public LongRange getRange() {
		return range;
	}

	@Override
	public int hashCode() {
		return RangedList.hashCode(this);
	}
	
	/**
	 * Returns a new iterator on the elements of the RangedList this instance provides
	 * access to. 
	 */
	@Override
	public Iterator<T> iterator() {
		return new It<T>(this);
	}

	/**
	 * Returns a new iterator which starts at the provided index
	 * @param l starting index of the iterator
	 * @return iterator on the elements this {@link RangedListView} grants 
	 * access to starting at the specified index
	 */
	public Iterator<T> iterator(long l) {
		return new It<T>(this, l);
	}

	@Override
	public void read(Kryo kryo, Input input) {
		@SuppressWarnings("unchecked")
		Chunk<T> chunk = (Chunk<T>) kryo.readClassAndObject(input);
		this.base = chunk;
		this.range = chunk.getRange();
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		@SuppressWarnings("unchecked")
		Chunk<T> chunk = (Chunk<T>) in.readObject();
		this.base = chunk;
		this.range = chunk.getRange();
	}
	
	/**
	 * Set the given value at the specified index. 
	 *  
	 * @param index index at which the value should be set
	 * @param v value to set at the specified index
	 * @return old value at the specified index, or {@code null} if there was no
	 * previous vale or the previous value was {@code null}
	 * @throws IndexOutOfBoundsException if the given index is out of the range allowed by the view
	 */
	@Override
	public T set(long index, T v) {
		rangeCheck(index); 
		return base.set(index, v);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <S> void setupFrom(RangedList<S> from, Function<? super S, ? extends T> func) {
		rangeCheck(from.getRange());
		base.setupFrom(from, func);
	}

	/**
	 * Returns the number of indices this {@link RangedListView} provides access
	 * to
	 */
	@Override
	public long size() {
		return super.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] toArray() {
		return toArray(range);
	}
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] toArray(LongRange range) {
		rangeCheck(range);
		return base.toArray(range);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Chunk<T> toChunk(LongRange range) {
		rangeCheck(range);
		return base.toChunk(range);
	}
	
	@Override
	public List<T> toList(LongRange r) {
		rangeCheck(r);
		return base.toList(r);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if(range==null) return "RangedListView under construction";
		sb.append("[" + range + "]");
		long sz = Config.omitElementsToString ? Math.min(size(), Config.maxNumElementsToString) : size();
		long c = 0;
		for (long i = range.from; i < range.to; i++) {
			if (c++ > 0) {
				sb.append(",");
			}
			sb.append("" + base.get(i));
			if (c == sz) {
				break;
			}
		}
		if (sz < size()) {
			sb.append("...(omitted " + (size() - sz) + " elements)");
		}
		// sb.append("@" + range.begin + ".." + last() + "]");
		return sb.toString();
	}
	
	@Override
	public void write(Kryo kryo, Output output) {
		Chunk<T> chunk = this.toChunk(range);
		kryo.writeClassAndObject(output, chunk);
	}
	// TODO this implement generates redundant RangedListView at receiver node.
	private void writeObject(ObjectOutputStream out) throws IOException {
		Chunk<T> chunk = this.toChunk(range);
		out.writeObject(chunk);
	}

	/*
    public static void main(String[] args) {
        long i = 10;
        Chunk<Integer> c = new Chunk<>(new LongRange(10 * i, 11 * i));
        System.out.println("prepare:" + c);
        for (long j = 0; j < i; j++) {
            int v = (int) (10 * i + j);
            System.out.println("set@" + v);
            c.set(10 * i + j, v);
        }
        System.out.println("Chunk :" + c);
        RangedList<Integer> r1 = c.subList(10 * i + 0, 10 * i + 2);
        RangedList<Integer> r2 = c.subList(10 * i + 2, 10 * i + 8);
        RangedList<Integer> r3 = c.subList(10 * i + 8, 10 * i + 9);
        RangedList<Integer> r4 = c.subList(10 * i + 0, 10 * i + 9);
        System.out.println("RangedListView: " + r1);
        System.out.println("RangedListView: " + r2);
        System.out.println("RangedListView: " + r3);
        System.out.println("RangedListView: " + r4);
    }
	 */
}
