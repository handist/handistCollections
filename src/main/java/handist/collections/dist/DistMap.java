/*******************************************************************************
 * Copyright (c) 2020 Handy Tools for Distributed Computing (HanDist) project.
 *
 * This program and the accompanying materials are made available to you under 
 * the terms of the Eclipse Public License 1.0 which accompanies this 
 * distribution, and is available at https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 *******************************************************************************/
package handist.collections.dist;

import static apgas.Constructs.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import apgas.Constructs;
import apgas.Place;
import apgas.util.GlobalID;

import apgas.util.SerializableWithReplace;

import handist.collections.dist.util.IntLongPair;
import handist.collections.dist.util.LazyObjectReference;
import handist.collections.dist.util.MemberOfLazyObjectReference;
import handist.collections.function.DeSerializer;
import handist.collections.function.SerializableConsumer;
import handist.collections.function.Serializer;
import mpi.MPI;
import mpi.MPIException;

/**
 * A Map data structure spread over the multiple places.
 *
 * @param <K> type of the key used in the {@link DistMap}
 * @param <V> type of the value mapped to each key in the {@link DistMap}
 */
public class DistMap<K, V> implements Map<K, V>, AbstractDistCollection<V, DistMap<K, V>>, KeyRelocatable<K>,SerializableWithReplace {

	public class DistMapGlobal extends GlobalOperations<V, DistMap<K,V>> {
		DistMapGlobal(DistMap<K,V> handle) {
			super(handle);
		}

		@Override
		public Object writeReplace() throws ObjectStreamException {
			final TeamedPlaceGroup pg1 = localHandle.placeGroup();
			final GlobalID id1 = localHandle.id();
			return new MemberOfLazyObjectReference<DistMap<K,V>,DistMap<K,V>.DistMapGlobal>(
					pg1, 
					id1, 
					()-> {return new DistMap<K,V> (pg1,id1);}, 
					(handle)->{return handle.GLOBAL;}
				);
		}
	}
	
	public class DistMapTeam extends TeamOperations<V, DistMap<K,V>> {
		public DistMapTeam(DistMap<K,V> handle) {
			super(handle);
		}

		@Override
		public void size(long[] result) {
			TeamedPlaceGroup pg = handle.placeGroup;
			long localSize = data.size(); // int->long
			long[] sendbuf = new long[] { localSize };
			// team.alltoall(tmpOverCounts, 0, overCounts, 0, 1);
			try {
				pg.comm.Allgather(sendbuf, 0, 1, MPI.LONG, result, 0, 1, MPI.LONG);
			} catch (MPIException e) {
				e.printStackTrace();
				throw new Error("[DistMap] network error in balance()");
			}
		}

		@Override
		public void updateDist() {
			// TODO Auto-generated method stub
			
		}
	}
	
	// TODO
	/*
	 * public void setupBranches(Generator<T,U> gen) { final DistMap<T,U> handle =
	 * this; finish(()->{ placeGroup.broadcastFlat(()->{ gen.accept(here(), handle);
	 * }); }); }
	 */

	// TODO implements Relocatable

	private static int _debug_level = 5;

	/**
	 * Implementation of the local Map collection 
	 */
	protected Map<K, V> data;
	final GlobalID id;
	public transient float[] locality;


	public final TeamedPlaceGroup placeGroup;

	private Function<K, V> proxyGenerator;

	public final DistMap<K, V>.DistMapGlobal GLOBAL;

	/**
	 * Construct an empty DistMap which can have local handles on all the hosts
	 * in the computation. 
	 */
	public DistMap() {
		this(TeamedPlaceGroup.world);
	}

	/**
	 * Construct a DistMap which can have local handles on the hosts of the 
	 * specified {@link TeamedPlaceGroup}.
	 *
	 * @param pg the group of hosts that are susceptible to manipulate this
	 *  {@link DistMap}
	 */
	public DistMap(TeamedPlaceGroup pg) {
		this(pg,new GlobalID());
	}

