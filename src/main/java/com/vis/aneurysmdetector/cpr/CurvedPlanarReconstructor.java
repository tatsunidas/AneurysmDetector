package com.vis.aneurysmdetector.cpr;

import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.cpr.VesselPathInterpolator.PathPoint;

import ij.ImagePlus;
import ij.process.FloatProcessor;

import java.util.List;

/**
 * 補間された血管パス（PathPointリスト）と元の3Dボリュームを用いて、 診断用の Straightened
 * CPR（引き延ばし断面）画像を生成するクラス。
 * 
 * // 1. パスの補間とフレーム計算 (サンプリング間隔 0.5mm) List<PathPoint> smoothPath =
 * VesselPathInterpolator.createSmoothPath(targetBranch, segVolume, 0.5);
 * 
 * // 2. CPR画像の生成 (横幅 30mm, 解像度 0.5mm/pixel, 断面角度 0度) ImagePlus cprImage =
 * CurvedPlanarReconstructor.extractStraightenedCPR( segVolume, smoothPath,
 * 30.0, 0.5, 0.0);
 * 
 * // コントラスト(Window/Level)を元画像に合わせる
 * cprImage.setDisplayRange(rawImp.getDisplayRangeMin(),
 * rawImp.getDisplayRangeMax());
 * 
 * // 3. 既存の2Dビューアに表示 new SeriesWindow(cprImage, null, ViewMode.Normal);
 */
public class CurvedPlanarReconstructor {

    /**
     * 指定されたパスに沿ってボリュームデータをサンプリングし、CPR画像を生成します。
     * * @param volume           サンプリング元の3D画像データ
     * @param path             VesselPathInterpolatorで生成された滑らかなパスとフレーム
     * @param widthMm          生成するCPR画像の物理的な横幅（例: 20.0 mm）
     * @param pixelSpacingMm   生成するCPR画像の1ピクセルあたりのサイズ（例: 0.5 mm）
     * @param rotationAngleDeg 中心線を軸とした断面の回転角度（0度でNormal方向、90度でBinormal方向）
     * @return 展開された2Dの ImagePlus（Float形式）
     */
    public static ImagePlus extractStraightenedCPR(
            Image3D volume, 
            List<PathPoint> path, 
            double widthMm, 
            double pixelSpacingMm, 
            double rotationAngleDeg) {

        if (path == null || path.isEmpty()) return null;

        int height = path.size();
        int width = (int) Math.ceil(widthMm / pixelSpacingMm);
        
        // 医療画像の広いダイナミックレンジを保持するため FloatProcessor を使用
        FloatProcessor processor = new FloatProcessor(width, height);

        double angleRad = Math.toRadians(rotationAngleDeg);
        double cosA = Math.cos(angleRad);
        double sinA = Math.sin(angleRad);

        // Y軸（画像の縦方向）はパスの進行方向に相当
        for (int y = 0; y < height; y++) {
            PathPoint pp = path.get(y);

            // 指定された回転角度に基づいて、サンプリング方向のベクトルを合成
            // Direction = cos(θ) * Normal + sin(θ) * Binormal
            double dirX = cosA * pp.normal.x + sinA * pp.binormal.x;
            double dirY = cosA * pp.normal.y + sinA * pp.binormal.y;
            double dirZ = cosA * pp.normal.z + sinA * pp.binormal.z;

            // X軸（画像の横方向）はパスからの横距離に相当
            for (int x = 0; x < width; x++) {
                // 画像の中心(width/2)を中心線(距離0)とする
                double distFromCenterMm = (x - width / 2.0) * pixelSpacingMm;

                // サンプリングする3D物理座標(mm)を計算
                double physX = pp.position.x + distFromCenterMm * dirX;
                double physY = pp.position.y + distFromCenterMm * dirY;
                double physZ = pp.position.z + distFromCenterMm * dirZ;

                // 物理座標(mm)をボリュームデータのボクセルインデックスに変換
                double voxX = physX / volume.getSpacingX();
                double voxY = physY / volume.getSpacingY();
                double voxZ = physZ / volume.getSpacingZ();

                // Tri-linear補間で輝度値をサンプリング
                float pixelValue = (float) sampleTrilinear(volume, voxX, voxY, voxZ);
                
                // FloatProcessorに値をセット（Yは上から下へ）
                processor.setf(x, y, pixelValue);
            }
        }

        // ImagePlusとしてラップして返す
        ImagePlus cprImp = new ImagePlus("Straightened CPR (" + rotationAngleDeg + " deg)", processor);
        
        // 2D画像のキャリブレーション（ピクセルサイズ）をセット
        ij.measure.Calibration cal = cprImp.getCalibration();
        cal.pixelWidth = pixelSpacingMm;
        cal.pixelHeight = pixelSpacingMm; // Y方向の間隔は、VesselPathInterpolatorで指定した samplingIntervalMm に一致させるのが理想
        cal.setUnit("mm");
        
        return cprImp;
    }

    /**
     * Tri-linear（三線形）補間を用いて、小数座標のボクセル輝度値を滑らかに取得します。
     */
    private static double sampleTrilinear(Image3D vol, double x, double y, double z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        // 各軸における端数（重み）
        double xd = x - x0;
        double yd = y - y0;
        double zd = z - z0;

        // 周囲8点のピクセル値を取得
        double c000 = getSafePixel(vol, x0, y0, z0);
        double c100 = getSafePixel(vol, x1, y0, z0);
        double c010 = getSafePixel(vol, x0, y1, z0);
        double c110 = getSafePixel(vol, x1, y1, z0);
        double c001 = getSafePixel(vol, x0, y0, z1);
        double c101 = getSafePixel(vol, x1, y0, z1);
        double c011 = getSafePixel(vol, x0, y1, z1);
        double c111 = getSafePixel(vol, x1, y1, z1);

        // X軸方向の補間
        double c00 = c000 * (1 - xd) + c100 * xd;
        double c10 = c010 * (1 - xd) + c110 * xd;
        double c01 = c001 * (1 - xd) + c101 * xd;
        double c11 = c011 * (1 - xd) + c111 * xd;

        // Y軸方向の補間
        double c0 = c00 * (1 - yd) + c10 * yd;
        double c1 = c01 * (1 - yd) + c11 * yd;

        // Z軸方向の補間
        return c0 * (1 - zd) + c1 * zd;
    }

    /**
     * ボリュームの境界外アクセスを防ぐ安全なピクセル取得メソッド。
     * 境界外は背景(0.0)として扱います。
     */
    private static double getSafePixel(Image3D vol, int x, int y, int z) {
        if (x < 0 || x >= vol.getWidth() ||
            y < 0 || y >= vol.getHeight() ||
            z < 0 || z >= vol.getDepth()) {
            return 0.0;
        }
        return vol.getPixelValue(x, y, z);
    }
}