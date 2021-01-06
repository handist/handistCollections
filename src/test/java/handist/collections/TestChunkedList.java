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

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import handist.collections.dist.util.ObjectInput;
import handist.collections.dist.util.ObjectOutput;

public class TestChunkedList {

	public static class Element implements Serializable {
		/** Serial Version UID */
		private static final long serialVersionUID = 5318079351127834274L;
		public int n = 0;
		public Element(int i) {
			n = i;
		}
		public Element increase(int i) {
			n += i;
			return this;
		}
		@Override
		public String toString() {
			return String.valueOf(n);
		}
	}

	public class MultiIntegerReceiver implements ParallelReceiver<Integer> {

		private class PConsumer implements Consumer<Integer> {

			int number;

			PConsumer(int nb) {
				number = nb;
			}

			@Override
			public void accept(Integer t) {
				((ConcurrentSkipListSet<Integer>) parallelAcceptors[number]).add(t);
			}

		}
		private int nextReceiver;

		ConcurrentSkipListSet<Integer> [] parallelAcceptors;

		/**
		 * Builds a Receiver of {@link Integer} that can accept objects
		 * from the specified number of concurrent threads. 
		 * 
		 * @param parallelism the number of threads susceptible to send {@link Integer}s to be accepted by this object
		 */
		@SuppressWarnings("unchecked")
		public MultiIntegerReceiver (int parallelism) {
			nextReceiver = 0;
			parallelAcceptors = new ConcurrentSkipListSet[parallelism];
			for(int i=0; i<parallelism; i++) {
				parallelAcceptors[i] = new ConcurrentSkipListSet<Integer>();
			}
		}

		@Override
		public Consumer<Integer> getReceiver() {
			return new PConsumer(nextReceiver++);
		}
	}

	/** chunkedList filled with some initial members */
	ChunkedList<Element> chunkedList;
	/** Contains 3 different Chunk instances in an array */
	@SuppressWarnings("unchecked")
	Chunk<Element>[] chunks = new Chunk[3];

	/** Contains 6 initialized instances of class Element */
	Element[] elems = new Element[6];
	/** freshly created chunkedList which is empty */
	ChunkedList<Element> newlyCreatedChunkedList;


	@Before
	public void setUp() throws Exception {
		newlyCreatedChunkedList = new ChunkedList<>();

		Chunk<Element> firstChunk = new Chunk<>(new LongRange(0, 3)); 
		Chunk<Element> secondChunk = new Chunk<>(new LongRange(3,5));
		Chunk<Element> thirdChunk = new Chunk<>(new LongRange(5,6));
		chunks[0] = firstChunk;
		chunks[1] = secondChunk;
		chunks[2] = thirdChunk;

		for(int i = 0; i < 6; i++) { 
			elems[i] = new Element(i);
		}
		for(int i = 0; i < 3; i++) { 
			chunks[0].set(i, elems[i]);
		}
		for(int i = 3; i < 5; i++) {
			chunks[1].set(i, elems[i]);
		}
		chunks[2].set(5, elems[5]);

		chunkedList = new ChunkedList<>();
		chunkedList.add(chunks[0]);
		chunkedList.add(chunks[1]);
		chunkedList.add(chunks[2]);
		chunkedList.set(4, null);	// include value for a null test
	}

//	@Test(expected = UnsupportedOperationException.class)
//	public void testAdd() {
//		chunkedList.add(null);
//	}
//
//	@Test
//	public void testAddAll() {
//		chunkedList.addAll();
//	}

	@Test
	public void testAddChunk() {
		assertEquals(0, newlyCreatedChunkedList.numChunks());
		newlyCreatedChunkedList.add(chunks[1]);
		assertEquals(2l, newlyCreatedChunkedList.size());
		assertEquals(1, newlyCreatedChunkedList.numChunks());
	}

	@Test(expected = RuntimeException.class)
	public void testAddChunkErrorIdenticalChunk() {
		chunkedList.add(chunks[1]);
	}

	@Test(expected = RuntimeException.class)
	public void testAddChunkErrorOverlapChunk1() {
		chunkedList.add(new Chunk<>(new LongRange(0)));
	}