	public DistMap(TeamedPlaceGroup pg, GlobalID globalId) {
		//super(pg, id);
		placeGroup = pg;
		id = globalId;
		locality = new float[pg.size];
		Arrays.fill(locality, 1.0f);
		this.data = new HashMap<>();
		GLOBAL = new DistMapGlobal(this);
	}

//	Method moved to TEAM and GLOBAL operations
//	@Override
//	public void distSize(long[] result) {
//		TeamedPlaceGroup pg = this.placeGroup;
//		long localSize = data.size(); // int->long
//		long[] sendbuf = new long[] { localSize };
//		// team.alltoall(tmpOverCounts, 0, overCounts, 0, 1);
//		try {
//			pg.comm.Allgather(sendbuf, 0, 1, MPI.LONG, result, 0, 1, MPI.LONG);
//		} catch (MPIException e) {
//			e.printStackTrace();
//			throw new Error("[DistMap] network error in balance()");
//		}
//	}

	/**
	 * Remove the all local entries.
	 */
	public void clear() {
		this.data.clear();
	}

	/**
	 * Return true if the specified entry is exist in the local collection.
	 *
	 * @param key a key.
	 * @return true is the specified object is a key present in the local map, 
	 */
	public boolean containsKey(Object key) {
		return data.containsKey(key);
	}

	/**
	 * Indicates if the provided value is contained in the local map.
	 */
	public boolean containsValue(Object value) {
		return data.containsValue(value);
	}

	boolean debugPrint() { return true; }

	/**
	 * Removes the provided key from the local map, returns {@code true} if 
	 * there was a previous obejct mapped to this key, {@code false} if there 
	 * were no mapping with this key or if the mapping was a {@code null} object
	 * @param key the key to remove from this local map
	 * @return true if a mapping was removed as a result of this operation, 
	 * false otherwise
	 */
	public boolean delete(K key) {
		V result = data.remove(key);
		return (result != null);
	}

	/**
	 * Return the Set of local entries.
	 *
	 * @return the Set of local entries.
	 */
	public Set<Map.Entry<K, V>> entrySet() {
		return data.entrySet();
	}
	/**
	 * Apply the specified operation with each Key/Value pair contained in the
	 * local collection.
	 * @param action the operation to perform
	 */
	public void forEach(BiConsumer<? super K, ? super V> action) {
		if (!data.isEmpty())
			data.forEach(action);
	}

	/**
	 * Return the element for the provided key. If there is no element at the index, return null.
	 *
	 * When an agent generator is set on this instance and there is no element at the index, a proxy value for the index is generated as a return value.
	 *
	 * @param key the index of the value to retrieve
	 * @return the element associated with {@code key}.
	 */
	@SuppressWarnings("unchecked")
	public V get(Object key) {
		V result = data.get(key);
		if(result != null) return result;
		if(proxyGenerator!=null && !data.containsKey(key)) {
			return proxyGenerator.apply((K)key);
		} else {
			return null;
		}
	}

	@Override
	public Collection<K> getAllKeys() {
		return keySet();
	}

	/**
	 * Returns a subset of the keys contained in the local map. If the specified
	 * number of keys is greater than the number of keys actually contained in
	 * the local map, the entire keyset is returned. If a nil or negative number
	 * of keys is asked for, an empty collection is returned.
	 * @param count number of keys desired
	 * @return a collection containing the specified number of keys, or less if
	 * the local map contains fewer keys than the specified parameter
	 */
	private Collection<K> getNKeys(int count) {
		if (count <= 0) {
			return Collections.emptySet();
		}
		ArrayList<K> keys = new ArrayList<>();
		for (K key : data.keySet()) {
			keys.add(key);
			--count;
			if (count == 0)
				return keys;
		}
		return data.keySet();
	}

	@Override
	public GlobalOperations<V, DistMap<K,V>> global() {
		return GLOBAL;
	}

	@Override
	public GlobalID id() {
		return id;
	}

	// TODO remove this method, we already have #putAll
	public void integrate(Map<K, V> src) {
		for(Map.Entry<K,V> e: src.entrySet()) {
			put(e.getKey(), e.getValue());
		}
	}

	/**
	 * Indicates if the local distributed map is empty or not
	 * @return {@code true} if there are no mappings in the local map
	 */
	@Override
	public boolean isEmpty() {
		return data.isEmpty();
	}

