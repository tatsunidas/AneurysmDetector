package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
import com.vis.core.view.D3.ui.GLCanvas;
import com.vis.core.view.D3.ui.VolumeData;
import com.vis.core.view.D3.ui.VolumeLoader;

import org.lwjgl.opengl.awt.GLData;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import ij.process.AutoThresholder;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Phase 2（前処理パイプライン）の動作を検証するためのテスト用実行クラス。
 * 提供された GLCanvas と VolumeLoader を用いて3Dレンダリングを行います。
 */
public class Phase2TestRunner extends JFrame {

    private GLCanvas segCanvas;
    private GLCanvas skelCanvas;

    public Phase2TestRunner() {
        setTitle("Phase 2: Preprocessing Pipeline Test");
        setSize(1200, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 3Dビューアを左右に並べる分割ペイン
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        // 1. OpenGLの設定データを作成（Viewer3DMainと同様）
        GLData data = new GLData();
        data.majorVersion = 3;
        data.minorVersion = 3;
        data.profile = GLData.Profile.CORE;
        data.doubleBuffer = false; // swapBufferのためfalse
        data.forwardCompatible = true;

        // 左側：セグメンテーション結果の3Dビュー
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Vessel Segmentation (3D MIP)"));
        segCanvas = new GLCanvas(data);
        leftPanel.add(segCanvas, BorderLayout.CENTER);

        // 右側：スケルトン化結果の3Dビュー
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Skeletonization (3D MIP)"));
        skelCanvas = new GLCanvas(data);
        rightPanel.add(skelCanvas, BorderLayout.CENTER);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        add(splitPane, BorderLayout.CENTER);

        // レンダリングループ（約60FPSでキャンバスを更新）
        javax.swing.Timer timer = new javax.swing.Timer(16, e -> {
            if (segCanvas != null) {
                segCanvas.render(); // AWTGLCanvasのメソッド
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

    /**
     * パイプラインを実行し、各ビューアに結果を転送します。
     */
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
        
        // NLMの結果はImageJの標準ウィンドウで2Dスライスとして表示（比較用）
        SwingUtilities.invokeLater(() -> denoisedImage.getImagePlus().show());

        // --- 2. 血管セグメンテーション (閾値処理 + 3D CCA) ---
        VesselSegmenter segmenter = new VesselSegmenter(0.001, 100);
        Image3D segmentedMask = segmenter.segment(denoisedImage);
        
        // VolumeLoaderは内部でimp.close()を呼ぶため、破壊されないよう duplicate() を渡す
        VolumeData segVol = VolumeLoader.loadDicom(segmentedMask.getImagePlus().duplicate());
        
        SwingUtilities.invokeLater(() -> {
            if (segVol != null) {
                segCanvas.setVolumeData(segVol);
                segCanvas.setMIPMode(true); // 血管の全体像はMIPが見やすい
            }
        });

        // --- 3. スケルトナイズ (細線化) ---
        Skeletonizer skeletonizer = new Skeletonizer();
        Image3D skeletonImage = skeletonizer.skeletonize(segmentedMask);
        
        VolumeData skelVol = VolumeLoader.loadDicom(skeletonImage.getImagePlus().duplicate());
        
        SwingUtilities.invokeLater(() -> {
            if (skelVol != null) {
                skelCanvas.setVolumeData(skelVol);
                skelCanvas.setMIPMode(true); // スケルトンもMIPで表示
            }
        });

        System.out.println("Pipeline testing completed successfully!");
    }

    public static void main(String[] args) {
        // UIの構築はEDT上で行う
        SwingUtilities.invokeLater(() -> {
            Phase2TestRunner tester = new Phase2TestRunner();
            tester.setVisible(true);

            // テスト用のサンプル画像パスを選択させるダイアログ
//            JFileChooser chooser = new JFileChooser();
//            chooser.setDialogTitle("Select MRA Image (TIFF or DICOM)");
//            if (chooser.showOpenDialog(tester) == JFileChooser.APPROVE_OPTION) {
//                String path = chooser.getSelectedFile().getAbsolutePath();
//                
//                // 別スレッドでパイプラインを実行し、UIフリーズを防ぐ
//                new Thread(() -> tester.runPipeline(path)).start();
//            }
            
            String path = "C:\\Users\\t_kob\\graphy-workspace\\aneurysmdetector\\test-mra\\2.25.60897258363892151286751972916588634459\\2.25.255462386210815247290642388116372930462";
            new Thread(() -> tester.runPipeline(path)).start();
        });
    }
}