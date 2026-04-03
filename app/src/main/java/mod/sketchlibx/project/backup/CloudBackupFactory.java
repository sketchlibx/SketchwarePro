package mod.sketchlibx.project.backup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.editor.manage.block.ExtraBlockInfo;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hey.studios.project.backup.BackupFactory;
import mod.hey.studios.project.custom_blocks.CustomBlocksManager;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.utility.FileUtil;

public class CloudBackupFactory {
    public static final String EXTENSION = "swb";
    public static final String CLOUD_TEMP_DIR = ".sketchware/.cloudbackup/";

    private final String sc_id;
    private File outPath;

    public CloudBackupFactory(String sc_id) {
        this.sc_id = sc_id;
    }

    public static String getCloudBackupDir() {
        return new File(Environment.getExternalStorageDirectory(), CLOUD_TEMP_DIR).getAbsolutePath();
    }

    public void backup(Context context, String project_name) {
        String customFileName = ConfigActivity.getBackupFileName();
        String versionName = yB.c(lC.b(sc_id), "sc_ver_name");
        String versionCode = yB.c(lC.b(sc_id), "sc_ver_code");
        String pkgName = yB.c(lC.b(sc_id), "my_sc_pkg_name");
        String projectNameOnly = project_name.replace("_d", "").replace(File.separator, "");
        String finalFileName;

        try {
            finalFileName = customFileName
                    .replace("$projectName", projectNameOnly)
                    .replace("$versionCode", versionCode)
                    .replace("$versionName", versionName)
                    .replace("$pkgName", pkgName)
                    .replace("$timeInMs", String.valueOf(Calendar.getInstance(Locale.ENGLISH).getTimeInMillis()));
            Matcher matcher = Pattern.compile("\\$time\\((.*?)\\)").matcher(customFileName);
            while (matcher.find()) {
                finalFileName = finalFileName.replaceFirst(Pattern.quote(Objects.requireNonNull(matcher.group(0))),
                        new SimpleDateFormat(matcher.group(1), Locale.ENGLISH).format(Calendar.getInstance().getTime()));
            }
        } catch (Exception ignored) {
            finalFileName = projectNameOnly + " v" + versionName + " (" + pkgName + ", " + versionCode + ") " +
                    new SimpleDateFormat("yyyy-M-dd'T'HHmmss", Locale.ENGLISH).format(Calendar.getInstance().getTime());
        }

        FileUtil.makeDir(getCloudBackupDir());
        File outFolder = new File(getCloudBackupDir(), project_name + "_temp");
        File outZip = new File(getCloudBackupDir(), finalFileName + "." + EXTENSION);

        if (outFolder.exists()) FileUtil.deleteFile(outFolder.getAbsolutePath());
        FileUtil.makeDir(outFolder.getAbsolutePath());

        // Copy Data
        File dataF = new File(outFolder, "data");
        FileUtil.makeDir(dataF.getAbsolutePath());
        BackupFactory.copySafe(new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + sc_id), dataF);

        // Copy Resources
        File resF = new File(outFolder, "resources");
        FileUtil.makeDir(resF.getAbsolutePath());
        String[] resSubfolders = {"fonts", "icons", "images", "sounds"};
        for (String subfolder : resSubfolders) {
            File resSubf = new File(resF, subfolder);
            FileUtil.makeDir(resSubf.getAbsolutePath());
            BackupFactory.copySafe(new File(Environment.getExternalStorageDirectory(), ".sketchware/resources/" + subfolder + "/" + sc_id), resSubf);
            if (!subfolder.equals("icons")) BackupFactory.createNomediaFileIn(resSubf);
        }

        // Copy Project
        File projectF = new File(outFolder, "project");
        BackupFactory.copy(new File(Environment.getExternalStorageDirectory(), ".sketchware/mysc/list/" + sc_id + "/project"), projectF);

        // Copy Local Libs
        File localLibs = new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + sc_id + "/local_library");
        if (localLibs.exists()) {
            try {
                JSONArray ja = new JSONArray(FileUtil.readFile(localLibs.getAbsolutePath()));
                File libsF = new File(outFolder, "local_libs");
                libsF.mkdirs();
                for (int i = 0; i < ja.length(); i++) {
                    JSONObject jo = ja.getJSONObject(i);
                    File f = new File(jo.getString("dexPath")).getParentFile();
                    BackupFactory.copy(f, new File(libsF, f.getName()));
                }
            } catch (Exception ignored) {}
        }

        // Copy Custom Blocks
        if (context != null) {
            CustomBlocksManager cbm = new CustomBlocksManager(context, sc_id);
            Set<ExtraBlockInfo> blocks = new HashSet<>();
            Set<String> block_names = new HashSet<>();
            for (BlockBean bean : cbm.getUsedBlocks()) {
                if (!block_names.contains(bean.opCode)) {
                    block_names.add(bean.opCode);
                    blocks.add(cbm.contains(bean.opCode) ? cbm.getExtraBlockInfo(bean.opCode) : BlockLoader.getBlockInfo(bean.opCode));
                }
            }
            FileUtil.writeFile(new File(dataF, "custom_blocks").getAbsolutePath(), new Gson().toJson(blocks));
        }

        try {
            BackupFactory.zipFolder(outFolder, outZip);
            outPath = outZip;
        } catch (Exception e) {
            Log.e("CloudBackupFactory", "Zipping failed", e);
            outPath = null;
        } finally {
            FileUtil.deleteFile(outFolder.getAbsolutePath()); // Hamesha Temp files mita dega
        }
    }

    public File getOutFile() { return outPath; }
}
