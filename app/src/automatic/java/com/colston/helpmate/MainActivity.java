package com.colston.helpmate;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Our WalkieTalkie Activity. This Activity has 3 {@link State}s.
 *
 * <p>{@link State#UNKNOWN}: We cannot do anything while we're in this state. The app is likely in
 * the background.
 *
 * <p>{@link State#SEARCHING}: Our default state (after we've connected). We constantly listen for a
 * device to advertise near us, while simultaneously advertising ourselves.
 *
 * <p>{@link State#CONNECTED}: We've connected to another device and can now talk to them by holding
 * down the volume keys and speaking into the phone. Advertising and discovery have both stopped.
 */
public class MainActivity extends ConnectionsActivity {
    /**
     * If true, debug logs are shown on the device.
     */
    private static final boolean DEBUG = true;

    /**
     * The connection strategy we'll use for Nearby Connections. In this case, we've decided on
     * P2P_CLUSTER, as creating a network mesh is easier with this strategy.
     */
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    /**
     * Length of state change animations.
     */
    private static final long ANIMATION_DURATION = 600;

    /**
     * /**
     * A set of background colors. We'll hash the authentication token we get from connecting to a
     * device to pick a color randomly from this list. Devices with the same background color are
     * talking to each other securely (with 1/COLORS.length chance of collision with another pair of
     * devices).
     */
    @ColorInt
    private static final int[] COLORS =
            new int[]{
                    0xFF4CAF50 /* red */,
                    0xFF4CAF50 /* deep purple */,
                    0xFF4CAF50 /* teal */,
                    0xFF4CAF50 /* green */,
                    0xFF4CAF50 /* amber */,
                    0xFF4CAF50 /* orange */,
                    0xFF4CAF50 /* brown */
            };

    /**
     * This service id lets us find other nearby devices that are interested in the same thing. Our
     * sample does exactly one thing, so we hardcode the ID.
     */
    private static final String SERVICE_ID =
            "com.colston.helpmate.automatic.SERVICE_ID";

    /**
     * The state of the app. As the app changes states, the UI will update and advertising/discovery
     * will start/stop.
     */
    private State mState = State.UNKNOWN;

    /**
     * A random UID used as this device's endpoint name.
     */
    private String mName;

    /**
     * The background color of the 'CONNECTED' state. This is randomly chosen from the {@link #COLORS}
     * list, based off the authentication token.
     */
    @ColorInt
    private int mConnectedColor = COLORS[0];

    /**
     * Displays the previous state during animation transitions.
     */
    private TextView mPreviousStateView;

    /**
     * Displays the current state.
     */
    private TextView mCurrentStateView;

    /**
     * An animator that controls the animation from previous state to current state.
     */
    @Nullable
    private Animator mCurrentAnimator;

    /**
     * A running log of debug messages. Only visible when DEBUG=true.
     */
    private TextView mDebugLogView;

    EditText et_msg, et_dest;
    Button btn_send;

    /**
     * Listens to holding/releasing the volume rocker.
     */
    private final GestureDetector mGestureDetector =
            new GestureDetector(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP) {
                @Override
                protected void onHold() {
                    logV("onHold");
                    startRecording();
                }

                @Override
                protected void onRelease() {
                    logV("onRelease");
                    stopRecording();
                }
            };

    /**
     * For recording audio as the user speaks.
     */
    @Nullable
    private AudioRecorder mRecorder;

    /**
     * For playing audio from other users nearby.
     */
    @Nullable
    private AudioPlayer mAudioPlayer;

    /**
     * The phone's original media volume.
     */
    private int mOriginalVolume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar()
                .setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.actionBar));

        mPreviousStateView = (TextView) findViewById(R.id.previous_state);
        mCurrentStateView = (TextView) findViewById(R.id.current_state);
        btn_send = findViewById(R.id.btn_send);
        et_msg = findViewById(R.id.et_msg);
        et_dest = findViewById(R.id.et_destAdsress);
        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = et_msg.getText().toString();
                if (TextUtils.isEmpty(msg) || TextUtils.isEmpty(msg.trim())) {
                    et_msg.setError(getString(R.string.err_invalidMsg));
                    return;
                }
                String destAddress = et_dest.getText().toString();
                if (TextUtils.isEmpty(destAddress) || TextUtils.isEmpty(destAddress.trim())) {
                    et_dest.setError(getString(R.string.err_invalidDestination));
                    return;
                }
                int destId = -1;
                try {
                    destId = Integer.parseInt(destAddress);
                } catch (Exception e) {
                    destId = -1;
                }
                byte[] dataArray = msg.getBytes();
                int sourceId = PeerDetails.getInstance().peerAddressToInt();

                logV("Send Message to => " + destId + " : Message =>" + msg);
                send(dataArray, destId, sourceId, 4);
                //  et_dest.setText("");
                et_msg.setText("");
            }
        });

        mDebugLogView = (TextView) findViewById(R.id.debug_log);
        mDebugLogView.setVisibility(DEBUG ? View.VISIBLE : View.GONE);
        mDebugLogView.setMovementMethod(new ScrollingMovementMethod());

        mName = generateRandomName();

        ((TextView) findViewById(R.id.name)).setText("Peer Name : " + mName);
    }

    private void send(byte[] dataArray, int destId, int sourceId, int hope) {

        byte[] data = new byte[dataArray.length + 3];
        data[0] = (byte) sourceId;
        data[1] = (byte) destId;
        data[2] = (byte) hope;
        System.arraycopy(dataArray, 0, data, 3, dataArray.length);
        Payload payload = Payload.fromBytes(data);
        send(payload);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mState == State.CONNECTED && mGestureDetector.onKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Set the media volume to max.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mOriginalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);

        setState(State.SEARCHING);
    }

    @Override
    protected void onStop() {
        // Restore the original volume.
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mOriginalVolume, 0);
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);

        // Stop all audio-related threads
        if (isRecording()) {
            stopRecording();
        }
        if (isPlaying()) {
            stopPlaying();
        }

        // After our Activity stops, we disconnect from Nearby Connections.
        setState(State.UNKNOWN);

        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }

        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (getState() == State.CONNECTED) {
            setState(State.SEARCHING);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onEndpointDiscovered(Endpoint endpoint) {
        Set<String> connectedEndPoints = mEstablishedConnections.keySet();
        for (String node : connectedEndPoints) {
            if (endpoint.getName().equals(mEstablishedConnections.get(node).getName())) {
                return;
            }
        }

        if (mEstablishedConnections.get(endpoint.getId()) != null) {
            return;
        }
        // We found an advertiser!
        // if (endpoint.getName().equals("" + (PeerDetails.getInstance().peerAddressToInt() + 1))) {
        // stopDiscovering();
        startAction(Action.CONNECTING, endpoint);
        //  }
    }

    private static String generateRandomName() {
        String name = PeerDetails.getInstance().getPeerAddress();
   /* Random random = new Random();
    for (int i = 0; i < 5; i++) {
      name += random.nextInt(10);
    }*/
        return name;
    }

    @Override
    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {
        // A connection to another device has been initiated! We'll use the auth token, which is the
        // same on both devices, to pick a color to use when we're connected. This way, users can
        // visually see which device they connected with.
        mConnectedColor = COLORS[connectionInfo.getAuthenticationToken().hashCode() % COLORS.length];

        // We accept the connection immediately.
        acceptConnection(endpoint);
    }

    @Override
    protected void onEndpointConnected(Endpoint endpoint) {
        Toast.makeText(
                        this, getString(R.string.toast_connected, endpoint.getName()), Toast.LENGTH_SHORT)
                .show();
        // mDebugLogView.setText("");
        setState(State.CONNECTED);
        updateTextView(mCurrentStateView, State.CONNECTED);
    }

    @Override
    protected void onEndpointDisconnected(Endpoint endpoint) {
        Toast.makeText(
                        this, getString(R.string.toast_disconnected, endpoint.getName()), Toast.LENGTH_SHORT)
                .show();
        setState(State.DIS_CONNECTED);
        updateTextView(mCurrentStateView, State.DIS_CONNECTED);

    }

    @Override
    protected void onConnectionFailed(Endpoint endpoint) {
        // Let's try someone else.
        if (getState() == State.SEARCHING) {
            //   startDiscovering();
        }
    }

    /**
     * The state has changed. I wonder what we'll be doing now.
     *
     * @param state The new state.
     */
    private void setState(State state) {
        if (mState == state) {
            logW("State set to " + state + " but already in that state");
            return;
        }

        logD("State set to " + state);
        State oldState = mState;
        mState = state;
        onStateChanged(oldState, state);
    }

    /**
     * @return The current state.
     */
    private State getState() {
        return mState;
    }

    /**
     * State has changed.
     *
     * @param oldState The previous state we were in. Clean up anything related to this state.
     * @param newState The new state we're now in. Prepare the UI for this state.
     */
    private void onStateChanged(State oldState, State newState) {
        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }

        // Update Nearby Connections to the new state.
        switch (newState) {
            case SEARCHING:
                disconnectFromAllEndpoints();
                stopDiscovering();
                stopAdvertising();

                startAction(Action.DISCOVERY, null);
                startAction(Action.ADVERTISING, null);
                break;
            case CONNECTED:
                //  stopDiscovering();
                //   stopAdvertising();
                break;
            case DIS_CONNECTED:
                stopDiscovering();
                stopAdvertising();

                startAction(Action.DISCOVERY, null);
                startAction(Action.ADVERTISING, null);
                break;
            case UNKNOWN:
                stopAllEndpoints();
                break;
            default:
                // no-op
                break;
        }

        // Update the UI.
        switch (oldState) {
            case UNKNOWN:
                // Unknown is our initial state. Whatever state we move to,
                // we're transitioning forwards.
                transitionForward(oldState, newState);
                break;
            case SEARCHING:
                switch (newState) {
                    case UNKNOWN:
                        transitionBackward(oldState, newState);
                        break;
                    case CONNECTED:
                        transitionForward(oldState, newState);
                        break;
                    default:
                        // no-op
                        break;
                }
                break;
            case CONNECTED:
                // Connected is our final state. Whatever new state we move to,
                // we're transitioning backwards.
                if (mEstablishedConnections.size() <= 0) {
                    transitionBackward(oldState, newState);
                }
                break;
        }
    }

    private void startAction(final Action action, final Endpoint endpoint) {
        Random random = new Random();
        long delay = random.nextInt(500);
        if (action == Action.CONNECTING) {
            delay = delay + 1000;
        }
        logD("Started Action : " + action + " with delay : " + delay);
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {

                switch (action) {
                    case DISCOVERY:
                        startDiscovering();
                        break;
                    case ADVERTISING:
                        startAdvertising();
                        break;
                    case CONNECTING:
                        connectToEndpoint(endpoint);

                        break;
                }


            }
        };
        timer.schedule(task, delay);
    }

    /**
     * Transitions from the old state to the new state with an animation implying moving forward.
     */
    @UiThread
    private void transitionForward(State oldState, final State newState) {
        mPreviousStateView.setVisibility(View.VISIBLE);
        mCurrentStateView.setVisibility(View.VISIBLE);

        updateTextView(mPreviousStateView, oldState);
        updateTextView(mCurrentStateView, newState);

        if (ViewCompat.isLaidOut(mCurrentStateView)) {
            mCurrentAnimator = createAnimator(false /* reverse */);
            mCurrentAnimator.addListener(
                    new AnimatorListener() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            updateTextView(mCurrentStateView, newState);
                        }
                    });
            mCurrentAnimator.start();
        }
    }

    /**
     * Transitions from the old state to the new state with an animation implying moving backward.
     */
    @UiThread
    private void transitionBackward(State oldState, final State newState) {
        mPreviousStateView.setVisibility(View.VISIBLE);
        mCurrentStateView.setVisibility(View.VISIBLE);

        updateTextView(mCurrentStateView, oldState);
        updateTextView(mPreviousStateView, newState);

        if (ViewCompat.isLaidOut(mCurrentStateView)) {
            mCurrentAnimator = createAnimator(true /* reverse */);
            mCurrentAnimator.addListener(
                    new AnimatorListener() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            updateTextView(mCurrentStateView, newState);
                        }
                    });
            mCurrentAnimator.start();
        }
    }

    @NonNull
    private Animator createAnimator(boolean reverse) {
        Animator animator;
        if (Build.VERSION.SDK_INT >= 21) {
            int cx = mCurrentStateView.getMeasuredWidth() / 2;
            int cy = mCurrentStateView.getMeasuredHeight() / 2;
            int initialRadius = 0;
            int finalRadius = Math.max(mCurrentStateView.getWidth(), mCurrentStateView.getHeight());
            if (reverse) {
                int temp = initialRadius;
                initialRadius = finalRadius;
                finalRadius = temp;
            }
            animator =
                    ViewAnimationUtils.createCircularReveal(
                            mCurrentStateView, cx, cy, initialRadius, finalRadius);
        } else {
            float initialAlpha = 0f;
            float finalAlpha = 1f;
            if (reverse) {
                float temp = initialAlpha;
                initialAlpha = finalAlpha;
                finalAlpha = temp;
            }
            mCurrentStateView.setAlpha(initialAlpha);
            animator = ObjectAnimator.ofFloat(mCurrentStateView, "alpha", finalAlpha);
        }
        animator.addListener(
                new AnimatorListener() {
                    @Override
                    public void onAnimationCancel(Animator animator) {
                        mPreviousStateView.setVisibility(View.GONE);
                        mCurrentStateView.setAlpha(1);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        mPreviousStateView.setVisibility(View.GONE);
                        mCurrentStateView.setAlpha(1);
                    }
                });
        animator.setDuration(ANIMATION_DURATION);
        return animator;
    }

    /**
     * Updates the {@link TextView} with the correct color/text for the given {@link State}.
     */
    @UiThread
    private void updateTextView(TextView textView, State state) {
        switch (state) {
            case SEARCHING:
                textView.setBackgroundResource(R.color.state_searching);
                textView.setText(R.string.status_searching);
                break;
            case CONNECTED:
            case DIS_CONNECTED:

                Set<String> connectedEndPoints = mEstablishedConnections.keySet();

                String connectedNodes = getString(R.string.status_connected);
                for (String node : connectedEndPoints) {
                    connectedNodes = connectedNodes + " | " + mEstablishedConnections.get(node).getName();
                }
                if (connectedEndPoints.size() <= 0) {
                    connectedNodes = "No nodes connected";
                }
                textView.setBackgroundColor(mConnectedColor);
                textView.setText(connectedNodes);
                break;
            default:
                textView.setBackgroundResource(R.color.state_unknown);
                textView.setText(R.string.status_unknown);
                break;
        }
    }

    /**
     * {@see ConnectionsActivity#onReceive(Endpoint, Payload)}
     */
    @Override
    protected void onReceive(Endpoint endpoint, Payload payload) {
        if (payload.getType() == Payload.Type.STREAM) {
            if (mAudioPlayer != null) {
                mAudioPlayer.stop();
                mAudioPlayer = null;
            }

            AudioPlayer player =
                    new AudioPlayer(payload.asStream().asInputStream()) {
                        @WorkerThread
                        @Override
                        protected void onFinish() {
                            runOnUiThread(
                                    new Runnable() {
                                        @UiThread
                                        @Override
                                        public void run() {
                                            mAudioPlayer = null;
                                        }
                                    });
                        }
                    };
            mAudioPlayer = player;
            player.start();
        } else if (payload.getType() == Payload.Type.BYTES) {
            byte[] data = payload.asBytes();
            int mySourceId = PeerDetails.getInstance().peerAddressToInt();
            int sourceId = data[0];
            int destId = data[1];
            int hope = data[2];

            byte[] dataArray = new byte[data.length - 3];

            System.arraycopy(data, 3, dataArray, 0, dataArray.length);
            if (destId == mySourceId) {
                logD("Message received to the correct node with hope = " + hope);
                logI("Message : " + new String(dataArray));
            } else {
                if (sourceId != mySourceId) {
                    logD("Hope received : " + hope);
                    hope--;
                    if (hope > 0) {
                        logD("Retransmitting data : From=> " + sourceId + " : To=>" + destId);
                        send(dataArray, destId, sourceId, hope);
                    } else {
                        logE("Message Discarding since  hope ended : " + hope);

                    }
                } else {
                    logW("My message received by retransmission....");
                }
            }

        }
    }

    /**
     * Stops all currently streaming audio tracks.
     */
    private void stopPlaying() {
        logV("stopPlaying()");
        if (mAudioPlayer != null) {
            mAudioPlayer.stop();
            mAudioPlayer = null;
        }
    }

    /**
     * @return True if currently playing.
     */
    private boolean isPlaying() {
        return mAudioPlayer != null;
    }

    /**
     * Starts recording sound from the microphone and streaming it to all connected devices.
     */
    private void startRecording() {
        logV("startRecording()");
        try {
            ParcelFileDescriptor[] payloadPipe = ParcelFileDescriptor.createPipe();

            // Send the first half of the payload (the read side) to Nearby Connections.
            send(Payload.fromStream(payloadPipe[0]));

            // Use the second half of the payload (the write side) in AudioRecorder.
            mRecorder = new AudioRecorder(payloadPipe[1]);
            mRecorder.start();
        } catch (IOException e) {
            logE("startRecording() failed", e);
        }
    }

    /**
     * Stops streaming sound from the microphone.
     */
    private void stopRecording() {
        logV("stopRecording()");
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder = null;
        }
    }

    /**
     * @return True if currently streaming from the microphone.
     */
    private boolean isRecording() {
        return mRecorder != null && mRecorder.isRecording();
    }

    /**
     * {@see ConnectionsActivity#getRequiredPermissions()}
     */
    @Override
    protected String[] getRequiredPermissions() {
        return join(
                super.getRequiredPermissions(),
                Manifest.permission.RECORD_AUDIO);
    }

    /**
     * Joins 2 arrays together.
     */
    private static String[] join(String[] a, String... b) {
        String[] join = new String[a.length + b.length];
        System.arraycopy(a, 0, join, 0, a.length);
        System.arraycopy(b, 0, join, a.length, b.length);
        return join;
    }

    /**
     * Queries the phone's contacts for their own profile, and returns their name. Used when
     * connecting to another device.
     */
    @Override
    protected String getName() {
        return mName;
    }

    /**
     * {@see ConnectionsActivity#getServiceId()}
     */
    @Override
    public String getServiceId() {
        return SERVICE_ID;
    }

    /**
     * {@see ConnectionsActivity#getStrategy()}
     */
    @Override
    public Strategy getStrategy() {
        return STRATEGY;
    }

    @Override
    protected void logV(String msg) {
        super.logV(msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_verbose)));
    }

    @Override
    protected void logD(String msg) {
        super.logD(msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_debug)));
    }

    @Override
    protected void logI(String msg) {
        super.logI(msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_info)));
    }

    @Override
    protected void logW(String msg) {
        super.logW(msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_warning)));
    }

    @Override
    protected void logW(String msg, Throwable e) {
        super.logW(msg, e);
        appendToLogs(toColor(msg + " Error : " + e.getLocalizedMessage(), getResources().getColor(R.color.log_warning)));
    }

    @Override
    protected void logE(String msg, Throwable e) {
        super.logE(msg, e);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_error)));
    }

    @Override
    protected void logE(String msg) {
        super.logE(msg);
        appendToLogs(toColor(msg, getResources().getColor(R.color.log_error)));
    }


    /*
        @Override
        protected void logI(String msg, Throwable e) {
            super.logI(msg, e);
            appendToLogs(toColor(msg, getResources().getColor(R.color.log_error)));
        }*/
    private void appendToLogs(final CharSequence msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDebugLogView.append("\n");
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss.SSS");
                String formattedTime = sdf.format(new Date(System.currentTimeMillis()));

                mDebugLogView.append(formattedTime + ": ");
                mDebugLogView.append(msg);
            }
        });

    }

    private static CharSequence toColor(String msg, int color) {
        SpannableString spannable = new SpannableString(msg);
        spannable.setSpan(new ForegroundColorSpan(color), 0, msg.length(), 0);
        return spannable;
    }


    /**
     * Provides an implementation of Animator.AnimatorListener so that we only have to override the
     * method(s) we're interested in.
     */
    private abstract static class AnimatorListener implements Animator.AnimatorListener {
        @Override
        public void onAnimationStart(Animator animator) {
        }

        @Override
        public void onAnimationEnd(Animator animator) {
        }

        @Override
        public void onAnimationCancel(Animator animator) {
        }

        @Override
        public void onAnimationRepeat(Animator animator) {
        }
    }

    /**
     * States that the UI goes through.
     */
    public enum State {
        UNKNOWN,
        SEARCHING,
        CONNECTED,
        DIS_CONNECTED
    }

    public enum Action {
        ADVERTISING,
        CONNECTING,
        DISCOVERY
    }
}
