package com.rockhopper.resumequeue.demo;

import java.util.HashSet;

import android.support.v7.app.AppCompatActivity;
import com.rockhopper.resumequeue.ResumeStateListener;
import com.rockhopper.resumequeue.ResumeStateProvider;

/**
 * Created by flisar on 28.04.2016.
 */
public class PauseAwareActivity extends AppCompatActivity implements ResumeStateProvider {
	private boolean mPaused = true;
	private HashSet<ResumeStateListener> mListeners = new HashSet<>();

	@Override
	protected void onResume() {
		super.onResume();
		mPaused = false;
		for (ResumeStateListener mListener : mListeners) {
			mListener.onResumedChanged(true);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mPaused = true;
		for (ResumeStateListener mListener : mListeners) {
			mListener.onResumedChanged(false);
		}
	}

	// --------------
	// Interface RxResumeQueueBus
	// --------------

	@Override
	public boolean isResumeState() {
		return !mPaused;
	}

	@Override
	public void addResumeStateListener(ResumeStateListener listener, boolean callListener) {
		mListeners.add(listener);
		if (callListener) {
			listener.onResumedChanged(isResumeState());
		}
	}

	@Override
	public void removeResumeStateListener(ResumeStateListener listener) {
		mListeners.remove(listener);
	}
}