	@Test(expected = RuntimeException.class)
	public void testAddChunkErrorOverlapChunk2() {
		chunkedList.add(new Chunk<>(new LongRange(2,3)));
	}

	@Test(expected = RuntimeException.class)
	public void testAddChunkErrorOverlapChunk3() {
		chunkedList.add(new Chunk<>(new LongRange(3,4)));
	}

	@Test(expected = RuntimeException.class)
	public void testAddChunkErrorOverlapChunk4() {
		chunkedList.add(new Chunk<>(new LongRange(-1,7)));
	}

	@Test(expected = NullPointerException.class)
	public void testAddChunkNullArg() {
		newlyCreatedChunkedList.add(null);
	}

	@Test
	public void testAsyncForEachBiConsumerMultiReceiver() throws InterruptedException, ExecutionException {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		chunkedList.set(4l, elems[4]);

		final MultiIntegerReceiver accumulator = new MultiIntegerReceiver(2);

		chunkedList.asyncForEach(	pool, 2, 
				(t, consumer)-> consumer.accept(-t.n), 
				accumulator).get();

		assertEquals(2, accumulator.parallelAcceptors.length);
		// Due to race conditions, there are cases where only one accumulator is used during the test. 
		// We cannot expect both to accumulators to be exactly of length 3
		// assertEquals(3, ((ArrayList<Integer>) accumulator.parallelAcceptors[0]).size());
		// assertEquals(3, ((ArrayList<Integer>)accumulator.parallelAcceptors[1]).size());
		int totalAccepted = 0;
		for (ConcurrentSkipListSet<Integer> a : accumulator.parallelAcceptors) {
			totalAccepted += a.size();
		}
		assertEquals(6, totalAccepted);
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testAsyncForEachConsumer() {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		Future<ChunkedList<Element>> future = 
				chunkedList.asyncForEach(pool, 2, (e) -> {
					if (e != null) {
						e.increase(10);
					}
				});

		ChunkedList<Element> result = null;
		try {
			result = future.get();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
			fail();
		} catch (ExecutionException e1) {
			e1.printStackTrace();
			fail();
		}
		assertEquals(6l, result.size());
		assertEquals(3, result.numChunks());
		assertEquals(10, result.get(0).n);
		assertEquals(11, result.get(1).n);
		assertEquals(12, result.get(2).n);
		assertEquals(13, result.get(3).n);
		assertEquals(null, result.get(4));
		assertEquals(15, result.get(5).n);
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testAsyncForEachLongTBiConsumer() {
		chunkedList.set(4l, elems[4]);
		int [] originalValues = new int[elems.length];
		for (int i = 0; i < elems.length; i++) {
			originalValues[i] = elems[i].n;
		}

		ExecutorService pool = Executors.newFixedThreadPool(2);

		Future<ChunkedList<Element>> future = chunkedList.asyncForEach(pool, 2, (l,e)-> {
			e.increase((int)l);
		});
		ChunkedList<Element> result = null;
		try {
			result = future.get();
		} catch (InterruptedException | ExecutionException e1) {
			e1.printStackTrace();
			fail();
		}

		for (long i=0; i<elems.length; i++) {
			assertEquals((int) originalValues[(int)i]+i, result.get(i).n);
		}
	}

	@Test
	public void testAsyncMap() throws InterruptedException, ExecutionException {
		// First, remove the null element
		chunkedList.set(4, new Element(42));
		ExecutorService pool = Executors.newFixedThreadPool(2);

		Future<ChunkedList<Integer>> future = chunkedList.asyncMap(pool, 2, e-> e.n + 5);
		ChunkedList<Integer> result = null;
		try {
			result = future.get();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
			fail();
		} catch (ExecutionException e1) {
			e1.printStackTrace();
			fail();
		}

		assertNotSame(chunkedList, result);
		assertEquals(6, chunkedList.size());
		assertSame(chunkedList.size(), result.size());
		Iterator<Element> originalIterator = chunkedList.iterator();
		Iterator<Integer> integerIterator = result.iterator();
		while (integerIterator.hasNext()) {
			assertSame(integerIterator.next(), originalIterator.next().n + 5);
		}
	}

	@Test
	public void testClear() {
		assertFalse(chunkedList.isEmpty());
		assertTrue(chunkedList.size() > 0l);
		chunkedList.clear();
		assertTrue(chunkedList.isEmpty());
		assertEquals(0l, chunkedList.size());
		
		// Check that calling clean on an empty ChunkedList does not cause 
		// any problem
		newlyCreatedChunkedList.clear();
		assertTrue(newlyCreatedChunkedList.isEmpty());
		assertEquals(0l, newlyCreatedChunkedList.size());
	}

	@Test
	public void testContains() {
		assertTrue(chunkedList.contains(elems[0]));
		assertFalse(chunkedList.contains(new Element(0)));	
		assertTrue(chunkedList.contains(null));
	}

	@Test
	public void testContainsAll() {
		ArrayList<Element> aList1 = new ArrayList<>(Arrays.asList(elems[0], elems[1]));
		assertTrue(chunkedList.containsAll(aList1));

		ArrayList<Element> aList2 = new ArrayList<>(Arrays.asList(new Element(0), new Element(1)));
		assertFalse(chunkedList.containsAll(aList2));

		ArrayList<Element> aList3 = new ArrayList<>();
		assertTrue(chunkedList.containsAll(aList3));
	}

	@Test
	public void testContainsChunk() {
		assertTrue(chunkedList.containsChunk(chunks[0]));
		assertFalse(chunkedList.containsChunk(new Chunk<Element>(new LongRange(6, 9))));
		assertFalse(chunkedList.containsChunk(null));	
	}

	//containsIndex, contains, containsAll, containsChunk
	@Test
	public void testContainsIndex() {
		assertTrue(chunkedList.containsIndex(0));
		assertTrue(chunkedList.containsIndex(5));
		assertFalse(chunkedList.containsIndex(100));
		assertFalse(chunkedList.containsIndex(-1));
	}

	@Test
	public void testFilterChunk() {
		List<RangedList<Element>> l = chunkedList.filterChunk(chunk -> chunk.isEmpty());
		assertSame(l.size(), 0);

		l = chunkedList.filterChunk(chunk -> chunk == chunks[0]);
		assertSame(l.size(), 1);
	}

	@Test
	public void testForEach() {
		// Remove the null value
		chunkedList.set(4l, elems[4]);

		// Keep the original values of the elements on the side
		int [] originalValues = new int[elems.length];
		for (int i = 0; i < elems.length; i ++) {
			originalValues[i] = elems[i].n;
		}

		// Increase each element by 2
		chunkedList.forEach((e) -> e.increase(2));

		// Check that each element has its value increased by 2
		for (int i = 0; i < elems.length; i++) {
			assertEquals(originalValues[i]+2, chunkedList.get(i).n);
		}
	}

	@Test
	public void testForEachBiConsumerCollection() {
		// Remove the null value
		chunkedList.set(4l, elems[4]);

		ArrayList<Integer> accumulator = new ArrayList<>();

		//Accumulate the opposites of each element
		chunkedList.forEach((t, consumer)->consumer.accept(-t.n), accumulator);

		assertEquals(chunkedList.size(), accumulator.size());
	}

	@Test
	public void testForEachBiConsumerConsumer() {
		// Remove the null value
		chunkedList.set(4l, elems[4]);

		// Compute an average of the members in the ChunkedList
		Consumer<Integer> averageComputation = new Consumer<Integer>() {
			public int count = 0;
			public int sum = 0;

			@Override
			public void accept(Integer t) {
				sum += t;
				count++;
			}

			public String toString() {
				return sum + " / " + count;
			}
		};

		chunkedList.forEach((t, consumer)->consumer.accept(t.n), averageComputation);


		//Expected result
		int expectedSum = 0;
		for(Element e : elems) {
			expectedSum += e.n;
		}
		int expectedCount = elems.length;
		String expectedOutput = expectedSum + " / " + expectedCount;

		assertEquals(expectedOutput, averageComputation.toString());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testForEachBiConsumerMultiReceiver() throws InterruptedException, ExecutionException {		
		ExecutorService pool = Executors.newFixedThreadPool(2);
		chunkedList.set(4l, elems[4]);

		final MultiIntegerReceiver accumulator = new MultiIntegerReceiver(2);

		chunkedList.forEach(pool, 2, (t, consumer)-> consumer.accept(-t.n),	accumulator);

		assertEquals(2, accumulator.parallelAcceptors.length);
		assertEquals(3, ((ConcurrentSkipListSet<Integer>) accumulator.parallelAcceptors[0]).size());
		assertEquals(3, ((ConcurrentSkipListSet<Integer>)accumulator.parallelAcceptors[1]).size());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testForEachConsumer() {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		chunkedList.forEach(pool, 2, (e) -> {
			if (e != null) {
				e.increase(10);
			}
		});

		assertEquals(6l, chunkedList.size());
		assertEquals(3, chunkedList.numChunks());
		assertEquals(10, chunkedList.get(0).n);
		assertEquals(11, chunkedList.get(1).n);
		assertEquals(12, chunkedList.get(2).n);
		assertEquals(13, chunkedList.get(3).n);
		assertEquals(null, chunkedList.get(4));
		assertEquals(15, chunkedList.get(5).n);
	}

	@Test
	public void testForEachLongTBiconsumer() {
		chunkedList.set(4l, elems[4]);

		int [] originalValues = new int[elems.length];
		for (int i = 0; i < elems.length; i++) {
			originalValues[i] = elems[i].n;
		}

		chunkedList.forEach((l,e)-> {
			e.increase((int)l);
		});

		for (long i=0; i<elems.length; i++) {
			assertEquals((int) originalValues[(int)i]+i, chunkedList.get(i).n);
		}
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testForEachLongTBiConsumerParallel() {
		ExecutorService service = Executors.newFixedThreadPool(2);
		chunkedList.set(4l, elems[4]);

		int [] originalValues = new int[elems.length];
		for (int i = 0; i < elems.length; i++) {
			originalValues[i] = elems[i].n;
		}

		chunkedList.forEach(service, 2, (l,e)-> {
			e.increase((int)l);
		});

		for (long i=0; i<elems.length; i++) {
			assertEquals((int) originalValues[(int)i]+i, chunkedList.get(i).n);
		}
	}
	
	@Test
	public void testParallelForEachBiConsumer() throws InterruptedException, ExecutionException {
		chunkedList.set(4l, elems[4]);

		int processors = Runtime.getRuntime().availableProcessors() * 2;
		final MultiIntegerReceiver accumulator = new MultiIntegerReceiver(processors);

		chunkedList.parallelForEach((t, consumer)-> consumer.accept(-t.n), accumulator);

		assertEquals(processors, accumulator.parallelAcceptors.length);
		
		int n = 0;
		for(int i = 0; i < processors; i++) {
			n += ((ConcurrentSkipListSet<Integer>) accumulator.parallelAcceptors[i]).size();
		}
		assertEquals(6, n);	
	}
	
	@Test
	public void testParallelForEachConsumer() {
		chunkedList.parallelForEach((e) -> {
			if (e != null) {
				e.increase(10);
			}
		});

		assertEquals(6l, chunkedList.size());
		assertEquals(3, chunkedList.numChunks());
		assertEquals(10, chunkedList.get(0).n);
		assertEquals(11, chunkedList.get(1).n);
		assertEquals(12, chunkedList.get(2).n);
		assertEquals(13, chunkedList.get(3).n);
		assertEquals(null, chunkedList.get(4));
		assertEquals(15, chunkedList.get(5).n);
	}
	
	@Test
	public void testParallelForEachLongTBiConsumer() {
		chunkedList.set(4l, elems[4]);

		int [] originalValues = new int[elems.length];
		for (int i = 0; i < elems.length; i++) {
			originalValues[i] = elems[i].n;
		}

		chunkedList.parallelForEach((l,e)-> {
			e.increase((int)l);
		});

		for (long i=0; i<elems.length; i++) {
			assertEquals((int) originalValues[(int)i]+i, chunkedList.get(i).n);
		}
	}

	@Test(expected=NullPointerException.class)
	public void testForEachWithNull() {
		chunkedList.forEach((e) -> e.increase(2));
	}

	@Test
	public void testGet() {
		assertEquals(elems[0], chunkedList.get(0));
		assertEquals(elems[3], chunkedList.get(3));
		assertEquals(elems[5], chunkedList.get(5));

	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetError() {
		chunkedList.get(6);
	}

	@Test
	public void testIsEmpty() {
		assertFalse(chunkedList.isEmpty());
		assertTrue(newlyCreatedChunkedList.isEmpty());
	}

	@SuppressWarnings("unused")
	@Test
	public void testItOnEmptyChunkedList() {
		for (Element e : newlyCreatedChunkedList) {
			fail("The ChunkedList iterator got an element from an empty ChunkedList");
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testItOutOfBounds() {
		Iterator<Element> it = newlyCreatedChunkedList.iterator();
		assertFalse(it.hasNext());
		it.next();
	}


	@Test
	public void testLongSize() {
		assertEquals(6l, chunkedList.size());
		assertEquals(0, newlyCreatedChunkedList.size());
	}
	
	
	@Test
	public void testConstructor() {
		TreeMap<LongRange, RangedList<Element>> tMap = new TreeMap<>();
		tMap.put(chunks[0].getRange(), chunks[0]);
		tMap.put(chunks[1].getRange(), chunks[1]);
		tMap.put(chunks[2].getRange(), chunks[2]);
		ChunkedList<Element> cList = new ChunkedList<Element>(tMap);
		
		assertEquals(6l, cList.size());
	}
	

	@Test
	public void testMap() {
		// First, remove the null element
		chunkedList.set(4, new Element(42));
		ChunkedList<Integer> integerChunkedList = chunkedList.map(e-> e.n + 5);

		// Check that the returned ChunkedList and the original have 
		// the same number of elements and that the values held by the 
		// result are indeed the ones we expect
		assertNotSame(chunkedList, integerChunkedList);
		assertEquals(6, chunkedList.size());
		assertSame(chunkedList.size(), integerChunkedList.size());
		Iterator<Element> originalIterator = chunkedList.iterator();
		Iterator<Integer> integerIterator = integerChunkedList.iterator();
		while (integerIterator.hasNext()) {
			assertSame(integerIterator.next(), originalIterator.next().n + 5);
		}
	}

	@Test
	public void testMapExecutorService() {
		// First, remove the null element
		chunkedList.set(4, new Element(42));
		ExecutorService pool = Executors.newFixedThreadPool(2);

		ChunkedList<Integer> result = chunkedList.map(pool, 2, e-> e.n + 5);

		assertNotSame(chunkedList, result);
		assertEquals(6, chunkedList.size());
		assertSame(chunkedList.size(), result.size());
		Iterator<Element> originalIterator = chunkedList.iterator();
		Iterator<Integer> integerIterator = result.iterator();
		while (integerIterator.hasNext()) {
			assertSame(integerIterator.next(), originalIterator.next().n + 5);
		}
	}

	@Test(expected = NullPointerException.class)
	public void testMapWithNullElement() {		
		chunkedList.map(e -> e.increase(5));
	}


	@Test
	public void testNumChunks() {
		assertSame(chunkedList.numChunks(), 3);

		chunkedList.add(new Chunk<Element>(new LongRange(6, 9)));
		assertSame(chunkedList.numChunks(), 4);

		chunkedList = new ChunkedList<Element>();
		assertSame(chunkedList.numChunks(), 0);
	}

	@Test
	public void testRanges() {
		int i = 0;
		for(LongRange range: chunkedList.ranges()) {
			assertEquals(range, chunks[i].getRange());
			i++;
		}
	}

	@Test
	public void testRemoveChunk() {
		//Chunk<Element> chunkToRemove = new Chunk<>(new LongRange(-1l, 0l));
		LongRange rangeToRemove = new LongRange(-1l,  0l);
		// Removes nothing, the indices do not intersect. 
		RangedList<Element> removed = chunkedList.remove(rangeToRemove);
		assertEquals(6L, chunkedList.size());
		assertNull(removed);

		// A Chunk that is included but not identical to a chunk of the
		// chunked list is not removed
		rangeToRemove = new LongRange(0l, 1l);
		chunkedList.remove(rangeToRemove);
		assertEquals(6L, chunkedList.size());

		// A chunk with the same range will be removed
		rangeToRemove = new LongRange(0l, 3l);
		removed = chunkedList.remove(rangeToRemove);
		assertEquals(3L, chunkedList.size());
		assertEquals(3l, removed.size());
		for (Element e : removed) {
			assertTrue(removed.contains(e));
			assertFalse(chunkedList.contains(e));
		}

		// If the same object is given as parameter, also works
		removed = chunkedList.remove(chunks[1].getRange());
		assertEquals(1L, chunkedList.size());
		assertEquals(2l, removed.size());
		for (Element e : removed) {
			assertTrue(removed.contains(e));
			assertFalse(chunkedList.contains(e));
		}
	}

	@Test
	public void testSeparate() {
		List<ChunkedList<Element>> cLists = chunkedList.separate(2);
		assertSame(cLists.size(), 2);

		assertSame(cLists.get(0).size(), 3L);
		assertSame(cLists.get(1).size(), 3L);

		assertEquals(cLists.get(0).get(0), elems[0]);
		assertEquals(cLists.get(1).get(5), elems[5]);

		cLists.clear();
		cLists = chunkedList.separate(4);
		assertSame(4, cLists.size());

		assertSame(2L, cLists.get(0).size());
		assertSame(2L, cLists.get(1).size());
		assertSame(1L, cLists.get(2).size());
		assertSame(1L, cLists.get(3).size());

		assertEquals(cLists.get(0).get(0), elems[0]);
		assertEquals(cLists.get(1).get(2), elems[2]);
		// Manually inserted null value
		assertEquals(cLists.get(2).get(4), null);
		assertEquals(cLists.get(3).get(5), elems[5]);

		cLists = chunkedList.separate((10));
		assertSame(cLists.size(), 10);
		assertSame(cLists.get(5).size(), 1L);
		assertSame(cLists.get(9).size(), 0L);
		
		//Special case with empty ChunkedList
		assertTrue(new ChunkedList<Element>().separate(42).isEmpty());
	}

	@Test
	public void testSet() {
		Element e = new Element(-1);
		chunkedList.set(0, e);
		assertEquals(chunkedList.get(0), e);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testSetError() {
		chunkedList.set(6, new Element(-1));
	}

	@Test
	public void testSize() {
		assertEquals(6L, chunkedList.size());	
		assertEquals(0L, newlyCreatedChunkedList.size());
	}

//
//	@Test(expected = UnsupportedOperationException.class)
//	public void testToArray() {
//		chunkedList.toArray();
//	}
//
//	@Test(expected = UnsupportedOperationException.class)
//	public void testToArrayWithParameters() {
//		chunkedList.toArray(new Object[2]);
//	}

	@Test
	public void testToString() {
		assertEquals("[ChunkedList(3),[[0,3)]:0,1,2,[[3,5)]:3,null,[[5,6)]:5]", chunkedList.toString());
		assertEquals("[ChunkedList(0)]", newlyCreatedChunkedList.toString());
	}
	
	@Test
	public void testSerializable()  throws IOException, ClassNotFoundException {
	    assertTrue(chunkedList instanceof Serializable);
	    ByteArrayOutputStream out0 = new ByteArrayOutputStream();
	    ObjectOutputStream out = new ObjectOutputStream(out0);
	    out.writeObject(chunkedList);
	    out.close();
	    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(out0.toByteArray()));
	    @SuppressWarnings("unchecked")
		ChunkedList<Element> c2 = (ChunkedList<Element>)in.readObject();
	    assertEquals(c2.size(), chunkedList.size());
        c2.forEach((long index, Element e)-> {
            if(e==null) assertNull(chunkedList.get(index));
            if(e!=null) assertEquals(chunkedList.get(index).n, e.n);  
         });
	}
	
	@Test
	public void testKryoSerializable() {
	    ByteArrayOutputStream out0 = new ByteArrayOutputStream();
	    ObjectOutput out = new ObjectOutput(out0);
	    out.writeObject(chunkedList);
	    out.close();
	    ObjectInput in = new ObjectInput(new ByteArrayInputStream(out0.toByteArray()));
	    @SuppressWarnings("unchecked")
		ChunkedList<Element> c2 = (ChunkedList<Element>)in.readObject();
	    assertEquals(c2.size(), chunkedList.size());
        c2.forEach((long index, Element e)-> {
            if(e==null) assertNull(chunkedList.get(index));
            if(e!=null) assertEquals(chunkedList.get(index).n, e.n);  
         });
	}
}
