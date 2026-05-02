package mod.hey.studios.activity.managers.java;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
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

public class ManageJavaActivity extends BaseAppCompatActivity {

    private static final String PACKAGE_DECL_REGEX = "package (.*?);?\\n";

    private static final String ACTIVITY_TEMPLATE = """
            package %s;
            
            import android.app.Activity;
            import android.os.Bundle;
            
            public class %s extends Activity {
            
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                }
            }
            """;

    private static final String CLASS_TEMPLATE = """
            package %s;
            
            public class %s {
               \s
            }
            """;

    private static final String KT_ACTIVITY_TEMPLATE = """
            package %s
            
            import android.app.Activity
            import android.os.Bundle
            
            class %s : Activity() {
            
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                }
            }
            """;

    private static final String KT_CLASS_TEMPLATE = """
            package %s
            
            class %s {
               \s
            }
            """;

    ManageFileBinding binding;
    private String current_path;
    private FilePathUtil fpu;
    private FileResConfig frc;
    private String sc_id;
    private FilesAdapter filesAdapter;

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
        frc = new FileResConfig(sc_id);
        fpu = new FilePathUtil();
        current_path = Uri.parse(fpu.getPathJava(sc_id)).getPath();
        refresh();
    }

    @Override
    public void onBackPressed() {
        if (isTreeViewEnabled) {
            super.onBackPressed();
        } else {
            if (Objects.equals(Uri.parse(current_path).getPath(), Uri.parse(fpu.getPathJava(sc_id)).getPath())) {
                super.onBackPressed();
            } else {
                current_path = current_path.substring(0, current_path.lastIndexOf("/"));
                refresh();
            }
        }
    }

    private void setupUI() {
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.topAppBar.setTitle("Java/Kotlin Manager");
        binding.showOptionsButton.setOnClickListener(view -> hideShowOptionsButton(false));
        binding.closeButton.setOnClickListener(view -> hideShowOptionsButton(true));
        binding.createNewButton.setOnClickListener(v -> {
            showCreateDialog(isTreeViewEnabled ? fpu.getPathJava(sc_id) : current_path);
            hideShowOptionsButton(true);
        });
        binding.importNewButton.setOnClickListener(v -> {
            showImportDialog(isTreeViewEnabled ? fpu.getPathJava(sc_id) : current_path);
            hideShowOptionsButton(true);
        });
    }

    private void hideShowOptionsButton(boolean isHide) {
        binding.optionsLayout.animate().translationY(isHide ? 300 : 0).alpha(isHide ? 0 : 1).setInterpolator(new OvershootInterpolator());
        binding.showOptionsButton.animate().translationY(isHide ? 0 : 300).alpha(isHide ? 1 : 0).setInterpolator(new OvershootInterpolator());
    }

    private String getPkgNameForPath(String targetPath) {
        String pkgName = getIntent().getStringExtra("pkgName");
        try {
            String trimmedPath = Helper.trimPath(fpu.getPathJava(sc_id));
            String substring = targetPath.substring(targetPath.indexOf(trimmedPath) + trimmedPath.length());
            if (substring.endsWith("/")) substring = substring.substring(0, substring.length() - 1);
            if (substring.startsWith("/")) substring = substring.substring(1);
            String replace = substring.replace("/", ".");
            return replace.isEmpty() ? pkgName : pkgName + "." + replace;
        } catch (Exception e) {
            return pkgName;
        }
    }

    private void showCreateDialog(String targetPath) {
        DialogCreateNewFileLayoutBinding dialogBinding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;

        var dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setTitle("Create new")
                .setMessage("File will be created in the selected directory.")
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            inputText.requestFocus();

            Button positiveButton = ((androidx.appcompat.app.AlertDialog) dialogInterface).getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                if (Helper.getText(inputText).isEmpty()) {
                    SketchwareUtil.toastError("Invalid file name");
                    return;
                }

                String name = Helper.getText(inputText);
                String packageName = getPkgNameForPath(targetPath);
                String extension;
                String newFileContent;
                int checkedChipId = dialogBinding.chipGroupTypes.getCheckedChipId();
                if (checkedChipId == R.id.chip_java_class) {
                    newFileContent = String.format(CLASS_TEMPLATE, packageName, name);
                    extension = ".java";
                } else if (checkedChipId == R.id.chip_java_activity) {
                    newFileContent = String.format(ACTIVITY_TEMPLATE, packageName, name);
                    extension = ".java";
                } else if (checkedChipId == R.id.chip_kotlin_class) {
                    newFileContent = String.format(KT_CLASS_TEMPLATE, packageName, name);
                    extension = ".kt";
                } else if (checkedChipId == R.id.chip_kotlin_activity) {
                    newFileContent = String.format(KT_ACTIVITY_TEMPLATE, packageName, name);
                    extension = ".kt";
                } else if (checkedChipId == R.id.chip_folder) {
                    FileUtil.makeDir(new File(targetPath, name).getAbsolutePath());
                    forceRefreshTree();
                    SketchwareUtil.toast("Folder was created successfully");
                    dialog.dismiss();
                    return;
                } else {
                    SketchwareUtil.toast("Select a file type");
                    return;
                }

                FileUtil.writeFile(new File(targetPath, name + extension).getAbsolutePath(), newFileContent);
                forceRefreshTree();
                SketchwareUtil.toast("File was created successfully");
                dialog.dismiss();
            });

            dialogBinding.chipFolder.setVisibility(View.VISIBLE);
            dialogBinding.chipJavaClass.setVisibility(View.VISIBLE);
            dialogBinding.chipJavaActivity.setVisibility(View.VISIBLE);
            dialogBinding.chipKotlinClass.setVisibility(View.VISIBLE);
            dialogBinding.chipKotlinActivity.setVisibility(View.VISIBLE);
        });

        dialog.show();
    }

    private void showImportDialog(String targetPath) {
        FilePickerOptions options = new FilePickerOptions();
        options.setMultipleSelection(true);
        options.setExtensions(new String[]{"java", "kt"});
        options.setTitle("Select Java/Kotlin file(s)");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends File> files) {
                for (File file : files) {
                    String fileContent = FileUtil.readFile(file.getAbsolutePath());
                    if (fileContent.contains("package ")) {
                        fileContent = fileContent.replaceFirst(PACKAGE_DECL_REGEX, "package " + getPkgNameForPath(targetPath) + (file.getName().endsWith(".java") ? ";" : "") + "\n");
                    }
                    FileUtil.writeFile(new File(targetPath, file.getName()).getAbsolutePath(), fileContent);
                }
                forceRefreshTree();
            }
        };

        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "filePicker");
    }

    private void showRenameDialog(int position) {
        DialogInputLayoutBinding dialogBinding = DialogInputLayoutBinding.inflate(getLayoutInflater());
        var inputText = dialogBinding.inputText;
        var renameOccurrencesCheckBox = dialogBinding.renameOccurrencesCheckBox;

        var dialog = new MaterialAlertDialogBuilder(this).setTitle("Rename " + filesAdapter.getFileName(position))
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Rename", (dialogInterface, i) -> {
            if (!Helper.getText(inputText).isEmpty()) {
                if (!filesAdapter.isFolder(position)) {
                    if (frc.getJavaManifestList().contains(filesAdapter.getFullName(position))) {
                        frc.getJavaManifestList().remove(filesAdapter.getFullName(position));
                        FileUtil.writeFile(fpu.getManifestJava(sc_id), new Gson().toJson(frc.listJavaManifest));
                        SketchwareUtil.toast("NOTE: Removed Activity from manifest");
                    }
                    if (renameOccurrencesCheckBox.isChecked()) {
                        String fileContent = FileUtil.readFile(filesAdapter.getItem(position));
                        FileUtil.writeFile(filesAdapter.getItem(position), fileContent.replaceAll(filesAdapter.getFileNameWoExt(position), FileUtil.getFileNameNoExtension(Helper.getText(inputText))));
                    }
                }
                FileUtil.renameFile(filesAdapter.getItem(position), new File(new File(filesAdapter.getItem(position)).getParent(), Helper.getText(inputText)).getAbsolutePath());
                forceRefreshTree();
                SketchwareUtil.toast("Renamed successfully");
            }
            dialogInterface.dismiss();
        }).create();

        inputText.setText(filesAdapter.getFileName(position));
        boolean isFolder = filesAdapter.isFolder(position);

        if (!isFolder) {
            renameOccurrencesCheckBox.setVisibility(View.VISIBLE);
            renameOccurrencesCheckBox.setText("Rename occurrences of \"" + filesAdapter.getFileNameWoExt(position) + "\" in file");
        }
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        inputText.requestFocus();
    }

    private void showDeleteDialog(int position) {
        boolean isInManifest = frc.getJavaManifestList().contains(filesAdapter.getFullName(position));
        new MaterialAlertDialogBuilder(this).setTitle("Delete " + filesAdapter.getFileName(position) + "?")
                .setMessage("Are you sure you want to delete this " + (filesAdapter.isFolder(position) ? "folder" : "file") + "? " + (isInManifest ? "This will also remove it from AndroidManifest. " : "") + "This action cannot be undone.")
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> {
            if (!filesAdapter.isFolder(position) && isInManifest) {
                frc.getJavaManifestList().remove(filesAdapter.getFullName(position));
                FileUtil.writeFile(fpu.getManifestJava(sc_id), new Gson().toJson(frc.listJavaManifest));
            }
            FileUtil.deleteFile(filesAdapter.getItem(position));
            forceRefreshTree();
            SketchwareUtil.toast("Deleted successfully");
        }).setNegativeButton(R.string.common_word_cancel, null).create().show();
    }

    private void forceRefreshTree() {
        rootNodes = null;
        refresh();
    }

    private void sortTreePaths(ArrayList<String> paths) {
        paths.sort((p1, p2) -> {
            boolean isDir1 = new File(p1).isDirectory();
            boolean isDir2 = new File(p2).isDirectory();
            if (isDir1 && !isDir2) return -1;
            if (!isDir1 && isDir2) return 1;
            return String.CASE_INSENSITIVE_ORDER.compare(new File(p1).getName(), new File(p2).getName());
        });
    }

    private void refresh() {
        if (!FileUtil.isExistFile(fpu.getPathJava(sc_id))) {
            FileUtil.makeDir(fpu.getPathJava(sc_id));
        }
        if (!FileUtil.isExistFile(fpu.getManifestJava(sc_id))) {
            FileUtil.writeFile(fpu.getManifestJava(sc_id), "");
        }

        isTreeViewEnabled = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_TREE_VIEW)
                            && ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_JAVA_TREE_VIEW);

        if (isTreeViewEnabled) {
            if (rootNodes == null) {
                rootNodes = new ArrayList<>();
                ArrayList<String> paths = new ArrayList<>();
                FileUtil.listDir(fpu.getPathJava(sc_id), paths);
                sortTreePaths(paths);
                for (String p : paths) rootNodes.add(new FileNode(p, 0));
            }
            refreshFlatList();
        } else {
            ArrayList<String> currentTree = new ArrayList<>();
            FileUtil.listDir(current_path, currentTree);
            sortTreePaths(currentTree);
            
            flatNodesList.clear();
            for (String p : currentTree) {
                flatNodesList.add(new FileNode(p, 0)); 
            }
            
            if (filesAdapter == null) {
                filesAdapter = new FilesAdapter(flatNodesList);
                binding.filesListRecyclerView.setAdapter(filesAdapter);
            } else {
                filesAdapter.notifyDataSetChanged();
            }
            binding.noContentLayout.setVisibility(flatNodesList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshFlatList() {
        flatNodesList.clear();
        for (FileNode node : rootNodes) addNodeToFlatList(node);
        
        if (filesAdapter == null) {
            filesAdapter = new FilesAdapter(flatNodesList);
            binding.filesListRecyclerView.setAdapter(filesAdapter);
        } else {
            filesAdapter.notifyDataSetChanged();
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
                sortTreePaths(paths);
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

    public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.ViewHolder> {
        private final ArrayList<FileNode> nodes;

        public FilesAdapter(ArrayList<FileNode> nodes) {
            this.nodes = nodes;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ManageJavaItemHsBinding binding = ManageJavaItemHsBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileNode node = nodes.get(position);
            String fileName = node.name;

            holder.binding.title.setText(fileName);

            if (isTreeViewEnabled) {
                int indentPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, node.depth * 24, getResources().getDisplayMetrics());
                ViewGroup.MarginLayoutParams iconParams = (ViewGroup.MarginLayoutParams) holder.binding.icon.getLayoutParams();
                iconParams.setMarginStart(indentPx);
                holder.binding.icon.setLayoutParams(iconParams);
                holder.binding.getRoot().setPadding(0, 0, 0, 0);

                int iconPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
                holder.binding.title.setCompoundDrawablePadding(iconPadding);
                
                holder.binding.more.setVisibility(View.GONE);

                if (node.isFolder) {
                    holder.binding.title.setTypeface(null, Typeface.BOLD);
                    holder.binding.icon.setVisibility(View.VISIBLE);
                    holder.binding.icon.setImageResource(node.isExpanded ? R.drawable.ic_mtrl_arrow_down : R.drawable.ic_mtrl_chevron_right_24);
                    holder.binding.icon.setColorFilter(ThemeUtils.getColor(ManageJavaActivity.this, R.attr.colorOnSurfaceVariant)); 
                    holder.binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_mtrl_folder, 0, 0, 0);
                } else {
                    holder.binding.title.setTypeface(null, Typeface.NORMAL);
                    holder.binding.icon.setVisibility(View.INVISIBLE);
                    holder.binding.icon.clearColorFilter();
                    int fileIcon = fileName.endsWith(".kt") ? R.drawable.ic_mtrl_kotlin : R.drawable.ic_mtrl_java;
                    holder.binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(fileIcon, 0, 0, 0);
                }
            } else {
                ViewGroup.MarginLayoutParams iconParams = (ViewGroup.MarginLayoutParams) holder.binding.icon.getLayoutParams();
                iconParams.setMarginStart(0);
                holder.binding.icon.setLayoutParams(iconParams);
                holder.binding.getRoot().setPadding(0, 0, 0, 0);
                
                holder.binding.icon.setVisibility(View.VISIBLE);
                holder.binding.icon.clearColorFilter();
                holder.binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                holder.binding.title.setTypeface(null, Typeface.NORMAL);

                if (node.isFolder) {
                    holder.binding.icon.setImageResource(R.drawable.ic_mtrl_folder);
                } else if (fileName.endsWith(".kt")) {
                    holder.binding.icon.setImageResource(R.drawable.ic_mtrl_kotlin);
                } else {
                    holder.binding.icon.setImageResource(R.drawable.ic_mtrl_java);
                }
            }

            holder.binding.getRoot().setOnClickListener(view -> {
                if (node.isFolder) {
                    current_path = node.path; 
                    if (isTreeViewEnabled) {
                        node.isExpanded = !node.isExpanded;
                        refreshFlatList();
                    } else {
                        refresh();
                    }
                    return;
                }
                goEditFile(position);
            });

            holder.binding.getRoot().setOnLongClickListener(view -> {
                if (isTreeViewEnabled) {
                    treeContextMenu(view, position, node);
                } else {
                    current_path = node.isFolder ? node.path : new File(node.path).getParent();
                    itemContextMenu(view, position, Gravity.CENTER);
                }
                return true;
            });

            Helper.applyRipple(ManageJavaActivity.this, holder.binding.more);
            holder.binding.more.setOnClickListener(v -> itemContextMenu(v, position, Gravity.RIGHT));
        }

        @Override
        public int getItemCount() {
            return nodes.size();
        }

        public String getItem(int position) {
            return nodes.get(position).path;
        }

        public String getFullName(int position) {
            String readFile = FileUtil.readFile(getItem(position));
            if (!readFile.contains("package ")) return getFileNameWoExt(position);
            Matcher m = Pattern.compile(PACKAGE_DECL_REGEX).matcher(readFile);
            if (m.find()) return m.group(1) + "." + getFileNameWoExt(position);
            return getFileNameWoExt(position);
        }

        public String getFileName(int position) {
            return nodes.get(position).name;
        }

        public String getFileNameWoExt(int position) {
            return FileUtil.getFileNameNoExtension(getItem(position));
        }

        public boolean isFolder(int position) {
            return nodes.get(position).isFolder;
        }

        public void goEditFile(int position) {
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), SrcCodeEditor.class);
            intent.putExtra("java", "");
            intent.putExtra("title", getFileName(position));
            intent.putExtra("content", getItem(position));
            startActivity(intent);
        }

        private void treeContextMenu(View v, int position, FileNode node) {
            PopupMenu popupMenu = new PopupMenu(ManageJavaActivity.this, v, Gravity.CENTER);
            Menu popupMenuMenu = popupMenu.getMenu();

            boolean isActivityInManifest = frc.getJavaManifestList().contains(getFullName(position));
            boolean isServiceInManifest = frc.getServiceManifestList().contains(getFullName(position));

            if (node.isFolder) {
                popupMenuMenu.add("Create inside");
                popupMenuMenu.add("Import here");
            } else {
                if (isActivityInManifest) popupMenuMenu.add("Remove Activity from manifest");
                else if (!isServiceInManifest) popupMenuMenu.add("Add as Activity to manifest");

                if (isServiceInManifest) popupMenuMenu.add("Remove Service from manifest");
                else if (!isActivityInManifest) popupMenuMenu.add("Add as Service to manifest");

                popupMenuMenu.add("Edit");
                popupMenuMenu.add("Edit with...");
            }

            popupMenuMenu.add("Rename");
            popupMenuMenu.add("Delete");

            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getTitle().toString()) {
                    case "Add as Activity to manifest" -> {
                        frc.getJavaManifestList().add(getFullName(position));
                        FileUtil.writeFile(fpu.getManifestJava(sc_id), new Gson().toJson(frc.listJavaManifest));
                        SketchwareUtil.toast("Successfully added " + getFileNameWoExt(position) + " as Activity to AndroidManifest");
                    }
                    case "Remove Activity from manifest" -> {
                        if (frc.getJavaManifestList().remove(getFullName(position))) {
                            FileUtil.writeFile(fpu.getManifestJava(sc_id), new Gson().toJson(frc.listJavaManifest));
                            SketchwareUtil.toast("Successfully removed Activity " + getFileNameWoExt(position) + " from AndroidManifest");
                        } else SketchwareUtil.toast("Activity was not defined in AndroidManifest.");
                    }
                    case "Add as Service to manifest" -> {
                        frc.getServiceManifestList().add(getFullName(position));
                        FileUtil.writeFile(fpu.getManifestService(sc_id), new Gson().toJson(frc.listServiceManifest));
                        SketchwareUtil.toast("Successfully added " + getFileNameWoExt(position) + " as Service to AndroidManifest");
                    }
                    case "Remove Service from manifest" -> {
                        if (frc.getServiceManifestList().remove(getFullName(position))) {
                            FileUtil.writeFile(fpu.getManifestService(sc_id), new Gson().toJson(frc.listServiceManifest));
                            SketchwareUtil.toast("Successfully removed Service " + getFileNameWoExt(position) + " from AndroidManifest");
                        } else SketchwareUtil.toast("Service was not defined in AndroidManifest.");
                    }
                    case "Create inside" -> showCreateDialog(node.path);
                    case "Import here" -> showImportDialog(node.path);
                    case "Edit" -> goEditFile(position);
                    case "Edit with..." -> {
                        Intent launchIntent = new Intent(Intent.ACTION_VIEW);
                        launchIntent.setDataAndType(Uri.fromFile(new File(getItem(position))), "text/plain");
                        startActivity(launchIntent);
                    }
                    case "Rename" -> showRenameDialog(position);
                    case "Delete" -> showDeleteDialog(position);
                    default -> { return false; }
                }
                return true;
            });
            popupMenu.show();
        }

        private void itemContextMenu(View v, int position, int gravity) {
            PopupMenu popupMenu = new PopupMenu(ManageJavaActivity.this, v, gravity);
            popupMenu.inflate(R.menu.popup_menu_double);

            Menu popupMenuMenu = popupMenu.getMenu();
            popupMenuMenu.clear();

            boolean isActivityInManifest = frc.getJavaManifestList().contains(getFullName(position));
            boolean isServiceInManifest = frc.getServiceManifestList().contains(getFullName(position));

            if (!isFolder(position)) {
                if (isActivityInManifest) popupMenuMenu.add("Remove Activity from manifest");
                else if (!isServiceInManifest) popupMenuMenu.add("Add as Activity to manifest");

                if (isServiceInManifest) popupMenuMenu.add("Remove Service from manifest");
                else if (!isActivityInManifest) popupMenuMenu.add("Add as Service to manifest");

                popupMenuMenu.add("Edit");
                popupMenuMenu.add("Edit with...");
            }

            popupMenuMenu.add("Rename");
            popupMenuMenu.add("Delete");

            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getTitle().toString()) {
                    case "Add as Activity to manifest" -> {
                        frc.getJavaManifestList().add(getFullName(position));
                        FileUtil.writeFile(fpu.getManifestJava(sc_id), new Gson().toJson(frc.listJavaManifest));
                        SketchwareUtil.toast("Successfully added " + getFileNameWoExt(position) + " as Activity to AndroidManifest");
                    }
                    case "Remove Activity from manifest" -> {
                        if (frc.getJavaManifestList().remove(getFullName(position))) {
                            FileUtil.writeFile(fpu.getManifestJava(sc_id), new Gson().toJson(frc.listJavaManifest));
                            SketchwareUtil.toast("Successfully removed Activity " + getFileNameWoExt(position) + " from AndroidManifest");
                        } else SketchwareUtil.toast("Activity was not defined in AndroidManifest.");
                    }
                    case "Add as Service to manifest" -> {
                        frc.getServiceManifestList().add(getFullName(position));
                        FileUtil.writeFile(fpu.getManifestService(sc_id), new Gson().toJson(frc.listServiceManifest));
                        SketchwareUtil.toast("Successfully added " + getFileNameWoExt(position) + " as Service to AndroidManifest");
                    }
                    case "Remove Service from manifest" -> {
                        if (frc.getServiceManifestList().remove(getFullName(position))) {
                            FileUtil.writeFile(fpu.getManifestService(sc_id), new Gson().toJson(frc.listServiceManifest));
                            SketchwareUtil.toast("Successfully removed Service " + getFileNameWoExt(position) + " from AndroidManifest");
                        } else SketchwareUtil.toast("Service was not defined in AndroidManifest.");
                    }
                    case "Edit" -> goEditFile(position);
                    case "Edit with..." -> {
                        Intent launchIntent = new Intent(Intent.ACTION_VIEW);
                        launchIntent.setDataAndType(Uri.fromFile(new File(getItem(position))), "text/plain");
                        startActivity(launchIntent);
                    }
                    case "Rename" -> showRenameDialog(position);
                    case "Delete" -> showDeleteDialog(position);
                    default -> { return false; }
                }
                return true;
            });
            popupMenu.show();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ManageJavaItemHsBinding binding;
            public ViewHolder(ManageJavaItemHsBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
