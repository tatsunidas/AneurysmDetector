package com.vis.aneurysmdetector.cpr;

import com.vis.aneurysmdetector.core.Branch;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.Image3D;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 血管のBranchパス（離散的なボクセル座標）を滑らかな物理座標に補間し、
 * CPR（Curved Planar Reconstruction）に必要な Parallel Transport Frame を計算するクラス。
 */
public class VesselPathInterpolator {

    public static class PathPoint {
        public Vector3d position; // 物理座標 (mm)
        public Vector3d tangent;  // 進行方向の単位ベクトル
        public Vector3d normal;   // 断面の横方向 (X軸)
        public Vector3d binormal; // 断面の縦方向 (Y軸)

        public PathPoint(Vector3d pos) {
            this.position = pos;
        }
    }

    /**
     * Branchから滑らかなCPRパス（フレーム付き）を生成します。
     * @param branch 補間対象の血管ブランチ
     * @param image3D 物理空間への変換用スペース情報
     * @param samplingIntervalMm 補間ポイントの間隔（例: 0.5mm）
     * @return 補間・フレーム計算済みのPathPointリスト
     */
    public static List<PathPoint> createSmoothPath(Branch branch, Image3D image3D, double samplingIntervalMm) {
        List<Point3D> rawNodes = branch.getPath();
        if (rawNodes == null || rawNodes.size() < 2) {
            return new ArrayList<>();
        }

        // 1. ボクセル座標(Point3D)を物理座標(Vector3d)に変換
        List<Vector3d> physicalPoints = new ArrayList<>();
        for (Point3D p : rawNodes) {
            double px = p.x * image3D.getSpacingX();
            double py = p.y * image3D.getSpacingY();
            double pz = p.z * image3D.getSpacingZ();
            physicalPoints.add(new Vector3d(px, py, pz));
        }

        // 2. Catmull-Rom スプラインで滑らかに等間隔サンプリング
        List<Vector3d> smoothPoints = resampleSpline(physicalPoints, samplingIntervalMm);

        // 3. Parallel Transport Frame (Bishop Frame) を計算してねじれを防ぐ
        return calculateParallelTransportFrames(smoothPoints);
    }

    /**
     * Catmull-Rom スプラインを用いて、指定した物理距離間隔でパスを再サンプリングします。
     */
    private static List<Vector3d> resampleSpline(List<Vector3d> points, double interval) {
        List<Vector3d> result = new ArrayList<>();
        if (points.size() < 2) return result;

        // 総延長からサンプル数を決定
        double totalLength = 0;
        double[] distances = new double[points.size()];
        distances[0] = 0;
        for (int i = 1; i < points.size(); i++) {
            totalLength += points.get(i - 1).distance(points.get(i));
            distances[i] = totalLength;
        }

        int numSamples = (int) Math.ceil(totalLength / interval);
        
        for (int i = 0; i <= numSamples; i++) {
            double t = i * interval;
            if (t > totalLength) t = totalLength;
            
            // 現在の t がどの区間にあるかを探す
            int idx = 0;
            while (idx < distances.length - 2 && distances[idx + 1] < t) {
                idx++;
            }

            // Catmull-Rom のための 4点 (P0, P1, P2, P3) を取得（端の処理を含む）
            Vector3d p0 = points.get(Math.max(0, idx - 1));
            Vector3d p1 = points.get(idx);
            Vector3d p2 = points.get(Math.min(points.size() - 1, idx + 1));
            Vector3d p3 = points.get(Math.min(points.size() - 1, idx + 2));

            // 区間内の局所的なパラメータ u (0.0 ~ 1.0)
            double segmentLength = distances[idx + 1] - distances[idx];
            double u = (segmentLength == 0) ? 0 : (t - distances[idx]) / segmentLength;

            Vector3d interpPos = catmullRom(p0, p1, p2, p3, u);
            result.add(interpPos);
        }

        return result;
    }

    /**
     * Catmull-Rom スプラインの補間計算
     */
    private static Vector3d catmullRom(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double u) {
        double u2 = u * u;
        double u3 = u2 * u;

        double f0 = -0.5 * u3 + u2 - 0.5 * u;
        double f1 = 1.5 * u3 - 2.5 * u2 + 1.0;
        double f2 = -1.5 * u3 + 2.0 * u2 + 0.5 * u;
        double f3 = 0.5 * u3 - 0.5 * u2;

        return new Vector3d(
            f0 * p0.x + f1 * p1.x + f2 * p2.x + f3 * p3.x,
            f0 * p0.y + f1 * p1.y + f2 * p2.y + f3 * p3.y,
            f0 * p0.z + f1 * p1.z + f2 * p2.z + f3 * p3.z
        );
    }

    /**
     * Parallel Transport Frame (Bishop Frame) を用いてねじれのない法線ベクトル群を計算します。
     */
    private static List<PathPoint> calculateParallelTransportFrames(List<Vector3d> points) {
        List<PathPoint> path = new ArrayList<>();
        if (points.size() < 2) return path;

        // 1. 各点の初期セットアップと接線(Tangent)の計算
        for (int i = 0; i < points.size(); i++) {
            PathPoint pp = new PathPoint(points.get(i));
            
            // 中心差分で接線を計算（両端は前進/後退差分）
            Vector3d t = new Vector3d();
            if (i == 0) {
                points.get(1).sub(points.get(0), t);
            } else if (i == points.size() - 1) {
                points.get(i).sub(points.get(i - 1), t);
            } else {
                points.get(i + 1).sub(points.get(i - 1), t);
            }
            t.normalize();
            pp.tangent = t;
            path.add(pp);
        }

        // 2. 最初の点の法線(Normal)と従法線(Binormal)を決定
        PathPoint p0 = path.get(0);
        Vector3d initialNormal = new Vector3d();
        
        // PlanarSupport の堅牢な正規化・直交化ロジックを借用
        // (Tangent を Z軸に見立てて、適当な初期 X軸を計算させる)
        Vector3d arbitraryUp = new Vector3d(0, 0, 1);
        if (Math.abs(p0.tangent.z) > 0.9) arbitraryUp.set(1, 0, 0); // 進行方向がZ軸に近い場合の回避
        
        p0.tangent.cross(arbitraryUp, initialNormal).normalize();
        p0.normal = initialNormal;
        
        p0.binormal = new Vector3d();
        p0.tangent.cross(p0.normal, p0.binormal).normalize();

        // 3. 前の点のフレームを次の点へ「ねじらずに」回転して伝播させる (Parallel Transport)
        for (int i = 1; i < path.size(); i++) {
            PathPoint prev = path.get(i - 1);
            PathPoint curr = path.get(i);

            // 前の接線から現在の接線への回転軸(cross)と角度(dot)を計算
            Vector3d axis = new Vector3d();
            prev.tangent.cross(curr.tangent, axis);
            double angle = Math.acos(Math.max(-1.0, Math.min(1.0, prev.tangent.dot(curr.tangent))));

            curr.normal = new Vector3d(prev.normal);
            curr.binormal = new Vector3d(prev.binormal);

            // 接線が変化している場合のみ、NormalとBinormalを同じように回転させる
            if (axis.lengthSquared() > 1e-8) {
                axis.normalize();
                curr.normal.rotateAxis(angle, axis.x, axis.y, axis.z);
                curr.binormal.rotateAxis(angle, axis.x, axis.y, axis.z);
            }
            
            // 念のため誤差吸収の再直交化（PlanarSupportの考え方を適用）
            curr.normal.normalize();
            curr.tangent.cross(curr.normal, curr.binormal).normalize();
        }

        return path;
    }
}