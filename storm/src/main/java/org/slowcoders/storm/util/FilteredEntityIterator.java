package org.slowcoders.storm.util;

import java.util.Iterator;

import com.google.common.collect.ImmutableList;
import org.slowcoders.storm.EntityFilter;

import org.slowcoders.storm.ORMEntity;

public class FilteredEntityIterator<T extends ORMEntity> implements Iterator<T> {
	private EntityFilter<T> filter;
	private ImmutableList<T> entities;
	private int idx;
	private T nextEntity;
	
	public FilteredEntityIterator(ImmutableList<T> ormEntities, EntityFilter<T> filter) {
		this.entities = ormEntities;
		this.filter = filter;
		prepareNext();
	}

	@Override
	public boolean hasNext() {
		return nextEntity != null;
	}

	@Override
	public T next() {
		T v = nextEntity;
		idx ++;
		prepareNext();
		return v;
	}

	private void prepareNext() {
		try {
			while (idx < entities.size()) {
				nextEntity = entities.get(idx);
				if (filter.matches(nextEntity)) {
					return;
				}
				idx ++;
			}
			nextEntity = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return;
	}

	public void resetToFirst(){
		idx = 0;
		this.prepareNext();
	}
}