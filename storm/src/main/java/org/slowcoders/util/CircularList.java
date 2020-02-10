package org.slowcoders.util;

import java.util.AbstractList;
import java.util.List;

public class CircularList<T> extends AbstractList<T>  {

	private T elementData[];
	private int start;
	private int count;
	
	private static class NullObject {
		public boolean equals(Object o) {
			return o == null;
		}
	}
	
	private static Object gNullCheck = new NullObject(); 

    public CircularList() {
    	this.elementData = (T[])new Object[10];
    }
	
    public CircularList(T[] data, int count) {
    	this.elementData = (T[])new Object[count];
    	this.count = count;
    	System.arraycopy(data, 0, elementData, 0, count);
    }

    public CircularList(List<T> data) {
    	this.count = data.size();
    	this.elementData = (T[])new Object[this.count];
    	for (int i = 0; i < this.count; i ++) {
    		this.elementData[i] = data.get(i);
    	}
    	
    }
    
    public final int size() {
        return count;
    }
    
    private int offsetToIndex(int offset) {
        if (offset < 0 || offset >= this.count)
            throw new IndexOutOfBoundsException("Index: "+offset+", Size: "+count);
    	return (start + offset) % elementData.length;
    }
    
    public synchronized T get(int offset) {
    	int idx = offsetToIndex(offset);
    	return elementData[idx];
    }
    
    public synchronized T set(int offset, T value) {
    	if (offset == this.count) {
    		this.add(value);
    		return null;
    	}
    	int idx = offsetToIndex(offset);
    	T old = elementData[idx];
    	elementData[idx] = value;
    	return old;
    }

	public synchronized int indexOf(Object element) {
    	int size = this.count;
		if (element == null) {
			element = (T)gNullCheck;
		}
		int idx = this.start;
        for (int offset = 0; offset < size; idx ++, offset++) {
        	if (idx == elementData.length) {
        		idx = 0;
        	}
        	if (element.equals(elementData[idx])) {
                return offset;
            }
    	}
        return -1;
	}

	public synchronized int lastIndexOf(Object element) {
		if (element == null) {
			element = (T)gNullCheck;
		}
		int idx = this.start + this.count;
        for (int offset = this.count; --offset >= 0;) {
        	if (--idx < 0) {
        		idx = elementData.length - 1;
        	}
        	if (element.equals(elementData[idx])) {
                return offset;
            }
    	}
        return -1;
	}
	
	@Override
	public boolean isEmpty() {
		return this.count == 0;
	}

	@Override
	public boolean contains(Object o) {
		return this.indexOf(o) >= 0;
	}

	@Override
	public synchronized boolean add(T e) {
    	this.ensureCapacity(this.count + 1);
    	int idx = offsetToIndex(this.count ++);
    	this.elementData[idx] = e;
    	return true;
	}

	private void ensureCapacity(int capacity) {
		int buff_len = this.elementData.length;
		if (capacity > buff_len) {
			int newCapacity = capacity + capacity / 2;
			Object[] newArray = new Object[newCapacity];
			System.arraycopy(this.elementData, this.start, newArray, 0, buff_len - this.start);
			if (buff_len - this.start < this.count) {
				System.arraycopy(this.elementData, 0, newArray, this.count - this.start, this.start);
			}
			this.elementData = (T[])newArray;
			this.start = 0;
		}
	}

	@Override
	public synchronized boolean remove(Object o) {
		int offset = this.indexOf(o);
		if (offset >= 0) {
			this.remove(offset);
			return true;
		}
		return false;
	}


	@Override
	public synchronized void clear() {
		int idx = this.start + this.count;
		for (int offset = this.count; --offset >= 0; ) {
			if (--idx < 0) {
				idx = this.elementData.length - 1;
			}
			this.elementData[idx] = null;
		}
		this.start = this.count = 0;
	}

	@Override
	public synchronized void add(int offset, T element) {
		if (offset == this.count) {
			this.add(element);
			return;
		}
		if (offset == 0) {
			this.insertFirst(element);
			return;
		}
		

		this.ensureCapacity(this.count + 1);
		int idx = offsetToIndex(offset);
		copy_data: {
			if (start > 0) {
				int LL = idx - start;
				int right = this.elementData.length - (this.count + this.start) - 1;
				if (LL >= 0 && (right < 0 || LL < right)) {	
					System.arraycopy(elementData, start, elementData, start - 1, LL+1);
					start--;
					idx --;
					break copy_data;
				}
			}
			System.arraycopy(elementData, idx, elementData, idx + 1, this.count - offset);
		}
		this.elementData[idx] = element;
		count++;
	}

	@Override
	public synchronized T remove(int offset) {
		if (offset == 0) {
			return this.removeFirst();
		}
		else if (offset == this.count - 1) {
			return this.removeLast();
		}
		
		int idx = offsetToIndex(offset);
		T retired = this.elementData[idx];
		int LL = idx - start;
		int end = (this.count + this.start);
		int RR = this.elementData.length - end;
		if (LL >= 0 && (RR < 0 || LL < RR)) {
			System.arraycopy(elementData, start, elementData, start + 1, LL);
			this.elementData[this.start ++] = null;
		}
		else {
			System.arraycopy(elementData, idx + 1, elementData, idx, this.count - offset - 1);
			this.elementData[(end - 1) % elementData.length] = null;
		}
		count--;
		return retired;
	}

	public synchronized void insertFirst(T element) {
		this.ensureCapacity(count + 1);
		this.start = (this.elementData.length + this.start - 1) % this.elementData.length;
		this.elementData[this.start] = element; 
		this.count++;
	}

	public synchronized T removeFirst() {
		if (this.size() == 0) {
			throw new IllegalArgumentException("List is empty");
		}
		T element = this.elementData[this.start];
		this.elementData[this.start] = null;
		this.start = (this.start + 1) % this.elementData.length;
		this.count--;
		return element;
	}

	public synchronized T removeLast() {
		if (this.size() == 0) {
			throw new IllegalArgumentException("List is empty");
		}
		this.count--;
		int idx = (this.start + this.count)  % this.elementData.length;
		T element = this.elementData[idx];
		this.elementData[idx] = null;
		return element;
	}
	
	
	public static void main(String args[]) {
		CircularList<Integer> list = new CircularList<>();
		for (int i = 0; i < 10; i++) {
			list.add(i);
		}
		for (int i = 0; i < 5; i++) {
			Integer v = list.removeFirst();
			list.add(v);
		}
		Integer v;
		v = list.remove(1);
		list.add(1, v);
		
		v = list.remove(8);
		list.add(8, v);
		
		v = list.remove(0);
		list.add(0, v);
		
		v = list.remove(9);
		list.add(9, v);

		list.add(5, -1);
	}
}
