package org.slowcoders.sample.android.post;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import org.slowcoders.sample.SampleEnv;
import org.slowcoders.sample.android.sample.R;
import org.slowcoders.sample.orm.gen.IxPost;
import org.slowcoders.util.Debug;

import java.sql.SQLException;

import androidx.appcompat.app.AppCompatActivity;

import static org.slowcoders.sample.orm.gen.storm._TableBase.tPost;

public class PostEditorActivity extends AppCompatActivity {

    public static final String EXTRA_ENTITY_ID = "entity_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_editor);

        Intent intent = getIntent();
        long id = intent.getLongExtra(EXTRA_ENTITY_ID, -1);

        EditText etxBody = findViewById(R.id.post_body);
        EditText etxSubject = findViewById(R.id.post_subject);

        final IxPost.Editor editor;

        if (id > 0) {
            IxPost postRef = tPost.findEntityReference(id);
            editor = postRef.loadSnapshot().editEntity();
            etxBody.setText(editor.getBody().getBody());
            etxSubject.setText(editor.getSubject());
        } else {
            editor = tPost.newEntity();
        }

        View btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener((v) -> {
            String subject = etxSubject.getText().toString();
            String body = etxBody.getText().toString();
            savePost(editor, subject, body);
            finish();
        });
    }

    private void savePost(IxPost.Editor editor, String subject, String body) {
        try {
            editor.editBody().setBody(body);
            editor.setSubject(subject);
            editor.setUser(SampleEnv.user);
            editor.save();
        } catch (SQLException e) {
            throw Debug.wtf(e);
        }
    }
}
