/*
 * copyright visionary imaging services, inc.
 */
package com.vis.aneurysmdetector.feature;

import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 指定された3D領域（ボクセル集合）から、幾何学的な形状特徴量を計算するクラス。
 * 
 * 将来的なVolumetry評価用
 * 
 */
public class ShapeMetrics {

    private double voxelVolume;
    private double voxelFaceAreaX;
    private double voxelFaceAreaY;
    private double voxelFaceAreaZ;

    public ShapeMetrics(Image3D image) {
        double sx = image.getSpacingX();
        double sy = image.getSpacingY();
        double sz = image.getSpacingZ();
        
        this.voxelVolume = sx * sy * sz;
        
        // 各軸に垂直な面の面積（mm^2）
        this.voxelFaceAreaX = sy * sz;
        this.voxelFaceAreaY = sx * sz;
        this.voxelFaceAreaZ = sx * sy;
    }

    /**
     * 体積 (Volume) を計算します (単位: mm^3)
     */
    public double calculateVolume(List<Point3D> regionVoxels) {
        if (regionVoxels == null || regionVoxels.isEmpty()) return 0.0;
        return regionVoxels.size() * voxelVolume;
    }

    /**
     * 表面積 (Surface Area) を計算します (単位: mm^2)
     * 背景（領域外）と接しているボクセルの面をカウントして面積を出します。
     */
    public double calculateSurfaceArea(List<Point3D> regionVoxels) {
        if (regionVoxels == null || regionVoxels.isEmpty()) return 0.0;

        Set<Point3D> voxelSet = new HashSet<>(regionVoxels);
        double surfaceArea = 0.0;

        // 6方向の隣接座標へのオフセットと、対応する面の面積
        int[][] offsets = {
            {1, 0, 0}, {-1, 0, 0}, // X軸方向 (左右)
            {0, 1, 0}, {0, -1, 0}, // Y軸方向 (前後)
            {0, 0, 1}, {0, 0, -1}  // Z軸方向 (上下)
        };
        double[] faceAreas = {
            voxelFaceAreaX, voxelFaceAreaX,
            voxelFaceAreaY, voxelFaceAreaY,
            voxelFaceAreaZ, voxelFaceAreaZ
        };

        for (Point3D p : regionVoxels) {
            for (int i = 0; i < offsets.length; i++) {
                Point3D neighbor = new Point3D(p.x + offsets[i][0], p.y + offsets[i][1], p.z + offsets[i][2]);
                // 隣接ボクセルが領域内に含まれていなければ、その面は「表面」である
                if (!voxelSet.contains(neighbor)) {
                    surfaceArea += faceAreas[i];
                }
            }
        }
        return surfaceArea;
    }

    /**
     * 真球度 (Sphericity) を計算します。
     * 完全な球体で 1.0 に近づき、細長い・いびつな形ほど小さくなります。
     * 公式: $\Psi = \frac{\pi^{1/3} (6V)^{2/3}}{A}$
     */
    public double calculateSphericity(double volume, double surfaceArea) {
        if (surfaceArea <= 0.0) return 0.0;
        
        double numerator = Math.pow(Math.PI, 1.0 / 3.0) * Math.pow(6.0 * volume, 2.0 / 3.0);
        return numerator / surfaceArea;
    }

    /**
     * 体積と表面積の比 (Volume-to-Surface Area Ratio) を計算します。
     * 複雑に入り組んだ偽陽性ノイズを排除するためのコンパクト性の指標です。
     */
    public double calculateVolumeToSurfaceRatio(double volume, double surfaceArea) {
        if (surfaceArea <= 0.0) return 0.0;
        return volume / surfaceArea;
    }

    /**
     * 対象領域の全特徴量を一括で計算し、結果を配列等で返します。
     */
    public double[] evaluateAllMetrics(List<Point3D> regionVoxels) {
        double vol = calculateVolume(regionVoxels);
        double sa = calculateSurfaceArea(regionVoxels);
        double sphericity = calculateSphericity(vol, sa);
        double vtsRatio = calculateVolumeToSurfaceRatio(vol, sa);
        
        return new double[]{vol, sa, sphericity, vtsRatio};
    }
}