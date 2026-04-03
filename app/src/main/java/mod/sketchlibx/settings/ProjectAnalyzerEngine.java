package mod.sketchlibx.settings;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.ViewBean;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import a.a.a.jC;
import a.a.a.yq;
import pro.sketchware.utility.FileUtil;

public class ProjectAnalyzerEngine {

    public static class UnusedResource {
        public int type; // 1=Image, 2=Sound, 3=Font, 4=CustomLayout
        public String name;
        public String detail;
        public Object reference;

        public UnusedResource(int type, String name, String detail, Object reference) {
            this.type = type;
            this.name = name;
            this.detail = detail;
            this.reference = reference;
        }
    }

    public static List<UnusedResource> scanForUnusedResources(String sc_id) {
        List<UnusedResource> unusedList = new ArrayList<>();
        
        ArrayList<ProjectFileBean> allFiles = new ArrayList<>();
        if (jC.b(sc_id).b() != null) allFiles.addAll(jC.b(sc_id).b());
        if (jC.b(sc_id).c() != null) allFiles.addAll(jC.b(sc_id).c());
        
        if (allFiles.isEmpty()) return unusedList;

        Set<String> usedResources = new HashSet<>();
        Set<String> usedCustomViews = new HashSet<>();

        yq javaCodeGenerator = new yq(pro.sketchware.SketchApplication.getContext(), sc_id);
        StringBuilder allJavaCodeBuilder = new StringBuilder();

        // 1. Extract used IDs and Resources from Blocks and Layouts
        for (ProjectFileBean file : allFiles) {
            String targetFileName = file.getXmlName().isEmpty() ? file.getJavaName() : file.getXmlName();
            ArrayList<ViewBean> views = jC.a(sc_id).d(targetFileName);
            HashMap<String, ArrayList<BlockBean>> events = jC.a(sc_id).b(file.getJavaName());

            // Add raw java code for scanning
            if (!file.getJavaName().isEmpty()) {
                 allJavaCodeBuilder.append(javaCodeGenerator.getFileSrc(file.getJavaName(), jC.b(sc_id), jC.a(sc_id), jC.c(sc_id))).append("\n");
                 
                 // Also read any Custom Java file if it exists
                 String customJavaPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_java/" + file.getJavaName();
                 if (FileUtil.isExistFile(customJavaPath)) {
                     allJavaCodeBuilder.append(FileUtil.readFile(customJavaPath)).append("\n");
                 }
            }

            if (events != null) {
                for (Map.Entry<String, ArrayList<BlockBean>> entry : events.entrySet()) {
                    for (BlockBean block : entry.getValue()) {
                        if (block.parameters != null) {
                            usedResources.addAll(block.parameters);
                            usedCustomViews.addAll(block.parameters);
                        }
                    }
                }
            }

            if (views != null) {
                for (ViewBean view : views) {
                    if (view.image != null && view.image.resName != null && !view.image.resName.equals("NONE")) {
                        usedResources.add(view.image.resName);
                    }
                    if (view.layout != null && view.layout.backgroundResource != null && !view.layout.backgroundResource.equals("NONE")) {
                        usedResources.add(view.layout.backgroundResource);
                    }
                    if (view.text != null && view.text.textFont != null && !view.text.textFont.equals("default_font")) {
                        usedResources.add(view.text.textFont);
                    }
                    if (view.customView != null && !view.customView.isEmpty()) {
                        usedCustomViews.add(view.customView);
                    }
                }
            }
        }
        
        String allJavaCode = allJavaCodeBuilder.toString();

        // 2. Identify Unused Images
        ArrayList<ProjectResourceBean> images = jC.d(sc_id).b;
        if (images != null) {
            for (ProjectResourceBean img : images) {
                if (!usedResources.contains(img.resName) && !img.resName.equals("app_icon") && !img.resName.equals("NONE")) {
                    // Check if it exists in raw java code (e.g. R.drawable.my_img)
                    if (!allJavaCode.contains(img.resName)) {
                         unusedList.add(new UnusedResource(1, img.resName, "Unused Image Drawable", img));
                    }
                }
            }
        }

        // 3. Identify Unused Sounds
        ArrayList<ProjectResourceBean> sounds = jC.d(sc_id).c;
        if (sounds != null) {
            for (ProjectResourceBean snd : sounds) {
                if (!usedResources.contains(snd.resName)) {
                     // Check if it exists in raw java code (e.g. R.raw.my_sound)
                     if (!allJavaCode.contains(snd.resName)) {
                         unusedList.add(new UnusedResource(2, snd.resName, "Unused Audio File", snd));
                     }
                }
            }
        }

        // 4. Identify Unused Fonts
        ArrayList<ProjectResourceBean> fonts = jC.d(sc_id).d;
        if (fonts != null) {
            for (ProjectResourceBean fnt : fonts) {
                if (!usedResources.contains(fnt.resName)) {
                     // Check if it exists in raw java code (e.g. "my_font.ttf")
                     if (!allJavaCode.contains(fnt.resName)) {
                         unusedList.add(new UnusedResource(3, fnt.resName, "Unused Font File", fnt));
                     }
                }
            }
        }

        // 5. Identify Unused Custom Views (Layouts)
        ArrayList<ProjectFileBean> customViews = jC.b(sc_id).c();
        if (customViews != null) {
            for (ProjectFileBean cv : customViews) {
                if (!usedCustomViews.contains(cv.fileName) && !usedCustomViews.contains(cv.getXmlName())) {
                     // Check if it exists in raw java code (e.g. R.layout.my_custom_view)
                     if (!allJavaCode.contains(cv.getXmlName().replace(".xml", ""))) {
                         unusedList.add(new UnusedResource(4, cv.getXmlName(), "Unused Custom Layout", cv));
                     }
                }
            }
        }

        return unusedList;
    }
}