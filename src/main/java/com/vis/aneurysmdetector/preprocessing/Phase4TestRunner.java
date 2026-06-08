package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.anatomy.TreeGraphBuilder;
import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.VesselTree;
import com.vis.aneurysmdetector.detection.BifurcationAnalyzer;
import com.vis.aneurysmdetector.detection.Pruner;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Phase 4（動脈瘤候補の検出・スコアリング・フィルタリング）の動作を検証するテストランナー。
 * 前処理からグラフ構築、最終判定までのバックエンド全工程を一気通貫でテストします。
 */
public class Phase4TestRunner {

    public void runTest(String imagePath) {
        System.out.println("Loading image from: " + imagePath);
        ImagePlus rawImp = FolderOpener.open(imagePath);
        if (rawImp == null) {
            System.err.println("Failed to load image.");
            return;
        }
        Image3D rawImage = new Image3D(rawImp);

        // ====================================================================
        // 1. Phase 2: 前処理パイプライン (固定版パーセンタイル法)
        // ====================================================================
        System.out.println("\n--- [Phase 2] Preprocessing Pipeline ---");
        DenoiseFilter denoiser = new DenoiseFilter(15, 1);
        Image3D denoisedImage = denoiser.apply(rawImage);

        // 先ほど結果の良かった上位0.5%を切り出す設定で固定
        VesselSegmenter segmenter = new VesselSegmenter(0.005, 100); 
        Image3D segmentedMask = segmenter.segment(denoisedImage);

        Skeletonizer skeletonizer = new Skeletonizer();
        Image3D skeletonImage = skeletonizer.skeletonize(segmentedMask);

        // ====================================================================
        // 2. Phase 3: グラフ構造の構築
        // ====================================================================
        System.out.println("\n--- [Phase 3] Graph Network Construction ---");
        TreeGraphBuilder graphBuilder = new TreeGraphBuilder();
        VesselTree tree = graphBuilder.build(skeletonImage);

        System.out.println("  - Total Initial Nodes: " + tree.getNodes().size());
        System.out.println("  - Total Initial Branches: " + tree.getBranches().size());

        // ====================================================================
        // 3. Phase 4: 動脈瘤検出ロジックの実行
        // ====================================================================
        System.out.println("\n--- [Phase 4] Aneurysm Detection Engine ---");
        
        // ① 側壁瘤（短い枝・Spurious Branch）の評価とプルーニング
        System.out.println("Running Pruner & Sidewall Evaluator...");
        Pruner pruner = new Pruner(segmentedMask);
        List<AneurysmCandidate> branchCandidates = pruner.pruneAndExtractCandidates(tree);
        
        // ② 分岐部瘤（Bifurcation Node）の幾何学評価
        System.out.println("Running Bifurcation Analyzer...");
        BifurcationAnalyzer bifurcationAnalyzer = new BifurcationAnalyzer(segmentedMask);
        List<AneurysmCandidate> bifurcationCandidates = bifurcationAnalyzer.analyze(tree);

        // ====================================================================
        // 4. 結果の統合とランキング出力
        // ====================================================================
        System.out.println("\n--- [Phase 4] Detection Results Summary ---");
        
        // すべての動脈瘤候補を一つのリストに統合
        List<AneurysmCandidate> allCandidates = new ArrayList<>();
        allCandidates.addAll(branchCandidates);
        allCandidates.addAll(bifurcationCandidates);

        // サリアンシースコア（動脈瘤らしさ）の降順（高い順）でソート
        allCandidates.sort(Comparator.comparingDouble(AneurysmCandidate::getSaliencyScore).reversed());

        System.out.println("Total Aneurysm Candidates Found: " + allCandidates.size());
        System.out.println("----------------------------------------------------------------------------");
        System.out.printf("%-10s | %-20s | %-15s | %-10s | %-8s\n", 
                          "Rank", "Candidate ID", "Type", "Score", "Location");
        System.out.println("----------------------------------------------------------------------------");

        int rank = 1;
        for (AneurysmCandidate candidate : allCandidates) {
            // スコア上位20件、あるいはスコア0.5以上の重要候補のみ表示
            if (rank > 20 && candidate.getSaliencyScore() < 0.5) break;

            String typeStr = candidate.getType().name();
            com.vis.aneurysmdetector.core.Point3D pt = candidate.getCenterPoint();
            String locStr = String.format("(%d,%d,%d)", pt.x, pt.y, pt.z);

            System.out.printf("No.%-2d      | %-20s | %-15s | %-10.4f | %-8s\n", 
                              rank, 
                              candidate.getCandidateId(), 
                              typeStr, 
                              candidate.getSaliencyScore(), 
                              locStr);
            
            // 内部幾何学パラメーターの詳細デバッグ出力
            System.out.printf("           [Detail] Sphericity: %.4f | DiaChange: %.2f | RegionRatio: %.2f\n",
                              candidate.getSphericity(),
                              candidate.getDiameterChangeRate(),
                              candidate.getVolumeToSurfaceRatio());
            
            rank++;
        }
        System.out.println("----------------------------------------------------------------------------");
        System.out.println("Phase 4 Testing Completed Successfully!");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
//            JFileChooser chooser = new JFileChooser();
//            chooser.setDialogTitle("Select MRA Image for Phase 4 Integration Test");
//            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
//                String path = chooser.getSelectedFile().getAbsolutePath();
//                
//                // 計算負荷を考慮し別スレッドで処理を回す
//                new Thread(() -> new Phase4TestRunner().runTest(path)).start();
//            }
            String path = "C:\\Users\\t_kob\\graphy-workspace\\aneurysmdetector\\test-mra\\2.25.60897258363892151286751972916588634459\\2.25.255462386210815247290642388116372930462";
            new Thread(() -> new Phase4TestRunner().runTest(path)).start();
        });
    }
}