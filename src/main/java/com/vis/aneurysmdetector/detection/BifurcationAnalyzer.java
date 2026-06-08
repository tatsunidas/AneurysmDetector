package com.vis.aneurysmdetector.detection;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.BifurcationNode;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.VesselBranch;
import com.vis.aneurysmdetector.core.VesselTree;
import com.vis.aneurysmdetector.feature.DiameterMetrics;
import com.vis.aneurysmdetector.feature.ShapeMetrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * すべての BifurcationNode（分岐部）を評価し、分岐部瘤の候補を抽出・スコアリングするクラス。
 */
public class BifurcationAnalyzer {

    private Image3D binaryMask;
    private ShapeMetrics shapeMetrics;
    private DiameterMetrics diameterMetrics;
    private SaliencyScorer scorer;

    // UIに提示する足切りスコア（これ以下の分岐部は表示しない）
    private static final double DISPLAY_SCORE_THRESHOLD = 0.4;

    public BifurcationAnalyzer(Image3D binaryMask) {
        this.binaryMask = binaryMask;
        this.shapeMetrics = new ShapeMetrics(binaryMask);
        this.diameterMetrics = new DiameterMetrics(binaryMask);
        this.scorer = new SaliencyScorer();
    }

    /**
     * ツリー内の全分岐部を評価し、サリアンシースコアの高い順にソートされた候補リストを返します。
     */
    public List<AneurysmCandidate> analyze(VesselTree tree) {
        List<AneurysmCandidate> candidates = new ArrayList<>();

        for (BifurcationNode node : tree.getNodes()) {
            
            // 1. ノード周辺の局所的なボクセル領域（クロップ）を取得
            List<Point3D> localRegion = extractLocalRegion(node.getCenterPoint(), 10); // 10ボクセル半径
            
            // 2. 特徴量の計算
            double[] shapeFeatures = shapeMetrics.evaluateAllMetrics(localRegion);
            double sphericity = shapeFeatures[2];
            double vtsRatio = shapeFeatures[3];
            
            // 分岐部における直径変化率（接続されている母血管の基本直径と、分岐点コアの最大直径を比較）
            double diameterChange = evaluateBifurcationDiameterChange(node);

            // 3. サリアンシースコアの計算
            double score = scorer.calculateScore(sphericity, vtsRatio, diameterChange);

            if (score >= DISPLAY_SCORE_THRESHOLD) {
                AneurysmCandidate candidate = new AneurysmCandidate(
                        "Bifur-" + UUID.randomUUID().toString(),
                        AneurysmCandidate.CandidateType.BIFURCATION_ANEURYSM,
                        node.getCenterPoint()
                );
                
                candidate.setRelatedNode(node);
                // 解剖学的タグ（接続されている枝から代表タグを一つ取得）
                candidate.setAnatomicalLabel(getRepresentativeLabel(node));
                candidate.setSphericity(sphericity);
                candidate.setVolumeToSurfaceRatio(vtsRatio);
                candidate.setDiameterChangeRate(diameterChange);
                candidate.setSaliencyScore(score);

                candidates.add(candidate);
            }
        }

        // スコアの高い順（怪しい順）に降順ソート
        candidates.sort(Comparator.comparingDouble(AneurysmCandidate::getSaliencyScore).reversed());

        return candidates;
    }

    /**
     * 指定された中心点から一定半径内の血管ボクセル（255）を抽出します。
     */
    private List<Point3D> extractLocalRegion(Point3D center, int radius) {
        List<Point3D> region = new ArrayList<>();
        // 球状の探索範囲
        int rSq = radius * radius;
        for (int z = -radius; z <= radius; z++) {
            for (int y = -radius; y <= radius; y++) {
                for (int x = -radius; x <= radius; x++) {
                    if (x*x + y*y + z*z <= rSq) {
                        int nx = center.x + x;
                        int ny = center.y + y;
                        int nz = center.z + z;
                        if (binaryMask.getPixelValue(nx, ny, nz) > 0) {
                            region.add(new Point3D(nx, ny, nz));
                        }
                    }
                }
            }
        }
        return region;
    }

    /**
     * 分岐部ノードにおける直径変化率を計算します。
     */
    private double evaluateBifurcationDiameterChange(BifurcationNode node) {
        // 分岐部の中心における絶対的な太さ（内接球の直径など）を取得（モックとして固定値）
        double coreDiameter = 6.0; 
        
        double maxBaseline = 0.0;
        for (VesselBranch branch : node.getConnectedBranches()) {
            double baseline = diameterMetrics.calculateBaselineDiameter(branch);
            if (baseline > maxBaseline) {
                maxBaseline = baseline;
            }
        }
        
        if (maxBaseline <= 0) return 0.0;
        return coreDiameter / maxBaseline;
    }

    private String getRepresentativeLabel(BifurcationNode node) {
        if (node.getConnectedBranches().isEmpty()) return "Unknown";
        return node.getConnectedBranches().get(0).getAnatomicalLabel();
    }
}