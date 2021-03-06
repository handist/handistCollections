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

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;


import java.io.*;

public class Bag<T> extends AbstractCollection<T> implements Serializable, MultiReceiver<T> {

    /** Serial Version UID */
	private static final long serialVersionUID = 5436363137856754303L;
	ConcurrentLinkedDeque<List<T>> bags = new ConcurrentLinkedDeque<>();

    @Override
    public boolean contains(Object v) {
        for(Collection<T> bag: bags) {
            if(bag.contains(v)) return true;
        }
        return false;
    }

    @Override
    public void clear() {
        bags.clear();
    }

    @Override
    public Bag<T> clone() {
        Bag<T> result = new Bag<T>();
        for (Collection<T> bag : bags) {
            ArrayList<T> nbag = new ArrayList<>(bag);
            result.addBag(nbag);
        }
        return result;
    }

    public void addBag(List<T> bag) {
        bags.add(bag);
    }

    @Override
    public int size() {
        int size = 0;
        for (Collection<T> bag : bags)
            size += bag.size();
        return size;
    }

    @Override
    public boolean isEmpty() {
        for (Collection<T> bag : bags) {
            if (!bag.isEmpty())
                return false;
        }
        return true;
    }

    public synchronized T remove() {
        while (true) {
            if (bags.isEmpty())
                return null;
            List<T> bag = bags.getLast();
            if (bag.isEmpty()) {
                bags.removeLast();
            } else {
                return bag.remove(bag.size() - 1);
            }
        }
    }
    
    public synchronized List<T> removeN(int count) {
        ArrayList<T> result = new ArrayList<T>(count);
        while(count > 0) {
        	if(bags.isEmpty())	return null;
        	List<T> bag = bags.getLast();
        	if(bag.isEmpty()) {
        		bags.removeLast();
        		continue;
        	}
        	result.add(bag.remove(bag.size() - 1)); 
        	count--;
        }        
        return result;
    }


    public Bag() {

    }

    private class It implements Iterator<T> {
        Iterator<List<T>> oIter;
        Iterator<T> cIter;

        public It() {
            oIter = bags.iterator();
            if (oIter.hasNext()) {
                cIter = oIter.next().iterator();
            } else {
                cIter = null;
            }
        }

        @Override
        public boolean hasNext() {
            if (cIter == null) {
                return false;
            }
            while(true) {
                if (cIter.hasNext()) {
                    return true;
                }
                if (oIter.hasNext()) {
                    cIter = oIter.next().iterator();
                } else {
                    cIter = null;
                    return false;
                }
            }
        }

        @Override
        public T next() {
            if (hasNext()) {
                return cIter.next();
            }
            throw new IndexOutOfBoundsException();
        }

    }

    @Override
    public Iterator<T> iterator() {
        return new It();
    }

    @Override
    public void forEach(final Consumer<? super T> action) {
        bags.forEach((Collection<T> bag) -> {
            bag.forEach(action);
        });
    }

    private List<Future<?>> forEachConst(ExecutorService pool, final Consumer<? super T> action) {
        ArrayList<Future<?>> futures = new ArrayList<>();
        for (Collection<T> bag : bags) {
            futures.add(pool.submit(() -> {
                bag.forEach(action);
            }));
        }
        return futures;
    }

    public void forEach(ExecutorService pool, final Consumer<? super T> action) {
        List<Future<?>> futures = forEachConst(pool, action);
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                throw new RuntimeException("[Bag] exception raised by worker threads.");
            }
        }
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Bag]");
        for (Collection<T> bag: bags) {
            sb.append(bag.toString()+ ":");
        }
        sb.append("end of Bag");
        return sb.toString();
    }

    /*
    public static void main(String[] args) {
        long i = 5;
        Chunk<Integer> c = new Chunk<>(new LongRange(10 * i, 11 * i));
        System.out.println("prepare: " + c);
        IntStream.range(0, (int) i).forEach(j -> {
            int v = (int) (10 * i + j);
            System.out.println("set@" + v);
            c.set(10 * i + j, v);
        });
        System.out.println("Chunk :" + c);
    }
*/
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        // System.out.println("writeChunk:"+this);
        out.writeInt(size());
        for (Collection<T> bag : bags) {
            for (T item : bag) {
                out.writeObject(item);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int size = in.readInt();
        bags = new ConcurrentLinkedDeque<>();
        ArrayList<T> bag1 = new ArrayList<T>(size);
        for (int i = 0; i < size; i++) {
            bag1.add((T) in.readObject());
        }
        bags.add(bag1);
    }

    public Consumer<T> getReceiver() {
        final ArrayList<T> bag = new ArrayList<>();
        bags.add(bag);
        return new Consumer<T>() {
            @Override
            public void accept(T t) {
                bag.add(t);
            }
        };
    }

    /**
     * Convert the bag into a list and clears the bag.
     * @return the contents of the Bag as a list
     */
    public List<T> convertToList() {
        // TODO: prepare more smart implementation
        ArrayList<T> result = new ArrayList<>(this.size());
        for (List<T> c : bags) {
            result.addAll(c);
        }
        bags.clear();
        return result;
    }

}
