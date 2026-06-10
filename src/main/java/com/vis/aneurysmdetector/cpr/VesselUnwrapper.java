package com.vis.aneurysmdetector.cpr;

import com.vis.aneurysmdetector.core.Branch;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.Branch;

import java.util.List;

/**
 * 血管の直線化および円柱展開（CPR）を実行するクラス。
 */
public class VesselUnwrapper {

    private int angleSteps;
    private double maxSearchRadius;

    /**
     * @param angleSteps 360度を何分割して計測するか（例: 36なら10度刻み）
     * @param maxSearchRadius レイを飛ばす最大距離（mmまたはボクセル）。計算コストの制限用。
     */
    public VesselUnwrapper(int angleSteps, double maxSearchRadius) {
        this.angleSteps = angleSteps;
        this.maxSearchRadius = maxSearchRadius;
    }

    /**
     * 指定された枝を展開し、距離マップを持つ UnwrappedImage2D を生成します。
     * @param binaryMask 血管の2値化マスク（壁の判定に使用）
     * @param branch 展開対象の血管枝
     * @return 展開された2D画像（距離マップ）
     */
    public UnwrappedImage2D unwrap(Image3D binaryMask, Branch branch) {
//        List<Point3D> centerline = branch.getCenterline();
//        int length = centerline.size();
//        
//        // 結果を格納する2D配列 [長さ][角度]
//        double[][] distanceMap = new double[length][angleSteps];
//
//        double sx = binaryMask.getSpacingX();
//        double sy = binaryMask.getSpacingY();
//        double sz = binaryMask.getSpacingZ();
//
//        for (int i = 0; i < length; i++) {
//            Point3D currentPoint = centerline.get(i);
//            
//            // 1. 中心線に直交するローカル座標系（Tangent, Normal, Binormal）を計算
//            double[][] frame = computeOrthogonalFrame(centerline, i);
//            double[] normal = frame[0];
//            double[] binormal = frame[1];
//
//            // 2. 360度各方向へレイを飛ばし、壁までの距離を測る
//            for (int a = 0; a < angleSteps; a++) {
//                double theta = (2.0 * Math.PI * a) / angleSteps;
//                
//                // レイの方向ベクトル $\vec{d} = \cos(\theta)\vec{n} + \sin(\theta)\vec{b}$
//                double dx = Math.cos(theta) * normal[0] + Math.sin(theta) * binormal[0];
//                double dy = Math.cos(theta) * normal[1] + Math.sin(theta) * binormal[1];
//                double dz = Math.cos(theta) * normal[2] + Math.sin(theta) * binormal[2];
//                
//                // レイキャストを実行して壁までの距離を取得
//                double distance = castRay(binaryMask, currentPoint, dx, dy, dz, sx, sy, sz);
//                distanceMap[i][a] = distance;
//            }
//        }
//
//        return new UnwrappedImage2D(branch, distanceMap);
    	
    	return null;
    }

    /**
     * 中心線の特定の点における直交フレーム（法線ベクトルと従法線ベクトル）を計算します。
     * @return { {normalX, normalY, normalZ}, {binormalX, binormalY, binormalZ} }
     */
    private double[][] computeOrthogonalFrame(List<Point3D> centerline, int index) {
        // 簡易的な接線ベクトル（Tangent）の計算
        Point3D pPrev = centerline.get(Math.max(0, index - 1));
        Point3D pNext = centerline.get(Math.min(centerline.size() - 1, index + 1));
        
        double tx = pNext.x - pPrev.x;
        double ty = pNext.y - pPrev.y;
        double tz = pNext.z - pPrev.z;
        
        double lenT = Math.sqrt(tx*tx + ty*ty + tz*tz);
        if (lenT > 0) { tx /= lenT; ty /= lenT; tz /= lenT; } 
        else { tx = 1; ty = 0; tz = 0; } // フォールバック

        // 接線と線形独立な任意のベクトルを用いて外積から法線（Normal）を生成
        double refX = 0, refY = 1, refZ = 0;
        if (Math.abs(ty) > 0.9) {
            refX = 1; refY = 0; refZ = 0;
        }

        // Normal = Tangent x Reference
        double nx = ty * refZ - tz * refY;
        double ny = tz * refX - tx * refZ;
        double nz = tx * refY - ty * refX;
        double lenN = Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (lenN > 0) { nx /= lenN; ny /= lenN; nz /= lenN; }

        // Binormal = Tangent x Normal
        double bx = ty * nz - tz * ny;
        double by = tz * nx - tx * nz;
        double bz = tx * ny - ty * nx;
        double lenB = Math.sqrt(bx*bx + by*by + bz*bz);
        if (lenB > 0) { bx /= lenB; by /= lenB; bz /= lenB; }

        return new double[][]{ {nx, ny, nz}, {bx, by, bz} };
    }

    /**
     * 指定された方向（レイ）へステップを進め、血管外（背景）に出るまでの距離を返します。
     */
    private double castRay(Image3D mask, Point3D origin, double dx, double dy, double dz, double sx, double sy, double sz) {
        double stepSize = 0.5; // サンプリングステップ（ボクセル単位）
        double currentDist = 0.0;

        while (currentDist <= maxSearchRadius) {
            currentDist += stepSize;
            
            int sampleX = (int) Math.round(origin.x + currentDist * dx);
            int sampleY = (int) Math.round(origin.y + currentDist * dy);
            int sampleZ = (int) Math.round(origin.z + currentDist * dz);

            // 境界チェックまたは背景（0）に到達したら探索終了
            if (sampleX < 0 || sampleX >= mask.getWidth() ||
                sampleY < 0 || sampleY >= mask.getHeight() ||
                sampleZ < 0 || sampleZ >= mask.getDepth() ||
                mask.getPixelValue(sampleX, sampleY, sampleZ) == 0) {
                break;
            }
        }
        
        // ボクセル距離にSpacingを掛けて物理距離（mm）に近い値に補正する（簡易実装）
        // 厳密には方向ベクトルに基づくスカラー計算が必要
        double physicalDist = Math.sqrt(
            Math.pow(currentDist * dx * sx, 2) +
            Math.pow(currentDist * dy * sy, 2) +
            Math.pow(currentDist * dz * sz, 2)
        );
        
        return physicalDist;
    }
}