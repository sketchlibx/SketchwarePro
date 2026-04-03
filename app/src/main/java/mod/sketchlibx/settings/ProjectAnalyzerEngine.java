package mod.sketchlibx.settings;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import a.a.a.jC;

public class ProjectAnalyzerEngine {

    public static String analyze(String sc_id) {
        StringBuilder report = new StringBuilder();
        
        ArrayList<ProjectFileBean> allFiles = jC.b(sc_id).b();
        if (allFiles == null || allFiles.isEmpty()) return "Failed to analyze project.";

        int unusedViewsCount = 0;
        int duplicateIdCount = 0;
        int heavyLayoutCount = 0;
        int unusedResourcesCount = 0;

        Set<String> usedViewIds = new HashSet<>();
        Set<String> declaredViewIds = new HashSet<>();
        Set<String> usedResources = new HashSet<>();

        for (ProjectFileBean file : allFiles) {
            String targetFileName = file.getXmlName().isEmpty() ? file.getJavaName() : file.getXmlName();
            ArrayList<ViewBean> views = jC.a(sc_id).d(targetFileName);
            HashMap<String, ArrayList<BlockBean>> events = jC.a(sc_id).b(file.getJavaName());

            // Extract all Block arguments looking for View IDs and Resource Names
            if (events != null) {
                for (Map.Entry<String, ArrayList<BlockBean>> entry : events.entrySet()) {
                    for (BlockBean block : entry.getValue()) {
                        if (block.parameters != null) {
                            usedViewIds.addAll(block.parameters);
                            usedResources.addAll(block.parameters); // Resources used in logic blocks
                        }
                    }
                }
            }

            if (views != null) {
                if (views.size() > 60) heavyLayoutCount++; // Heavy Layout Detection

                for (ViewBean view : views) {
                    // Check for Duplicates
                    if (!declaredViewIds.add(view.id)) {
                        duplicateIdCount++;
                        report.append("- Duplicate ID: ").append(view.id).append(" in ").append(targetFileName).append("\n");
                    }

                    // Check for Unused Views (If it's not the root Layout and not used in blocks)
                    if (!view.id.equals("linear1") && !view.id.equals("root") && !usedViewIds.contains(view.id)) {
                        unusedViewsCount++;
                    }

                    // Capture Resources used directly in XML properties
                    if (view.image != null && view.image.resName != null && !view.image.resName.equals("NONE")) {
                        usedResources.add(view.image.resName);
                    }
                    if (view.layout != null && view.layout.backgroundResource != null && !view.layout.backgroundResource.equals("NONE")) {
                        usedResources.add(view.layout.backgroundResource);
                    }
                }
            }
        }

        // Scan for Unused Images
        ArrayList<ProjectResourceBean> images = jC.d(sc_id).b;
        if (images != null) {
            for (ProjectResourceBean img : images) {
                // If the image is not in usedResources and is not the default app_icon
                if (!usedResources.contains(img.resName) && !img.resName.equals("app_icon") && !img.resName.equals("NONE")) {
                    unusedResourcesCount++;
                }
            }
        }

        // Format a clean, professional header
        StringBuilder header = new StringBuilder();
        header.append("--- Project Analysis Report ---\n\n");
        header.append("Duplicate IDs: ").append(duplicateIdCount).append("\n");
        header.append("Unused Views: ").append(unusedViewsCount).append("\n");
        header.append("Unused Resources: ").append(unusedResourcesCount).append("\n");
        header.append("Heavy Layouts (>60 views): ").append(heavyLayoutCount).append("\n");

        if (duplicateIdCount > 0) {
            header.append("\nDetails:\n");
        }

        report.insert(0, header.toString());

        // Clean status message at the bottom
        if (duplicateIdCount == 0 && unusedViewsCount < 5 && heavyLayoutCount == 0 && unusedResourcesCount < 5) {
            report.append("\nStatus: Your project is well optimized.");
        } else {
            report.append("\nStatus: Project could use some optimization.");
        }

        return report.toString();
    }
}
