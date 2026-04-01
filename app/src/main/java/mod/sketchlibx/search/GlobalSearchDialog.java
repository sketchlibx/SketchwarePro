package mod.sketchlibx.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.design.DesignActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;

public class GlobalSearchDialog extends BottomSheetDialogFragment {

    private final String sc_id;
    private final DesignActivity activity;
    private RecyclerView recyclerView;
    private SearchAdapter adapter;
    private ProjectSearchEngine searchEngine;
    private TextView searchInfo;

    public GlobalSearchDialog(String sc_id, DesignActivity activity) {
        this.sc_id = sc_id;
        this.activity = activity;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_global_search, container, false);

        TextInputEditText searchBox = root.findViewById(R.id.edit_search);
        recyclerView = root.findViewById(R.id.rv_search_results);
        searchInfo = root.findViewById(R.id.search_info);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SearchAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        searchEngine = new ProjectSearchEngine(sc_id);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString();
                if (query.isEmpty()) {
                    searchInfo.setVisibility(View.VISIBLE);
                    adapter.updateData(new ArrayList<>());
                    return;
                }
                
                searchInfo.setVisibility(View.GONE);
                
                new Thread(() -> {
                    List<SearchResult> results = searchEngine.search(query);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> adapter.updateData(results));
                    }
                }).start();
            }
        });

        return root;
    }

    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private List<SearchResult> items;

        public SearchAdapter(List<SearchResult> items) {
            this.items = items;
        }

        public void updateData(List<SearchResult> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_global_search_result, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchResult result = items.get(position);
            
            holder.title.setText("[" + result.category + "] " + result.title);
            holder.subtitle.setText(result.fileName + " • " + result.description);

            switch (result.category) {
                case "View": holder.icon.setImageResource(R.drawable.ic_mtrl_screen); break;
                case "Logic Block": holder.icon.setImageResource(R.drawable.ic_mtrl_puzzle); break;
                case "Component": holder.icon.setImageResource(R.drawable.ic_mtrl_component); break;
                default: holder.icon.setImageResource(R.drawable.ic_mtrl_file);
            }

            holder.itemView.setOnClickListener(v -> {
                dismiss();
                activity.jumpToFileAndTab(result.fileName, result.tabIndex);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            ImageView icon;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_title);
                subtitle = itemView.findViewById(R.id.tv_subtitle);
                icon = itemView.findViewById(R.id.img_icon);
            }
        }
    }
}
