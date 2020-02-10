package org.slowcoders.storm;

import org.slowcoders.io.serialize.*;
import org.slowcoders.storm.orm.ORMProxy;
import org.slowcoders.util.Debug;

import java.sql.SQLException;
import java.util.*;

public class EditableEntities<F extends ORMEntity.UpdateForm, D extends EntityEditor> extends AbstractList<F> {

	private F[] elementData;
	private int size;
	private int cntDeleted;

	private static final ORMEntity.UpdateForm[] empty = new ORMEntity.UpdateForm[0];

	public EditableEntities(Collection<? extends ORMEntity> entities) {
		if (entities != null) {
			this.size = entities.size();
			this.elementData = (F[]) entities.toArray(new ORMEntity.UpdateForm[this.size]);
		}
		else {
			this.elementData = (F[]) empty;
		}
	}

	public EditableEntities(StormRowSet rowSet) {
		this(rowSet.loadEntities());
	}

	public final int size() {
		return size;
	}

	protected final void rangeCheck(int index) {
		if (index >= size)
			throw new IndexOutOfBoundsException("Index: "+index+", Size: "+ size);
	}

	protected synchronized long getEntityId(int index) {
		this.rangeCheck(index);
		EntityReference ref = elementData[index].getEntityReference();
		return ref == null ? - 1 : ref.getEntityId();
	}

	public F get(int idx) {
		this.rangeCheck(idx);
		return elementData[idx];
	}

	public synchronized D edit(int idx) {
		this.rangeCheck(idx);
		try {
			EntityEditor dm = EntityEditor.asEditor(elementData[idx]);
			if (dm == null) {
                EntityReference ref = elementData[idx].getEntityReference();
                dm = ref.getTable().edit(ref.loadSnapshot());
			}
			elementData[idx] = (F) dm;
			return (D)dm;
		}
		catch (InvalidEntityReferenceException e) {
			throw new IllegalStateException(e.getMessage());
		}
		catch (Exception e) {
			throw Debug.wtf(e);
		}
	}


//	@msg.Daehoon.To_Daehoon("아래 함수 구현")
//	public synchronized D editNew() {
//		int count = this.size;
//		ensureCapacity(count+cntDeleted+1);
//		D newEntity = null;// getRowSet().
//		elementData[count] = newEntity;
//		this.size = (count+1);
//		return newEntity;
//	}

	public boolean isEditable() {
		return true;
	}

	public synchronized int indexOf(F entity) {
		return indexOfEntity(entity, this.size);
	}

	final int indexOfEntity(F entity, int cntElement) {
		EntityReference ref = entity.getEntityReference();
		if (ref != null) {
			long id = ref.getEntityId();
			for (int index = 0; index < cntElement; index++) {
				F e = elementData[index];
				if (e == entity || e.getEntityReference() == ref) {
					return index;
				}
			}
		}

		for (int index = 0; index < cntElement; index++) {
			if (elementData[index] == entity) {
				return index;
			}
		}
		return -1;
	}


	public synchronized boolean add(F newEntity) {
		int index = this.indexOfEntity(newEntity, this.size + cntDeleted);
		if (index >= 0) {
			if (index >= this.size) {
				if (index != this.size) {
					F e = elementData[index];
					elementData[index] = elementData[this.size];
					index = this.size;
				}
				this.cntDeleted --;
				this.size = (index + 1);
			}
			elementData[index] = newEntity;
			return true;
		}
		int count = this.size;
		ensureCapacity(count+cntDeleted+1);
		if (cntDeleted > 0) {
			elementData[count+cntDeleted] = elementData[count];
		}
		elementData[count] = newEntity;
		this.size = (count+1);
		return true;
	}


	public synchronized boolean remove(F entity) {
		int index = indexOf(entity);
		if (index < 0) {
			return false;
		}
		removeFast(index);
		return true;
	}

	public synchronized F remove(int index) {
		this.rangeCheck(index);
		return removeFast(index);
	}

	public synchronized void removeAll() {
		this.cntDeleted += this.size;
		this.size = (0);
	}

