package com.rockhopper.resumequeue.demo;

import java.util.concurrent.TimeUnit;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.rockhopper.resumequeue.ResumeQueue;
import com.rockhopper.resumequeue.ResumeStateObservable;
import com.rockhopper.resumequeue.RxValve;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by flisar on 28.04.2016.
 */
public class DemoActivity extends PauseAwareActivity {
	private static final String TAG = PauseAwareActivity.class.getSimpleName();

	// for demo purposes we use a static list and only add items to it when activity is created
	private static CompositeSubscription mSubscriptions = new CompositeSubscription();

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// -----------------
		// Queued bus
		// -----------------

		// Explanation this will retrieve all String events, if they are not exclusively bound to a key as well
		Subscription queuedSubscription = ResumeQueue.Builder.create(String.class)
			.setResumeStateProvider(this)
			.withOnNext(new Action1<String>() {
				@Override
				public void call(String s) {
					// activity IS resumed, you can safely update your UI for example
					Log.d(TAG, "QUEUED BUS: " + s + " | " + getIsResumedMessage());
				}
			})
			.buildSubscription();
		mSubscriptions.add(queuedSubscription);


		Observable.interval(5, TimeUnit.SECONDS)
			.doOnNext(new Action1<Long>() {
				@Override
				public void call(Long aLong) {
					Log.d("PHW", "INTERVAL - " + aLong);
				}
			})
			.lift(new RxValve<Long>(ResumeStateObservable.create(this), isResumeState()))
			.subscribe(new Action1<Long>() {
				@Override
				public void call(Long aLong) {
					Log.d("PHW", "QUEUED INTERVAL - " + aLong);
				}
			});

		startActivity(new Intent(this, NewActivity.class));
	}

	@Override
	public void onDestroy() {
		// unsubscribe
		mSubscriptions.clear();
		super.onDestroy();
	}

	// -----------------------------
	// Logging
	// -----------------------------

	private String getIsResumedMessage() {
		return "isResumeState=" + isResumeState();
	}
}