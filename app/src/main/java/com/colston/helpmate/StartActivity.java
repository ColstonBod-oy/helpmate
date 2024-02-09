package com.colston.helpmate;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class StartActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        Button btn_continue = findViewById(R.id.btn_continue);
        final EditText et_nodeName = findViewById(R.id.editText);
        Random random = new Random();
        long node = random.nextInt(50) + 1;
        et_nodeName.setText("" + node);
        btn_continue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String nodName = et_nodeName.getText().toString();
                if (TextUtils.isEmpty(nodName) || TextUtils.isEmpty(nodName.trim())) {
                    et_nodeName.setError(getString(R.string.err_invalidNodeName));
                    return;
                }
                nodName = nodName.trim();
                PeerDetails.getInstance().setPeerAddress(nodName);
                startActivity(new Intent(StartActivity.this, MainActivity.class));
            }
        });
    }
}
