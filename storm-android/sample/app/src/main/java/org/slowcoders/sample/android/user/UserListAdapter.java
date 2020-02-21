package org.slowcoders.sample.android.user;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.slowcoders.sample.SampleEnv;
import org.slowcoders.sample.android.BaseAdapter;
import org.slowcoders.sample.android.post.PostListActivity;
import org.slowcoders.sample.android.sample.R;
import org.slowcoders.sample.orm.gen.IxUser;
import org.slowcoders.storm.ObservableCachedEntities;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.hdodenhof.circleimageview.CircleImageView;

public class UserListAdapter extends BaseAdapter<IxUser.Snapshot, UserListAdapter.AccountViewHolder> {

    private UserListActivity context;

    UserListAdapter(UserListActivity context, ObservableCachedEntities.SnapshotList<IxUser.Snapshot> entities) {
        super(entities);
        this.context = context;
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.user_list_layout, parent, false);
        return new AccountViewHolder(view);
    }

    @Override
    protected void onBindView(AccountViewHolder holder, IxUser.Snapshot user) {
        Glide.with(context)
                .load(user.getPhoto().getPhoto())
                .into(holder.profilePic);

        holder.name.setText(user.getName());
        holder.accountLayout.setOnClickListener(v -> {
            SampleEnv.user = user.getEntityReference();

            Intent intent = new Intent(context, PostListActivity.class);
            context.startActivity(intent);
        });
    }

    static class AccountViewHolder extends RecyclerView.ViewHolder {

        LinearLayout accountLayout;
        CircleImageView profilePic;
        TextView name;

        AccountViewHolder(@NonNull View view) {
            super(view);

            accountLayout = view.findViewById(R.id.account_layout);
            profilePic = view.findViewById(R.id.profile_image);
            name = view.findViewById(R.id.name);
        }
    }
}
