package com.yuri.kirikiri2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.tvp.kirikiri2.KR2Activity;

import com.yuri.kirikiri2.bridge.Kr2Bridge;

/**
 * 引擎界面（cocos/SDL）。
 *
 * 外壳（LauncherActivity）通过 CLEAR_TOP + SINGLE_TOP 拉起本 Activity：
 *  - 首次进入：引擎初始化时会消费外壳暂存的待启动路径，直接开始游戏；
 *  - 已存在实例：走 onNewIntent，把新路径再交给引擎做热切换。
 *
 * 游戏内菜单由 GameMenuLayer 以 Compose 浮层承载（失败则自动回退到 cocos 菜单）。
 */
public class MainActivity extends KR2Activity {

	private GameMenuLayer gameMenuLayer;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		deliverPendingPath(getIntent());
		setupGameMenu(savedInstanceState);
	}

	private void setupGameMenu(Bundle savedInstanceState) {
		try {
			gameMenuLayer = new GameMenuLayer(this);
			gameMenuLayer.onCreate(savedInstanceState);
		} catch (Throwable t) {
			Log.w("KR2", "game menu layer init failed: " + t.getMessage());
			gameMenuLayer = null;
		}
	}

	@Override
	public void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		deliverPendingPath(intent);
	}

	/** 把外壳传来的启动路径再交给引擎（此时引擎已在运行，会就地切换游戏） */
	private void deliverPendingPath(Intent intent) {
		if (intent == null) return;
		String path = intent.getStringExtra("path");
		if (path == null || path.isEmpty()) return;
		// 游戏库：记录本次启动并开始计时（热切换时自动结算上一段）
		try {
			com.yuri.kirikiri2.data.LibraryStore.INSTANCE.startSession(this, path);
		} catch (Throwable t) {
			Log.w("KR2", "library startSession failed: " + t.getMessage());
		}
		try {
			System.loadLibrary("krkr2yuri");
			Kr2Bridge.INSTANCE.markAvailable();
			Kr2Bridge.INSTANCE.nativeSetDataDir(getFilesDir().getAbsolutePath());
			boolean hot = Kr2Bridge.INSTANCE.nativeLaunch(path);
			Log.d("KR2", "MainActivity deliver path=" + path + " hot=" + hot);
		} catch (Throwable t) {
			Log.w("KR2", "deliver failed: " + t.getMessage());
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (gameMenuLayer != null) gameMenuLayer.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (gameMenuLayer != null) gameMenuLayer.onResume();
		// 游戏库计时继续 + 系统截图联动（三指下滑等截屏自动收入当前游戏的截图库）
		try {
			com.yuri.kirikiri2.data.LibraryStore.INSTANCE.resumeSession(this);
			com.yuri.kirikiri2.data.SystemShotLinker.INSTANCE.start(this, n -> {
				android.widget.Toast.makeText(this,
						"已把 " + n + " 张系统截图收入游戏截图库",
						android.widget.Toast.LENGTH_SHORT).show();
				return kotlin.Unit.INSTANCE;
			});
		} catch (Throwable t) {
			Log.w("KR2", "library resume failed: " + t.getMessage());
		}
	}

	@Override
	protected void onPause() {
		try {
			com.yuri.kirikiri2.data.SystemShotLinker.INSTANCE.stop();
			com.yuri.kirikiri2.data.LibraryStore.INSTANCE.pauseSession(this);
		} catch (Throwable t) {
			Log.w("KR2", "library pause failed: " + t.getMessage());
		}
		if (gameMenuLayer != null) gameMenuLayer.onPause();
		super.onPause();
	}

	@Override
	protected void onStop() {
		if (gameMenuLayer != null) gameMenuLayer.onStop();
		super.onStop();
	}

	@Override
	public void onDestroy() {
		try {
			com.yuri.kirikiri2.data.SystemShotLinker.INSTANCE.stop();
			com.yuri.kirikiri2.data.LibraryStore.INSTANCE.endSession(this);
		} catch (Throwable t) {
			Log.w("KR2", "library endSession failed: " + t.getMessage());
		}
		if (gameMenuLayer != null) gameMenuLayer.onDestroy();
		super.onDestroy();
	}

	@Override
	public int get_res_sd_operate_step() { return R.drawable.sd_operate_step; }
}
