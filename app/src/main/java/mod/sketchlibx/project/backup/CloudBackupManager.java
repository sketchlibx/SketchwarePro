package mod.sketchlibx.project.backup;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CloudBackupManager {

    private static final String TAG = "CloudBackupManager";
    private static final String FOLDER_SPACE = "appDataFolder";
    private Drive driveService;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private String initError = null;

    public CloudBackupManager(Context context, GoogleSignInAccount account) {
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        try {
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    context, Collections.singleton(DriveScopes.DRIVE_APPDATA));
            credential.setSelectedAccount(account.getAccount());

            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential)
                    .setApplicationName("Sketchware Pro Backup")
                    .build();
        } catch (Exception e) {
            initError = Log.getStackTraceString(e);
            Log.e(TAG, "Failed to initialize Drive service", e);
        }
    }

    public interface BackupCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface FileListCallback {
        void onSuccess(List<File> files);
        void onError(String error);
    }

    public void uploadBackupToCloud(final java.io.File swbFile, final String projectName, final BackupCallback callback) {
        if (driveService == null) {
            postError(callback, "Drive service initialization failed.\n\nDetails:\n" + initError);
            return;
        }
        
        executor.execute(() -> {
            try {
                String fileName = swbFile.getName();
                String query = "name = '" + fileName + "' and '" + FOLDER_SPACE + "' in parents and trashed = false";
                
                FileList result = driveService.files().list()
                        .setSpaces(FOLDER_SPACE)
                        .setQ(query)
                        .setFields("files(id, name)")
                        .execute();

                FileContent mediaContent = new FileContent("application/zip", swbFile);

                if (result.getFiles() != null && !result.getFiles().isEmpty()) {
                    String existingFileId = result.getFiles().get(0).getId();
                    File updateMetadata = new File();
                    updateMetadata.setProperties(Collections.singletonMap("projectName", projectName));
                    
                    driveService.files().update(existingFileId, updateMetadata, mediaContent).execute();
                    postSuccess(callback, "Backup overwritten successfully in cloud!");
                } else {
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
                postError(callback, "Cloud upload failed:\n" + Log.getStackTraceString(e));
            }
        });
    }

    public void getCloudBackupsList(final FileListCallback callback) {
        if (driveService == null) {
            mainHandler.post(() -> callback.onError("Drive service initialization failed.\n\nDetails:\n" + initError));
            return;
        }

        executor.execute(() -> {
            try {
                FileList result = driveService.files().list()
                        .setSpaces(FOLDER_SPACE)
                        .setFields("files(id, name, createdTime, size, properties)")
                        .execute();
                mainHandler.post(() -> callback.onSuccess(result.getFiles()));
            } catch (Exception e) {
                Log.e(TAG, "Failed to get backup list", e);
                mainHandler.post(() -> callback.onError("Failed to fetch cloud backups:\n" + Log.getStackTraceString(e)));
            }
        });
    }

    public void downloadBackupFromCloud(final String fileId, final String fileName, final String downloadPath, final BackupCallback callback) {
        if (driveService == null) {
            postError(callback, "Drive service initialization failed.\n\nDetails:\n" + initError);
            return;
        }

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
                postError(callback, "Failed to download backup:\n" + Log.getStackTraceString(e));
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
