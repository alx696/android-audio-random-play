package red.lilu.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.HandlerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;

import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import java9.util.Lists;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import red.lilu.app.databinding.ActivityMainBinding;
import red.lilu.app.tool.Dns;

@ExperimentalCoroutinesApi
public class ActivityMain extends AppCompatActivity {

    private static final String T = "调试-主界面";
    public static final String ACTION_AUDIO_UPDATE = "音频更新";
    private final DateTimeFormatter timeFormatter = DateTimeFormat.forPattern("H:m");
    private final Random random = new Random();
    private ActivityMainBinding b;
    private MyApplication application;
    private Handler handler;
    private ExoPlayer player;
    private Timer timer;
    private String ruleText = "";
    private Intent dataIntent;
    private int screenBright;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        dataIntent = getIntent();

        // 初始
        application = (MyApplication) getApplication();

        // 播放器
        player = new ExoPlayer.Builder(this).build();
        player.setRepeatMode(Player.REPEAT_MODE_ALL); // 全部循环
        player.setShuffleModeEnabled(true); // 随机播放
        player.addListener(
                new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        String name = "";
                        MediaItem currentMediaItem = player.getCurrentMediaItem();
                        if (currentMediaItem != null) {
                            name = currentMediaItem.mediaId;
                        }
                        Log.d(T, String.format("播放状态变化, 是否播放: %s 播放内容: %s", isPlaying, name));
                    }
                }
        );
        updateAudioFile();

        // 通信
        handler = HandlerCompat.createAsync(
                Looper.getMainLooper(),
                msg -> {
                    if (msg.what == 1) {
                        Log.i(T, "收到播放指令");

                        if (ruleText.startsWith("0")) {
                            Log.i(T, "DNS设置关闭, 暂不播放");
                            return true;
                        }

                        fileLog("播放");
                        player.play();

                        return true;
                    } else if (msg.what == 2) {
                        Log.i(T, "收到暂停指令");
                        fileLog("暂停");
                        player.pause();

                        return true;
                    } else if (msg.what == 3) {
                        updateDnsConf();
                        handler.sendEmptyMessageDelayed(3, 120000); // 2分钟后再次执行

                        return true;
                    } else if (msg.what == 4) {
                        reset();

                        return true;
                    } else if (msg.what == 5) {
                        updateScreenProtectTextPlace();
                        handler.sendEmptyMessageDelayed(5, 120000); // 2分钟后再次执行

                        return true;
                    }

                    return false;
                }
        );

        // 功能
        b.buttonAudio.setOnClickListener(v -> {
            startActivity(
                    new Intent(this, ActivityConfAudio.class)
            );
        });
        b.buttonBegin.setOnClickListener(v -> {
            if (timer != null) {
                fileLog("手动停止");
                player.pause();
                timer.cancel();
                timer = null;
                b.textRule.setText("");
                b.buttonBegin.setText("开始");
                b.buttonScreenProtect.setEnabled(false);
                return;
            }

            fileLog("手动开始");
            b.buttonBegin.setText("停止");
            b.buttonScreenProtect.setEnabled(true);
            reset();
        });
        b.buttonScreenProtect.setOnClickListener(v -> {
            startScreenProtect();
        });
        b.layoutScreenProtect.setOnClickListener(v -> {
            stopScreenProtect();
        });

        // 自动更新DNS设置
        handler.sendEmptyMessage(3);
    }

    // onCreate时此方法不会被调用, 后续每次都会被调用
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(T, "onNewIntent");

        dataIntent = intent;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(T, "onResume");

        if (dataIntent != null) {
            Log.d(T, "处理传入数据");

            if (dataIntent.getAction() != null && dataIntent.getAction().equals(ACTION_AUDIO_UPDATE)) {
                updateAudioFile();
            }

            dataIntent = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(T, "onDestroy");

        handler.removeCallbacksAndMessages(null);

        player.stop();
        player.release();
        player = null;
    }

    /**
     * AndroidManifest.xml需要设置 android:configChanges="orientation|screenSize"
     *
     * @param newConfig The new device configuration.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(T, "onConfigurationChanged");

        if (b.layoutScreenProtect.getVisibility() == View.VISIBLE) {
            updateScreenProtectTextPlace();
        }
    }

    private void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private static class OrderingHour extends Ordering<Integer> {
        @Override
        public int compare(Integer left, Integer right) {
            return Ints.compare(left, right);
        }
    }

    private void fileLog(String text) {
        text = String.format("%s %s", LocalDateTime.now(), text);
        File file = new File(getExternalCacheDir(), "log.txt");
        try {
            FileUtils.writeStringToFile(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.w(T, e);
        }
    }

    private void reset() {
        fileLog("重置");

        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();
        StringBuilder stringBuilder = new StringBuilder();

        // 第二天重置任务
        DateTime tomorrowDateTime = LocalTime.parse(
                        "00:00:01", DateTimeFormat.forPattern("HH:mm:ss")
                )
                .toDateTimeToday()
                .plusDays(1);
        Log.d(T, String.format("第二天重置: %s", tomorrowDateTime));
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        handler.sendEmptyMessage(4);
                    }
                },
                tomorrowDateTime.toDate()
        );

        // 按照规则生成随机任务
        try {
            String[] textArray = ruleText.split(",");
            if (textArray.length >= 6) {
                int hourBegin = Integer.parseInt(textArray[1]);
                int hourEnd = Integer.parseInt(textArray[2]);
                int hourCount = hourEnd - hourBegin + 1;
                int minuteMax = Integer.parseInt(textArray[3]);
                int playSecondsMin = Integer.parseInt(textArray[4]);
                int playSecondsMax = Integer.parseInt(textArray[5]);
                ArrayList<Integer> hourList = new ArrayList<>();
                for (int x = 0; x < hourCount; x++) {
                    int i = random.nextInt(hourCount) + hourBegin;
                    while (hourList.contains(i)) {
                        i = random.nextInt(hourCount) + hourBegin;
                    }
                    hourList.add(i);
                }
                Lists.sort(hourList, new OrderingHour());
                for (Integer hour : hourList) {
                    DateTime playDateTime1 = LocalTime.parse(String.format("%s:0", hour), timeFormatter)
                            .plusMinutes(
                                    random.nextInt(minuteMax + 1)
                            )
                            .toDateTimeToday();
                    DateTime playDateTime2 = LocalTime.parse(String.format("%s:30", hour), timeFormatter)
                            .plusMinutes(
                                    random.nextInt(minuteMax + 1)
                            )
                            .toDateTimeToday();
                    DateTime pauseDateTime1 = playDateTime1.plusSeconds(
                            random.nextInt(playSecondsMax - playSecondsMin + 1) + playSecondsMin
                    );
                    DateTime pauseDateTime2 = playDateTime2.plusSeconds(
                            random.nextInt(playSecondsMax - playSecondsMin + 1) + playSecondsMin
                    );

                    // 记录规则
                    stringBuilder
                            .append(
                                    playDateTime1.toString("HH:mm:ss")
                            )
                            .append("播,")
                            .append(
                                    pauseDateTime1.toString("HH:mm:ss")
                            )
                            .append(";")
                            .append(
                                    playDateTime2.toString("HH:mm:ss")
                            )
                            .append("播,")
                            .append(
                                    pauseDateTime2.toString("HH:mm:ss")
                            )
                            .append("停\n");

                    // 安排任务
                    if (pauseDateTime1.isAfter(DateTime.now())) {
                        // 播放
                        timer.schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {
                                        handler.sendEmptyMessage(1);
                                    }
                                },
                                playDateTime1.toDate()
                        );

                        // 暂停
                        timer.schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {
                                        handler.sendEmptyMessage(2);
                                    }
                                },
                                pauseDateTime1.toDate()
                        );
                    }
                    if (pauseDateTime2.isAfter(DateTime.now())) {
                        // 播放
                        timer.schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {
                                        handler.sendEmptyMessage(1);
                                    }
                                },
                                playDateTime2.toDate()
                        );

                        // 暂停
                        timer.schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {
                                        handler.sendEmptyMessage(2);
                                    }
                                },
                                pauseDateTime2.toDate()
                        );
                    }
                }
            } else {
                stringBuilder.append("规则无效:")
                        .append(ruleText);
            }
        } catch (Exception e) {
            Log.w(T, e);
            stringBuilder.append("规则有误:")
                    .append(ruleText);
        }

        b.textRule.setText(stringBuilder);
    }

    /**
     * 更新DNS配置
     */
    private void updateDnsConf() {
        Dns.getInstance().txt(
                application.getExecutorService(),
                "rule.auto-play.app.lilu.red",
                error -> Log.w(T, error),
                txtList -> {
                    String text = "";
                    for (String txt : txtList) {
                        text = txt;
                    }

                    if (text.equals(ruleText)) {
                        return;
                    }

                    Log.d(T, "DNS设置变化");
                    ruleText = text;

                    runOnUiThread(() -> {
                        b.textDns.setText(
                                String.format(
                                        "%s %s",
                                        DateTime.now().toString("YYYY-MM-dd_HH:mm:ss"),
                                        ruleText
                                )
                        );

                        if (timer != null) {
                            reset();
                        }
                    });
                }
        );
    }

    /**
     * 更新音频文件
     */
    private void updateAudioFile() {
        Log.d(T, "更新音频文件");

        player.pause();
        player.clearMediaItems();
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            Log.w(T, "没有外部存储");
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            Log.w(T, "没有存储文件");
            return;
        }
        ArrayList<String> nameList = new ArrayList<>();
        for (File file : files) {
            Log.d(T, "音频:" + file.getName());
            nameList.add(file.getName());
            player.addMediaItem(
                    new MediaItem.Builder()
                            .setUri(
                                    Uri.fromFile(file)
                            )
                            .setMediaId(file.getName())
                            .build()
            );
        }
        player.prepare();

        b.textAudio.setText(
                String.join(",", nameList)
        );
        b.buttonBegin.setEnabled(
                nameList.size() > 0
        );
    }

    /**
     * 获取屏幕亮度
     *
     * @return 0-255
     */
    private int getScreenBright() {
        int v = 50;
        return Settings.System.getInt(
                getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                v
        );
    }

    /**
     * 更新屏幕保护文字位置
     */
    private void updateScreenProtectTextPlace() {
        MyApplication.SizeInfo sizeInfo = application.getSizeInfo(this);

        int leftMax = sizeInfo.screenWidthPixels - b.textScreenProtect.getMeasuredWidth();
        int topMax = sizeInfo.screenHeightPixels - b.textScreenProtect.getMeasuredHeight()
                - sizeInfo.statusBarHeightPixels - sizeInfo.navigationBarHeightPixels;

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) b.textScreenProtect.getLayoutParams();
        layoutParams.setMargins(
                random.nextInt(leftMax), random.nextInt(topMax), 0, 0
        );
        b.textScreenProtect.setLayoutParams(layoutParams);
    }

    /**
     * 设置屏幕亮度
     *
     * @param v 0-255
     */
    private void setScreenBright(int v) {
        Window window = getWindow();
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.screenBrightness = v / 255f;
        window.setAttributes(attributes);
    }

    /**
     * 开始屏幕保护
     */
    private void startScreenProtect() {
        hideSystemUI();
        b.layoutScreenProtect.setVisibility(View.VISIBLE);
        screenBright = getScreenBright();
        setScreenBright(10);
        handler.sendEmptyMessage(5);
    }

    /**
     * 停止屏幕保护
     */
    private void stopScreenProtect() {
        showSystemUI();
        b.layoutScreenProtect.setVisibility(View.GONE);
        setScreenBright(screenBright);
        handler.removeMessages(5);
    }

}