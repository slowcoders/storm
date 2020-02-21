package org.slowcoders.sample.android;

import android.view.ViewGroup;

import org.slowcoders.observable.ChangeType;
import org.slowcoders.storm.ObservableCachedEntities;
import org.slowcoders.storm.EntityReference;
import org.slowcoders.storm.EntitySnapshot;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public abstract class BaseAdapter<SNAPSHOT extends EntitySnapshot, VH extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter implements ObservableCachedEntities.Observer {

    private ObservableCachedEntities.SnapshotList<SNAPSHOT> entities;

    protected BaseAdapter(ObservableCachedEntities.SnapshotList<SNAPSHOT> entities) {
        this.entities = entities;
        this.entities.addAsyncObserver(this);
    }

    @Override
    public void onDataChanged(ChangeType type, int index, int movedIndex, EntityReference ref) {
        if (type == ChangeType.Create) {
            notifyItemInserted(index);
        } else if (type == ChangeType.Update) {
            notifyItemChanged(index);
        } else if (type == ChangeType.Move) {
            notifyItemChanged(index);
            notifyItemMoved(index, movedIndex);
        } else {
            notifyItemRemoved(index);
        }
    }

    @Override
    public void onEntireChanged() {
        notifyDataSetChanged();
    }

    private SNAPSHOT getItem(int pos) {
        return entities.get(pos);
    }

    @NonNull
    @Override
    public abstract RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType);

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        onBindView((VH) holder, getItem(position));
    }

    protected abstract void onBindView(VH holder, SNAPSHOT item);

    @Override
    public int getItemCount() {
        return entities.size();
    }

    public void onDestroy() {
        entities.removeObserver(this);
    }
}
