package com.vis.aneurysmdetector.ui;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.preprocessing.DenoiseFilter;
import com.vis.aneurysmdetector.preprocessing.JermanFilter3D;
import com.vis.aneurysmdetector.preprocessing.VesselSegmenter;
import com.vis.core.view.D3.ui.GLCanvas;
import com.vis.core.view.D3.ui.VolumeData;
import com.vis.core.view.D3.ui.VolumeLoader;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import org.lwjgl.opengl.awt.GLData;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * AneurysmGLCanvas の描画能力（血管マスク ＋ 3D枠線）をテストするクラス。
 */
public class CanvasTestRunner extends JFrame {

    private AneurysmGLCanvas canvas;

    public CanvasTestRunner() {
        setTitle("AneurysmGLCanvas Test (Segmented Vessel + Bounding Box)");
        setSize(800, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ========================================================
        // ★修正: OpenGLの初期化パラメーター（Phase 2と同じ設定を復元）
        // ========================================================
        GLData data = new GLData();
        data.majorVersion = 3;
        data.minorVersion = 3;
        data.profile = GLData.Profile.CORE;
        data.doubleBuffer = false;       // ★必須！Linux等でのAWTバッファ競合（グレー画面）を防ぐ
        data.forwardCompatible = true;   // ★必須！Core Profileの安定稼働

        JPanel glPanel = new JPanel(new BorderLayout());
        canvas = new AneurysmGLCanvas(data);
        canvas.setPreferredSize(new Dimension(800, 800)); // 初期サイズゼロを防止
        glPanel.add(canvas, BorderLayout.CENTER);
        add(glPanel, BorderLayout.CENTER);

        // 再描画タイマー
        Timer timer = new Timer(16, e -> {
            if (canvas != null && canvas.isValid()) {
                canvas.render();
                canvas.repaint();
            }
        });
        timer.start();
    }

    public void runPipeline(String imagePath) {
        System.out.println("Loading image from: " + imagePath);
        ImagePlus rawImp = FolderOpener.open(imagePath);
        if (rawImp == null) {
            System.err.println("Failed to load image.");
            return;
        }
        Image3D rawImage = new Image3D(rawImp);

        System.out.println("Running Pipeline...");
        DenoiseFilter denoiser = new DenoiseFilter(15, 1);
        Image3D denoisedImage = denoiser.apply(rawImage);

        JermanFilter3D jermanFilter = new JermanFilter3D();
        Image3D jermanImage = jermanFilter.apply(denoisedImage, new double[]{1.0, 2.0, 3.0});
        
        VesselSegmenter segmenter = new VesselSegmenter();
        Image3D segmentedMask = segmenter.segment(jermanImage);

        VolumeData segVol = VolumeLoader.loadDicom(segmentedMask.getImagePlus().duplicate());

        List<AneurysmCandidate> candidates = new ArrayList<>();
        AneurysmCandidate dummy = new AneurysmCandidate(null);
        int cx = segVol.width / 2;
        int cy = segVol.height / 2;
        int cz = segVol.depth / 2;
        dummy.addPoint(new Point3D(cx, cy, cz), 3.0, 0.9, 0.1); 
        candidates.add(dummy);

        SwingUtilities.invokeLater(() -> {
            if (segVol != null) {
                canvas.setVolumeData(segVol);
                canvas.setCandidates(candidates);
                canvas.setShowBoundingBoxes(true); 
                canvas.setOrthoRoiMode(GLCanvas.OrthoRoiMode.FLOAT_3D);
                canvas.optimizeContrast(); // コントラストを自動調整
                canvas.resetCamera();
            }
        });
        
        System.out.println("Pipeline testing completed successfully!");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CanvasTestRunner tester = new CanvasTestRunner();
            tester.setVisible(true);
            String path = "./test-mra";
            new Thread(() -> tester.runPipeline(path)).start();
        });
    }
}