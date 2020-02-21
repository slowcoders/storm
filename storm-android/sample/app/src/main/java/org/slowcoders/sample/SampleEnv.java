package org.slowcoders.sample;

import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.slowcoders.sample.android.BaseApplication;
import org.slowcoders.sample.android.sample.R;
import org.slowcoders.sample.orm.def.ORMDatabase;
import org.slowcoders.sample.orm.gen.IxPost;
import org.slowcoders.sample.orm.gen.IxUser;
import org.slowcoders.sample.orm.gen.storm.User_Table;
import org.slowcoders.sample.orm.gen.storm._TableBase;
import org.slowcoders.util.Debug;

import java.io.InputStream;
import java.sql.SQLException;

public class SampleEnv {

    public static IxUser user;

    public static void init() {
        ORMDatabase.initialize();
    }

    static {
        user = createAccount("storm_sample_1@slowcoders.com", "Slow Coder", R.raw.slow_coder);
        createAccount("storm_sample_2@slowcoders.com", "Beyonce", R.raw.beyonce);
        createAccount("storm_sample_3@slowcoders.com", "Steve Jobs", R.raw.steve_jobs);

        createItems();
    }

    private static IxUser createAccount(String emailAddress, String name, int resourceId) {
        try {
            IxUser.Editor editUser = _TableBase.tUser.edit_withEmailAddress(emailAddress);
            if (editUser.getOriginalData() == null) {
                editUser.setName(name);
                editUser.editPhoto().setPhoto(findPhoto(resourceId));
                editUser.save();
            }
            return editUser.getEntityReference();
        } catch (SQLException e) {
            throw Debug.wtf(e);
        }
    }

    private static byte[] findPhoto(int resourceId) {
        try {
            byte[] buffer = new byte[1024 * 100];
            InputStream in = BaseApplication.getContext().getResources().openRawResource(resourceId);
            int b;
            int i = 0;
            while ((b = in.read()) >= 0) {
                buffer[i++] = (byte) b;
            }
            byte[] result = new byte[i];
            System.arraycopy(buffer, 0, result, 0, i);
            return result;
        } catch (Exception e) {
            throw Debug.wtf(e);
        }
    }

    private static void createItems() {
        int cnt = _TableBase.tPost.getEntityCount();
        if (cnt > 0) {
            return;
        }

        try {
            ImmutableList<IxUser> userRefs = _TableBase.tUser.selectEntities();

            int c = 'Z' - 'A';
            for (IxUser userRef : userRefs) {
                for (int i = 0; i < 10; i++) {
                    IxPost.Editor postEditor = _TableBase.tPost.newEntity();
                    postEditor.setUser(userRef);

                    int j = (int) (Math.random() * c);
                    postEditor.setSubject((char)('A' + j) + " - Hi, I'm " + userRef.loadSnapshot().getName());
                    postEditor.editBody().setBody("please like my post");
                    postEditor.setCreatedTime(DateTime.now().plusDays((int)(Math.random() * 100)));
                    postEditor.save();
                }
            }
        } catch (Exception e) {
            throw Debug.wtf(e);
        }
    }
}
