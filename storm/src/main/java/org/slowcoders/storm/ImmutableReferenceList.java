package org.slowcoders.storm;

import com.google.common.collect.ReadOnlyList;

/*internal*/ class ImmutableReferenceList<T extends EntityReference> extends ReadOnlyList<T> {

    private T entities[];
    private int mCount;

	ImmutableReferenceList(T ids[], int count) {
		super();
		this.entities = ids;
		this.mCount = (count);
	}

	public synchronized T get(int idx) {
    	rangeCheck(idx);
        T ref = entities[idx];
        return ref;
    }

    protected final void rangeCheck(int index) {
        if (index >= mCount)
            throw new IndexOutOfBoundsException("Index: "+index+", Size: "+ mCount);
    }

	public T[] toArray(T[] array) {
    	if (array.length < this.mCount) {
    		array = (T[])java.lang.reflect.Array
					.newInstance(array.getClass().getComponentType(), mCount);
		}
    	System.arraycopy(this.entities, 0, array, 0, this.mCount);
    	return array;
	}


	@Override
	public int size() {
		return mCount;
	}
	
}