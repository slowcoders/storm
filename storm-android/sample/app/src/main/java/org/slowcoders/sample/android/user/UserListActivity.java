package org.slowcoders.sample.android.user;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;

import org.slowcoders.sample.android.sample.R;
import org.slowcoders.sample.orm.gen.IxUser;
import org.slowcoders.storm.ObservableCachedEntities;

import static org.slowcoders.sample.orm.gen.storm._TableBase.tUser;

public class UserListActivity extends AppCompatActivity {

    private UserListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        RecyclerView userList = findViewById(R.id.account_list);
        userList.setLayoutManager(new LinearLayoutManager(this));

        ObservableCachedEntities.SnapshotList<IxUser.Snapshot> entities = new ObservableCachedEntities.SnapshotList<>();
        entities.bindFilter(tUser);
        adapter = new UserListAdapter(this, entities);
        userList.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        adapter.onDestroy();

        super.onDestroy();
    }
}
