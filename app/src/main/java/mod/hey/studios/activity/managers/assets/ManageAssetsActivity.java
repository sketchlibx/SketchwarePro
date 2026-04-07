package mod.hey.studios.activity.managers.assets;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import dev.pranav.filepicker.SelectionMode;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogCreateNewFileLayoutBinding;
import pro.sketchware.databinding.DialogInputLayoutBinding;
import pro.sketchware.databinding.ManageFileBinding;
import pro.sketchware.databinding.ManageJavaItemHsBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class ManageAssetsActivity extends BaseAppCompatActivity {

    private String current_path;
    private FilePathUtil fpu;
    private AssetsAdapter assetsAdapter;
    private String sc_id;
    private ManageFileBinding binding;

    private boolean isTreeViewEnabled;
    private ArrayList<FileNode> rootNodes;
    private final ArrayList<FileNode> flatNodesList = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ManageFileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sc_id = getIntent().getStringExtra("sc_id");
        Helper.fixFileprovider();
        setupUI();

        fpu = new FilePathUtil();
        current_path = Uri.parse(fpu.getPathAssets(sc_id)).getPath();

        refresh();
    }

    private void setupUI() {
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.showOptionsButton.setOnClickListener(view -> hideShowOptionsButton(false));
        binding.closeButton.setOnClickListener(view -> hideShowOptionsButton(true));
        binding.createNewButton.setOnClickListener(v -> {
            showCreateDialog();
            hideShowOptionsButton(true);
        });
        binding.importNewButton.setOnClickListener(v -> {
            showImportDialog();
            hideShowOptionsButton(true);
        });
    }

    private void hideShowOptionsButton(boolean isHide) {
        binding.optionsLayout.animate()
                .translationY(isHide ? 300 : 0)
                .alpha(isHide ? 0 : 1)
                .setInterpolator(new OvershootInterpolator());

        binding.showOptionsButton.animate()
                .translationY(isHide ? 0 : 300)
                .alpha(isHide ? 1 : 0)
                .setInterpolator(new OvershootInterpolator());
    }

    @Override
    public void onBackPressed() {
        if (isTreeViewEnabled) {
            super.onBackPressed();
        } else {
            if (Objects.equals(
                    Uri.parse(current_path).getPath(),
                    Uri.parse(fpu.getPathAssets(sc_id)).getPath()
            )) {
                super.onBackPressed();
            } else {
                current_path = current_path.substring(0, current_path.lastIndexOf(File.separator));
                refresh();
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void showCreateDialog() {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;

        var dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setTitle("Create new")
                .setMessage("If you're creating a file, make sure to add an extension.")
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = ((AlertDialog) dialogInterface).getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                String editable = Helper.getText(inputText).trim();

                if (editable.isEmpty()) {
                    SketchwareUtil.toastError("Invalid name");
                    return;
                }

                int checkedChipId = dialogBinding.chipGroupTypes.getCheckedChipId();
                if (checkedChipId == R.id.chip_file) {
                    FileUtil.writeFile(new File(current_path, editable).getAbsolutePath(), "");
                } else if (checkedChipId == R.id.chip_folder) {
                    FileUtil.makeDir(new File(current_path, editable).getAbsolutePath());
                } else {
                    SketchwareUtil.toast("Select a file type");
                    return;
                }

                forceRefreshTree();
                SketchwareUtil.toast("File was created successfully");
                dialogInterface.dismiss();
            });
        });

        dialogBinding.chipFile.setVisibility(View.VISIBLE);
        dialogBinding.chipFolder.setVisibility(View.VISIBLE);

        dialog.setView(dialogBinding.getRoot());
        dialog.show();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        inputText.requestFocus();
    }

    private void showImportDialog() {
        FilePickerOptions options = new FilePickerOptions();
        options.setSelectionMode(SelectionMode.BOTH);
        options.setMultipleSelection(true);
        options.setTitle("Select an asset file");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends File> files) {
                for (File file : files) {
                    try {
                        FileUtil.copyDirectory(file, new File(current_path, file.getName()));
                        forceRefreshTree();
                    } catch (IOException e) {
                        SketchwareUtil.toastError("Couldn't import file! [" + e.getMessage() + "]");
                    }
                }
            }
        };

        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "filePicker");
    }

    private void showRenameDialog(int position) {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;

        var dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Rename " + assetsAdapter.getFileName(position))
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Rename", (dialogInterface, i) -> {
                    if (!Helper.getText(inputText).isEmpty()) {
                        FileUtil.renameFile(assetsAdapter.getItem(position), new File(new File(assetsAdapter.getItem(position)).getParent(), Helper.getText(inputText)).getAbsolutePath());
                        forceRefreshTree();
                        SketchwareUtil.toast("Renamed successfully");
                    }
                    dialogInterface.dismiss();
                })
                .create();

        inputText.setText(assetsAdapter.getFileName(position));
        dialog.show();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        inputText.requestFocus();
    }

    private void showDeleteDialog(int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + assetsAdapter.getFileName(position) + "?")
                .setMessage("Are you sure you want to delete this " + (assetsAdapter.isFolder(position) ? "folder" : "file") + "? "
                        + "This action cannot be undone.")
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> {
                    FileUtil.deleteFile(assetsAdapter.getItem(position));
                    forceRefreshTree();
                    SketchwareUtil.toast("Deleted successfully");
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .create()
                .show();
    }

    private void forceRefreshTree() {
        rootNodes = null;
        refresh();
    }

    private void refresh() {
        if (!FileUtil.isExistFile(fpu.getPathAssets(sc_id))) {
            FileUtil.makeDir(fpu.getPathAssets(sc_id));
        }

        isTreeViewEnabled = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_TREE_VIEW)
                && ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ASSETS_TREE_VIEW);

        if (isTreeViewEnabled) {
            if (rootNodes == null) {
                rootNodes = new ArrayList<>();
                ArrayList<String> paths = new ArrayList<>();
                FileUtil.listDir(fpu.getPathAssets(sc_id), paths);
                Helper.sortPaths(paths);
                for (String p : paths) rootNodes.add(new FileNode(p, 0));
            }
            refreshFlatList();
        } else {
            ArrayList<String> currentTree = new ArrayList<>();
            FileUtil.listDir(current_path, currentTree);
            Helper.sortPaths(currentTree);

            flatNodesList.clear();
            for (String p : currentTree) {
                flatNodesList.add(new FileNode(p, 0));
            }

            if (assetsAdapter == null) {
                assetsAdapter = new AssetsAdapter();
                binding.filesListRecyclerView.setAdapter(assetsAdapter);
            } else {
                assetsAdapter.notifyDataSetChanged();
            }
            binding.noContentLayout.setVisibility(flatNodesList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshFlatList() {
        flatNodesList.clear();
        for (FileNode node : rootNodes) addNodeToFlatList(node);

        if (assetsAdapter == null) {
            assetsAdapter = new AssetsAdapter();
            binding.filesListRecyclerView.setAdapter(assetsAdapter);
        } else {
            assetsAdapter.notifyDataSetChanged();
        }
        binding.noContentLayout.setVisibility(flatNodesList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addNodeToFlatList(FileNode node) {
        flatNodesList.add(node);
        if (node.isFolder && node.isExpanded) {
            if (node.children == null) {
                node.children = new ArrayList<>();
                ArrayList<String> paths = new ArrayList<>();
                FileUtil.listDir(node.path, paths);
                Helper.sortPaths(paths);
                for (String p : paths) {
                    node.children.add(new FileNode(p, node.depth + 1));
                }
            }
            for (FileNode child : node.children) {
                addNodeToFlatList(child);
            }
        }
    }

    public static class FileNode {
        public String path;
        public String name;
        public boolean isFolder;
        public boolean isExpanded;
        public int depth;
        public ArrayList<FileNode> children;

        public FileNode(String p, int d) {
            path = p;
            name = new File(p).getName();
            isFolder = FileUtil.isDirectory(p);
            depth = d;
            isExpanded = false;
        }
    }

    public class AssetsAdapter extends RecyclerView.Adapter<AssetsAdapter.AssetsViewHolder> {

        private static final String[] textExtensions = {
                ".txt", ".xml", ".java", ".json", ".csv", ".html", ".css", ".js",
                ".md", ".rtf", ".log", ".sql", ".yml", ".yaml", ".properties", ".ini",
                ".kt", ".toml", ".kts", ".php", ".py", ".ts", ".md", ".sh", ".c", ".h",
                ".hpp", ".cpp"
        };

        @NonNull
        @Override
        public AssetsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            ManageJavaItemHsBinding binding = ManageJavaItemHsBinding.inflate(inflater, parent, false);
            var layoutParams = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            binding.getRoot().setLayoutParams(layoutParams);
            return new AssetsViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull AssetsViewHolder holder, int position) {
            FileNode node = flatNodesList.get(position);
            String item = node.path;
            var binding = holder.binding;

            binding.title.setText(node.name);

            if (isTreeViewEnabled) {
                int indentPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, node.depth * 24, getResources().getDisplayMetrics());
                ViewGroup.MarginLayoutParams iconParams = (ViewGroup.MarginLayoutParams) binding.icon.getLayoutParams();
                iconParams.setMarginStart(indentPx);
                binding.icon.setLayoutParams(iconParams);
                binding.getRoot().setPadding(0, 0, 0, 0);

                int iconPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
                binding.title.setCompoundDrawablePadding(iconPadding);

                binding.more.setVisibility(View.VISIBLE);

                if (node.isFolder) {
                    binding.title.setTypeface(null, Typeface.BOLD);
                    binding.icon.setVisibility(View.VISIBLE);
                    binding.icon.setImageResource(node.isExpanded ? R.drawable.ic_mtrl_arrow_down : R.drawable.ic_mtrl_chevron_right_24);
                    binding.icon.setColorFilter(ThemeUtils.getColor(ManageAssetsActivity.this, R.attr.colorOnSurfaceVariant));
                    binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_mtrl_folder, 0, 0, 0);
                } else {
                    binding.title.setTypeface(null, Typeface.NORMAL);
                    binding.icon.setVisibility(View.INVISIBLE);
                    binding.icon.clearColorFilter();
                    try {
                        if (FileUtil.isImageFile(item)) {
                            Glide.with(holder.binding.icon.getContext()).load(new File(item)).into(holder.binding.icon);
                            binding.icon.setVisibility(View.VISIBLE);
                            ViewGroup.MarginLayoutParams imgParams = (ViewGroup.MarginLayoutParams) binding.icon.getLayoutParams();
                            imgParams.setMarginStart(indentPx + (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
                            binding.icon.setLayoutParams(imgParams);
                            binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                        } else {
                            binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_mtrl_file, 0, 0, 0);
                        }
                    } catch (Exception ignored) {
                        binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_mtrl_file, 0, 0, 0);
                    }
                }
            } else {
                ViewGroup.MarginLayoutParams iconParams = (ViewGroup.MarginLayoutParams) binding.icon.getLayoutParams();
                iconParams.setMarginStart(0);
                binding.icon.setLayoutParams(iconParams);
                binding.getRoot().setPadding(0, 0, 0, 0);

                binding.icon.setVisibility(View.VISIBLE);
                binding.icon.clearColorFilter();
                binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                binding.title.setTypeface(null, Typeface.NORMAL);

                binding.more.setVisibility(node.isFolder ? View.GONE : View.VISIBLE);

                if (node.isFolder) {
                    binding.icon.setImageResource(R.drawable.ic_mtrl_folder);
                } else {
                    try {
                        if (FileUtil.isImageFile(item)) {
                            Glide.with(holder.binding.icon.getContext()).load(new File(item)).into(binding.icon);
                        } else {
                            binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                        }
                    } catch (Exception ignored) {
                        binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                    }
                }
            }

            binding.getRoot().setOnClickListener(view -> {
                if (node.isFolder) {
                    current_path = item;
                    if (isTreeViewEnabled) {
                        node.isExpanded = !node.isExpanded;
                        refreshFlatList();
                    } else {
                        refresh();
                    }
                } else {
                    goEditFile(position);
                }
            });

            binding.getRoot().setOnLongClickListener(view -> {
                binding.more.performClick();
                return true;
            });

            Helper.applyRipple(holder.itemView.getContext(), binding.more);
            binding.more.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(holder.itemView.getContext(), v);

                if (!node.isFolder) {
                    popupMenu.getMenu().add(0, 0, 0, "Edit");
                }

                popupMenu.getMenu().add(0, 1, 0, "Rename");
                popupMenu.getMenu().add(0, 2, 0, "Delete");

                popupMenu.setOnMenuItemClickListener(itemMenu -> {
                    switch (itemMenu.getItemId()) {
                        case 0 -> goEditFile(position);
                        case 1 -> showRenameDialog(position);
                        case 2 -> showDeleteDialog(position);
                        default -> {
                            return false;
                        }
                    }
                    return true;
                });
                popupMenu.show();
            });
        }

        @Override
        public int getItemCount() {
            return flatNodesList.size();
        }

        public String getItem(int position) {
            return flatNodesList.get(position).path;
        }

        public String getFileName(int position) {
            return flatNodesList.get(position).name;
        }

        public boolean isFolder(int position) {
            return flatNodesList.get(position).isFolder;
        }

        public void goEditFile(int position) {
            if (Arrays.stream(textExtensions).anyMatch(getItem(position)::endsWith)) {
                Intent launchIntent = new Intent();
                launchIntent.setClass(getApplicationContext(), SrcCodeEditor.class);
                launchIntent.putExtra("title", getFileName(position));
                launchIntent.putExtra("content", getItem(position));
                startActivity(launchIntent);
            } else {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.setDataAndType(Uri.fromFile(new File(getItem(position))), "*/*");
                viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(viewIntent);
            }
        }

        public static class AssetsViewHolder extends RecyclerView.ViewHolder {
            ManageJavaItemHsBinding binding;
            public AssetsViewHolder(ManageJavaItemHsBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}