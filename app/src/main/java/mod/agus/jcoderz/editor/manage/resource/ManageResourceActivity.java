package mod.agus.jcoderz.editor.manage.resource;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import dev.pranav.filepicker.SelectionMode;
import mod.bobur.VectorDrawableLoader;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogCreateNewFileLayoutBinding;
import pro.sketchware.databinding.DialogInputLayoutBinding;
import pro.sketchware.databinding.ManageFileBinding;
import pro.sketchware.databinding.ManageJavaItemHsBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileResConfig;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

@SuppressLint("SetTextI18n")
public class ManageResourceActivity extends BaseAppCompatActivity {

    private CustomAdapter adapter;
    private FilePickerDialogFragment dialog;
    private FilePathUtil fpu;
    private FileResConfig frc;
    private String numProj;
    private String temp;

    private ManageFileBinding binding;

    private boolean isTreeViewEnabled;
    private ArrayList<FileNode> rootNodes;
    private final ArrayList<FileNode> flatNodesList = new ArrayList<>();

    public static String getLastDirectory(String path) {
        int lastSlashIndex = path.lastIndexOf('/');
        String parentPath = path.substring(0, lastSlashIndex);
        lastSlashIndex = parentPath.lastIndexOf('/');
        return parentPath.substring(lastSlashIndex + 1);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ManageFileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getIntent().hasExtra("sc_id")) {
            numProj = getIntent().getStringExtra("sc_id");
        }
        Helper.fixFileprovider();
        frc = new FileResConfig(numProj);
        fpu = new FilePathUtil();
        setupDialog();
        checkDir();
        initToolbar();
    }

    private void checkDir() {
        if (FileUtil.isExistFile(fpu.getPathResource(numProj))) {
            temp = fpu.getPathResource(numProj);
            refresh();
            return;
        }
        FileUtil.makeDir(fpu.getPathResource(numProj));
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/anim");
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/drawable");
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/drawable-xhdpi");
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/layout");
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/menu");
        FileUtil.makeDir(fpu.getPathResource(numProj) + "/values");
        checkDir();
    }

    private void forceRefreshTree() {
        rootNodes = null;
        refresh();
    }

    private void refresh() {
        isTreeViewEnabled = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_TREE_VIEW)
                && ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_RESOURCE_TREE_VIEW);

        if (isTreeViewEnabled) {
            if (rootNodes == null) {
                rootNodes = new ArrayList<>();
                ArrayList<String> paths = frc.getResourceFile(fpu.getPathResource(numProj));
                Collections.sort(paths, String.CASE_INSENSITIVE_ORDER);
                for (String p : paths) {
                    rootNodes.add(new FileNode(p, 0));
                }
            }
            refreshFlatList();
            handleFab();
        } else {
            ArrayList<String> resourceFile = frc.getResourceFile(temp);
            Collections.sort(resourceFile, String.CASE_INSENSITIVE_ORDER);
            
            flatNodesList.clear();
            for (String p : resourceFile) {
                flatNodesList.add(new FileNode(p, 0));
            }
            
            if (adapter == null) {
                adapter = new CustomAdapter();
                binding.filesListRecyclerView.setAdapter(adapter);
            } else {
                adapter.notifyDataSetChanged();
            }
            binding.noContentLayout.setVisibility(resourceFile.isEmpty() ? View.VISIBLE : View.GONE);
            handleFab();
        }
    }

    private void refreshFlatList() {
        flatNodesList.clear();
        for (FileNode node : rootNodes) addNodeToFlatList(node);

        if (adapter == null) {
            adapter = new CustomAdapter();
            binding.filesListRecyclerView.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
        binding.noContentLayout.setVisibility(flatNodesList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addNodeToFlatList(FileNode node) {
        flatNodesList.add(node);
        if (node.isFolder && node.isExpanded) {
            if (node.children == null) {
                node.children = new ArrayList<>();
                ArrayList<String> paths = frc.getResourceFile(node.path);
                Collections.sort(paths, String.CASE_INSENSITIVE_ORDER);
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
            name = Uri.parse(p).getLastPathSegment();
            isFolder = FileUtil.isDirectory(p);
            depth = d;
            isExpanded = false;
        }
    }

    private boolean isInMainDirectory() {
        return temp.equals(fpu.getPathResource(numProj));
    }

    private void handleFab() {
        var optionsButton = binding.showOptionsButton;
        if (isInMainDirectory() || isTreeViewEnabled) {
            optionsButton.setText("Create new");
            hideShowOptionsButton(true);
        } else {
            optionsButton.setText("Create or import");
        }
    }

    private void initToolbar() {
        binding.topAppBar.setTitle("Resource Manager");
        binding.topAppBar.setNavigationOnClickListener(v -> onBackPressed());
        binding.showOptionsButton.setOnClickListener(view -> {
            if (isInMainDirectory() || isTreeViewEnabled) {
                createNewDialog(true);
                return;
            }
            hideShowOptionsButton(false);
        });
        binding.closeButton.setOnClickListener(view -> hideShowOptionsButton(true));
        binding.createNewButton.setOnClickListener(v -> {
            createNewDialog(isInMainDirectory() || isTreeViewEnabled);
            hideShowOptionsButton(true);
        });
        binding.importNewButton.setOnClickListener(v -> {
            dialog.show(getSupportFragmentManager(), "filePicker");
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
    public void onResume() {
        super.onResume();
        if (binding.filesListRecyclerView.getAdapter() != null) {
            binding.filesListRecyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public void onBackPressed() {
        if (isTreeViewEnabled) {
            setResult(RESULT_OK);
            finish();
            super.onBackPressed();
        } else {
            try {
                temp = temp.substring(0, temp.lastIndexOf("/"));
                if (temp.contains("resource")) {
                    refresh();
                    return;
                }
            } catch (IndexOutOfBoundsException ignored) {
            }
            setResult(RESULT_OK);
            finish();
            super.onBackPressed();
        }
    }

    private void createNewDialog(boolean isFolder) {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;

        var dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setTitle(isFolder ? "Create a new folder" : "Create a new file")
                .setMessage("Enter a name for the new " + (isFolder ? "folder" : "file"))
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Create", null)
                .create();

        dialogBinding.chipGroupTypes.setVisibility(View.GONE);
        if (!isFolder) {
            dialogBinding.inputText.setText(".xml");
        }

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = ((androidx.appcompat.app.AlertDialog) dialogInterface).getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                if (Helper.getText(inputText).isEmpty()) {
                    SketchwareUtil.toastError("Invalid name");
                    return;
                }

                String name = Helper.getText(inputText);
                String path;
                if (isFolder) {
                    path = fpu.getPathResource(numProj) + "/" + name;
                } else {
                    path = new File(temp + File.separator + name).getAbsolutePath();
                }

                if (FileUtil.isExistFile(path)) {
                    SketchwareUtil.toastError("File exists already");
                    return;
                }
                if (isFolder) {
                    FileUtil.makeDir(path);
                } else {
                    FileUtil.writeFile(path, "<?xml version=\"1.0\" encoding=\"utf-8\"?>");
                }
                forceRefreshTree();
                SketchwareUtil.toast("Created file successfully");
                dialog.dismiss();
            });

            dialog.setView(dialogBinding.getRoot());
            dialog.show();

            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            inputText.requestFocus();

            if (!isFolder) {
                inputText.setSelection(0);
            }
        });

        dialog.show();
    }

    private void setupDialog() {
        FilePickerOptions options = new FilePickerOptions();
        options.setSelectionMode(SelectionMode.BOTH);
        options.setMultipleSelection(true);
        options.setTitle("Select resource files");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends File> files) {
                if (files.isEmpty()) {
                    SketchwareUtil.toastError("No files selected");
                    return;
                }
                for (File file : files) {
                    try {
                        FileUtil.copyDirectory(file, new File(temp + File.separator + file.getName()));
                    } catch (IOException e) {
                        SketchwareUtil.toastError("Couldn't import resource! [" + e.getMessage() + "]");
                    }
                }
                forceRefreshTree();
            }
        };

        dialog = new FilePickerDialogFragment(options, callback);
    }

    private void showRenameDialog(String path) {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;

        var dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Rename")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Rename", (dialogInterface, i) -> {
                    if (!Helper.getText(inputText).isEmpty()) {
                        if (FileUtil.renameFile(path, path.substring(0, path.lastIndexOf("/")) + "/" + Helper.getText(inputText))) {
                            SketchwareUtil.toast("Renamed successfully");
                        } else {
                            SketchwareUtil.toastError("Renaming failed");
                        }
                        forceRefreshTree();
                    }
                    dialogInterface.dismiss();
                })
                .create();

        try {
            inputText.setText(path.substring(path.lastIndexOf("/") + 1));
        } catch (IndexOutOfBoundsException e) {
            inputText.setText(path);
        }

        dialog.setView(dialogBinding.getRoot());
        dialog.show();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        inputText.requestFocus();
    }

    private void showDeleteDialog(String path) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + Uri.fromFile(new File(path)).getLastPathSegment() + "?")
                .setMessage("Are you sure you want to delete this " + (FileUtil.isDirectory(path) ? "folder" : "file") + "? "
                        + "This action cannot be undone.")
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> {
                    FileUtil.deleteFile(path);
                    forceRefreshTree();
                    SketchwareUtil.toast("Deleted");
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .create()
                .show();
    }

    private void goEdit(String path) {
        if (path.endsWith("xml")) {
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), SrcCodeEditor.class);
            intent.putExtra("title", Uri.parse(path).getLastPathSegment());
            intent.putExtra("content", path);
            intent.putExtra("xml", "");
            if (getIntent().hasExtra("sc_id")) {
                intent.putExtra("sc_id", getIntent().getStringExtra("sc_id"));
            }
            startActivity(intent);
        } else {
            SketchwareUtil.toast("Only XML files can be edited");
        }
    }

    private void goEdit2(String path) {
        if (path.endsWith("xml")) {
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), SrcCodeEditor.class);
            intent.putExtra("title", Uri.parse(path).getLastPathSegment());
            intent.putExtra("content", path);
            intent.putExtra("xml", "");
            startActivity(intent);
        } else {
            SketchwareUtil.toast("Only XML files can be edited");
        }
    }

    private class CustomAdapter extends RecyclerView.Adapter<CustomAdapter.ViewHolder> {

        public CustomAdapter() {
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ManageJavaItemHsBinding binding = ManageJavaItemHsBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileNode node = flatNodesList.get(position);
            String path = node.path;
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
                    binding.icon.setColorFilter(ThemeUtils.getColor(ManageResourceActivity.this, R.attr.colorOnSurfaceVariant));
                    binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_mtrl_folder, 0, 0, 0);
                } else {
                    binding.title.setTypeface(null, Typeface.NORMAL);
                    binding.icon.setVisibility(View.INVISIBLE);
                    binding.icon.clearColorFilter();
                    try {
                        if (FileUtil.isImageFile(path)) {
                            Glide.with(ManageResourceActivity.this).load(new File(path)).into(binding.icon);
                            binding.icon.setVisibility(View.VISIBLE);
                            ViewGroup.MarginLayoutParams imgParams = (ViewGroup.MarginLayoutParams) binding.icon.getLayoutParams();
                            imgParams.setMarginStart(indentPx + (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
                            binding.icon.setLayoutParams(imgParams);
                            binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                        } else if (path.endsWith(".xml") && "drawable".equals(getLastDirectory(path))) {
                            new VectorDrawableLoader().setImageVectorFromFile(binding.icon, path);
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
                        if (FileUtil.isImageFile(path)) {
                            Glide.with(ManageResourceActivity.this).load(new File(path)).into(binding.icon);
                        } else if (path.endsWith(".xml") && "drawable".equals(getLastDirectory(path))) {
                            new VectorDrawableLoader().setImageVectorFromFile(binding.icon, path);
                        } else {
                            binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                        }
                    } catch (Exception ignored) {
                        binding.icon.setImageResource(R.drawable.ic_mtrl_file);
                    }
                }
            }

            binding.more.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(ManageResourceActivity.this, v);
                popupMenu.inflate(R.menu.popup_menu_double);
                if (node.isFolder) {
                    popupMenu.getMenu().getItem(0).setVisible(false);
                    popupMenu.getMenu().getItem(1).setVisible(false);
                }
                popupMenu.setOnMenuItemClickListener(item -> {
                    switch (item.getTitle().toString()) {
                        case "Edit with..." -> {
                            if (path.endsWith("xml")) {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setDataAndType(Uri.fromFile(new File(path)), "text/plain");
                                startActivity(intent);
                            } else {
                                SketchwareUtil.toast("Only XML files can be edited");
                            }
                        }
                        case "Edit" -> goEdit2(path);
                        case "Delete" -> showDeleteDialog(path);
                        case "Rename" -> showRenameDialog(path);
                        default -> {
                            return false;
                        }
                    }
                    return true;
                });
                popupMenu.show();
            });

            binding.getRoot().setOnLongClickListener(v -> {
                if (node.isFolder) {
                    PopupMenu popupMenu = new PopupMenu(ManageResourceActivity.this, binding.more);
                    popupMenu.getMenu().add("Delete");
                    popupMenu.setOnMenuItemClickListener(item -> {
                        showDeleteDialog(path);
                        return true;
                    });
                    popupMenu.show();
                } else {
                    binding.more.performClick();
                }
                return true;
            });
            
            binding.getRoot().setOnClickListener(view -> {
                if (node.isFolder) {
                    temp = path;
                    if (isTreeViewEnabled) {
                        node.isExpanded = !node.isExpanded;
                        refreshFlatList();
                    } else {
                        refresh();
                    }
                    return;
                }
                goEdit(path);
            });
        }

        public String getItem(int position) {
            return flatNodesList.get(position).path;
        }

        @Override
        public int getItemCount() {
            return flatNodesList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ManageJavaItemHsBinding binding;

            public ViewHolder(ManageJavaItemHsBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
