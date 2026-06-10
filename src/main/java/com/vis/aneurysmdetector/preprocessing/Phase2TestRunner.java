package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
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
 * Phase 2（前処理パイプライン）の動作を検証するためのテスト用実行クラス。
 * NLM -> Jerman -> Segmenter(Percentile + Morphology + CCA) -> Skeletonizer
 */
public class Phase2TestRunner extends JFrame {

    private GLCanvas segCanvas;
    private GLCanvas skelCanvas;

    public Phase2TestRunner() {
        setTitle("Phase 2: Preprocessing Pipeline Test (Jerman Filter + Morphology)");
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

        // --- 1. ノイズ除去 (NLM) ---
        DenoiseFilter denoiser = new DenoiseFilter(15, 1);
        Image3D denoisedImage = denoiser.apply(rawImage);

        // --- 2. 血管強調 (Jerman 3D Filter) ---
        // 血管の太さに合わせたスケールを設定 (1.0, 2.0, 3.0)
        JermanFilter3D jermanFilter = new JermanFilter3D();
        double[] sigmas = {1.0, 2.0, 3.0}; 
        Image3D jermanImage = jermanFilter.apply(denoisedImage, sigmas);
                
//        IJ.saveAsTiff(jermanImage.getImagePlus(), "testJerman.tif");
        
		// --- 3. 血管セグメンテーション (Top-Percentile + Morphology + 3D CCA) ---
		// デフォルト: 上位0.5%、微小ゴミ100vox、クロージング半径2.0
		VesselSegmenter segmenter = new VesselSegmenter();
		Image3D segmentedMask = segmenter.segment(jermanImage);

		VolumeData segVol = VolumeLoader.loadDicom(segmentedMask.getImagePlus().duplicate());
		SwingUtilities.invokeLater(() -> {
			if (segVol != null) {
				segCanvas.setVolumeData(segVol);
				segCanvas.setMIPMode(true);
			}
		});

		// --- 4. スケルトナイズ (細線化) ---
		Skeletonizer skeletonizer = new Skeletonizer();
		Image3D skeletonImage = skeletonizer.skeletonize(segmentedMask);

		VolumeData skelVol = VolumeLoader.loadDicom(skeletonImage.getImagePlus().duplicate());
		SwingUtilities.invokeLater(() -> {
			if (skelVol != null) {
				skelCanvas.setVolumeData(skelVol);
				skelCanvas.setMIPMode(true);
			}
		});

        System.out.println("Pipeline testing completed successfully!");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Phase2TestRunner tester = new Phase2TestRunner();
            tester.setVisible(true);
            String path = "./test-mra";
            new Thread(() -> tester.runPipeline(path)).start();
        });
    }
}