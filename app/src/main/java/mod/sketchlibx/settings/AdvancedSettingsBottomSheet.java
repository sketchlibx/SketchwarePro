package mod.sketchlibx.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.design.DesignActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.project.ProjectSettings;
import mod.hilal.saif.activities.android_manifest.AndroidManifestInjection;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;

public class AdvancedSettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "AdvancedSettingsBottomSheet";

    private final String sc_id;
    private final String currentJavaFileName;
    private final DesignActivity activity;
    private ProjectSettings projectSettings;

    private MaterialSwitch cbForceAndroidX;
    private MaterialSwitch cbKotlinConversion;
    private MaterialSwitch cbCustomJava;
    private MaterialSwitch cbCustomManifest;
    
    private View btnCustomJava;
    private View btnCustomManifest;

    public AdvancedSettingsBottomSheet(String sc_id, String currentJavaFileName, DesignActivity activity) {
        this.sc_id = sc_id;
        this.currentJavaFileName = currentJavaFileName;
        this.activity = activity;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_MaterialComponents_BottomSheetDialog);
    }

    @Override
    public void onStart() {
        super.onStart();
        View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            BottomSheetBehavior.from(bottomSheet).setSkipCollapsed(true);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_advanced_settings, container, false);
        projectSettings = new ProjectSettings(sc_id);

        cbForceAndroidX = root.findViewById(R.id.switch_force_androidx);
        cbKotlinConversion = root.findViewById(R.id.switch_java_to_kotlin);
        cbCustomJava = root.findViewById(R.id.switch_custom_java);
        cbCustomManifest = root.findViewById(R.id.switch_custom_manifest);
        
        btnCustomJava = root.findViewById(R.id.btn_custom_java);
        btnCustomManifest = root.findViewById(R.id.btn_custom_manifest);

        // Load existing settings
        cbForceAndroidX.setChecked(projectSettings.getValue(ProjectSettings.SETTING_FORCE_ANDROIDX, "false").equals("true"));
        cbKotlinConversion.setChecked(projectSettings.getValue(ProjectSettings.SETTING_JAVA_TO_KOTLIN, "false").equals("true"));
        cbCustomJava.setChecked(projectSettings.getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, "false").equals("true"));
        cbCustomManifest.setChecked(projectSettings.getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_MANIFEST, "false").equals("true"));

        // Set initial button visibility
        btnCustomJava.setVisibility(cbCustomJava.isChecked() ? View.VISIBLE : View.GONE);
        btnCustomManifest.setVisibility(cbCustomManifest.isChecked() ? View.VISIBLE : View.GONE);

        // Live Toggle Listeners
        cbCustomJava.setOnCheckedChangeListener((buttonView, isChecked) -> btnCustomJava.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        cbCustomManifest.setOnCheckedChangeListener((buttonView, isChecked) -> btnCustomManifest.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        root.findViewById(R.id.btn_project_analyzer).setOnClickListener(v -> {
            dismiss();
            runProjectAnalyzer();
        });

        btnCustomJava.setOnClickListener(v -> {
            saveSettings(); // Automatically save state before leaving
            dismiss();
            openCustomJavaEditor();
        });

        btnCustomManifest.setOnClickListener(v -> {
            saveSettings(); // Automatically save state before leaving
            dismiss();
            openCustomManifestEditor();
        });

        root.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.btn_save).setOnClickListener(v -> {
            saveSettings();
            activity.invalidateOptionsMenu(); // Refresh DesignActivity menus
            dismiss();
        });

        return root;
    }

    private void saveSettings() {
        projectSettings.setValue(ProjectSettings.SETTING_FORCE_ANDROIDX, cbForceAndroidX.isChecked() ? "true" : "false");
        projectSettings.setValue(ProjectSettings.SETTING_JAVA_TO_KOTLIN, cbKotlinConversion.isChecked() ? "true" : "false");
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, cbCustomJava.isChecked() ? "true" : "false");
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_MANIFEST, cbCustomManifest.isChecked() ? "true" : "false");
    }

    private void runProjectAnalyzer() {
        AlertDialog progress = new MaterialAlertDialogBuilder(activity)
                .setTitle("Analyzing Project...")
                .setMessage("Scanning for unused views, unused resources, duplicate IDs and heavy layouts. Please wait.")
                .setCancelable(false)
                .show();

        new Thread(() -> {
            String report = ProjectAnalyzerEngine.analyze(sc_id);
            activity.runOnUiThread(() -> {
                progress.dismiss();
                new MaterialAlertDialogBuilder(activity)
                        .setTitle("Analyzer Report")
                        .setMessage(report)
                        .setPositiveButton("Dismiss", null)
                        .show();
            });
        }).start();
    }

    private void openCustomJavaEditor() {
        String customJavaDir = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_java/";
        FileUtil.makeDir(customJavaDir);
        String customJavaPath = customJavaDir + currentJavaFileName;

        if (!FileUtil.isExistFile(customJavaPath)) {
            String source = new yq(activity.getApplicationContext(), sc_id).getFileSrc(currentJavaFileName, jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
            FileUtil.writeFile(customJavaPath, source);
        }

        Intent intent = new Intent(activity, SrcCodeEditor.class);
        intent.putExtra("content", customJavaPath);
        intent.putExtra("title", "Custom " + currentJavaFileName);
        intent.putExtra("sc_id", sc_id);
        startActivity(intent);
    }

    private void openCustomManifestEditor() {
        String customManifestPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_manifest.xml";
        if (!FileUtil.isExistFile(customManifestPath)) {
            String source = new yq(activity.getApplicationContext(), sc_id).getFileSrc("AndroidManifest.xml", jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
            FileUtil.writeFile(customManifestPath, source);
        }
        Intent intent = new Intent(activity, SrcCodeEditor.class);
        intent.putExtra("content", customManifestPath);
        intent.putExtra("title", "Custom AndroidManifest.xml");
        intent.putExtra("sc_id", sc_id);
        startActivity(intent);
    }
}
