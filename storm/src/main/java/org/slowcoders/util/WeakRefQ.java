package org.slowcoders.util;

public class WeakRefQ<T> extends ListQ<T> {
	Chain<T> _top;
	Chain<T> _bottom;

	public WeakRefQ() {
		this(Type.Weak);
	}
	
	public WeakRefQ(Type type) {
		super(type, false);
	}

	public WeakRefQ(Type type, boolean autoClearReference) {
		super(type, false);
	}
	
	public static <T> ListQ<T> createFIFO(Type type) {
		return new FIFO<>(type);
	}
	
	public synchronized boolean add(T item) {
		this.pushTop(item);
		return true;
	}

	public final synchronized void pushTop(T item) {
		Chain<T> rookie = super.createChain_internal(item);
		rookie.setNextSibling(this._top);
		this._top = rookie;
		if (_bottom == null) {
			_bottom = rookie;
		}
	}
	
	public synchronized T pop() {
		Chain<T> retiree = this._top;
		if (retiree == null) {
			return null;
		}
		
		for (; retiree != null; retiree = retiree.getNext()) {
			T item = retiree.get();
			if (item != null) {
				this._top = retiree.getNext();
				return item;
			}
		}
		this._top = this._bottom = null;
		return null;
	}
		
	public final LinkedEntry<T> clear() {
		LinkedEntry<T> top = this._top;
		_top = _bottom = null; 
		return top;
	}
	
	/*********************************************************************
	 * First-In First-Out Queue
	 */
	
	public static class FIFO<T> extends WeakRefQ<T> {

		protected FIFO(Type type) {
			super(type);
		}
		
		public synchronized boolean add(T item) {
			Chain<T> rookie = createChain_internal(item);
			Chain<T> last = this._bottom;
			if (last == null) {
				_top = rookie; 
			}
			else {
				_bottom.setNextSibling(rookie);
			}
			_bottom = rookie;
			return true;
		}
		
	}

	@Override
	protected final Chain<T> getTop_internal() {
		return _top;
	}

	@Override
	protected final synchronized Chain<T> removeEntryAndGetNext(Chain<T> prev, Chain<T> entry) {
		Chain<T> next = entry.getNext();
		if (prev != null) {
			prev.setNextSibling(next);
		}
		else {
			this._top = next;
		}
		return next;
	}
}
