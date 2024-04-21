package com.colston.helpmate;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.*;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

import java.security.SecureRandom;

public class StartActivity extends AppCompatActivity {
    private static final int REQUEST_CHECK_SETTINGS = 0;
    private static final String ALPHA_NUMERIC_STRING = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        Button btn_continue = findViewById(R.id.btn_continue);
        final EditText et_nodeName = findViewById(R.id.editText);
        et_nodeName.setText(generateNodeName(10));

        // Set up a location request
        LocationRequest locationRequest = new LocationRequest.Builder(LocationRequest.PRIORITY_HIGH_ACCURACY, 10000).build();

        // Get current location settings
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        // Prompt the user to change the location settings
        // and enable GPS system settings to allow device discovery
        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(StartActivity.this,
                                REQUEST_CHECK_SETTINGS);
                    } catch (IntentSender.SendIntentException sendEx) {
                        // Ignore the error.
                    }
                }
            }
        });

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

    public static String generateNodeName(int length) {
        StringBuilder builder = new StringBuilder();
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(index));
        }

        return builder.toString();
    }
}
