package com.rockhopper.resumequeue.demo;

import java.util.concurrent.TimeUnit;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import com.rockhopper.resumequeue.ResumeStateProvider;
import com.rockhopper.resumequeue.RxBus;
import com.rockhopper.resumequeue.RxResumeQueue;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by flisar on 28.04.2016.
 */
public class DemoActivity extends AppCompatActivity {
	private static final String TAG = DemoActivity.class.getSimpleName();

	// for demo purposes we use a static list and only add items to it when activity is created
	private static CompositeSubscription subscriptions = new CompositeSubscription();
	private static ResumeStateProvider resumeStateProvider = new ResumeStateProvider();

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// -----------------
		// Queued bus
		// -----------------

		// Explanation this will retrieve all String events, if they are not exclusively bound to a key as well
		Subscription queuedSubscription = RxBus.Builder.create(String.class)
			.setResumeStateProvider(resumeStateProvider)
			.withOnNext(new Action1<String>() {
				@Override
				public void call(String s) {
					// activity IS resumed, you can safely update your UI for example
					Log.d(TAG, "QUEUED BUS: " + s + " | " + getIsResumedMessage());
				}
			})
			.buildSubscription();
		subscriptions.add(queuedSubscription);

		Observable.interval(5, TimeUnit.SECONDS)
			.doOnNext(new Action1<Long>() {
				@Override
				public void call(Long aLong) {
					Log.d("PHW", "INTERVAL - " + aLong);
				}
			})
			.lift(RxResumeQueue.<Long>create(resumeStateProvider))
			.subscribe(new Action1<Long>() {
				@Override
				public void call(Long aLong) {
					Log.d("PHW", "QUEUED INTERVAL - " + aLong);
				}
			});

		startActivity(new Intent(this, NewActivity.class));
	}

	@Override
	protected void onResume() {
		super.onResume();
		resumeStateProvider.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		resumeStateProvider.onPause();
	}

	@Override
	public void onDestroy() {
		// unsubscribe
		subscriptions.clear();
		super.onDestroy();
	}

	// -----------------------------
	// Logging
	// -----------------------------

	private String getIsResumedMessage() {
		return "isResumeState=" + resumeStateProvider.isResumeState();
	}
}