	/**
	 * Return the Set of local keys.
	 *
	 * @return the Set of local keys.
	 */
	public Set<K> keySet() {
		return data.keySet();
	}

	@Override
	public float[] locality() {
		return locality;
	}

	/**
	 * Apply the same operation on the all elements including remote places and
	 * creates a new {@link DistMap} with the same keys as this instance and the
	 * result of the mapping operation as values.
	 *
	 * @param <W> result type of mapping operation
	 * @param op the map operation from type <code>V</code> to <code>W</code>
	 * @return a DistMap from <code>K</code> to <code>W</code> built from
	 *  applying the mapping operation on each element of this instance
	 */
	public <W> DistMap<K, W> map(Function<V, W> op) {
		throw new Error("not supported yet");
		// TODO
		/*
		 * return new DistMap<T,S>(placeGroup, team, () -> { val dst = new
		 * HashMap<T,S>(); for (entry in entries()) { val key = entry.getKey(); val
		 * value = entry.getValue(); dst(key) = op(value); } return dst; });
		 */
	}

	@Override
	@SuppressWarnings("unchecked")
	public void moveAtSync(Collection<K> keys, Place pl, MoveManagerLocal mm) {
		if (pl.equals(Constructs.here()))
			return;
		final DistMap<K, V> collection = this;
		Serializer serialize = (ObjectOutputStream s) -> {
			int size = keys.size();
			s.writeInt(size);
			for (K key : keys) {
				V value = collection.remove(key);
				s.writeObject(key);
				s.writeObject(value);
			}
		};
		DeSerializer deserialize = (ObjectInputStream ds) -> {
			int size = ds.readInt();
			for (int i = 1; i <= size; i++) {
				K key = (K) ds.readObject();
				V value = (V) ds.readObject();
				collection.putForMove(key, value);
			}
		};
		mm.request(pl, serialize, deserialize);
	}

	@Override
	public void moveAtSync(Distribution<K> dist, MoveManagerLocal mm) {
		Function<K, Place> rule = (K key) -> {
			return dist.place(key);
		};
		moveAtSync(rule, mm);
	}
	public void moveAtSync(Function<K, Place> rule, MoveManagerLocal mm) {
		DistMap<K, V> collection = this;
		HashMap<Place, List<K>> keysToMove = new HashMap<>();
		collection.forEach((K key, V value) -> {
			Place destination = rule.apply(key);
			if (!keysToMove.containsKey(destination)) {
				keysToMove.put(destination, new ArrayList<K>());
			}
			keysToMove.get(destination).add(key);
		});
		for (Map.Entry<Place, List<K>> entry : keysToMove.entrySet()) {
			moveAtSync(entry.getValue(), entry.getKey(), mm);
		}
	}


