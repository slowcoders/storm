package org.slowcoders.sample.android.post.comment;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.common.collect.ImmutableList;

import org.slowcoders.observable.ChangeType;
import org.slowcoders.sample.SampleEnv;
import org.slowcoders.sample.android.BaseAdapter;
import org.slowcoders.sample.android.sample.R;
import org.slowcoders.sample.orm.def.SubComment_ORM;
import org.slowcoders.sample.orm.gen.IxComment;
import org.slowcoders.sample.orm.gen.IxPost;
import org.slowcoders.sample.orm.gen.IxSubComment;
import org.slowcoders.storm.EditableEntities;
import org.slowcoders.storm.ObservableCachedEntities;
import org.slowcoders.storm.StormFilter;
import org.slowcoders.storm.EntityReference;
import org.slowcoders.util.Debug;

import java.sql.SQLException;
import java.util.ArrayDeque;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.hdodenhof.circleimageview.CircleImageView;

import static org.slowcoders.sample.orm.gen.storm._TableBase.tSubComment;

public class CommentListAdapter extends BaseAdapter<IxComment.Snapshot, CommentListAdapter.CommentViewHolder> {

    private CommentListActivity context;

    private ArrayDeque<View> recyclerQueue = new ArrayDeque<>();

    CommentListAdapter(CommentListActivity context, ObservableCachedEntities.SnapshotList<IxComment.Snapshot> comments) {
        super(comments);
        this.context = context;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.comment_list_layout, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    protected void onBindView(CommentViewHolder holder, IxComment.Snapshot comment) {
        byte[] photo = comment.getUser().loadSnapshot().getPhoto().getPhoto();
        if (photo != null) {
            Glide.with(context)
                    .load(photo)
                    .into(holder.profilePic);
        }
        holder.text.setText(comment.getText());
        holder.addSubComment.setOnClickListener(v -> {
            holder.addSubCommentLayout.setVisibility(View.VISIBLE);
            holder.subComment.requestFocus();
        });
        holder.addSubCommentLayout.setVisibility(View.GONE);
        holder.btnAddSubComment.setOnClickListener(v -> {
            String text = holder.subComment.getText().toString();

            addSubComment(comment, text);

            holder.addSubCommentLayout.setVisibility(View.GONE);
            holder.subComment.setText("");
        });
        if (comment.getUser() == SampleEnv.user) {
            holder.delete.setVisibility(View.VISIBLE);
        } else {
            holder.delete.setVisibility(View.GONE);
        }
        holder.delete.setOnClickListener(v -> {
            deleteComment(comment);
        });

        StormFilter<IxSubComment.Snapshot, IxSubComment, IxSubComment.Editor> rowSet = comment.getEntityReference().getSubComments().orderBy(
                SubComment_ORM.CreatedTime.createSortableColumn(tSubComment, true)
        );
        ImmutableList<IxSubComment.Snapshot> subComments = rowSet.loadEntities();

        if (subComments.size() == 0) {
            holder.subCommentLayout.setVisibility(View.GONE);
            return;
        }

        for (int i = 0, count = holder.subCommentLayout.getChildCount(); i < count; i++) {
            View view = holder.subCommentLayout.getChildAt(i);
            recyclerQueue.push(view);
        }
        holder.subCommentLayout.removeAllViews();

        for (IxSubComment.Snapshot subComment : subComments) {
            View view = recyclerQueue.isEmpty() ? null : recyclerQueue.pop();
            if (view == null) {
                view = LayoutInflater.from(context).inflate(R.layout.sub_comment_list_layout, holder.subCommentLayout, false);
            }
            SubCommentHolder subCommentHolder = new SubCommentHolder(view);
            subCommentHolder.bindData(subComment);
            holder.subCommentLayout.addView(view);
        }
        holder.subCommentLayout.setVisibility(View.VISIBLE);
    }

    private void deleteComment(IxComment.Snapshot comment) {
        try {
            IxPost.Editor editPost = comment.getPost().loadSnapshot().editEntity();
            EditableEntities<IxComment.UpdateForm, IxComment.Editor> editComments = editPost.editComments();
            editComments.remove(comment);
            editPost.save();
        } catch (SQLException e) {
            throw Debug.wtf(e);
        }
    }
    private void addSubComment(IxComment.Snapshot comment, String text) {
        try {
            IxComment.Editor editComment = comment.editEntity();

            IxSubComment.Editor editSubComment = tSubComment.newEntity();
            editSubComment.setComment(editComment);
            editSubComment.setUser(SampleEnv.user);
            editSubComment.setText(text);

            editComment.editSubComments().add(editSubComment);
            editComment.save();
        } catch (SQLException e) {
            throw Debug.wtf(e);
        }
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {

        CircleImageView profilePic;
        TextView text;
        TextView addSubComment;
        RelativeLayout addSubCommentLayout;
        TextView btnAddSubComment;
        EditText subComment;
        LinearLayout subCommentLayout;
        ImageView delete;

        CommentViewHolder(@NonNull View view) {
            super(view);

            profilePic = view.findViewById(R.id.profile_image);
            text = view.findViewById(R.id.text);
            addSubComment = view.findViewById(R.id.add_subComment);
            addSubCommentLayout = view.findViewById(R.id.add_subComment_layout);
            btnAddSubComment = view.findViewById(R.id.btn_add_subComment);
            subComment = view.findViewById(R.id.subComment);
            subCommentLayout = view.findViewById(R.id.subComment_layout);
            delete = view.findViewById(R.id.delete);
        }
    }

    static class SubCommentHolder extends RecyclerView.ViewHolder {

        Context context;

        CircleImageView profilePic;
        TextView text;
        ImageView delete;

        SubCommentHolder(@NonNull View view) {
            super(view);
            context = view.getContext();

            profilePic = view.findViewById(R.id.profile_image);
            text = view.findViewById(R.id.text);
            delete = view.findViewById(R.id.delete);
        }

        void bindData(IxSubComment.Snapshot subComment) {
            byte[] photo = subComment.getUser().loadSnapshot().getPhoto().getPhoto();
            if (photo != null) {
                Glide.with(context)
                        .load(photo)
                        .into(profilePic);
            }
            text.setText(subComment.getText());
            if (subComment.getUser() == SampleEnv.user) {
                delete.setVisibility(View.VISIBLE);
            } else {
                delete.setVisibility(View.GONE);
            }
            delete.setOnClickListener(v -> {
               deleteSubComment(subComment);
            });
        }

        private void deleteSubComment(IxSubComment.Snapshot subComment) {
            try {
                IxComment.Editor editComment = subComment.getComment().loadSnapshot().editEntity();
                EditableEntities<IxSubComment.UpdateForm, IxSubComment.Editor> editSubComments = editComment.editSubComments();
                editSubComments.remove(subComment);
                editComment.save();
            } catch (SQLException e) {
                throw Debug.wtf(e);
            }
        }

    }
}
