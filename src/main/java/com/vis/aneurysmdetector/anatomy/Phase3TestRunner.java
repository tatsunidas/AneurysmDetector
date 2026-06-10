package com.vis.aneurysmdetector.anatomy;

import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.VesselTree;
import com.vis.aneurysmdetector.preprocessing.DenoiseFilter;
import com.vis.aneurysmdetector.preprocessing.JermanFilter3D;
import com.vis.aneurysmdetector.preprocessing.Skeletonizer;
import com.vis.aneurysmdetector.preprocessing.VesselSegmenter;
import com.vis.core.view.D3.ui.GLCanvas;
import com.vis.core.view.D3.ui.VolumeData;
import com.vis.core.view.D3.ui.VolumeLoader;

import org.lwjgl.opengl.awt.GLData;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;

import javax.swing.*;
import java.awt.*;

/**
 * Phase 3（グラフ構築・最適化パイプライン）の動作を検証するためのテスト用実行クラス。
 * NLM -> Jerman -> Segmentation -> Skeletonize -> Graph Build -> Pruning & Merging
 */
public class Phase3TestRunner extends JFrame {

    private GLCanvas segCanvas;
    private GLCanvas skelCanvas;

    public Phase3TestRunner() {
        setTitle("Phase 3: Graph Construction & Optimization Test");
        setSize(1200, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        GLData data = new GLData();
        data.majorVersion = 3;
        data.minorVersion = 3;
        data.profile = GLData.Profile.CORE;
        data.doubleBuffer = false;
        data.forwardCompatible = true;

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Vessel Segmentation (3D MIP)"));
        segCanvas = new GLCanvas(data);
        leftPanel.add(segCanvas, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Skeletonization (3D MIP)"));
        skelCanvas = new GLCanvas(data);
        rightPanel.add(skelCanvas, BorderLayout.CENTER);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        add(splitPane, BorderLayout.CENTER);

        javax.swing.Timer timer = new javax.swing.Timer(16, e -> {
            if (segCanvas != null) {
                segCanvas.render();
                segCanvas.repaint();
            }
            if (skelCanvas != null) {
                skelCanvas.render();
                skelCanvas.repaint();
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    public void runPipeline(String imagePath) {
        System.out.println("Loading image from: " + imagePath);
        ImagePlus rawImp = FolderOpener.open(imagePath);
        if (rawImp == null) {
            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(this, "Failed to load image.", "Error", JOptionPane.ERROR_MESSAGE)
            );
            return;
        }
        Image3D rawImage = new Image3D(rawImp);

        // ==============================================================
        // Phase 1 & 2: Preprocessing Pipeline (前回の成果物)
        // ==============================================================
        System.out.println("\n--- Starting Phase 1 & 2: Preprocessing ---");

        // 1. ノイズ除去 (NLM)
        DenoiseFilter denoiser = new DenoiseFilter(15, 1);
        Image3D denoisedImage = denoiser.apply(rawImage);

        // 2. 血管強調 (Jerman 3D Filter - 厳密版)
        JermanFilter3D jermanFilter = new JermanFilter3D();
        double[] sigmas = {1.0, 2.0, 3.0}; 
        Image3D jermanImage = jermanFilter.apply(denoisedImage, sigmas);
        
        // 3. 血管セグメンテーション (Top-Percentile + Morphology + 3D CCA)
        VesselSegmenter segmenter = new VesselSegmenter();
        Image3D segmentedMask = segmenter.segment(jermanImage);
        
        VolumeData segVol = VolumeLoader.loadDicom(segmentedMask.getImagePlus().duplicate());
        SwingUtilities.invokeLater(() -> {
            if (segVol != null) {
                segCanvas.setVolumeData(segVol);
                segCanvas.setMIPMode(true);
            }
        });

        // 4. スケルトナイズ (細線化)
        Skeletonizer skeletonizer = new Skeletonizer();
        Image3D skeletonImage = skeletonizer.skeletonize(segmentedMask);
        
        VolumeData skelVol = VolumeLoader.loadDicom(skeletonImage.getImagePlus().duplicate());
        SwingUtilities.invokeLater(() -> {
            if (skelVol != null) {
                skelCanvas.setVolumeData(skelVol);
                skelCanvas.setMIPMode(true);
            }
        });

        // ==============================================================
        // Phase 3: Graph Construction & Optimization (今回のメイン)
        // ==============================================================
        System.out.println("\n--- Starting Phase 3: Graph Construction & Optimization ---");

        // 5. グラフ構築
        TreeGraphBuilder builder = new TreeGraphBuilder();
        VesselTree tree = builder.build(skeletonImage);

        System.out.println("\n[Before Optimization]");
        tree.printStatistics();

        // 6. グラフの最適化 (枝刈り＆結合)
        GraphPruner pruner = new GraphPruner();
        
        // 6.1 長さ 3.0mm 未満の末端ヒゲを刈り取る（再帰的）
        // ※閾値は実際のMRA画像の解像度に合わせて調整してください（2.0〜5.0程度）
        pruner.prune(tree, 3.0); 

        // 6.2 ヒゲが消えて「1本道」になった箇所の中継ノードを消し、枝をガッチャンコする
        pruner.mergeLinearBranches(tree);

        System.out.println("\n[After Optimization]");
        tree.printStatistics();

        System.out.println("\nPipeline testing completed successfully!");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
        	Phase3TestRunner tester = new Phase3TestRunner();
            tester.setVisible(true);
        	String path = "./test-mra/";
            new Thread(() -> tester.runPipeline(path)).start();
        });
    }
}