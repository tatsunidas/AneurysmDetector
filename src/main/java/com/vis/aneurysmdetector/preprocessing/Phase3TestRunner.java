package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.anatomy.TreeGraphBuilder;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.VesselBranch;
import com.vis.aneurysmdetector.core.VesselTree;
import com.vis.aneurysmdetector.feature.DiameterMetrics;
import com.vis.aneurysmdetector.feature.ShapeMetrics;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import ij.process.AutoThresholder;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.util.List;

/**
 * Phase 3（グラフ構築と幾何学特徴量計算）の動作を検証するCUIテストランナー。
 */
public class Phase3TestRunner {

    public void runTest(String imagePath) {
        System.out.println("Loading image from: " + imagePath);
        ImagePlus rawImp = FolderOpener.open(imagePath);
        if (rawImp == null) {
            System.err.println("Failed to load image.");
            return;
        }
        Image3D rawImage = new Image3D(rawImp);

        // --- Phase 2: 前処理パイプラインの実行 ---
        System.out.println("\n--- Executing Phase 2: Preprocessing ---");
        DenoiseFilter denoiser = new DenoiseFilter(15, 1);
        Image3D denoisedImage = denoiser.apply(rawImage);

        VesselSegmenter segmenter = new VesselSegmenter();
        Image3D segmentedMask = segmenter.segment(denoisedImage);

        Skeletonizer skeletonizer = new Skeletonizer();
        Image3D skeletonImage = skeletonizer.skeletonize(segmentedMask);

        // --- Phase 3: グラフ構築 ---
        System.out.println("\n--- Executing Phase 3: Graph Building ---");
        TreeGraphBuilder graphBuilder = new TreeGraphBuilder();
        VesselTree tree = graphBuilder.build(skeletonImage);

        List<VesselBranch> branches = tree.getBranches();
        System.out.println("Graph built successfully!");
        System.out.println("Total Bifurcation Nodes: " + tree.getNodes().size());
        System.out.println("Total Vessel Branches: " + branches.size());

        // --- Phase 3: 特徴量計算のテスト ---
        System.out.println("\n--- Executing Phase 3: Feature Extraction Test ---");
        ShapeMetrics shapeMetrics = new ShapeMetrics(segmentedMask);
        DiameterMetrics diameterMetrics = new DiameterMetrics(segmentedMask);

        // 全て出力すると膨大になるため、最初の5本の枝のみテスト
        int limit = Math.min(5, branches.size());
        for (int i = 0; i < limit; i++) {
            VesselBranch branch = branches.get(i);
            System.out.println("\n[Testing Branch " + branch.getBranchId() + "]");
            System.out.println("Length (Voxels): " + branch.getCenterline().size());

            // 特徴量の計算
            double[] shapeFeatures = shapeMetrics.evaluateAllMetrics(branch.getCenterline());
            double volume = shapeFeatures[0];
            double surfaceArea = shapeFeatures[1];
            double sphericity = shapeFeatures[2];
            double baselineDia = diameterMetrics.calculateBaselineDiameter(branch);
            double maxDia = diameterMetrics.calculateMaxDiameter(branch);
            double diaChangeRate = diameterMetrics.calculateDiameterChangeRate(branch);

            // 結果の出力
            System.out.printf("  - Volume: %.2f mm^3\n", volume);
            System.out.printf("  - Surface Area: %.2f mm^2\n", surfaceArea);
            System.out.printf("  - Sphericity: %.4f (1.0=Perfect Sphere)\n", sphericity);
            System.out.printf("  - Baseline Diameter: %.2f mm\n", baselineDia);
            System.out.printf("  - Max Diameter: %.2f mm\n", maxDia);
            System.out.printf("  - Diameter Change Rate: %.2f\n", diaChangeRate);
        }
        
        System.out.println("\nPhase 3 Testing Completed!");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
//            JFileChooser chooser = new JFileChooser();
//            chooser.setDialogTitle("Select MRA Image for Phase 3 Test");
//            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
//                String path = chooser.getSelectedFile().getAbsolutePath();
//                new Thread(() -> new Phase3TestRunner().runTest(path)).start();
//            }
        	String path = "C:\\Users\\t_kob\\graphy-workspace\\aneurysmdetector\\test-mra\\2.25.60897258363892151286751972916588634459\\2.25.255462386210815247290642388116372930462";
        	new Thread(() -> new Phase3TestRunner().runTest(path)).start();
        });
    }
}