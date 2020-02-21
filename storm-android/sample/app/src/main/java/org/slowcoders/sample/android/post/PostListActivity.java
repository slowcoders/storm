package org.slowcoders.sample.android.post;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.slowcoders.sample.android.sample.R;
import org.slowcoders.sample.orm.def.Post_ORM;
import org.slowcoders.sample.orm.gen.IxPost;
import org.slowcoders.storm.ObservableCachedEntities;
import org.slowcoders.storm.SortableColumn;
import org.slowcoders.storm.StormFilter;
import org.slowcoders.util.Debug;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static org.slowcoders.sample.orm.gen.storm._TableBase.tPost;

public class PostListActivity extends AppCompatActivity {

    private PostListAdapter postAdapter;
    private ObservableCachedEntities.SnapshotList<IxPost.Snapshot> entities;
    private OrderBy orderBy = OrderBy.CreatedNewToOld;

    public enum OrderBy {
        SubjectAtoZ(R.id.subject_a_to_z),
        SubjectZtoA(R.id.subject_z_to_a),
        CreatedNewToOld(R.id.created_new_to_old),
        CreatedOldToNew(R.id.created_old_to_new);

        private int resourceId;

        OrderBy(int resourceId) {
            this.resourceId = resourceId;
        }

        static OrderBy findByResourceId(int resourceId) {
            OrderBy[] orders = OrderBy.values();
            for (OrderBy order : orders) {
                if (order.resourceId == resourceId) {
                    return order;
                }
            }
            throw Debug.shouldNotBeHere("Order by for resourceId : " + resourceId + " not found");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_list);

        View home = findViewById(R.id.home_layout);
        home.setOnClickListener(v -> {
            finish();
        });

        View addPost = findViewById(R.id.btn_add_post);
        addPost.setOnClickListener((v) -> {
            Intent intent = new Intent(PostListActivity.this, PostEditorActivity.class);
            PostListActivity.this.startActivity(intent);
        });

        View btnOptions = findViewById(R.id.btn_options);
        btnOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.inflate(R.menu.account_select);
            Menu menu = popup.getMenu();

            for (int i = 0, count = menu.size(); i < count; i++) {
                MenuItem item = menu.getItem(i);
                item.setOnMenuItemClickListener(menuItem -> {
                    OrderBy orderBy = OrderBy.findByResourceId(item.getItemId());
                    setOrderBy(orderBy);
                    return true;
                });
            }
            popup.show();
        });
        entities = new ObservableCachedEntities.SnapshotList<>();

        RecyclerView postList = findViewById(R.id.rvPosts);
        postList.setLayoutManager(new LinearLayoutManager(this));
        postAdapter = new PostListAdapter(this, entities);
        postList.setAdapter(postAdapter);

        reloadItemList();
    }

    private void setOrderBy(OrderBy orderBy) {
        if (this.orderBy == orderBy) {
            return;
        }
        this.orderBy = orderBy;
        reloadItemList();
    }

    private void reloadItemList() {
        SortableColumn col;
        switch (orderBy) {
            case CreatedNewToOld:
                col = Post_ORM.CreatedTime.createSortableColumn(tPost, false);
                break;
            case CreatedOldToNew:
                col = Post_ORM.CreatedTime.createSortableColumn(tPost, true);
                break;
            case SubjectAtoZ:
                col = Post_ORM.Subject.createSortableColumn(tPost, true);
                break;
            case SubjectZtoA:
                col = Post_ORM.Subject.createSortableColumn(tPost, false);
                break;
            default:
                throw new RuntimeException("should not be here");
        }
        StormFilter filter = tPost.orderBy(col);
        entities.bindFilter(filter);
    }

    @Override
    protected void onDestroy() {
        postAdapter.onDestroy();

        super.onDestroy();
    }
}
