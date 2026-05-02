package mod.sketchlibx.project.backup;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import a.a.a.lC;
import pro.sketchware.utility.FileUtil;

public class AutoBackupWorker extends Worker {

    private static final String TAG = "AutoBackupWorker";
    private static final String CHANNEL_ID = "cloud_backup_channel";
    private static final int NOTIFICATION_ID = 9988;

    private NotificationManager notificationManager;

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo == null || !netInfo.isConnected() || !netInfo.isAvailable()) {
            Log.e(TAG, "No active internet connection. Retrying later.");
            return Result.retry(); 
        }

        SharedPreferences prefs = context.getSharedPreferences("cloud_backup_prefs", Context.MODE_PRIVATE);
        long lastBackupTime = prefs.getLong("last_backup_time", 0);
        int intervalType = prefs.getInt("auto_backup_interval", 2); 
        
        if (intervalType == 0) return Result.success(); 
        
        long intervalMs = switch (intervalType) {
            case 1 -> 24L * 60 * 60 * 1000; 
            case 2 -> 7L * 24 * 60 * 60 * 1000; 
            case 3 -> 30L * 24 * 60 * 60 * 1000; 
            default -> 7L * 24 * 60 * 60 * 1000;
        };
        
        if (System.currentTimeMillis() - lastBackupTime < intervalMs) {
            Log.d(TAG, "Backup skipped. Interval hasn't passed yet.");
            return Result.success(); 
        }
        
        prefs.edit().putLong("last_backup_time", System.currentTimeMillis()).apply();
        
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) {
            Log.e(TAG, "Not signed in to Google.");
            return Result.failure();
        }

        CloudBackupManager cloudManager = new CloudBackupManager(context, account);

        ArrayList<HashMap<String, Object>> projects = lC.a();
        if (projects == null || projects.isEmpty()) {
            return Result.success();
        }

        Set<String> selectedScIds = prefs.getStringSet("auto_backup_sc_ids", new HashSet<>());
        ArrayList<HashMap<String, Object>> projectsToBackup = new ArrayList<>();
        if (selectedScIds.isEmpty()) {
            projectsToBackup.addAll(projects);
        } else {
            for (HashMap<String, Object> project : projects) {
                if (selectedScIds.contains((String) project.get("sc_id"))) {
                    projectsToBackup.add(project);
                }
            }
        }

        int total = projectsToBackup.size();
        if (total == 0) return Result.success();

        boolean allSuccess = true;
        for (int i = 0; i < total; i++) {
            HashMap<String, Object> project = projectsToBackup.get(i);
            String scId = (String) project.get("sc_id");
            String projectName = (String) project.get("my_app_name");

            if (scId == null || projectName == null) continue;

            updateNotification("Backing up: " + projectName, i + 1, total);

            CloudBackupFactory backupFactory = new CloudBackupFactory(scId);
            backupFactory.backup(context, projectName);
            File swbFile = backupFactory.getOutFile();

            if (swbFile != null && swbFile.exists() && swbFile.length() > 0) {
                try {
                    uploadSync(cloudManager, swbFile, projectName);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to upload: " + projectName, e);
                    allSuccess = false;
                } finally {
                    if (swbFile.exists()) {
                        boolean deleted = swbFile.delete();
                        Log.d(TAG, "Temp backup deleted to save storage: " + deleted);
                    }
                }
            } else {
                Log.e(TAG, "Backup file generation failed for: " + projectName);
                allSuccess = false;
            }
        }

        FileUtil.deleteFile(CloudBackupFactory.getCloudBackupDir());

        if (allSuccess) {
            updateNotification("Cloud Backup Complete!", total, total);
            return Result.success();
        } else {
            updateNotification("Cloud Backup finished with errors.", total, total);
            return Result.retry();
        }
    }

    private void updateNotification(String text, int current, int total) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Sketchware Cloud Sync")
                .setContentText(text)
                .setProgress(total, current, false)
                .setOngoing(current < total)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (current == 1) {
            try {
                setForegroundAsync(new ForegroundInfo(NOTIFICATION_ID, builder.build()));
            } catch (Exception ignored){}
        } else {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Cloud Backup", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows progress of cloud backups");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void uploadSync(CloudBackupManager cloudManager, File swbFile, String projectName) throws Exception {
        final Object lock = new Object();
        final Exception[] uploadError = {null};
        final boolean[] isDone = {false};

        cloudManager.uploadBackupToCloud(swbFile, projectName, new CloudBackupManager.BackupCallback() {
            @Override
            public void onSuccess(String message) {
                synchronized (lock) { 
                    isDone[0] = true;
                    lock.notify(); 
                }
            }
            @Override
            public void onError(String error) {
                synchronized (lock) { 
                    uploadError[0] = new Exception(error);
                    isDone[0] = true;
                    lock.notify(); 
                }
            }
        });

        synchronized (lock) {
            if (!isDone[0]) {
                lock.wait(120000); // Strict 2-minute timeout to prevent infinite deadlocks
            }
        }
        
        if (!isDone[0]) {
            throw new Exception("Upload timed out after 2 minutes.");
        }
        if (uploadError[0] != null) throw uploadError[0];
    }
}
