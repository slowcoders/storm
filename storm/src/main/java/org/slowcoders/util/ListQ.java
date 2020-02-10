package org.slowcoders.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.NoSuchElementException;


public abstract class ListQ<T> implements Iterable<T> {

	public enum Type {
		Strong,
		Soft,
		Weak,
	}
	
	public interface Visitor<T> {
		boolean visit(T obj);
	}

	protected interface Chain<T> extends LinkedEntry<T> {
		
		boolean isRemoved();

		boolean markRemoved();
		
		Chain<T> getNext();
		
		void setNextSibling(Chain<T> next);

		default Chain<T> getPrev() {
			throw Debug.shouldNotBeHere();
		}
		
		default void setPrev(Chain<T> prev) {
			throw Debug.shouldNotBeHere();
		}

	}
	
	private final Type type;
	protected final boolean autoRefClear;
	private static RefCleaner refCleaner = new RefCleaner();

	public ListQ(Type type, boolean autoRefClear) {
		this.type = type;
		this.autoRefClear = autoRefClear;
	}

	public abstract boolean add(T item);
	
	public abstract T pop();
	
	public abstract void pushTop(T item);
	
	public abstract LinkedEntry<T> clear();
	
	public void forEach(Visitor<T> visitor) {
		Chain<T> prev = null, next;
		for (Chain<T> entry = getTop_internal(); entry != null; entry = next) {
			T item = entry.get();
			if (item == null || !visitor.visit(item)) {
				next = this.removeEntryAndGetNext(prev, entry);
			}
			else {
				prev = entry;
				next = entry.getNext();
			}
		}
	}

	
	public boolean remove(T retiree) {
		return findEntry(retiree, true);
	}
	
	public boolean contains(T target) {
		return findEntry(target, false);
	}
	
	protected abstract Chain<T> getTop_internal();
	
	protected void notifyNotEmpty() {}

	private boolean findEntry(T retiree, boolean doRemove) {
		Chain<T> prev = null, next;
		for (Chain<T> entry = getTop_internal(); entry != null; entry = next) {
			T item = entry.get();
			if (item == null) {
				next = this.removeEntryAndGetNext(prev, entry);
			}
			else if (item == retiree) {
				if (doRemove) {
					this.removeEntryAndGetNext(prev, entry);
				}
				return true;
			}
			else {
				prev = entry;
				next = entry.getNext();
			}
		}
		return false;
	}
	
	protected abstract Chain<T> removeEntryAndGetNext(Chain<T> prev, Chain<T> entry);
	
	
	protected Chain<T> createChain_internal(T value) {
		Chain<T> entry;
		if (this.autoRefClear) {
			switch (type) {
			case Weak:
				entry = new WeakChain.DoubleLink<T>(this, value);
				break;
			case Soft:
				entry = new SoftChain.DoubleLink<T>(this, value);
				break;
			case Strong:
			default:
				entry = new StrongChain.DoubleLink<T>(value);
				break;
			}
		}
		else {
			switch (type) {
			case Weak:
				entry = new WeakChain<T>(value);
				break;
			case Soft:
				entry = new SoftChain<T>(value);
				break;
			case Strong:
			default:
				entry = new StrongChain<T>(value);
				break;
			}
		}
		return entry;
	}
	
	@Override
	public Iterator<T> iterator() {
		return new EntryIterator();
	}
	
	protected class EntryIterator implements Iterator<T> {
		private Chain<T> curEntry;
		private T value;
		
		public EntryIterator() {
			setNext(getTop_internal());
		}

		private void setNext(Chain<T> entry) {
			Chain<T> prev = this.curEntry;
			
			while (entry != null) {
				if ((this.value = entry.get()) != null) {
					break;
				}
				entry = removeEntryAndGetNext(prev, entry);
			}
			this.curEntry = entry;
		}

		@Override
		public boolean hasNext() {
			return value != null;
		}

		@Override
		public T next() {
			T e = this.value;
            if (e == null)
                throw new NoSuchElementException();
			setNext(curEntry.getNext());
			return e;
		}
		
