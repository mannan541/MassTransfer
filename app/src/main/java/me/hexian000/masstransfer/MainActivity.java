package me.hexian000.masstransfer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class MainActivity extends Activity {
	private static final int REQUEST_SEND = 1;
	private static final int REQUEST_RECEIVE = 2;
	private static final int REQUEST_CHOOSE = 3;
	final Handler handler = new Handler();
	private List<String> items;
	private ArrayAdapter adapter;
	private String host;
	private Timer timer;
	private DiscoverService discoverService;
	private Button receiveButton;
	private final ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			DiscoverService.Binder binder = (DiscoverService.Binder) service;
			discoverService = binder.getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			discoverService = null;
		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		unbindService(serviceConnection);
		Log.d(LOG_TAG, "unbind DiscoverService in MainActivity");
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		final TransferApp app = (TransferApp) getApplicationContext();
		if (app.receiveService != null) {
			receiveButton.setText(R.string.receive_cancel_button);
		} else {
			receiveButton.setText(R.string.receive_button);
		}

		Intent intent1 = new Intent(this, DiscoverService.class);
		bindService(intent1, serviceConnection, Context.BIND_AUTO_CREATE);
		Log.d(LOG_TAG, "bind DiscoverService in MainActivity");

		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (adapter != null) {
					handler.post(() -> {
						items.clear();
						if (discoverService != null && discoverService.discoverer != null) {
							items.addAll(discoverService.discoverer.getPeers());
						}
						adapter.notifyDataSetChanged();
					});
				}
			}
		}, 0, 200);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != RESULT_OK) {
			return;
		}
		switch (requestCode) {
		case REQUEST_SEND: {
			Uri uriTree = data.getData();
			if (uriTree != null) {
				Intent intent = new Intent(this, ChooseActivity.class);
				intent.setData(uriTree);
				startActivityForResult(intent, REQUEST_CHOOSE);
			}
		}
		break;
		case REQUEST_RECEIVE: {
			Uri uriTree = data.getData();
			if (uriTree != null) {
				Intent intent = new Intent(this, ReceiveService.class);
				intent.setData(uriTree);
				startForegroundServiceCompat(intent);
				Toast.makeText(MainActivity.this, R.string.start_receive_service, Toast.LENGTH_SHORT).show();
				receiveButton.setText(R.string.receive_cancel_button);
			}
		}
		break;
		case REQUEST_CHOOSE: {
			Bundle extras = data.getExtras();
			if (extras == null) {
				break;
			}
			String[] files = extras.getStringArray("files");
			if (files == null) {
				break;
			}
			Intent intent = new Intent(this, SendService.class);
			intent.setData(data.getData());
			intent.putExtra("host", host);
			intent.putExtra("files", files);
			startForegroundServiceCompat(intent);
			finish();
		}
		break;
		}
	}

	void updateReceiveButton() {
		if (((TransferApp) getApplicationContext()).receiveService == null) {
			receiveButton.setText(R.string.receive_button);
		} else {
			receiveButton.setText(R.string.receive_cancel_button);
		}
	}

	@Override
	protected void onDestroy() {
		((TransferApp) getApplicationContext()).mainActivity = null;
		super.onDestroy();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		final TransferApp app = (TransferApp) getApplicationContext();
		app.mainActivity = this;

		receiveButton = findViewById(R.id.ReceiveButton);
		receiveButton.setOnClickListener((View v) -> {
			if (app.receiveService != null) {
				Intent intent = new Intent(this, ReceiveService.class);
				intent.setAction("cancel");
				startForegroundServiceCompat(intent);
			} else {
				Toast.makeText(MainActivity.this, R.string.choose_storage_directory, Toast.LENGTH_SHORT).show();
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				startActivityForResult(intent, REQUEST_RECEIVE);
			}
		});

		items = new ArrayList<>();
		ListView peersList = findViewById(R.id.PeerList);
		adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, items);
		peersList.setAdapter(adapter);
		peersList.setOnItemClickListener((adapterView, view, i, l) -> {
			if (((TransferApp) getApplicationContext()).sendService != null) {
				Toast.makeText(MainActivity.this, R.string.transfer_service_is_already_running, Toast.LENGTH_SHORT)
						.show();
				return;
			}

			Toast.makeText(MainActivity.this, R.string.choose_send_directory, Toast.LENGTH_SHORT).show();
			host = (String) adapter.getItem(i);
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
			startActivityForResult(intent, REQUEST_SEND);
		});
	}

	private void startForegroundServiceCompat(Intent intent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(intent);
		} else {
			startService(intent);
		}
	}
}
