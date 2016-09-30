package com.rockhopper.resumequeue.demo;

import java.util.HashSet;
import java.util.Iterator;

import android.support.v7.app.AppCompatActivity;
import com.rockhopper.resumequeue.ResumeStateObservable;
import com.rockhopper.resumequeue.ResumeStateProvider;

/**
 * Created by flisar on 28.04.2016.
 */
public class PauseAwareActivity extends AppCompatActivity implements ResumeStateProvider {
	private boolean mPaused = true;
	private HashSet<ResumeStateObservable.ResumeStateListener> mListeners = new HashSet<>();

	@Override
	protected void onResume() {
		super.onResume();
		mPaused = false;
		for (ResumeStateObservable.ResumeStateListener mListener : mListeners) {
			mListener.onResumedChanged(true);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mPaused = true;
		for (ResumeStateObservable.ResumeStateListener mListener : mListeners) {
			mListener.onResumedChanged(false);
		}
	}

	// --------------
	// Interface ResumeQueue
	// --------------

	@Override
	public boolean isResumeState() {
		return !mPaused;
	}

	@Override
	public void addResumeStateListener(ResumeStateObservable.ResumeStateListener listener, boolean callListener) {
		mListeners.add(listener);
		if (callListener) {
			listener.onResumedChanged(isResumeState());
		}
	}

	@Override
	public void removeResumeStateListener(ResumeStateObservable.ResumeStateListener listener) {
		mListeners.remove(listener);
	}
}
