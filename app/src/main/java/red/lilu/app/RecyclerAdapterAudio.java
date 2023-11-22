package red.lilu.app;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import red.lilu.app.databinding.ItemAudioBinding;

public class RecyclerAdapterAudio extends RecyclerView.Adapter<RecyclerAdapterAudio.ViewHolder> {

    private static final String T = "调试-音频列表适配器";
    private final List<DataInfo> dataList = new ArrayList<>();
    private final HashSet<String> checkSet = new HashSet<>();
    private final java9.util.function.Consumer<HashSet<String>> onCheckChange;

    public static class DataInfo {
        public String name;

        public DataInfo(String name) {
            this.name = name;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemAudioBinding b;

        public ViewHolder(ItemAudioBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    public RecyclerAdapterAudio(java9.util.function.Consumer<HashSet<String>> onCheckChange) {
        this.onCheckChange = onCheckChange;
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        return new ViewHolder(
                ItemAudioBinding.inflate(
                        LayoutInflater.from(viewGroup.getContext()),
                        viewGroup,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, final int position) {
        DataInfo data = dataList.get(position);

        h.b.text.setText(data.name);

        h.b.check.setChecked(
                checkSet.contains(data.name)
        );
        h.b.check.setOnCheckedChangeListener((CompoundButton compoundButton, boolean isChecked) -> {
            if (isChecked) {
                checkSet.add(data.name);
            } else {
                checkSet.remove(data.name);
            }

            HashSet<String> set = new HashSet<>(checkSet);
            onCheckChange.accept(set);
        });
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    /**
     * 设置数据
     */
    public void setDataList(List<DataInfo> list) {
        checkSet.clear();
        int size = dataList.size();
        dataList.clear();
        notifyItemRangeRemoved(0, size);

        dataList.addAll(list);
        notifyItemRangeInserted(0, dataList.size());
    }

}