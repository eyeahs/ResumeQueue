package com.rockhopper.resumequeue;

/**
 * Created by Prometheus on 22.04.2016.
 */
public interface ResumeStateProvider {
	boolean isResumeState();

	void addResumeStateListener(ResumeStateListener listener, boolean callListener);

	void removeResumeStateListener(ResumeStateListener listener);
}
