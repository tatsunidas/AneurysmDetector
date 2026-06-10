package com.vis.aneurysmdetector.detection;

import com.vis.aneurysmdetector.anatomy.GraphPruner;
import com.vis.aneurysmdetector.anatomy.TreeGraphBuilder;
import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.VesselTree;
import com.vis.aneurysmdetector.feature.DistanceTransform3D;
import com.vis.aneurysmdetector.feature.FeatureExtractor;
import com.vis.aneurysmdetector.preprocessing.DenoiseFilter;
import com.vis.aneurysmdetector.preprocessing.JermanFilter3D;
import com.vis.aneurysmdetector.preprocessing.Skeletonizer;
import com.vis.aneurysmdetector.preprocessing.VesselSegmenter;

import ij.ImagePlus;
import ij.plugin.FolderOpener;

import javax.swing.*;
import java.util.List;

/**
 * Phase 4（特徴量抽出と動脈瘤判定）までの全パイプラインを統合テストするランナー。
 */
public class Phase4TestRunner {

    public void runPipeline(String imagePath) {
        System.out.println("Loading image from: " + imagePath);
        ImagePlus rawImp = FolderOpener.open(imagePath);
        if (rawImp == null) {
            System.err.println("Failed to load image.");
            return;
        }
        Image3D rawImage = new Image3D(rawImp);

        // ==============================================================
        // Phase 1 & 2: Preprocessing
        // ==============================================================
        System.out.println("\n--- Phase 1 & 2: Preprocessing ---");
        DenoiseFilter denoiser = new DenoiseFilter(15, 1);
        Image3D denoisedImage = denoiser.apply(rawImage);

        JermanFilter3D jermanFilter = new JermanFilter3D();
        double[] sigmas = {1.0, 2.0, 3.0}; 
        Image3D jermanImage = jermanFilter.apply(denoisedImage, sigmas);
        
        VesselSegmenter segmenter = new VesselSegmenter();
        Image3D segmentedMask = segmenter.segment(jermanImage);
        
        Skeletonizer skeletonizer = new Skeletonizer();
        Image3D skeletonImage = skeletonizer.skeletonize(segmentedMask);

        // ==============================================================
        // Phase 3: Graph Construction & Optimization
        // ==============================================================
        System.out.println("\n--- Phase 3: Graph Construction & Optimization ---");
        TreeGraphBuilder builder = new TreeGraphBuilder();
        VesselTree tree = builder.build(skeletonImage);

        GraphPruner pruner = new GraphPruner();
        pruner.prune(tree, 3.0); 
        pruner.mergeLinearBranches(tree);
        tree.printStatistics();

        // ==============================================================
        // Phase 4: Feature Extraction & Detection
        // ==============================================================
        System.out.println("\n--- Phase 4: Feature Extraction & Detection ---");
        
        // 1. 距離マップの生成
        DistanceTransform3D dt3D = new DistanceTransform3D();
        Image3D distanceMap = dt3D.computeDistanceMap(segmentedMask);

        // 2. 特徴量の抽出 (Radius, Bulge Ratio, Shape Index, Gaussian Curvature)
        FeatureExtractor extractor = new FeatureExtractor();
        extractor.extractInscribedRadii(tree, distanceMap);
        extractor.extractBulgeRatiosAndCurvatures(tree, segmentedMask);

        // 3. 動脈瘤の検出 (閾値: Bulge>=1.35, SI>=0.65, K>0)
        AneurysmDetector detector = new AneurysmDetector(1.35, 0.65, 0.0);
        List<AneurysmCandidate> candidates = detector.detect(tree);

        // ==============================================================
        // 最終結果の出力
        // ==============================================================
        System.out.println("\n--- Final Detection Results ---");
        if (candidates.isEmpty()) {
            System.out.println("No aneurysms detected.");
        } else {
            for (int i = 0; i < candidates.size(); i++) {
                System.out.println("Candidate #" + (i + 1) + ": " + candidates.get(i).toString());
            }
        }
        
        System.out.println("\nPipeline completed successfully!");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
        	String path = "./test-mra/";
            new Thread(() -> new Phase4TestRunner().runPipeline(path)).start();
        });
    }
}