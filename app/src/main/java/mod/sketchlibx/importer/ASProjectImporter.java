package mod.sketchlibx.importer;

import android.app.Activity;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import a.a.a.jC;
import a.a.a.lC;
import a.a.a.nB;
import a.a.a.oB;
import a.a.a.wq;
import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import pro.sketchware.activities.main.fragments.projects.ProjectsFragment;
import pro.sketchware.databinding.ProgressMsgBoxBinding;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class ASProjectImporter extends AsyncTask<Void, String, Boolean> {

    private final WeakReference<Activity> activityRef;
    private final Uri zipUri;
    private final ProjectsFragment fragment;
    private AlertDialog loadingDialog;
    private ProgressMsgBoxBinding binding;
    private String errorMessage = "";

    private final String FAB_JSON = "{\"adSize\":\"\",\"adUnitId\":\"\",\"alpha\":1.0,\"checked\":0,\"choiceMode\":0,\"clickable\":1,\"convert\":\"\",\"customView\":\"\",\"dividerHeight\":1,\"enabled\":1,\"firstDayOfWeek\":1,\"id\":\"_fab\",\"image\":{\"rotate\":0,\"scaleType\":\"CENTER\"},\"indeterminate\":\"false\",\"index\":0,\"inject\":\"\",\"layout\":{\"backgroundColor\":16777215,\"borderColor\":-16740915,\"gravity\":0,\"height\":-2,\"layoutGravity\":85,\"marginBottom\":16,\"marginLeft\":16,\"marginRight\":16,\"marginTop\":16,\"orientation\":-1,\"paddingBottom\":0,\"paddingLeft\":0,\"paddingRight\":0,\"paddingTop\":0,\"weight\":0,\"weightSum\":0,\"width\":-2},\"max\":100,\"parentAttributes\":{},\"parentType\":-1,\"preIndex\":0,\"preParentType\":0,\"progress\":0,\"progressStyle\":\"?android:progressBarStyle\",\"scaleX\":1.0,\"scaleY\":1.0,\"spinnerMode\":1,\"text\":{\"hint\":\"\",\"hintColor\":16777215,\"imeOption\":0,\"inputType\":1,\"line\":0,\"singleLine\":0,\"text\":\"\",\"textColor\":16777215,\"textFont\":\"default_font\",\"textSize\":12,\"textType\":0},\"translationX\":0.0,\"translationY\":0.0,\"type\":16}";

    public ASProjectImporter(Activity activity, Uri zipUri, ProjectsFragment fragment) {
        this.activityRef = new WeakReference<>(activity);
        this.zipUri = zipUri;
        this.fragment = fragment;
    }

    public static void showPicker(Activity activity, ProjectsFragment fragment) {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"zip"});
        options.setTitle("Select AS Project (.zip)");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                new ASProjectImporter(activity, Uri.fromFile(file), fragment).execute();
            }
        };

        new FilePickerDialogFragment(options, callback).show(fragment.getChildFragmentManager(), "filePicker");
    }

    @Override
    protected void onPreExecute() {
        Activity act = activityRef.get();
        if (act != null) {
            binding = ProgressMsgBoxBinding.inflate(LayoutInflater.from(act));
            binding.tvProgress.setText("Initializing Importer...");
            loadingDialog = new MaterialAlertDialogBuilder(act)
                    .setTitle("Importing AS Project")
                    .setCancelable(false)
                    .setView(binding.getRoot())
                    .create();
            loadingDialog.show();
        }
    }

    @Override
    protected void onProgressUpdate(String... values) {
        if (binding != null && values.length > 0) {
            binding.tvProgress.setText(values[0]);
        }
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        Activity act = activityRef.get();
        if (act == null) return false;

        String cacheDir = wq.a() + File.separator + "cache" + File.separator + "as_import_tmp";
        FileUtil.deleteFile(cacheDir); 
        FileUtil.makeDir(cacheDir);

        try {
            publishProgress("Extracting ZIP...");
            extractZipFromUri(act, zipUri, cacheDir);

            String newScId = lC.b(); 
            publishProgress("Setting up project " + newScId + "...");

            File asSrcMain = findSrcMainFolder(new File(cacheDir));
            if (asSrcMain == null) {
                errorMessage = "Invalid AS Project: 'src/main' folder not found.";
                return false;
            }

            // --- 1. EXTRACT APP NAME & PACKAGE ACCURATELY ---
            String pkgName = "com.imported.project";
            String appName = "Imported App";
            String iconResName = "ic_launcher";
            boolean hasCustomIcon = false;
            
            File manifestFile = new File(asSrcMain, "AndroidManifest.xml");
            String manifestContent = "";
            if (manifestFile.exists()) {
                manifestContent = FileUtil.readFile(manifestFile.getAbsolutePath());
                Matcher pkgMatcher = Pattern.compile("package=\"([^\"]+)\"").matcher(manifestContent);
                if (pkgMatcher.find()) pkgName = pkgMatcher.group(1);
                
                Matcher iconMatcher = Pattern.compile("android:icon=\"@(mipmap|drawable)/([^\"]+)\"").matcher(manifestContent);
                if (iconMatcher.find()) iconResName = iconMatcher.group(2);
            }

            File stringsFile = new File(asSrcMain, "res/values/strings.xml");
            String stringsContent = stringsFile.exists() ? FileUtil.readFile(stringsFile.getAbsolutePath()) : "";
            
            if (!manifestContent.isEmpty()) {
                Matcher labelMatcher = Pattern.compile("android:label=\"@string/([^\"]+)\"").matcher(manifestContent);
                if (labelMatcher.find()) {
                    String strName = labelMatcher.group(1);
                    Matcher strMatcher = Pattern.compile("<string name=\"" + strName + "\">([^<]+)</string>").matcher(stringsContent);
                    if (strMatcher.find()) appName = strMatcher.group(1);
                } else {
                    labelMatcher = Pattern.compile("android:label=\"([^\"]+)\"").matcher(manifestContent);
                    if (labelMatcher.find()) appName = labelMatcher.group(1);
                }
            }

            String dataPath = wq.b(newScId);
            String filesPath = dataPath + File.separator + "files";
            FileUtil.makeDir(filesPath + File.separator + "java");
            FileUtil.makeDir(filesPath + File.separator + "resource");
            FileUtil.makeDir(filesPath + File.separator + "assets");
            FileUtil.makeDir(filesPath + File.separator + "app-icon");

            // --- 2. EXTRACT APP ICON ACCURATELY ---
            File resDir = new File(asSrcMain, "res");
            if (resDir.exists()) {
                String[] mipmapFolders = {"mipmap-xxxhdpi", "mipmap-xxhdpi", "mipmap-xhdpi", "mipmap-hdpi", "mipmap-mdpi", "drawable-xxhdpi", "drawable-xhdpi", "drawable"};
                for (String folder : mipmapFolders) {
                    File targetIcon = new File(resDir, folder + File.separator + iconResName + ".png");
                    if (targetIcon.exists()) {
                        FileUtil.copyFile(targetIcon.getAbsolutePath(), filesPath + File.separator + "app-icon" + File.separator + "icon.png");
                        hasCustomIcon = true;
                        break;
                    }
                    targetIcon = new File(resDir, folder + File.separator + iconResName + ".webp");
                    if (targetIcon.exists()) {
                        FileUtil.copyFile(targetIcon.getAbsolutePath(), filesPath + File.separator + "app-icon" + File.separator + "icon.png");
                        hasCustomIcon = true;
                        break;
                    }
                }
            }

            HashMap<String, Object> projMap = new HashMap<>();
            projMap.put("sc_id", newScId);
            projMap.put("my_ws_name", appName.replaceAll("[^a-zA-Z0-9 ]", "").trim());
            projMap.put("my_app_name", appName);
            projMap.put("my_sc_pkg_name", pkgName);
            projMap.put("sc_ver_code", "1");
            projMap.put("sc_ver_name", "1.0");
            projMap.put("color_primary", -10455380);
            projMap.put("color_primary_dark", -10455380);
            projMap.put("color_accent", -10455380);
            projMap.put("color_control_highlight", -2497793);
            projMap.put("color_control_normal", -10455380);
            projMap.put("my_sc_reg_dt", new nB().a("yyyyMMddHHmmss"));
            projMap.put("sketchware_ver", 158);
            projMap.put("isIconAdaptive", false);
            projMap.put("custom_icon", hasCustomIcon);
            lC.a(newScId, projMap);

            Gson gson = new Gson();
            oB fileEncryptor = new oB();

            // --- 3. SMART ACTIVITY SCANNER (Mapping Java to XML) ---
            publishProgress("Scanning Java for Activities...");
            File javaDir = new File(asSrcMain, "java");
            if (!javaDir.exists()) javaDir = new File(asSrcMain, "kotlin");

            ArrayList<String> activityLayouts = new ArrayList<>();
            HashMap<String, File> layoutToJavaFileMap = new HashMap<>();
            
            if (javaDir.exists()) {
                scanJavaFilesForLayouts(javaDir, layoutToJavaFileMap, activityLayouts);
            }
            activityLayouts.add("main"); // Ensure main is always an activity

            publishProgress("Parsing XML Layouts...");
            File layoutDir = new File(asSrcMain, "res/layout");
            
            StringBuilder fileStr = new StringBuilder();
            StringBuilder viewStr = new StringBuilder();
            StringBuilder logicStr = new StringBuilder();
            
            fileStr.append("@activity\n");
            ArrayList<String> customViewLayouts = new ArrayList<>();

            if (layoutDir.exists() && layoutDir.isDirectory()) {
                File[] layouts = layoutDir.listFiles();
                if (layouts != null) {
                    for (File xml : layouts) {
                        if (xml.getName().endsWith(".xml")) {
                            String rawName = xml.getName().replace(".xml", "");
                            
                            // It's an activity ONLY if it was found in Java scan or is "main"
                            boolean isActivity = activityLayouts.contains(rawName);
                            
                            ViewBeanParser parser = new ViewBeanParser(xml);
                            parser.setSkipRoot(true);
                            ArrayList<ViewBean> parsedBeans = parser.parse();

                            viewStr.append("@").append(rawName).append(".xml\n");
                            for (ViewBean bean : parsedBeans) {
                                viewStr.append(gson.toJson(bean)).append("\n");
                            }

                            if (isActivity) {
                                viewStr.append("@").append(rawName).append(".xml_fab\n").append(FAB_JSON).append("\n");
                                fileStr.append("{\"fileName\":\"").append(rawName).append("\",\"fileType\":0,\"keyboardSetting\":0,\"options\":0,\"orientation\":0,\"theme\":-1}\n");
                                
                                String javaName = getJavaName(rawName);
                                logicStr.append("@").append(javaName).append("_var\n");
                                logicStr.append("@").append(javaName).append("_components\n");
                                logicStr.append("@").append(javaName).append("_events\n");
                                logicStr.append("{\"eventName\":\"initializeLogic\",\"eventType\":3,\"targetId\":\"initializeLogic\",\"targetType\":0}\n");
                                
                                // Process the mapped Java File for this activity into the ASD block
                                File mappedJavaFile = layoutToJavaFileMap.get(rawName);
                                if (mappedJavaFile != null && mappedJavaFile.exists()) {
                                    String javaContent = FileUtil.readFile(mappedJavaFile.getAbsolutePath());
                                    // Remove imports to prevent duplicate errors inside onCreate
                                    javaContent = javaContent.replaceAll("package\\s+[^;]+;", "").replaceAll("import\\s+[^;]+;", "").trim();
                                    String escapedContent = javaContent.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
                                    
                                    String asdJson = "{\"color\":-10703429,\"id\":\"11\",\"nextBlock\":-1,\"opCode\":\"addSourceDirectly\",\"parameters\":[\"" + escapedContent + "\"],\"spec\":\"add source directly %s.str\",\"subStack1\":-1,\"subStack2\":-1,\"type\":\" \",\"typeName\":\"\"}\n";
                                    logicStr.append("@").append(javaName).append("_onCreate_initializeLogic\n");
                                    logicStr.append(asdJson);
                                }
                            } else {
                                customViewLayouts.add(rawName);
                            }
                        }
                    }
                }
            }

            fileStr.append("@customview\n");
            for (String cv : customViewLayouts) {
                fileStr.append("{\"fileName\":\"").append(cv).append("\",\"fileType\":1,\"keyboardSetting\":0,\"options\":0,\"orientation\":0,\"theme\":-1}\n");
            }

            publishProgress("Copying Extra Java Files...");
            if (javaDir.exists()) {
                String targetJavaPath = filesPath + File.separator + "java";
                copyMiscJavaFiles(javaDir, new File(targetJavaPath), layoutToJavaFileMap);
            }

            fileEncryptor.a(dataPath + File.separator + "view", fileEncryptor.d(viewStr.toString()));
            fileEncryptor.a(dataPath + File.separator + "file", fileEncryptor.d(fileStr.toString()));
            fileEncryptor.a(dataPath + File.separator + "logic", fileEncryptor.d(logicStr.toString()));

            publishProgress("Processing Resources...");
            StringBuilder resourceStr = new StringBuilder();
            resourceStr.append("@images\n");

            if (resDir.exists()) {
                String sketchwareImagesPath = Environment.getExternalStorageDirectory().getAbsolutePath() + 
                        "/.sketchware/resources/images/" + newScId;
                FileUtil.makeDir(sketchwareImagesPath);

                for (File dir : resDir.listFiles()) {
                    if (dir.getName().startsWith("drawable") || dir.getName().startsWith("mipmap")) {
                        File[] drawables = dir.listFiles();
                        if (drawables != null) {
                            for (File drawable : drawables) {
                                if (drawable.getName().endsWith(".png") || drawable.getName().endsWith(".jpg")) {
                                    String resFullName = drawable.getName();
                                    String resName = resFullName.substring(0, resFullName.lastIndexOf("."));
                                    
                                    if (!drawable.getName().contains("ic_launcher")) {
                                        File targetFile = new File(sketchwareImagesPath, resFullName);
                                        FileUtil.copyFile(drawable.getAbsolutePath(), targetFile.getAbsolutePath());
                                        resourceStr.append("{\"resFullName\":\"").append(resFullName).append("\",\"resName\":\"").append(resName).append("\",\"resType\":1}\n");
                                    }
                                } else if (drawable.getName().endsWith(".xml")) {
                                    File targetDir = new File(filesPath + File.separator + "resource" + File.separator + dir.getName());
                                    FileUtil.makeDir(targetDir.getAbsolutePath());
                                    FileUtil.copyFile(drawable.getAbsolutePath(), targetDir.getAbsolutePath() + File.separator + drawable.getName());
                                }
                            }
                        }
                    } else if (dir.getName().startsWith("values") || dir.getName().startsWith("xml") || dir.getName().startsWith("font")) {
                        File targetDir = new File(filesPath + File.separator + "resource" + File.separator + dir.getName());
                        FileUtil.makeDir(targetDir.getAbsolutePath());
                        File[] valFiles = dir.listFiles();
                        if (valFiles != null) {
                            for (File valFile : valFiles) {
                                FileUtil.copyFile(valFile.getAbsolutePath(), targetDir.getAbsolutePath() + File.separator + valFile.getName());
                            }
                        }
                    }
                }
            }

            resourceStr.append("@sounds\n@fonts\n");
            fileEncryptor.a(dataPath + File.separator + "resource", fileEncryptor.d(resourceStr.toString()));

            publishProgress("Setting up Firebase & Library...");
            String fbDbUrl = "", fbAppId = "", fbApiKey = "", fbProjectId = "";
            boolean useFb = false;
            
            File googleServicesFile = new File(asSrcMain.getParentFile().getParentFile(), "app/google-services.json");
            if (googleServicesFile.exists()) {
                useFb = true;
                try {
                    JSONObject gsJson = new JSONObject(FileUtil.readFile(googleServicesFile.getAbsolutePath()));
                    JSONObject projectInfo = gsJson.getJSONObject("project_info");
                    fbProjectId = projectInfo.optString("project_id", "");
                    fbDbUrl = projectInfo.optString("firebase_url", "");
                    
                    JSONArray clients = gsJson.getJSONArray("client");
                    if (clients.length() > 0) {
                        JSONObject client0 = clients.getJSONObject(0);
                        fbAppId = client0.getJSONObject("client_info").optString("mobilesdk_app_id", "");
                        JSONArray apiKeys = client0.getJSONArray("api_key");
                        if (apiKeys.length() > 0) {
                            fbApiKey = apiKeys.getJSONObject(0).optString("current_key", "");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            String firebaseDbData = "{\"adUnits\":[],\"appId\":\"\",\"configurations\":{},\"data\":\"" + fbDbUrl.replace("https://", "") + "\",\"libType\":0,\"reserved1\":\"" + fbAppId + "\",\"reserved2\":\"" + fbApiKey + "\",\"reserved3\":\"" + fbProjectId + ".appspot.com\",\"testDevices\":[],\"useYn\":\"" + (useFb ? "Y" : "N") + "\"}";
            String libraryStr = "@firebaseDB\n" + firebaseDbData + "\n" +
                    "@compat\n{\"adUnits\":[],\"appId\":\"\",\"configurations\":{\"material3\":true,\"dynamic_colors\":true,\"theme\":\"DayNight\"},\"data\":\"\",\"libType\":1,\"reserved1\":\"\",\"reserved2\":\"\",\"reserved3\":\"\",\"testDevices\":[],\"useYn\":\"Y\"}\n" +
                    "@admob\n{\"adUnits\":[],\"appId\":\"\",\"configurations\":{},\"data\":\"\",\"libType\":2,\"reserved1\":\"\",\"reserved2\":\"\",\"reserved3\":\"\",\"testDevices\":[],\"useYn\":\"N\"}\n" +
                    "@googleMap\n{\"adUnits\":[],\"appId\":\"\",\"configurations\":{},\"data\":\"\",\"libType\":3,\"reserved1\":\"\",\"reserved2\":\"\",\"reserved3\":\"\",\"testDevices\":[],\"useYn\":\"N\"}\n";
            fileEncryptor.a(dataPath + File.separator + "library", fileEncryptor.d(libraryStr));

            File assetsDir = new File(asSrcMain, "assets");
            if (assetsDir.exists()) {
                FileUtil.copyDirectory(assetsDir, new File(filesPath + File.separator + "assets"));
            }

            FileUtil.deleteFile(cacheDir);

            return true;

        } catch (Exception e) {
            errorMessage = e.getMessage();
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }

        if (success) {
            SketchwareUtil.toast("AS Project Imported Successfully!");
            if (fragment != null) {
                fragment.refreshProjectsList();
            }
        } else {
            SketchwareUtil.toastError("Import Failed: " + errorMessage, Toast.LENGTH_LONG);
        }
    }

    // --- HELPER METHODS FOR SMART EXTRACTION ---

    private void scanJavaFilesForLayouts(File dir, HashMap<String, File> map, ArrayList<String> activities) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanJavaFilesForLayouts(f, map, activities);
            } else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) {
                String content = FileUtil.readFile(f.getAbsolutePath());
                Matcher m = Pattern.compile("R\\.layout\\.([a-zA-Z0-9_]+)").matcher(content);
                if (m.find()) {
                    String layoutName = m.group(1);
                    map.put(layoutName, f);
                    activities.add(layoutName);
                }
            }
        }
    }

    private void copyMiscJavaFiles(File source, File targetMainDir, HashMap<String, File> activityJavaFiles) throws IOException {
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyMiscJavaFiles(child, targetMainDir, activityJavaFiles);
                }
            }
        } else if (source.getName().endsWith(".java") || source.getName().endsWith(".kt")) {
            // Copy ONLY if it's not mapped as an Activity (activities are moved to logic blocks)
            if (!activityJavaFiles.containsValue(source)) { 
                if (!targetMainDir.exists()) targetMainDir.mkdirs();
                File targetFile = new File(targetMainDir, source.getName());
                FileUtil.copyFile(source.getAbsolutePath(), targetFile.getAbsolutePath());
            }
        }
    }

    private String getJavaName(String xmlName) {
        if (xmlName.equals("main")) return "MainActivity.java";
        String nameToProcess = xmlName;
        if (nameToProcess.startsWith("activity_")) {
            nameToProcess = nameToProcess.substring(9);
        }
        String[] parts = nameToProcess.split("_");
        StringBuilder javaName = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                javaName.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        javaName.append("Activity.java");
        return javaName.toString();
    }

    private void extractZipFromUri(Activity context, Uri uri, String destFolder) throws IOException {
        InputStream is = context.getContentResolver().openInputStream(uri);
        if (is == null) throw new IOException("Cannot open URI");
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
        ZipEntry ze;
        byte[] buffer = new byte[8192];
        while ((ze = zis.getNextEntry()) != null) {
            File file = new File(destFolder, ze.getName());
            File dir = ze.isDirectory() ? file : file.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs())
                throw new FileNotFoundException("Failed to ensure directory: " + dir.getAbsolutePath());
            if (ze.isDirectory()) continue;
            FileOutputStream fout = new FileOutputStream(file);
            BufferedOutputStream bout = new BufferedOutputStream(fout, buffer.length);
            int count;
            while ((count = zis.read(buffer, 0, buffer.length)) != -1) {
                bout.write(buffer, 0, count);
            }
            bout.flush();
            bout.close();
            zis.closeEntry();
        }
        zis.close();
    }

    private File findSrcMainFolder(File root) {
        if (root.getName().equals("main") && root.getParentFile().getName().equals("src")) {
            return root;
        }
        if (root.isDirectory()) {
            for (File child : root.listFiles()) {
                File found = findSrcMainFolder(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
