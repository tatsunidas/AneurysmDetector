package com.vis.aneurysmdetector.feature;

import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.VesselBranch;

import java.util.List;

/**
 * 中心線に沿って断面を評価し、血管の基本直径や局所的な直径変化率を計算するクラス。
 */
public class DiameterMetrics {

    private Image3D binaryMask;
    
    // オプション: 距離変換マップ（Distance Transform Map）を保持しておくと高速化できます
    // private Image3D distanceMap;

    public DiameterMetrics(Image3D binaryMask) {
        this.binaryMask = binaryMask;
    }

    /**
     * 枝の母血管としての基本直径（Baseline Diameter）を計算します。
     * 瘤（異常な膨らみ）の影響を排除するため、両端付近の正常と思われる部分の平均、
     * または全体の中央値などを採用します。
     */
    public double calculateBaselineDiameter(VesselBranch branch) {
        List<Point3D> centerline = branch.getCenterline();
        if (centerline == null || centerline.isEmpty()) return 0.0;

        int size = centerline.size();
        if (size < 5) {
            // 枝が短すぎる場合は中央の点の直径を基本値とする
            return getLocalDiameterAt(centerline.get(size / 2));
        }

        // 枝の両端（分岐部付近）の数点の太さをサンプリングして平均化
        double sumDiameter = 0.0;
        int sampleCount = 0;
        
        // 始点側
        for (int i = 0; i < Math.min(3, size); i++) {
            sumDiameter += getLocalDiameterAt(centerline.get(i));
            sampleCount++;
        }
        // 終点側
        for (int i = Math.max(0, size - 3); i < size; i++) {
            sumDiameter += getLocalDiameterAt(centerline.get(i));
            sampleCount++;
        }
        
        return sumDiameter / sampleCount;
    }

    /**
     * 枝における局所的な最大直径（Local Maximum Diameter）を計算します。
     */
    public double calculateMaxDiameter(VesselBranch branch) {
        List<Point3D> centerline = branch.getCenterline();
        if (centerline == null || centerline.isEmpty()) return 0.0;

        double maxDia = 0.0;
        for (Point3D p : centerline) {
            double currentDia = getLocalDiameterAt(p);
            if (currentDia > maxDia) {
                maxDia = currentDia;
            }
        }
        return maxDia;
    }

    /**
     * 局所的な直径の変化率 (Local Diameter Change Rate) を計算します。
     * 値が 1.5 や 2.0 などを超える場合、局所的な膨瘤（動脈瘤候補）である可能性が高くなります。
     */
    public double calculateDiameterChangeRate(VesselBranch branch) {
        double baseline = calculateBaselineDiameter(branch);
        if (baseline <= 0.0) return 0.0; // ゼロ除算防止

        double maxDiameter = calculateMaxDiameter(branch);
        return maxDiameter / baseline;
    }

    /**
     * 特定の点における血管の直径（近似値）を取得します。
     * ※本来は距離変換（Distance Transform）マップから値を取得するのが高速ですが、
     * ここでは簡略化のため、指定点から背景（値=0）にぶつかるまでの最小距離を探索して2倍します。
     */
    private double getLocalDiameterAt(Point3D p) {
        double minDistanceSq = Double.MAX_VALUE;
        double sx = binaryMask.getSpacingX();
        double sy = binaryMask.getSpacingY();
        double sz = binaryMask.getSpacingZ();

        // 探索範囲（例えば半径10mm以内。計算コスト削減のため制限）
        int searchRadius = 15; // ピクセル数

        for (int dz = -searchRadius; dz <= searchRadius; dz++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                    int nx = p.x + dx;
                    int ny = p.y + dy;
                    int nz = p.z + dz;

                    // 背景（血管外）にぶつかった場合、そこまでの距離を計算
                    if (binaryMask.getPixelValue(nx, ny, nz) == 0) {
                        double distSq = (dx * sx) * (dx * sx) + 
                                        (dy * sy) * (dy * sy) + 
                                        (dz * sz) * (dz * sz);
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                        }
                    }
                }
            }
        }

        if (minDistanceSq == Double.MAX_VALUE) return 0.0;
        
        // 最小距離（半径）の2倍を直径とする
        double radius = Math.sqrt(minDistanceSq);
        return radius * 2.0;
    }
}