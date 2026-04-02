package com.besome.sketch.editor.view;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import pro.sketchware.R;

public class ViewTreeDrawerDialog extends DialogFragment {

    private final ArrayList<ViewBean> currentViews;
    private final OnViewSelectedListener listener;
    
    private final List<TreeNode> rootNodes = new ArrayList<>();
    private final List<TreeNode> displayNodes = new ArrayList<>();
    private TreeAdapter adapter;

    public interface OnViewSelectedListener {
        void onSelected(String viewId);
    }

    public ViewTreeDrawerDialog(ArrayList<ViewBean> views, OnViewSelectedListener listener) {
        this.currentViews = views;
        this.listener = listener;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog().getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.START);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setWindowAnimations(R.style.Animation_Design_BottomSheetDialog);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320, getResources().getDisplayMetrics()),
                ViewGroup.LayoutParams.MATCH_PARENT));

        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(R.attr.colorSurfaceContainerLow, typedValue, true);
        root.setBackgroundColor(typedValue.data);

        // Header: "Component Tree"
        TextView header = new TextView(requireContext());
        header.setText("Component Tree");
        header.setTextSize(18);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        requireContext().getTheme().resolveAttribute(R.attr.colorOnSurface, typedValue, true);
        header.setTextColor(typedValue.data);
        
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        header.setPadding(padding, padding + 8, padding, padding);
        root.addView(header);

        // Divider
        View divider = new View(requireContext());
        requireContext().getTheme().resolveAttribute(R.attr.colorSurfaceContainerHighest, typedValue, true);
        divider.setBackgroundColor(typedValue.data);
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics())));

        // RecyclerView
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setClipToPadding(false);
        rv.setPadding(0, 0, 0, padding);
        
        buildTree();
        refreshDisplayList();
        
        adapter = new TreeAdapter();
        rv.setAdapter(adapter);
        
        root.addView(rv);
        return root;
    }

    private void buildTree() {
        HashMap<String, List<ViewBean>> childrenMap = new HashMap<>();
        List<ViewBean> roots = new ArrayList<>();

        // Group children by their parent's ID
        for (ViewBean bean : currentViews) {
            if (bean.parent == null || bean.parent.equals("root") || bean.parent.isEmpty()) {
                roots.add(bean);
            } else {
                childrenMap.computeIfAbsent(bean.parent, k -> new ArrayList<>()).add(bean);
            }
        }

        for (ViewBean root : roots) {
            rootNodes.add(createNode(root, childrenMap, 0));
        }
    }

    private TreeNode createNode(ViewBean view, HashMap<String, List<ViewBean>> childrenMap, int depth) {
        TreeNode node = new TreeNode(view, depth);
        // By default, expand everything
        node.isExpanded = true; 

        List<ViewBean> children = childrenMap.get(view.id);
        if (children != null) {
            for (ViewBean child : children) {
                node.children.add(createNode(child, childrenMap, depth + 1));
            }
        }
        return node;
    }

    // 🔥 3. Flattens the tree into a list for the RecyclerView based on expanded state
    private void refreshDisplayList() {
        displayNodes.clear();
        for (TreeNode root : rootNodes) {
            addNodeToDisplay(root);
        }
    }

    private void addNodeToDisplay(TreeNode node) {
        displayNodes.add(node);
        if (node.isExpanded) {
            for (TreeNode child : node.children) {
                addNodeToDisplay(child);
            }
        }
    }

    // Tree Node Data Class
    private static class TreeNode {
        ViewBean viewBean;
        int depth;
        boolean isExpanded;
        List<TreeNode> children = new ArrayList<>();

        TreeNode(ViewBean viewBean, int depth) {
            this.viewBean = viewBean;
            this.depth = depth;
        }

        boolean hasChildren() {
            return !children.isEmpty();
        }
    }

    // RecyclerView Adapter
    private class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_view_tree_node, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TreeNode node = displayNodes.get(position);
            
            holder.tvTitle.setText(node.viewBean.id);
            
            String typeName = ViewBean.getViewTypeName(node.viewBean.type);
            if (node.viewBean.customView != null && !node.viewBean.customView.isEmpty() && !node.viewBean.customView.equals("none") && !node.viewBean.customView.equals("NONE")) {
                typeName += " (" + node.viewBean.customView + ")";
            }
            holder.tvSubtitle.setText(typeName);
            holder.imgIcon.setImageResource(ViewBean.getViewTypeResId(node.viewBean.type));

            int paddingBase = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            int paddingDepth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, node.depth * 24, getResources().getDisplayMetrics());
            holder.rootLayout.setPadding(paddingBase + paddingDepth, holder.rootLayout.getPaddingTop(), 
                    holder.rootLayout.getPaddingRight(), holder.rootLayout.getPaddingBottom());

            if (node.hasChildren()) {
                holder.imgExpand.setVisibility(View.VISIBLE);
                holder.imgExpand.setRotation(node.isExpanded ? 90f : 0f);
            } else {
                holder.imgExpand.setVisibility(View.INVISIBLE);
            }

            holder.imgExpand.setOnClickListener(v -> {
                node.isExpanded = !node.isExpanded;
                
                holder.imgExpand.animate().rotation(node.isExpanded ? 90f : 0f).setDuration(200).start();
                
                refreshDisplayList();
                notifyDataSetChanged();
            });

            holder.rootLayout.setOnClickListener(v -> {
                listener.onSelected(node.viewBean.id);
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return displayNodes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout rootLayout;
            TextView tvTitle, tvSubtitle;
            ImageView imgExpand, imgIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                rootLayout = itemView.findViewById(R.id.root_layout);
                imgExpand = itemView.findViewById(R.id.img_expand);
                imgIcon = itemView.findViewById(R.id.img_icon);
                tvTitle = itemView.findViewById(R.id.tv_title);
                tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
            }
        }
    }
}
