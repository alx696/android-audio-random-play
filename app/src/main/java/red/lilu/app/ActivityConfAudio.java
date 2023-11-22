package red.lilu.app;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.MimeTypeMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@kotlinx.coroutines.ExperimentalCoroutinesApi
public class ActivityConfAudio extends AppCompatActivity {
    private static final String T = "调试-设置音频界面";
    private File dir;
    private RecyclerAdapterAudio adapter;
    private HashSet<String> checkSet = new HashSet<>();
    private boolean isChange = false;
    private final ActivityResultLauncher<Intent> launcherOpenFile = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Uri uri = result.getData().getData();
                if (uri == null) {
                    return;
                }
                saveUri(uri);
                refreshList();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conf_audio);
        MaterialToolbar topAppBar = findViewById(R.id.top_bar);
        RecyclerView recyclerView = findViewById(R.id.list);
        FloatingActionButton addView = findViewById(R.id.add);
        BottomAppBar bottomBar = findViewById(R.id.bottom_bar);

        dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);

        topAppBar.setNavigationOnClickListener(view -> close());

        // 准备列表
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration itemDecoration = new DividerItemDecoration(
                recyclerView.getContext(),
                layoutManager.getOrientation()
        );
        recyclerView.addItemDecoration(itemDecoration);
        adapter = new RecyclerAdapterAudio(
                set -> {
                    checkSet = set;
                }
        );
        recyclerView.setAdapter(adapter);
        refreshList();

        addView.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            String[] mimeTypes = {"audio/*", "video/*"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

            launcherOpenFile.launch(intent);
        });

        bottomBar.setOnMenuItemClickListener(menuItem -> {
            // 删除功能
            if (menuItem.getItemId() == R.id.del) {
                if (checkSet.size() == 0) {
                    return true;
                }

                // 删除选中
                for (String name : checkSet) {
                    new File(dir, name).delete();
                }

                // 刷新
                refreshList();

                // 标记
                isChange = true;

                return true;
            }

            return false;
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //拦截返回(按返回键, 点返回按钮)
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            close();

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void saveUri(Uri uri) {
        ContentResolver contentResolver = getContentResolver();

        // 获取文件名称
        String fileName = String.format(
                "%s.%s",
                UUID.randomUUID().toString(),
                MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri))
        );
        Cursor queryCursor = contentResolver.query(uri, null, null, null, null);
        if (queryCursor == null) {
            return;
        }
        queryCursor.moveToFirst();
        int columnIndex = queryCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (columnIndex >= 0) {
            String name = queryCursor.getString(columnIndex);
            if (!name.isEmpty()) {
                fileName = name;
            }
        }
        queryCursor.close();

        // 保存文件
        File file = new File(
                dir,
                fileName
        );
        try (InputStream inputStream = contentResolver.openInputStream(uri)) {
            if (inputStream == null) {
                return;
            }

            FileUtils.copyInputStreamToFile(inputStream, file);
            Log.i(T, String.format("文件已经保存: %s", file.getAbsolutePath()));

            // 标记
            isChange = true;
        } catch (IOException e) {
            Log.w(T, e);
        }
    }

    private void refreshList() {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        List<RecyclerAdapterAudio.DataInfo> list = new ArrayList<>();
        for (File file : files) {
            list.add(
                    new RecyclerAdapterAudio.DataInfo(file.getName())
            );
        }
        adapter.setDataList(list);
    }

    private void close() {
        if (isChange) {
            Intent intent = new Intent(getApplicationContext(), ActivityMain.class);
            intent.setAction(ActivityMain.ACTION_AUDIO_UPDATE);
            startActivity(intent);
        }

        finish();
    }
}
