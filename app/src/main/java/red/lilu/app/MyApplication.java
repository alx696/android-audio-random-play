package red.lilu.app;

import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.datastore.core.Serializer;
import androidx.datastore.rxjava3.RxDataStore;
import androidx.datastore.rxjava3.RxDataStoreBuilder;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import red.lilu.app.data_store.Pref;

@kotlinx.coroutines.ExperimentalCoroutinesApi
public class MyApplication extends Application {

    private static final String T = "调试-我的应用";
    private String versionName = "";
    private long versionCode = 0;
    private static final Gson gson = new Gson();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    private static class PrefSerializer implements Serializer<Pref> {
        @Override
        public Pref getDefaultValue() {
            return Pref.getDefaultInstance();
        }

        @androidx.annotation.Nullable
        @Override
        public Pref readFrom(@NonNull InputStream inputStream, @NonNull Continuation<? super Pref> continuation) {
            try {
                return Pref.parseFrom(inputStream);
            } catch (IOException e) {
                continuation.resumeWith(e);
            }
            return null;
        }

        @androidx.annotation.Nullable
        @Override
        public Pref writeTo(Pref pref, @NonNull OutputStream outputStream, @NonNull Continuation<? super Unit> continuation) {
            try {
                pref.writeTo(outputStream);
            } catch (IOException e) {
                continuation.resumeWith(e);
            }

            return pref;
        }
    }

    private static RxDataStore<Pref> dataStore;

    @Override
    public void onCreate() {
        super.onCreate();

        // 获取版本名称
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionCode = packageInfo.getLongVersionCode();
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(T, e);
        }

        dataStore = new RxDataStoreBuilder<>(
                getApplicationContext(), "pref", new PrefSerializer()
        ).build();
    }

    public long getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public Gson getGson() {
        return gson;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public RxDataStore<Pref> getDataStore() {
        return dataStore;
    }

    public static class SizeInfo {
        public int screenWidthPixels, screenHeightPixels;
        public int statusBarHeightPixels;
        public int navigationBarHeightPixels;

        public SizeInfo(int screenWidthPixels, int screenHeightPixels,
                        int statusBarHeightPixels, int navigationBarHeightPixels) {
            this.screenWidthPixels = screenWidthPixels;
            this.screenHeightPixels = screenHeightPixels;
            this.statusBarHeightPixels = statusBarHeightPixels;
            this.navigationBarHeightPixels = navigationBarHeightPixels;
        }
    }

    public SizeInfo getSizeInfo(Activity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
//        Log.d(T, "宽" + displayMetrics.widthPixels);
//        Log.d(T, "高" + displayMetrics.heightPixels);

//        Log.d(T, "宽(DP)" + getResources().getConfiguration().screenWidthDp);
//        Log.d(T, "高(DP)" + getResources().getConfiguration().screenHeightDp);

        // status bar height
        int statusBarHeight = 0;
        int statusBarResourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (statusBarResourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(statusBarResourceId);
        }
//        Log.d(T, "状态栏" + statusBarHeight);

        // navigation bar height
        int navigationBarHeight = 0;
        int navigationBarResourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (navigationBarResourceId > 0) {
            navigationBarHeight = getResources().getDimensionPixelSize(navigationBarResourceId);
        }
//        Log.d(T, "导航栏" + navigationBarHeight);

        return new SizeInfo(
                displayMetrics.widthPixels, displayMetrics.heightPixels,
                statusBarHeight, navigationBarHeight
        );
    }

}
