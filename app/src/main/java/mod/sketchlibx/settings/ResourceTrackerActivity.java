package mod.sketchlibx.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import a.a.a.jC;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;

public class ResourceTrackerActivity extends BaseAppCompatActivity {

    private String sc_id;
    private RecyclerView recyclerView;
    private TrackerAdapter adapter;
    private List<ProjectAnalyzerEngine.UnusedResource> currentList = new ArrayList<>();
    
    private View contentLayout;
    private View noContentLayout;
    private ExtendedFloatingActionButton fabCleanAll;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resource_tracker);

        sc_id = getIntent().getStringExtra("sc_id");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Resource Usage Tracker");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        contentLayout = findViewById(R.id.contentLayout);
        noContentLayout = findViewById(R.id.noContentLayout);
        fabCleanAll = findViewById(R.id.fab_clean_all);
        
        recyclerView = findViewById(R.id.rv_resources);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fabCleanAll.setOnClickListener(v -> {
            if (currentList.isEmpty()) {
                SketchwareUtil.toast("Nothing to clean!");
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Clean All Resources?")
                    .setMessage("This will safely remove all " + currentList.size() + " unused resources from your project. This cannot be undone.")
                    .setPositiveButton("Clean All", (dialog, which) -> {
                        for (ProjectAnalyzerEngine.UnusedResource res : currentList) {
                            performDeletion(res);
                        }
                        saveAllChanges();
                        SketchwareUtil.toast("Optimized! Removed " + currentList.size() + " items.");
                        loadData();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        loadData();
    }

    private void loadData() {
        currentList = ProjectAnalyzerEngine.scanForUnusedResources(sc_id);
        if (adapter == null) {
            adapter = new TrackerAdapter();
            recyclerView.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }

        updateUIState();
    }

    private void updateUIState() {
        if (currentList.isEmpty()) {
            noContentLayout.setVisibility(View.VISIBLE);
            contentLayout.setVisibility(View.GONE);
            fabCleanAll.hide();
            getSupportActionBar().setSubtitle("Project is fully optimized");
        } else {
            noContentLayout.setVisibility(View.GONE);
            contentLayout.setVisibility(View.VISIBLE);
            fabCleanAll.show();
            getSupportActionBar().setSubtitle(currentList.size() + " Unused Items Found");
        }
    }

    private void performDeletion(ProjectAnalyzerEngine.UnusedResource res) {
        if (res.type == 1) {
            jC.d(sc_id).b.remove((ProjectResourceBean) res.reference);
        } else if (res.type == 2) {
            jC.d(sc_id).c.remove((ProjectResourceBean) res.reference);
        } else if (res.type == 3) {
            jC.d(sc_id).d.remove((ProjectResourceBean) res.reference);
        } else if (res.type == 4) {
            jC.b(sc_id).c().remove((ProjectFileBean) res.reference);
        }
    }

    private void saveAllChanges() {
        jC.d(sc_id).a();
        jC.b(sc_id).l();
        jC.b(sc_id).j();
    }

    private class TrackerAdapter extends RecyclerView.Adapter<TrackerAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_unused_resource, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectAnalyzerEngine.UnusedResource res = currentList.get(position);
            holder.title.setText(res.name);
            holder.subtitle.setText(res.detail);

            if (res.type == 1) holder.icon.setImageResource(R.drawable.ic_mtrl_image);
            else if (res.type == 2) holder.icon.setImageResource(R.drawable.ic_mtrl_music);
            else if (res.type == 3) holder.icon.setImageResource(R.drawable.ic_mtrl_font);
            else if (res.type == 4) holder.icon.setImageResource(R.drawable.ic_mtrl_devices);

            holder.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ResourceTrackerActivity.this)
                        .setTitle("Remove Unused Item?")
                        .setMessage("Are you sure you want to remove '" + res.name + "' from your project?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            performDeletion(res);
                            saveAllChanges();
                            currentList.remove(position);
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, currentList.size());
                            
                            updateUIState();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return currentList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            ImageView icon, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_title);
                subtitle = itemView.findViewById(R.id.tv_subtitle);
                icon = itemView.findViewById(R.id.img_icon);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