		public void remove() {
			Debug.notImplemented();
//            if (this.curEntry == null)
//                throw new IllegalStateException();
//			this.curEntry.clear();
		}

	}
	
	
	private static class RefCleaner extends ReferenceQueue<Object> implements Runnable {

		@Override
		public void run() {
			try {
				RefChain<?> ref = (RefChain<?>)this.remove();
				ref.remove();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}


	private static class StrongChain<T> implements Chain<T> {
		
		private Chain<T> next;
		private T value;
		boolean removed;

		protected StrongChain(T value) {
			this.value = value;
		}

		public T get() {
			return this.value;
		}

		public Chain<T> getNext() {
			return next;
		}

		public void setNextSibling(Chain<T> next) {
			this.next = next;
		}
		
		public boolean markRemoved() {
			if (this.removed) {
				return false;
			}
			this.removed = true;
			return true;
		}

		public boolean isRemoved() {
			return this.removed;
		}
		
		static class DoubleLink<T> extends StrongChain<T> {
			private Chain<T> prev;

			protected DoubleLink(T value) {
				super(value);
			}
			
			public void setNextSibling(Chain<T> next) {
				if (next != null) {
					next.setPrev(this);
				}
				super.setNextSibling(next);
			}
			
			public Chain<T> getPrev() {
				return this.prev;
			}

			public void setPrev(Chain<T> prev) {
				this.prev = prev;
			}
		}
	}

	private interface RefChain<T> extends Chain<T> {
		void remove();
	}
	
	private static class WeakChain<T> extends WeakReference<T> implements Chain<T> {
		Chain<T> next;
		private boolean removed;

		public WeakChain(T subEntity) {
			super(subEntity);
		}

		public WeakChain(T subEntity, ReferenceQueue q) {
			super(subEntity, q);
		}
		
		@Override
		public Chain<T> getNext() {
			return this.next;
		}
		
		public void setNextSibling(Chain<T> next) {
			this.next = next;
		}

		public boolean markRemoved() {
			if (this.removed) {
				return false;
			}
			this.removed = true;
			return true;
		}

		public boolean isRemoved() {
			return this.removed;
		}

		static class DoubleLink<T> extends WeakChain<T> implements RefChain<T> {
			ListQ<T> owner;
			Chain<T> prev;

			protected DoubleLink(ListQ<T> owner, T value) {
				super(value, refCleaner);
				this.owner = owner;
			}
			
			public void setNextSibling(Chain<T> next) {
				if (next != null) {
					next.setPrev(this);
				}
				super.setNextSibling(next);
			}
			
			public Chain<T> getPrev() {
				return this.prev;
			}

			public void setPrev(Chain<T> prev) {
				this.prev = prev;
			}
			
			public void remove() {
				this.owner.removeEntryAndGetNext(this.prev, this);
			}
		}
			
	}
		
	private static class SoftChain<T> extends WeakReference<T> implements Chain<T> {
		Chain<T> next;
		private boolean removed;

		public SoftChain(T subEntity) {
			super(subEntity);
		}

		public SoftChain(T subEntity, ReferenceQueue q) {
			super(subEntity, q);
		}
		
		
		public boolean markRemoved() {
			if (this.removed) {
				return false;
			}
			this.removed = true;
			return true;
		}

		public boolean isRemoved() {
			return this.removed;
		}
		
		@Override
		public Chain<T> getNext() {
			return this.next;
		}
		
		public void setNextSibling(Chain<T> next) {
			this.next = next;
		}

		static class DoubleLink<T> extends WeakChain<T> implements RefChain<T> {
			ListQ<T> owner;
			Chain<T> prev;

			protected DoubleLink(ListQ<T> owner, T value) {
				super(value, refCleaner);
				this.owner = owner;
			}
			
			public void setNextSibling(Chain<T> next) {
				if (next != null) {
					next.setPrev(this);
				}
				super.setNextSibling(next);
			}
			
			public Chain<T> getPrev() {
				return this.prev;
			}

			public void setPrev(Chain<T> prev) {
				this.prev = prev;
			}
			
			public void remove() {
				this.owner.removeEntryAndGetNext(this.prev, this);
			}
		}
	}
		
}
