package mod.sketchlibx.project.backup;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.utility.FileUtil;

public class CloudBackupManager {

    private static final String TAG = "CloudBackupManager";
    private static final String FOLDER_SPACE = "appDataFolder"; // Hidden folder for app data (Like WhatsApp)
    private final Drive driveService;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public CloudBackupManager(Context context, GoogleSignInAccount account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_APPDATA));
        credential.setSelectedAccount(account.getAccount());

        driveService = new Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("Sketchware Pro")
                .build();

        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public interface BackupCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface FileListCallback {
        void onSuccess(List<File> files);
        void onError(String error);
    }

    /**
     * Uploads the SWB file to Google Drive.
     * If a backup for this project already exists, it OVERWRITES it to save storage.
     */
    public void uploadBackupToCloud(final java.io.File swbFile, final String projectName, final BackupCallback callback) {
        executor.execute(() -> {
            try {
                String fileName = swbFile.getName(); // e.g. 688_Backup.swb

                // 1. Check if file already exists in cloud
                String query = "name = '" + fileName + "' and '" + FOLDER_SPACE + "' in parents and trashed = false";
                FileList result = driveService.files().list()
                        .setSpaces(FOLDER_SPACE)
                        .setQ(query)
                        .setFields("files(id, name)")
                        .execute();

                FileContent mediaContent = new FileContent("application/zip", swbFile);

                if (!result.getFiles().isEmpty()) {
                    // Overwrite existing backup
                    String existingFileId = result.getFiles().get(0).getId();
                    File updateMetadata = new File();
                    // Custom property to store actual project name for display
                    updateMetadata.setProperties(Collections.singletonMap("projectName", projectName));
                    
                    driveService.files().update(existingFileId, updateMetadata, mediaContent).execute();
                    postSuccess(callback, "Backup overwritten successfully in cloud!");
                } else {
                    // Create new backup
                    File fileMetadata = new File();
                    fileMetadata.setName(fileName);
                    fileMetadata.setParents(Collections.singletonList(FOLDER_SPACE));
                    fileMetadata.setProperties(Collections.singletonMap("projectName", projectName));

                    driveService.files().create(fileMetadata, mediaContent)
                            .setFields("id")
                            .execute();
                    postSuccess(callback, "New backup uploaded to cloud!");
                }
            } catch (Exception e) {
                Log.e(TAG, "Upload Failed", e);
                postError(callback, "Cloud upload failed: " + e.getMessage());
            }
        });
    }

    /**
     * Retrieves the list of all uploaded SWB backups from Google Drive.
     */
    public void getCloudBackupsList(final FileListCallback callback) {
        executor.execute(() -> {
            try {
                FileList result = driveService.files().list()
                        .setSpaces(FOLDER_SPACE)
                        .setFields("files(id, name, createdTime, size, properties)")
                        .execute();
                mainHandler.post(() -> callback.onSuccess(result.getFiles()));
            } catch (Exception e) {
                Log.e(TAG, "Failed to get backup list", e);
                mainHandler.post(() -> callback.onError("Failed to fetch cloud backups: " + e.getMessage()));
            }
        });
    }

    /**
     * Downloads a specific SWB backup from Google Drive to local storage.
     */
    public void downloadBackupFromCloud(final String fileId, final String fileName, final String downloadPath, final BackupCallback callback) {
        executor.execute(() -> {
            try {
                java.io.File destFile = new java.io.File(downloadPath, fileName);
                if (!destFile.getParentFile().exists()) {
                    destFile.getParentFile().mkdirs();
                }

                OutputStream outputStream = new FileOutputStream(destFile);
                driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
                outputStream.flush();
                outputStream.close();

                postSuccess(callback, "Backup downloaded successfully to " + destFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Download Failed", e);
                postError(callback, "Failed to download backup: " + e.getMessage());
            }
        });
    }

    private void postSuccess(BackupCallback callback, String msg) {
        mainHandler.post(() -> callback.onSuccess(msg));
    }

    private void postError(BackupCallback callback, String err) {
        mainHandler.post(() -> callback.onError(err));
    }
}
