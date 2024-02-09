package com.colston.helpmate;

import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * Detects HOLD for {@link KeyEvent}s. Pass the interesting key codes to GestureDetector in its
 * constructor and then forward all key events to {@link GestureDetector#onKeyEvent(KeyEvent)}.
 * Multiple key codes are fine as long as they're mutually exclusive (eg. Volume Up + Volume Down).
 */
public class GestureDetector {
    /**
     * Current or last derived state of the key. For example, if a key is being held down, it's state
     * will be {@link State#HOLD}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({State.UNKNOWN, State.HOLD, State.RELEASE})
    private @interface State {
        int UNKNOWN = 0;
        int HOLD = 1;
        int RELEASE = 2;
    }

    // Static inner class doesn't hold an implicit reference to the outer class
    private static class GestureHandler extends Handler {
        // Using a weak reference means you won't prevent garbage collection
        private final WeakReference<GestureDetector> gestureDetectorWeakReference;

        public GestureHandler(GestureDetector gestureDetector) {
            gestureDetectorWeakReference = new WeakReference<GestureDetector>(gestureDetector);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            GestureDetector gestureDetectorInstance = gestureDetectorWeakReference.get();

            if (gestureDetectorInstance != null) {
                switch (msg.what) {
                    case State.HOLD:
                        gestureDetectorInstance.onHold();
                        break;
                    case State.RELEASE:
                        gestureDetectorInstance.onRelease();
                        break;
                }
            }
        }
    }

    private final GestureHandler mHandler = new GestureHandler(this);
    private boolean mHandledDownAlready;
    private final Set<Integer> mKeyCodes = new HashSet<>();

    /**
     * Detects gestures on {@link KeyEvent}s.
     *
     * @param keyCodes The key codes you're interested in. If more than one is provided, make sure
     *                 they're mutually exclusive (like Volume Up + Volume Down).
     */
    public GestureDetector(int... keyCodes) {
        for (int keyCode : keyCodes) {
            mKeyCodes.add(keyCode);
        }
    }

    /**
     * The key is being held. Override this method to act on the event.
     */
    protected void onHold() {
    }

    /**
     * The key has been released. Override this method to act on the event.
     */
    protected void onRelease() {
    }

    /**
     * Processes a key event. Returns true if it consumes the event.
     */
    public boolean onKeyEvent(KeyEvent event) {
        if (!mKeyCodes.contains(event.getKeyCode())) {
            return false;
        }

        switch (event.getAction()) {
            case KeyEvent.ACTION_DOWN:
                // KeyEvents will call ACTION_DOWN over and over again while held.
                // We only care about the first event, so we can ignore the rest.
                if (mHandledDownAlready) {
                    break;
                }
                mHandledDownAlready = true;
                mHandler.sendEmptyMessage(State.HOLD);
                break;
            case KeyEvent.ACTION_UP:
                mHandledDownAlready = false;
                mHandler.sendEmptyMessage(State.RELEASE);
                break;
        }

        return true;
    }
}
