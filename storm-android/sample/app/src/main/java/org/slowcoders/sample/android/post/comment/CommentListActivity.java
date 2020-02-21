package org.slowcoders.sample.android.post.comment;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import org.slowcoders.sample.SampleEnv;
import org.slowcoders.sample.android.sample.R;
import org.slowcoders.sample.orm.def.Comment_ORM;
import org.slowcoders.sample.orm.gen.IxComment;
import org.slowcoders.sample.orm.gen.IxPost;
import org.slowcoders.sample.orm.gen.storm._TableBase;
import org.slowcoders.storm.EditableEntities;
import org.slowcoders.storm.ObservableCachedEntities;
import org.slowcoders.storm.StormFilter;
import org.slowcoders.util.Debug;

import java.sql.SQLException;

import static org.slowcoders.sample.orm.gen.storm._TableBase.tComment;

public class CommentListActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "post_id";

    private IxPost post;
    private CommentListAdapter commentAdapter;
    private RecyclerView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment_list);

        listView = findViewById(R.id.rv_comments);
        listView.setLayoutManager(new LinearLayoutManager(this));

        EditText commentText = findViewById(R.id.comment_text);
        TextView tvAddComment = findViewById(R.id.add_comment);

        tvAddComment.setOnClickListener(v -> {
            String text = commentText.getText().toString();
            addComment(text);
            commentText.setText("");
        });

        Intent intent = getIntent();
        long postId = intent.getLongExtra(EXTRA_POST_ID, -1);
        Debug.Assert(postId > 0);
        this.setCommentsWithPostId(postId);
    }

    private void setCommentsWithPostId(long postId) {
        post = _TableBase.tPost.findEntityReference(postId);
        StormFilter filter = tComment.findByPost(post).orderBy(
                Comment_ORM.CreatedTime.createSortableColumn(tComment, true)
        );

        ObservableCachedEntities.SnapshotList<IxComment.Snapshot> comments = new ObservableCachedEntities.SnapshotList<>();
        comments.bindFilter(filter);

        commentAdapter = new CommentListAdapter(this, comments);
        listView.setAdapter(commentAdapter);
    }

    private void addComment(String text) {
        try {
            IxPost.Editor postEditor = post.loadSnapshot().editEntity();
            EditableEntities<IxComment.UpdateForm, IxComment.Editor> commentEditor = postEditor.editComments();

            IxComment.Editor newComment = tComment.newEntity();
            newComment.setUser(SampleEnv.user);
            newComment.setPost(postEditor);
            newComment.setText(text);

            commentEditor.add(newComment);
            postEditor.save();
        } catch (SQLException e) {
            throw Debug.wtf(e);
        }
    }

    @Override
    protected void onDestroy() {
        commentAdapter.onDestroy();
        super.onDestroy();
    }
}
