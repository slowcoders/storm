package org.slowcoders.sample.android.post;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.daimajia.swipe.SwipeLayout;
import com.daimajia.swipe.implments.SwipeItemRecyclerMangerImpl;
import com.daimajia.swipe.interfaces.SwipeAdapterInterface;
import com.daimajia.swipe.interfaces.SwipeItemMangerInterface;
import com.daimajia.swipe.util.Attributes;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slowcoders.sample.SampleEnv;
import org.slowcoders.sample.android.BaseAdapter;
import org.slowcoders.sample.android.post.comment.CommentListActivity;
import org.slowcoders.sample.android.sample.R;
import org.slowcoders.sample.orm.gen.IxLike;
import org.slowcoders.sample.orm.gen.IxPost;
import org.slowcoders.sample.orm.gen.IxUser;
import org.slowcoders.storm.EditableEntities;
import org.slowcoders.storm.ObservableCachedEntities;
import org.slowcoders.util.Debug;

import java.sql.SQLException;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.hdodenhof.circleimageview.CircleImageView;

import static org.slowcoders.sample.orm.gen.storm._TableBase.tLike;

public class PostListAdapter extends BaseAdapter<IxPost.Snapshot, PostListAdapter.PostViewHolder> implements SwipeItemMangerInterface, SwipeAdapterInterface  {

    private PostListActivity context;
    private DateTimeFormatter formatter = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss");
    private SwipeItemRecyclerMangerImpl mItemManger = new SwipeItemRecyclerMangerImpl(this);

    PostListAdapter(PostListActivity context, ObservableCachedEntities.SnapshotList<IxPost.Snapshot> entities) {
        super(entities);
        this.context = context;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PostViewHolder(LayoutInflater.from(context)
                .inflate(R.layout.post_list_layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        mItemManger.bindView(holder.itemView, position);
    }

    @Override
    protected void onBindView(PostViewHolder view, IxPost.Snapshot post) {
        IxUser.Snapshot account = post.getUser().loadSnapshot();

        Glide.with(context)
                .load(account.getPhoto().getPhoto())
                .into(view.profilePic);

        view.postLayout.setVisibility(View.VISIBLE);
        view.delete.setOnClickListener(v -> {
            try {
                post.getEntityReference().deleteEntity();
                mItemManger.closeAllItems();
            } catch (SQLException e) {
                throw Debug.wtf(e);
            }
        });

        view.edit.setOnClickListener(v -> {
            Intent intent = new Intent(context, PostEditorActivity.class);
            intent.putExtra(PostEditorActivity.EXTRA_ENTITY_ID, post.getEntityId());
            context.startActivity(intent);
            mItemManger.closeAllItems();
        });


        view.likeLayout.setOnClickListener(v -> {
            likePost(post);
        });

        if (SampleEnv.user != post.getUser()) {
            view.swipeLayout.setSwipeEnabled(false);
        } else {
            view.swipeLayout.setSwipeEnabled(true);
        }

        IxLike.Snapshot likeRef = post.findLike(SampleEnv.user);
        if (likeRef != null) {
            view.likeEnabled.setVisibility(View.VISIBLE);
            view.likeDisabled.setVisibility(View.GONE);
        } else {
            view.likeEnabled.setVisibility(View.GONE);
            view.likeDisabled.setVisibility(View.VISIBLE);
        }

        view.comment.setOnClickListener(v -> {
            Intent intent = new Intent(context, CommentListActivity.class);
            intent.putExtra(CommentListActivity.EXTRA_POST_ID, post.getEntityId());
            context.startActivity(intent);
        });

        view.body.setText(post.getBody().getBody());
        view.subject.setText(post.getSubject());
        view.name.setText(account.getName());
        view.likeCnt.setText(post.getLikes().size() + " likes");
        view.commentCnt.setText(post.getComments().size() + " comments");
        view.createdTime.setText(formatter.print(post.getCreatedTime()));

        view.swipeLayout.setShowMode(SwipeLayout.ShowMode.PullOut);
        view.swipeLayout.addDrag(SwipeLayout.DragEdge.Right, view.swipeLayout.findViewById(R.id.swipe_layout_2));
    }

    private void likePost(IxPost.Snapshot post) {
        try {
            IxLike.Snapshot like = post.findLike(SampleEnv.user);
            IxPost.Editor editPost = post.editEntity();
            EditableEntities<IxLike.UpdateForm, IxLike.Editor> editLikes = editPost.editLikes();
            if (like != null) {
                editLikes.remove(like);
            } else {
                IxLike.Editor editLike = tLike.newEntity();
                editLike.setUser(SampleEnv.user);
                editLike.setPost(post);
                editLikes.add(editLike);
            }
            editPost.save();
        } catch (SQLException e) {
            throw Debug.wtf(e);
        }
    }

    @Override
    public void openItem(int position) {
        mItemManger.openItem(position);
    }

    @Override
    public void closeItem(int position) {
        mItemManger.closeItem(position);
    }

    @Override
    public void closeAllExcept(SwipeLayout layout) {
        mItemManger.closeAllExcept(layout);
    }

    @Override
    public void closeAllItems() {
        mItemManger.closeAllItems();
    }

    @Override
    public List<Integer> getOpenItems() {
        return mItemManger.getOpenItems();
    }

    @Override
    public List<SwipeLayout> getOpenLayouts() {
        return mItemManger.getOpenLayouts();
    }

    @Override
    public void removeShownLayouts(SwipeLayout layout) {
        mItemManger.removeShownLayouts(layout);
    }

    @Override
    public boolean isOpen(int position) {
        return mItemManger.isOpen(position);
    }

    @Override
    public Attributes.Mode getMode() {
        return mItemManger.getMode();
    }

    @Override
    public void setMode(Attributes.Mode mode) {
        mItemManger.setMode(mode);
    }

    @Override
    public int getSwipeLayoutResourceId(int position) {
        return R.id.swipe_layout;
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        SwipeLayout swipeLayout;

        LinearLayout postLayout;
        CircleImageView profilePic;
        TextView name;
        ImageView postImg;

        FrameLayout likeLayout;
        ImageView likeEnabled;
        ImageView likeDisabled;
        TextView likeCnt;
        TextView commentCnt;
        TextView createdTime;
        ImageView comment;

        TextView subject;
        TextView body;

        LinearLayout edit;
        LinearLayout delete;

        PostViewHolder(@NonNull View view) {
            super(view);

            postLayout = view.findViewById(R.id.post_layout);
            swipeLayout = view.findViewById(R.id.swipe_layout);
            profilePic = view.findViewById(R.id.profile_image);
            name = view.findViewById(R.id.text);
            subject = view.findViewById(R.id.post_subject);
            body = view.findViewById(R.id.post_body);
            postImg = view.findViewById(R.id.imgPost);
            likeCnt = view.findViewById(R.id.likeCnt);
            createdTime = view.findViewById(R.id.createdTime);
            delete = view.findViewById(R.id.delete);
            edit = view.findViewById(R.id.edit);
            comment = view.findViewById(R.id.comment);
            commentCnt = view.findViewById(R.id.commentCnt);

            likeLayout = view.findViewById(R.id.like_layout);
            likeDisabled = view.findViewById(R.id.like_disabled);
            likeEnabled = view.findViewById(R.id.like_enabled);
        }
    }

}