	/**
	 * Request that the specified element is relocated when #sync is called.
	 *
	 * @param key the key of the relocated entry.
	 * @param pl  the destination place.
	 * @param mm  MoveManagerLocal
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void moveAtSync(K key, Place pl, MoveManagerLocal mm) {
		if (pl.equals(Constructs.here()))
			return;
		final DistMap<K, V> toBranch = this;
		Serializer serialize = (ObjectOutputStream s) -> {
			V value = this.remove(key);
			s.writeObject(key);
			s.writeObject(value);
		};
		DeSerializer deserialize = (ObjectInputStream ds) ->  {
			K k = (K) ds.readObject();
			V v = (V) ds.readObject();
			toBranch.putForMove(k, v);
		};
		mm.request(pl, serialize, deserialize);
	}

	@Override
	public void moveAtSyncCount(final ArrayList<IntLongPair> moveList, final MoveManagerLocal mm) throws Exception {
		for (IntLongPair pair : moveList) {
			if (_debug_level > 5) {
				System.out.println("MOVE src: " + here() + " dest: " + pair.first + " size: " + pair.second);
			}
			if (pair.second > Integer.MAX_VALUE)
				throw new Error("One place cannot receive so much elements: " + pair.second);
			moveAtSyncCount((int) pair.second, placeGroup.get(pair.first), mm);
		}
	}

	public void moveAtSyncCount(int count, Place dest, MoveManagerLocal mm) {
		if (count == 0)
			return;
		moveAtSync(getNKeys(count), dest, mm);
	}

	@Override
	public TeamedPlaceGroup placeGroup() {
		return placeGroup;
	}

	void printLocalData(){
		System.out.println(this);
	}

	/**
	 * Put a new entry.
	 *
	 * @param key   the key of the new entry.
	 * @param value the value of the new entry.
	 * @return the previous value associated with {@code key}, or
	 * {@code null} if there was no mapping for {@code key}.(A {@code null}
	 * return can also indicate that the map previously associated {@code null}
	 * with {@code key}.)
	 */
	public V put(K key, V value) {
		return data.put(key, value);
	}
	/**
	 * Adds all the mappings contained in the specified map into this local map.
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		data.putAll(m);
	}

	private V putForMove(K key, V value) {
		if (data.containsKey(key)) {
			throw new RuntimeException("DistMap cannot override existing entry: " + key);
		}
		return data.put(key, value);
	}

	/*
    void teamedBalance() {
        LoadBalancer.MapBalancer<T, U> balance = new LoadBalancer.MapBalancer<>(this.data, placeGroup);
        balance.execute();
        if(debugPrint()) System.out.println(here() + " balance.check1");
        clear();
        if(debugPrint()) {
            System.out.println(here() + " balance.check2");
            System.out.println(here() + " balance.ArrayList.size() : " + data.size());
        }
        long time = - System.nanoTime();
        time += System.nanoTime();
        if(debugPrint()) {
    //        	System.out.println(here() + " count : " + (count) + " ms");
    //        	System.out.println(here() + " put : " + (total/(1000000)) + " ms");
            System.out.println(here() + " for : " + (time/(1000000)) + " ms");
            System.out.println(here() + " data.size() : " + size());
            System.out.println(here() + " balance.check3");
        }

    }*/

	/**
	 * Reduce the all elements including other place using the given operation.
	 *
	 * @param <S> type of the result produced by the reduction operation
	 * @param lop  the operation using in the local reduction.
	 * @param gop  the operation using in the reduction of the results of the local
	 *             reduction.
	 * @param unit the zero value of the reduction.
	 * @return the result of the reduction.
	 */
	public <S> S reduce(BiFunction<S, V, S> lop, BiFunction<S, S, S> gop, S unit) {
		// TODO
		throw new Error("Not implemented yet.");
		/*
		 * val reducer = new Reducible[S]() { public def zero() = unit; public operator
		 * this(a: S, b: S) = gop(a, b); }; return finish (reducer) {
		 * placeGroup.broadcastFlat(() => { offer(reduceLocal(lop, unit)); }); };
		 */

	}

	// TODO different naming convention of balance methods with DistMap

	/**
	 * Reduce the all elements including other place using the given operation.
	 *
	 * @param op   the operation.
	 * @param unit the neutral element of the reduction.
	 * @return the result of the reduction.
	 */
	public V reduce(BiFunction<V, V, V> op, V unit) {
		return reduce(op, op, unit);
	}

	/*    Abstractovdef create(placeGroup: PlaceGroup, team: TeamOperations, init: ()=>Map[T, U]){
        // return new DistMap[T,U](placeGroup, init) as AbstractDistCollection[Map[T,U]];
        return null as AbstractDistCollection[Map[T,U]];
    }*/
	/*
    public def versioningMap(srcName : String){
        // return new BranchingManager[DistMap[T,U], Map[T,U]](srcName, this);
        return null as BranchingManager[DistMap[T,U], Map[T,U]];
    }*/

	/**
	 * Reduce the all local elements using the given operation.
	 *
	 * @param <S> type of the result produced by the reduction operation
	 * @param op   the operation used in the reduction
	 * @param unit the neutral element of the reduction operation
	 * @return the result of the reduction
	 */
	public <S> S reduceLocal(BiFunction<S, V, S> op, S unit) {
		// TODO may be build-in method for Map
		S accum = unit;
		for (Map.Entry<K, V> entry : data.entrySet()) {
			accum = op.apply(accum, entry.getValue());
		}
		return accum;
	}

	public void relocate(Distribution<K> rule) throws Exception {
		relocate(rule, new MoveManagerLocal(placeGroup));
	}

