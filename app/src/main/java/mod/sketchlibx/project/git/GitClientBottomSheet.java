package mod.sketchlibx.project.git;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import a.a.a.jC;
import a.a.a.wq;
import a.a.a.yq;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class GitClientBottomSheet extends BottomSheetDialogFragment {

    private String sc_id;
    private Git git;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDirectPushEnabled = false;

    // Changes Tab Views
    private RecyclerView rvChanges;
    private GitChangeAdapter changesAdapter;
    private TextView tvStatusEmpty;
    private MaterialButton btnCommit;
    private MaterialButton btnCommitPush;
    private TextInputEditText etCommitMsg;
    private TextInputLayout tilCommitMsg;

    public GitClientBottomSheet() {
    }

    public GitClientBottomSheet(String sc_id) {
        this.sc_id = sc_id;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isDirectPushEnabled = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_GIT_DIRECT_PUSH);
        try {
            File gitDir = new File(wq.d(sc_id), ".git");
            if (gitDir.exists()) {
                git = Git.open(new File(wq.d(sc_id)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) d;
            View bottomSheetInternal = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                BottomSheetBehavior.from(bottomSheetInternal).setState(BottomSheetBehavior.STATE_EXPANDED);
                BottomSheetBehavior.from(bottomSheetInternal).setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_git_client, container, false);

        TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        ViewPager viewPager = view.findViewById(R.id.view_pager);

        GitPagerAdapter adapter = new GitPagerAdapter(requireContext(), inflater);
        viewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(viewPager);

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (git != null) {
            git.close();
        }
    }

    // ------------------------------------------------------
    // Core Git Logic (Changes Tab)
    // ------------------------------------------------------

    private void setupChangesTab(View view) {
        rvChanges = view.findViewById(R.id.rv_changes);
        tvStatusEmpty = view.findViewById(R.id.tv_status_empty);
        btnCommit = view.findViewById(R.id.btn_commit);
        btnCommitPush = view.findViewById(R.id.btn_commit_push);
        etCommitMsg = view.findViewById(R.id.et_commit_msg);
        tilCommitMsg = view.findViewById(R.id.til_commit_msg);
        MaterialButton btnStageAll = view.findViewById(R.id.btn_stage_all);
        ImageView btnRefresh = view.findViewById(R.id.btn_refresh);

        rvChanges.setLayoutManager(new LinearLayoutManager(getContext()));
        changesAdapter = new GitChangeAdapter();
        rvChanges.setAdapter(changesAdapter);

        if (isDirectPushEnabled) {
            btnCommitPush.setVisibility(View.VISIBLE);
        }

        updateCommitButtonState();
        etCommitMsg.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateCommitButtonState(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnRefresh.setOnClickListener(v -> loadGitStatus());
        btnStageAll.setOnClickListener(v -> performStageAll());
        btnCommit.setOnClickListener(v -> performCommit(false));
        btnCommitPush.setOnClickListener(v -> performCommit(true));

        loadGitStatus();
    }

    private void updateCommitButtonState() {
        boolean hasMessage = etCommitMsg.getText() != null && !etCommitMsg.getText().toString().trim().isEmpty();
        boolean hasStagedFiles = changesAdapter != null && changesAdapter.hasStagedFiles();
        
        btnCommit.setEnabled(hasMessage && hasStagedFiles);
        btnCommitPush.setEnabled(hasMessage && hasStagedFiles);

        if (!hasStagedFiles) {
            tilCommitMsg.setHelperText("Stage files to commit");
        } else {
            tilCommitMsg.setHelperText(null);
        }
    }

    private void loadGitStatus() {
        if (git == null) return;
        new Thread(() -> {
            try {
                Status status = git.status().call();
                List<GitFile> fileList = new ArrayList<>();

                // Unstaged (Untracked, Modified, Missing)
                for (String s : status.getUntracked()) fileList.add(new GitFile(s, "Untracked", false, Color.parseColor("#4CAF50")));
                for (String s : status.getModified()) fileList.add(new GitFile(s, "Modified", false, Color.parseColor("#2196F3")));
                for (String s : status.getMissing()) fileList.add(new GitFile(s, "Deleted", false, Color.parseColor("#F44336")));

                // Staged (Added, Changed, Removed)
                for (String s : status.getAdded()) fileList.add(new GitFile(s, "Added", true, Color.parseColor("#4CAF50")));
                for (String s : status.getChanged()) fileList.add(new GitFile(s, "Modified", true, Color.parseColor("#2196F3")));
                for (String s : status.getRemoved()) fileList.add(new GitFile(s, "Deleted", true, Color.parseColor("#F44336")));

                mainHandler.post(() -> {
                    if (fileList.isEmpty()) {
                        tvStatusEmpty.setVisibility(View.VISIBLE);
                        rvChanges.setVisibility(View.GONE);
                    } else {
                        tvStatusEmpty.setVisibility(View.GONE);
                        rvChanges.setVisibility(View.VISIBLE);
                    }
                    changesAdapter.setFiles(fileList);
                    updateCommitButtonState();
                });

            } catch (Exception e) {
                mainHandler.post(() -> SketchwareUtil.toastError("Failed to get status: " + e.getMessage()));
            }
        }).start();
    }

    private void performStageAll() {
        if (git == null) return;
        SketchwareUtil.toast("Auto-generating files & staging...");
        new Thread(() -> {
            try {
                // 🔥 MASTERSTROKE: Trigger Internal File Generator before Git Add!
                yq projectYq = new yq(getContext(), sc_id);
                // a(LibraryManager, FileManager, DataManager) generates all java/xml sources to disk!
                projectYq.a(jC.c(sc_id), jC.b(sc_id), jC.a(sc_id));

                // Stage everything
                git.add().addFilepattern(".").call();
                
                // Remove deleted files
                Status status = git.status().call();
                if (!status.getMissing().isEmpty()) {
                    org.eclipse.jgit.api.RmCommand rm = git.rm();
                    for (String missing : status.getMissing()) {
                        rm.addFilepattern(missing);
                    }
                    rm.call();
                }

                loadGitStatus();
                mainHandler.post(() -> SketchwareUtil.toast("All files generated & staged!"));

            } catch (Exception e) {
                mainHandler.post(() -> SketchwareUtil.toastError("Stage failed: " + e.getMessage()));
            }
        }).start();
    }

    private void performCommit(boolean pushAfter) {
        if (git == null) return;
        String message = etCommitMsg.getText().toString().trim();
        new Thread(() -> {
            try {
                git.commit().setMessage(message).call();
                mainHandler.post(() -> {
                    etCommitMsg.setText("");
                    SketchwareUtil.toast("Committed successfully!");
                    loadGitStatus();
                });
                
                if (pushAfter) {
                    mainHandler.post(() -> SketchwareUtil.toast("Push logic will be in Phase 3! ⚡"));
                    // Phase 3 logic here...
                }
            } catch (GitAPIException e) {
                mainHandler.post(() -> SketchwareUtil.toastError("Commit failed: " + e.getMessage()));
            }
        }).start();
    }


    // ------------------------------------------------------
    // UI Models & Adapters
    // ------------------------------------------------------

    private static class GitFile {
        String path;
        String statusLabel;
        boolean isStaged;
        int color;

        GitFile(String path, String statusLabel, boolean isStaged, int color) {
            this.path = path;
            this.statusLabel = statusLabel;
            this.isStaged = isStaged;
            this.color = color;
        }
    }

    private class GitChangeAdapter extends RecyclerView.Adapter<GitChangeAdapter.ViewHolder> {
        private List<GitFile> files = new ArrayList<>();

        public void setFiles(List<GitFile> files) {
            this.files = files;
            notifyDataSetChanged();
        }

        public boolean hasStagedFiles() {
            for (GitFile f : files) if (f.isStaged) return true;
            return false;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Programmatically creating item layout to save an XML file
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(0, 16, 0, 16);

            TextView tvBadge = new TextView(parent.getContext());
            tvBadge.setId(View.generateViewId());
            tvBadge.setTextSize(12f);
            tvBadge.setPadding(12, 4, 12, 4);
            tvBadge.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.setMarginEnd(16);
            root.addView(tvBadge, badgeParams);

            TextView tvPath = new TextView(parent.getContext());
            tvPath.setId(View.generateViewId());
            tvPath.setTextSize(14f);
            tvPath.setMaxLines(1);
            tvPath.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            tvPath.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface));
            LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            root.addView(tvPath, pathParams);

            TextView tvState = new TextView(parent.getContext());
            tvState.setId(View.generateViewId());
            tvState.setTextSize(12f);
            tvState.setPadding(16, 8, 16, 8);
            tvState.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorPrimary));
            root.addView(tvState);

            return new ViewHolder(root, tvBadge, tvPath, tvState);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GitFile file = files.get(position);
            
            // Badge
            holder.tvBadge.setText(file.statusLabel.substring(0, 1)); // M, A, D, U
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(file.color);
            gd.setCornerRadius(8f);
            holder.tvBadge.setBackground(gd);

            // Path
            holder.tvPath.setText(file.path);

            // Action Button
            holder.tvAction.setText(file.isStaged ? "UNSTAGE" : "STAGE");
            holder.tvAction.setOnClickListener(v -> {
                if (git == null) return;
                new Thread(() -> {
                    try {
                        if (file.isStaged) {
                            git.reset().addPath(file.path).call();
                        } else {
                            if (file.statusLabel.equals("Deleted")) {
                                git.rm().addFilepattern(file.path).call();
                            } else {
                                git.add().addFilepattern(file.path).call();
                            }
                        }
                        loadGitStatus();
                    } catch (Exception e) {
                        mainHandler.post(() -> SketchwareUtil.toastError("Action failed: " + e.getMessage()));
                    }
                }).start();
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvBadge, tvPath, tvAction;
            ViewHolder(View itemView, TextView tvBadge, TextView tvPath, TextView tvAction) {
                super(itemView);
                this.tvBadge = tvBadge;
                this.tvPath = tvPath;
                this.tvAction = tvAction;
            }
        }
    }


    // ------------------------------------------------------
    // Pager Adapter for our 5 Tabs
    // ------------------------------------------------------
    private class GitPagerAdapter extends PagerAdapter {
        private final Context context;
        private final LayoutInflater inflater;
        private final String[] tabTitles = {"Changes", "History", "Branches", "Remotes", "Settings"};

        public GitPagerAdapter(Context context, LayoutInflater inflater) {
            this.context = context;
            this.inflater = inflater;
        }

        @Override
        public int getCount() {
            return tabTitles.length;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View view;
            if (position == 0) {
                // Changes Tab
                view = inflater.inflate(R.layout.tab_git_changes, container, false);
                setupChangesTab(view);
            } else {
                // Placeholder for other tabs
                FrameLayout frameLayout = new FrameLayout(context);
                frameLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                TextView placeholderText = new TextView(context);
                placeholderText.setText("UI for " + tabTitles[position] + " will be here in Phase 2/3! ");
                placeholderText.setGravity(Gravity.CENTER);
                placeholderText.setTextSize(16f);
                
                frameLayout.addView(placeholderText);
                view = frameLayout;
            }
            container.addView(view);
            return view;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return tabTitles[position];
        }
    }
}
