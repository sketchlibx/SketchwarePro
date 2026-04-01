package mod.sketchlibx.project.backup;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.lC;

public class AutoBackupWorker extends Worker {

    private static final String TAG = "AutoBackupWorker";

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Auto Backup Started in background...");

        Context context = getApplicationContext();
        
        // 1. Check if user is logged into Google Drive
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) {
            Log.e(TAG, "User not signed in. Cannot perform cloud backup.");
            return Result.failure();
        }

        CloudBackupManager cloudManager = new CloudBackupManager(context, account);

        // 2. Fetch all projects locally
        ArrayList<HashMap<String, Object>> projects = lC.a();
        if (projects == null || projects.isEmpty()) {
            Log.d(TAG, "No projects found to backup.");
            return Result.success();
        }

        // 3. Process each project
        boolean allSuccess = true;
        for (HashMap<String, Object> project : projects) {
            String scId = (String) project.get("sc_id");
            String projectName = (String) project.get("my_app_name");

            if (scId == null || projectName == null) continue;

            // Generate local SWB backup silently
            BackupFactory backupFactory = new BackupFactory(scId);
            backupFactory.setBackupLocalLibs(true);
            backupFactory.setBackupCustomBlocks(true);
            
            // Note: In background, we pass 'null' for activity so it doesn't show UI dialogs
            backupFactory.backup(null, projectName);
            File swbFile = backupFactory.getOutFile();

            if (swbFile != null && swbFile.exists()) {
                // Perform synchronous upload in this worker thread
                try {
                    uploadSync(cloudManager, swbFile, projectName);
                    Log.d(TAG, "Successfully backed up: " + projectName);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to upload: " + projectName, e);
                    allSuccess = false;
                }
            } else {
                Log.e(TAG, "Failed to generate local SWB for: " + projectName);
                allSuccess = false;
            }
        }

        return allSuccess ? Result.success() : Result.retry();
    }

    // Custom synchronous wrapper to keep the Worker alive until upload completes
    private void uploadSync(CloudBackupManager cloudManager, File swbFile, String projectName) throws Exception {
        final Object lock = new Object();
        final Exception[] uploadError = {null};

        cloudManager.uploadBackupToCloud(swbFile, projectName, new CloudBackupManager.BackupCallback() {
            @Override
            public void onSuccess(String message) {
                synchronized (lock) { lock.notify(); }
            }

            @Override
            public void onError(String error) {
                uploadError[0] = new Exception(error);
                synchronized (lock) { lock.notify(); }
            }
        });

        synchronized (lock) {
            lock.wait(); // Wait for the async task inside CloudBackupManager to finish
        }

        if (uploadError[0] != null) {
            throw uploadError[0];
        }
    }
}