	public void relocate(Distribution<K> rule, MoveManagerLocal mm) throws Exception {
		for (K key: data.keySet()) {
			Place place = rule.place(key);
			// TODO
			// if(place==null) throw SomeException();
			moveAtSync(key, place, mm);
		}
		mm.sync();
	}

	public void relocate(Function<K,Place> rule) throws Exception {
		relocate(rule, new MoveManagerLocal(placeGroup));
	}
	
	public void relocate(Function<K, Place> rule, MoveManagerLocal mm) throws Exception {
		for (K key: data.keySet()) {
			Place place = rule.apply(key);
			moveAtSync(key, place, mm);
		}
		mm.sync();
	}

	/**
	 * Remove the entry corresponding to the specified key in the local map.
	 *
	 * @param key the key corresponding to the value.
	 * @return the previous value associated with the key, or {@code null} if
	 * 	there was no existing mapping (or the key was mapped to {@code null})
	 */
	public V remove(Object key) {
		return data.remove(key);
	}

	/**
	 * Sets the proxy generator for this instance. 
	 * <p>
	 * The proxy will be used to generate values when accesses to a key not 
	 * contained in this instance is made. Instead of throwing an exception, the
	 * proxy will be called with the attempted index and the program will 
	 * continue with the value returned by the proxy.
	 * <p>
	 * This feature is similar to {@link Map#getOrDefault(Object, Object)}  
	 * operation, the difference being that instead of returning a predetermined
	 * default value, the provided function is called with the key.
	 *
	 * @param proxy function which takes a key "K" as parameter and returns a 
	 * "V", or {@code null} to remove any previously set proxy 
	 */
	public void setProxyGenerator(Function<K, V> proxy) {
		proxyGenerator = proxy;
	}

	/**
	 * Return the number of the local entries.
	 *
	 * @return the number of the local entries.
	 */
	public int size() {
		return data.size();
	}

	@Override
	public TeamOperations<V, DistMap<K,V>> team() {
		// TODO Auto-generated method stub
		return null;
	}

	public String toString() {
		StringWriter out0 = new StringWriter();
		PrintWriter out = new PrintWriter(out0);
		out.println("at "+ here());
		for(Map.Entry<K,V> e : data.entrySet()) {
			out.println("key : "+e.getKey() + ", value : " + e.getValue());
		}
		out.close();
		return out0.toString();
	}

	/**
	 * Returns all the values of this local map in a collection.
	 */
	public Collection<V> values() {
		return data.values();
	}

	@Override
	public Object writeReplace() throws ObjectStreamException {
		final TeamedPlaceGroup pg1 = placeGroup;
		final GlobalID id1 = id;
		return new LazyObjectReference<DistMap<K, V>>(pg1, id1, () -> {
			return new DistMap<K, V>(pg1, id1);
		});
	}

	@Override
	public void forEach(SerializableConsumer<V> action) {
		data.values().forEach(action);
	}
	
	@Override
	public void parallelForEach(SerializableConsumer<V> action) {
		parallelForEachLocal(action);
	}
	
	private List<Collection<V>> separateLocalValues(int n) {
		List<Collection<V>> result = new ArrayList<>(n);
		long totalNum = size();
		long rem = totalNum % n;
		long quo = totalNum / n;
		if(data.isEmpty()) {
			return result;
		}
		Iterator it = data.values().iterator();
		List<V> list = new ArrayList<V>();
		for(long i = 0; i < n; i++) {
			list = new ArrayList<V>();
			long count = quo + ((i < rem) ? 1: 0);
			for(long j = 0; j < count; j++) {
				if(it.hasNext()) {
					list.add((V)it.next());
				}
			}
			result.add(list);
		}
		return result;
	}
	
	private void parallelForEachLocal(SerializableConsumer<V> action) {
		finish(() -> {
			forEachParallelBodyLocal(action);
		});
	}
	
	private void forEachParallelBodyLocal(SerializableConsumer<V> action) {
		List<Collection<V>> separated = separateLocalValues(Runtime.getRuntime().availableProcessors() * 2);
		for(Collection<V> sub : separated) {
			async(() -> {
				sub.forEach(action);
			});
		}
	}
}
