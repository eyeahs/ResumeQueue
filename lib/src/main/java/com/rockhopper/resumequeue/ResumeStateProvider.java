package com.rockhopper.resumequeue;

import java.util.HashSet;
import java.util.Iterator;

/**
 * Created by Prometheus on 22.04.2016.
 */
public class ResumeStateProvider {
	private boolean mPaused = true;
	private HashSet<ResumeStateListener> mListeners = new HashSet<>();

	public void onResume() {
		mPaused = false;
		final Iterator<ResumeStateListener> iterator = mListeners.iterator();
		while (iterator.hasNext()) {
			final ResumeStateListener listener = iterator.next();
			listener.onResumedChanged(iterator, true);
		}
	}

	public void onPause() {
		mPaused = true;
		final Iterator<ResumeStateListener> iterator = mListeners.iterator();
		while (iterator.hasNext()) {
			final ResumeStateListener listener = iterator.next();
			listener.onResumedChanged(iterator, false);
		}
	}

	public boolean isResumeState() {
		return !mPaused;
	}

	void addResumeStateListener(ResumeStateListener listener) {
		mListeners.add(listener);
	}

	interface ResumeStateListener {
		void onResumedChanged(Iterator iterator, boolean resumed);
	}
}