	private F removeFast(int index) {
		F deleted = elementData[index];
		int count = this.size - 1;
		this.size = (count);
		int numMoved = count - index;
		if (numMoved > 0) {
			System.arraycopy(elementData, index+1, elementData, index,
					numMoved);
			elementData[count] = deleted;
		}
		cntDeleted ++;
		return deleted;
	}

	private void ensureCapacity(int minCapacity) {
		if (minCapacity - elementData.length > 0) {
			if (minCapacity < 16) {
				minCapacity = 16;
			}
			int oldCapacity = elementData.length;
			int newCapacity = minCapacity + (oldCapacity / 2);
			elementData = Arrays.copyOf(elementData, newCapacity);
		}
	}

	public void replaceAll(Collection<? extends ORMProxy> entities) {
		this.removeAll();
		if (entities == null) {
			return;
		}
		this.addAll((Collection<? extends F>) entities);
	}

	public void save(StormRowSet rowSet) throws SQLException, RuntimeException {
		rowSet.getTable().getDatabase().executeInLocalTransaction(new SaveOperation(rowSet), this);
	}

	final void onSave_inTR(long trNo, StormRowSet rowSet) throws SQLException, RuntimeException {
		StormFilter selection = StormFilter.asFilter(rowSet);
		for (int i = 0, count = this.size; i < count; i ++) {
			EntityEditor editor = EntityEditor.asEditor(elementData[i]);
			if (editor != null) {
				if (selection != null) {
					selection.validate(editor);
				}
				editor.doSave_inTR(trNo);
			}
		}
		for (int i = 0, offset = this.size; i < cntDeleted; i ++) {
			F deleted = elementData[offset + i];
			EntityReference ref = deleted.getEntityReference();
			if (ref != null) {
				ref.deleteEntity();
			}
		}
		this.cntDeleted = 0;
	}

	private static class SaveOperation extends TransactionalOperation<EditableEntities> {

		private StormRowSet rowSet;

		private SaveOperation(StormRowSet rowSet) {
			this.rowSet = rowSet;
		}

		@Override
		protected <T> T execute_inTR(EditableEntities entities, long transactionId) throws SQLException {
			entities.onSave_inTR(transactionId, rowSet);
			return null;
		}
	}

	private static IOAdapter<EditableEntities, String> editableEntitiesAdapter =
			IOAdapterLoader.registerDefaultAdapter(EditableEntities.class, new IOAdapter<EditableEntities, String>() {
		@Override
		public EditableEntities read(DataReader rd) throws Exception {
			EntityEditor.EditorReader reader = (EntityEditor.EditorReader) rd;

			Debug.Assert(!reader.isClosed());

			String key = reader.readKey();
			Debug.Assert(key.equals("info"));
			String info = reader.readString();

			String[] strs = info.split("/");
			int size = Integer.parseInt(strs[0]);
			int cntDeleted = Integer.parseInt(strs[1]);
			int length = Integer.parseInt(strs[2]);

			Debug.Assert(!reader.isClosed());

			key = reader.readKey();
			Debug.Assert(key.equals("elementData"));

			ArrayList<ORMEntity.UpdateForm> list = new ArrayList<>();
			reader.readFormArray(list);

			for (int i = size + cntDeleted; i < length; i++) {
				list.add(null);
			}
			EditableEntities entities = new EditableEntities(list);
			entities.size = size;
			entities.cntDeleted = cntDeleted;
			return entities;
		}

		@Override
		public void write(EditableEntities v, DataWriter wr) throws Exception {
			EntityEditor.EditorWriter writer = (EntityEditor.EditorWriter) wr;
			if (v == null) {
				writer.writeNull();
				return;
			}

			writer.writeString("info");
			StringBuilder sb = new StringBuilder();
			sb.append(v.size).append("/");
			sb.append(v.cntDeleted).append("/");
			sb.append(v.elementData.length);

			writer.writeString(sb.toString());
			writer.writeString("elementData");

			sb.setLength(0);

			writer.writeFormArray(v.elementData);
		}

		@Override
		public EncodingType getPreferredTransferType() {
			return null;
		}

		@Override
		public EditableEntities decode(String encoded, boolean isImmutable) throws Exception {
			return null;
		}
	});

}