package mod.hilal.saif.activities.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.besome.sketch.editor.manage.library.LibraryCategoryView;
import com.besome.sketch.editor.manage.library.LibraryItemView;
import com.besome.sketch.help.SystemSettingActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.api.services.drive.DriveScopes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import a.a.a.lC;
import dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity;
import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import dev.pranav.filepicker.SelectionMode;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.project.backup.BackupRestoreManager;
import mod.sketchlibx.project.backup.AutoBackupWorker;
import mod.sketchlibx.project.backup.CloudBackupManager;
import mod.sketchlibx.project.backup.CloudBackupFactory;
import mod.hey.studios.util.Helper;
import mod.khaled.logcat.LogReaderActivity;
import pro.sketchware.R;
import pro.sketchware.activities.editor.component.ManageCustomComponentActivity;
import pro.sketchware.activities.settings.SettingsActivity;
import pro.sketchware.databinding.ActivityAppSettingsBinding;
import pro.sketchware.databinding.DialogSelectApkToSignBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class AppSettings extends BaseAppCompatActivity {

    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        var binding = ActivityAppSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        {
            View view = binding.appBarLayout;
            int left = view.getPaddingLeft();
            int top = view.getPaddingTop();
            int right = view.getPaddingRight();
            int bottom = view.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(view, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top + insets.top, right + insets.right, bottom + insets.bottom);
                return i;
            });
        }

        {
            View view = binding.contentScroll;
            int left = view.getPaddingLeft();
            int top = view.getPaddingTop();
            int right = view.getPaddingRight();
            int bottom = view.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(view, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(left, top, right, bottom + insets.bottom);
                return i;
            });
        }

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        googleSignInLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    SketchwareUtil.toast("Signed in as " + account.getEmail());
                    openCloudBackupDialog();
                } catch (ApiException e) {
                    showErrorDialog("Sign-In Error", "Google Sign-In failed with code: " + e.getStatusCode() + "\n" + e.getMessage());
                }
            }
        });

        setupPreferences(binding.content);
    }

    private void setupPreferences(ViewGroup content) {
        var preferences = new ArrayList<LibraryCategoryView>();

        LibraryCategoryView managersCategory = new LibraryCategoryView(this);
        managersCategory.setTitle("Managers");
        preferences.add(managersCategory);

        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_block, "Block manager", "Manage your own blocks to use in Logic Editor", new ActivityLauncher(new Intent(getApplicationContext(), BlocksManager.class))), true);
        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_pull_down, "Block selector menu manager", "Manage your own block selector menus", openSettingsActivity(SettingsActivity.BLOCK_SELECTOR_MANAGER_FRAGMENT)), true);
        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_component, "Component manager", "Manage your own components", new ActivityLauncher(new Intent(getApplicationContext(), ManageCustomComponentActivity.class))), true);
        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_list, "Event manager", "Manage your own events", openSettingsActivity(SettingsActivity.EVENTS_MANAGER_FRAGMENT)), true);
        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_box, "Local library manager", "Manage and download local libraries", new ActivityLauncher(new Intent(getApplicationContext(), ManageLocalLibraryActivity.class), new Pair<>("sc_id", "system"))), true);
        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_article, Helper.getResString(R.string.design_drawer_menu_title_logcat_reader), Helper.getResString(R.string.design_drawer_menu_subtitle_logcat_reader), new ActivityLauncher(new Intent(getApplicationContext(), LogReaderActivity.class))), false);

        LibraryCategoryView cloudCategory = new LibraryCategoryView(this);
        cloudCategory.setTitle("Cloud & Sync");
        preferences.add(cloudCategory);
        cloudCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_sync, "Cloud Backup", "Backup and restore projects securely to Google Drive", v -> openCloudBackupDialog()), false);

        LibraryCategoryView generalCategory = new LibraryCategoryView(this);
        generalCategory.setTitle("General");
        preferences.add(generalCategory);

        generalCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_settings_applications, "App settings", "Change general app settings", new ActivityLauncher(new Intent(getApplicationContext(), ConfigActivity.class))), true);
        generalCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_palette, Helper.getResString(R.string.settings_appearance), Helper.getResString(R.string.settings_appearance_description), openSettingsActivity(SettingsActivity.SETTINGS_APPEARANCE_FRAGMENT)), true);
        generalCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_folder, "Open working directory", "Open Sketchware Pro's directory and edit files in it", v -> openWorkingDirectory()), true);
        generalCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_apk_document, "Sign an APK file with testkey", "Sign an already existing APK file with testkey and signature schemes up to V4", v -> signApkFileDialog()), true);
        generalCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_settings, Helper.getResString(R.string.main_drawer_title_system_settings), "Auto-save and vibrations", new ActivityLauncher(new Intent(getApplicationContext(), SystemSettingActivity.class))), false);

        preferences.forEach(content::addView);
    }

    private void showErrorDialog(String title, String errorMessage) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(errorMessage)
            .setPositiveButton("Copy Error", (dialog, which) -> {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Error Log", errorMessage);
                    if (clipboard != null) clipboard.setPrimaryClip(clip);
                    SketchwareUtil.toast("Copied to clipboard!");
                } catch (Exception ignored) {
                    SketchwareUtil.toastError("Failed to copy");
                }
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void openCloudBackupDialog() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        
        if (account == null) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Cloud Backup")
                .setMessage("Sign in with Google to backup your projects to your personal Google Drive. Your backups are stored securely in a hidden folder.")
                .setPositiveButton("Sign In", (dialog, which) -> {
                    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
                            .build();
                    mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
                    googleSignInLauncher.launch(mGoogleSignInClient.getSignInIntent());
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
        } else {
            String[] options = {
                    "Manual Backup (Select Projects)", 
                    "Restore Projects from Cloud", 
                    "Configure Auto-Backup", 
                    "Sign Out (" + account.getEmail() + ")"
            };
            
            new MaterialAlertDialogBuilder(this)
                .setTitle("Cloud Backup Settings")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0 -> triggerSelectiveCloudBackup(account);
                        case 1 -> triggerSelectiveCloudRestore(account);
                        case 2 -> configureAutoBackupProjects();
                        case 3 -> GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                                .addOnCompleteListener(task -> SketchwareUtil.toast("Signed out successfully"));
                    }
                })
                .show();
        }
    }

    private void triggerSelectiveCloudBackup(GoogleSignInAccount account) {
        ArrayList<HashMap<String, Object>> projects = lC.a();
        if (projects == null || projects.isEmpty()) {
            SketchwareUtil.toast("No local projects found.");
            return;
        }

        String[] projectNames = new String[projects.size()];
        boolean[] checkedItems = new boolean[projects.size()];
        
        for (int i = 0; i < projects.size(); i++) {
            projectNames[i] = (String) projects.get(i).get("my_app_name");
            checkedItems[i] = true;
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("Select Projects to Backup")
            .setMultiChoiceItems(projectNames, checkedItems, (d, which, isChecked) -> checkedItems[which] = isChecked)
            .setPositiveButton("Start Backup", (d, which) -> {
                ArrayList<HashMap<String, Object>> selectedProjects = new ArrayList<>();
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) selectedProjects.add(projects.get(i));
                }
                if (selectedProjects.isEmpty()) {
                    SketchwareUtil.toast("No projects selected!");
                    return;
                }
                processManualBackupSequence(account, selectedProjects);
            })
            .setNeutralButton("Deselect All", null)
            .setNegativeButton(R.string.common_word_cancel, null)
            .create();

        dialog.setOnShowListener(d -> {
            Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            neutralButton.setOnClickListener(v -> {
                boolean isAnyChecked = false;
                for (boolean isChecked : checkedItems) {
                    if (isChecked) { isAnyChecked = true; break; }
                }
                boolean newState = !isAnyChecked;
                for (int i = 0; i < checkedItems.length; i++) {
                    checkedItems[i] = newState;
                    dialog.getListView().setItemChecked(i, newState);
                }
                neutralButton.setText(newState ? "Deselect All" : "Select All");
            });
        });

        dialog.show();
    }

    private void processManualBackupSequence(GoogleSignInAccount account, ArrayList<HashMap<String, Object>> projectsToBackup) {
        AlertDialog progress = new MaterialAlertDialogBuilder(this)
                .setTitle("Uploading Backups...")
                .setView(new ProgressBar(this))
                .setCancelable(false)
                .show();

        CloudBackupManager cloudManager = new CloudBackupManager(this, account);
        
        new Thread(() -> {
            int successCount = 0;
            for (HashMap<String, Object> proj : projectsToBackup) {
                String scId = (String) proj.get("sc_id");
                String projectName = (String) proj.get("my_app_name");

                CloudBackupFactory factory = new CloudBackupFactory(scId);
                factory.backup(null, projectName);
                
                java.io.File swbFile = factory.getOutFile();
                if (swbFile != null && swbFile.exists()) {
                    final Object lock = new Object();
                    cloudManager.uploadBackupToCloud(swbFile, projectName, new CloudBackupManager.BackupCallback() {
                        @Override public void onSuccess(String msg) { synchronized (lock) { lock.notify(); } }
                        @Override public void onError(String err) { synchronized (lock) { lock.notify(); } }
                    });
                    try { synchronized (lock) { lock.wait(); } successCount++; } catch (Exception ignored) {}
                }
            }
            FileUtil.deleteFile(CloudBackupFactory.getCloudBackupDir()); // Clean temp Cloud Directory
            
            int finalSuccessCount = successCount;
            runOnUiThread(() -> {
                progress.dismiss();
                SketchwareUtil.toast("Successfully backed up " + finalSuccessCount + "/" + projectsToBackup.size() + " projects!");
            });
        }).start();
    }

    private void triggerSelectiveCloudRestore(GoogleSignInAccount account) {
        AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this)
            .setTitle("Fetching cloud backups...")
            .setView(new ProgressBar(this))
            .setCancelable(false).show();

        CloudBackupManager cloudManager = new CloudBackupManager(this, account);
        cloudManager.getCloudBackupsList(new CloudBackupManager.FileListCallback() {
            @Override
            public void onSuccess(List<com.google.api.services.drive.model.File> files) {
                loadingDialog.dismiss();
                if (files == null || files.isEmpty()) {
                    SketchwareUtil.toast("No backups found in Google Drive.");
                    return;
                }

                String[] fileNames = new String[files.size()];
                boolean[] checkedItems = new boolean[files.size()];
                for (int i = 0; i < files.size(); i++) {
                    String pName = files.get(i).getProperties() != null ? files.get(i).getProperties().get("projectName") : null;
                    fileNames[i] = pName != null ? pName + " (" + files.get(i).getName() + ")" : files.get(i).getName();
                    checkedItems[i] = true;
                }

                AlertDialog restoreDialog = new MaterialAlertDialogBuilder(AppSettings.this)
                    .setTitle("Select Projects to Restore")
                    .setMultiChoiceItems(fileNames, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                    .setPositiveButton("Restore", (dialog, which) -> {
                        ArrayList<com.google.api.services.drive.model.File> toRestore = new ArrayList<>();
                        for (int i = 0; i < checkedItems.length; i++) if (checkedItems[i]) toRestore.add(files.get(i));
                        if (toRestore.isEmpty()) return;
                        processRestoreSequence(cloudManager, toRestore);
                    })
                    .setNeutralButton("Deselect All", null)
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .create();
                    
                restoreDialog.setOnShowListener(d -> {
                    Button neutralButton = restoreDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                    neutralButton.setOnClickListener(v -> {
                        boolean isAnyChecked = false;
                        for (boolean isChecked : checkedItems) {
                            if (isChecked) { isAnyChecked = true; break; }
                        }
                        boolean newState = !isAnyChecked;
                        for (int i = 0; i < checkedItems.length; i++) {
                            checkedItems[i] = newState;
                            restoreDialog.getListView().setItemChecked(i, newState);
                        }
                        neutralButton.setText(newState ? "Deselect All" : "Select All");
                    });
                });
                
                restoreDialog.show();
            }

            @Override
            public void onError(String error) {
                loadingDialog.dismiss();
                showErrorDialog("Fetch Error", error);
            }
        });
    }

    private void processRestoreSequence(CloudBackupManager cloudManager, List<com.google.api.services.drive.model.File> filesToRestore) {
        AlertDialog progress = new MaterialAlertDialogBuilder(this)
                .setTitle("Downloading & Restoring...")
                .setView(new ProgressBar(this))
                .setCancelable(false).show();

        String downloadPath = new java.io.File(Environment.getExternalStorageDirectory(), ".sketchware/.cloudbackup/temp_restore").getAbsolutePath();

        new Thread(() -> {
            for (com.google.api.services.drive.model.File driveFile : filesToRestore) {
                final Object lock = new Object();
                cloudManager.downloadBackupFromCloud(driveFile.getId(), driveFile.getName(), downloadPath, new CloudBackupManager.BackupCallback() {
                    @Override
                    public void onSuccess(String message) {
                        String fullPath = new java.io.File(downloadPath, driveFile.getName()).getAbsolutePath();
                        runOnUiThread(() -> new BackupRestoreManager(AppSettings.this, null).doRestore(fullPath, true));
                        synchronized (lock) { lock.notify(); }
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> showErrorDialog("Restore Failed", "File: " + driveFile.getName() + "\n" + error));
                        synchronized (lock) { lock.notify(); }
                    }
                });
                try { synchronized (lock) { lock.wait(); } } catch (Exception ignored) {}
            }
            FileUtil.deleteFile(downloadPath); // Clean temp restore path
            runOnUiThread(progress::dismiss);
        }).start();
    }

    private void configureAutoBackupProjects() {
        ArrayList<HashMap<String, Object>> projects = lC.a();
        if (projects == null || projects.isEmpty()) {
            SketchwareUtil.toast("No local projects found.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("cloud_backup_prefs", MODE_PRIVATE);
        Set<String> selectedScIds = prefs.getStringSet("auto_backup_sc_ids", new HashSet<>());

        String[] projectNames = new String[projects.size()];
        String[] scIds = new String[projects.size()];
        boolean[] checkedItems = new boolean[projects.size()];

        for (int i = 0; i < projects.size(); i++) {
            scIds[i] = (String) projects.get(i).get("sc_id");
            projectNames[i] = (String) projects.get(i).get("my_app_name");
            checkedItems[i] = selectedScIds.isEmpty() || selectedScIds.contains(scIds[i]);
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Select Projects for Auto-Backup")
            .setMultiChoiceItems(projectNames, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
            .setPositiveButton("Next", (dialog, which) -> {
                Set<String> newSelectedScIds = new HashSet<>();
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) newSelectedScIds.add(scIds[i]);
                }
                prefs.edit().putStringSet("auto_backup_sc_ids", newSelectedScIds).apply();
                openAutoBackupSettings();
            })
            .setNegativeButton(R.string.common_word_cancel, null)
            .show();
    }

    private void openAutoBackupSettings() {
        String[] intervals = {"Off (Manual Only)", "Daily", "Weekly", "Monthly"};
        SharedPreferences prefs = getSharedPreferences("cloud_backup_prefs", MODE_PRIVATE);
        int currentSelection = prefs.getInt("auto_backup_interval", 2);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Auto-Backup Frequency")
            .setSingleChoiceItems(intervals, currentSelection, (dialog, which) -> {
                prefs.edit().putInt("auto_backup_interval", which).apply();
                configureWorkManager(which);
                dialog.dismiss();
                SketchwareUtil.toast("Auto-backup schedule updated to: " + intervals[which]);
            })
            .setNegativeButton(R.string.common_word_cancel, null)
            .show();
    }

    private void configureWorkManager(int intervalType) {
        WorkManager workManager = WorkManager.getInstance(this);
        if (intervalType == 0) {
            workManager.cancelUniqueWork("CloudAutoBackup_Recurring");
        } else {
            long days = switch (intervalType) {
                case 1 -> 1;
                case 2 -> 7;
                case 3 -> 30;
                default -> 7;
            };
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AutoBackupWorker.class, days, TimeUnit.DAYS
            ).build();
            workManager.enqueueUniquePeriodicWork("CloudAutoBackup_Recurring", ExistingPeriodicWorkPolicy.KEEP, request);
        }
    }

    private View.OnClickListener openSettingsActivity(String fragmentTag) {
        return v -> {
            Intent intent = new Intent(v.getContext(), SettingsActivity.class);
            intent.putExtra(SettingsActivity.FRAGMENT_TAG_EXTRA, fragmentTag);
            v.getContext().startActivity(intent);
        };
    }

    private LibraryItemView createPreference(int icon, String title, String desc, View.OnClickListener listener) {
        LibraryItemView preference = new LibraryItemView(this);
        preference.enabled.setVisibility(View.GONE);
        preference.icon.setImageResource(icon);
        preference.title.setText(title);
        preference.description.setText(desc);
        preference.setOnClickListener(listener);
        return preference;
    }

    private void openWorkingDirectory() {
        FilePickerOptions options = new FilePickerOptions();
        options.setSelectionMode(SelectionMode.BOTH);
        options.setMultipleSelection(true);
        options.setTitle("Select an entry to modify");
        options.setInitialDirectory(getFilesDir().getParentFile().getAbsolutePath());

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NotNull List<? extends java.io.File> files) {
                boolean isDirectory = files.get(0).isDirectory();
                if (files.size() > 1 || isDirectory) {
                    new MaterialAlertDialogBuilder(AppSettings.this)
                            .setTitle("Select an action")
                            .setSingleChoiceItems(new String[]{"Delete"}, -1, (actionDialog, which) -> {
                                new MaterialAlertDialogBuilder(AppSettings.this)
                                        .setTitle("Delete " + (isDirectory ? "folder" : "file") + "?")
                                        .setMessage("Are you sure you want to delete this " + (isDirectory ? "folder" : "file") + " permanently? This cannot be undone.")
                                        .setPositiveButton(R.string.common_word_delete, (deleteConfirmationDialog, pressedButton) -> {
                                            for (java.io.File file : files) {
                                                FileUtil.deleteFile(file.getAbsolutePath());
                                                deleteConfirmationDialog.dismiss();
                                            }
                                        })
                                        .setNegativeButton(R.string.common_word_cancel, null)
                                        .show();
                                actionDialog.dismiss();
                            })
                            .show();
                } else {
                    new MaterialAlertDialogBuilder(AppSettings.this)
                            .setTitle("Select an action")
                            .setSingleChoiceItems(new String[]{"Edit", "Delete"}, -1, (actionDialog, which) -> {
                                switch (which) {
                                    case 0 -> {
                                        Intent intent = new Intent(getApplicationContext(), SrcCodeEditor.class);
                                        intent.putExtra("title", Uri.fromFile(files.get(0)).getLastPathSegment());
                                        intent.putExtra("content", files.get(0).getAbsolutePath());
                                        intent.putExtra("xml", "");
                                        startActivity(intent);
                                    }
                                    case 1 -> new MaterialAlertDialogBuilder(AppSettings.this)
                                            .setTitle("Delete file?")
                                            .setMessage("Are you sure you want to delete this file permanently? This cannot be undone.")
                                            .setPositiveButton(R.string.common_word_delete, (deleteDialog, pressedButton) ->
                                                    FileUtil.deleteFile(files.get(0).getAbsolutePath()))
                                            .setNegativeButton(R.string.common_word_cancel, null)
                                            .show();
                                }
                                actionDialog.dismiss();
                            })
                            .show();
                }
            }
        };

        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "file_picker");
    }

    private void signApkFileDialog() {
        boolean[] isAPKSelected = {false};
        MaterialAlertDialogBuilder apkPathDialog = new MaterialAlertDialogBuilder(this);
        apkPathDialog.setTitle("Sign APK with testkey");

        DialogSelectApkToSignBinding binding = DialogSelectApkToSignBinding.inflate(getLayoutInflater());
        View testkey_root = binding.getRoot();
        TextView apk_path_txt = binding.apkPathTxt;

        binding.selectFile.setOnClickListener(v -> {
            FilePickerOptions options = new FilePickerOptions();
            options.setExtensions(new String[]{"apk"});
            FilePickerCallback callback = new FilePickerCallback() {
                @Override
                public void onFileSelected(java.io.File file) {
                    isAPKSelected[0] = true;
                    apk_path_txt.setText(file.getAbsolutePath());
                }
            };
            FilePickerDialogFragment dialog = new FilePickerDialogFragment(options, callback);
            dialog.show(getSupportFragmentManager(), "file_picker");
        });

        apkPathDialog.setPositiveButton("Continue", (v, which) -> {
            if (!isAPKSelected[0]) {
                SketchwareUtil.toast("Please select an APK file to sign", Toast.LENGTH_SHORT);
                shakeView(binding.selectFile);
                return;
            }
            String input_apk_path = Helper.getText(apk_path_txt);
            String output_apk_file_name = Uri.fromFile(new java.io.File(input_apk_path)).getLastPathSegment();
            String output_apk_path = new java.io.File(Environment.getExternalStorageDirectory(),
                    "sketchware/signed_apk/" + output_apk_file_name).getAbsolutePath();

            if (new java.io.File(output_apk_path).exists()) {
                MaterialAlertDialogBuilder confirmOverwrite = new MaterialAlertDialogBuilder(this);
                confirmOverwrite.setIcon(R.drawable.color_save_as_new_96);
                confirmOverwrite.setTitle("File exists");
                confirmOverwrite.setMessage("An APK named " + output_apk_file_name + " already exists at /sketchware/signed_apk/.  Overwrite it?");

                confirmOverwrite.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
                confirmOverwrite.setPositiveButton("Overwrite", (view, which1) -> {
                    v.dismiss();
                    signApkFileWithDialog(input_apk_path, output_apk_path, true,
                            null, null, null, null);
                });
                confirmOverwrite.show();
            } else {
                signApkFileWithDialog(input_apk_path, output_apk_path, true,
                        null, null, null, null);
            }
        });

        apkPathDialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        apkPathDialog.setView(testkey_root);
        apkPathDialog.setCancelable(false);
        apkPathDialog.show();
    }

    private void signApkFileWithDialog(String inputApkPath, String outputApkPath, boolean useTestkey, String keyStorePath, String keyStorePassword, String keyStoreKeyAlias, String keyPassword) {
        View building_root = getLayoutInflater().inflate(R.layout.build_progress_msg_box, null, false);
        LinearLayout layout_quiz = building_root.findViewById(R.id.layout_quiz);
        TextView tv_progress = building_root.findViewById(R.id.tv_progress);

        ScrollView scroll_view = new ScrollView(this);
        TextView tv_log = new TextView(this);
        scroll_view.addView(tv_log);
        layout_quiz.addView(scroll_view);

        tv_progress.setText("Signing APK...");

        AlertDialog building_dialog = new MaterialAlertDialogBuilder(this)
                .setView(building_root)
                .create();

        mod.alucard.tn.apksigner.ApkSigner signer = new mod.alucard.tn.apksigner.ApkSigner();
        new Thread() {
            @Override
            public void run() {
                super.run();

                mod.alucard.tn.apksigner.ApkSigner.LogCallback callback = line -> runOnUiThread(() ->
                        tv_log.setText(Helper.getText(tv_log) + line));

                if (useTestkey) {
                    signer.signWithTestKey(inputApkPath, outputApkPath, callback);
                } else {
                    signer.signWithKeyStore(inputApkPath, outputApkPath,
                            keyStorePath, keyStorePassword, keyStoreKeyAlias, keyPassword, callback);
                }

                runOnUiThread(() -> {
                    if (mod.alucard.tn.apksigner.ApkSigner.LogCallback.errorCount.get() == 0) {
                        building_dialog.dismiss();
                        SketchwareUtil.toast("Successfully saved signed APK to: /Internal storage/sketchware/signed_apk/"
                                        + Uri.fromFile(new java.io.File(outputApkPath)).getLastPathSegment(),
                                Toast.LENGTH_LONG);
                    } else {
                        tv_progress.setText("An error occurred. Check the log for more details.");
                    }
                });
            }
        }.start();

        building_dialog.show();
    }

    private class ActivityLauncher implements View.OnClickListener {
        private final Intent launchIntent;
        private Pair<String, String> optionalExtra;

        ActivityLauncher(Intent launchIntent) {
            this.launchIntent = launchIntent;
        }

        ActivityLauncher(Intent launchIntent, Pair<String, String> optionalExtra) {
            this(launchIntent);
            this.optionalExtra = optionalExtra;
        }

        @Override
        public void onClick(View v) {
            if (optionalExtra != null) {
                launchIntent.putExtra(optionalExtra.first, optionalExtra.second);
            }
            startActivity(launchIntent);
        }
    }
}
