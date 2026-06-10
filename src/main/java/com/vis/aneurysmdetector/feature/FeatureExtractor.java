package com.vis.aneurysmdetector.feature;

import com.vis.aneurysmdetector.core.Branch;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.VesselTree;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.GaussianBlur3D;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 構築された血管ネットワークの各経路上に、距離マップ等から得られる 特徴量（本来の半径、膨らみ率など）を抽出・マッピングするクラス。
 * 論文定義に準拠したベースライン（R_normal）算出とハイブリッド評価を行います。
 */
public class FeatureExtractor {

	private static final int[][] NEIGHBORS_6 = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
			{ 0, 0, -1 } };

	/**
	 * 【Step 1】距離マップからの局所半径（ローカルスケール）の抽出
	 */
	public void extractInscribedRadii(VesselTree tree, Image3D distanceMap) {
		System.out.println("Extracting local inscribed radii from distance map...");
		ImageStack distStack = distanceMap.getImagePlus().getStack();
		int w = distStack.getWidth();
		int h = distStack.getHeight();
		int d = distStack.getSize();

		int totalPointsCount = 0;
		for (Branch branch : tree.getBranches()) {
			List<Point3D> path = branch.getPath();
			for (int i = 0; i < path.size(); i++) {
				Point3D p = path.get(i);
				if (p.x >= 0 && p.x < w && p.y >= 0 && p.y < h && p.z >= 0 && p.z < d) {
					ImageProcessor ip = distStack.getProcessor(p.z + 1);
					float radiusMM = ip.getf(p.x, p.y);
					branch.setInscribedRadiusAt(i, radiusMM);
					totalPointsCount++;
				}
			}
		}
		System.out.println("  Extracted local radii for " + totalPointsCount + " centerline points.");
	}

	/**
     * 【Step 2 & 3】膨らみ率（Bulge Ratio）と曲率（SI, Gaussian Curvature）の一括計算
     * 
     * ベースライン半径の算出: 各枝（Branch）ごとに、経路上の中央値（Median）などを計算し、それを不変の $R_{normal}$
	 * とする。中心線由来の膨らみ評価: distanceMap[CL] / R_{normal} を計算する。（紡錘状・分岐部コブの検知）表面由来の膨らみ評価:
	 * KDTreeで表面から最も近いCLを探し、actualDistance / R_{normal} を計算する。（側壁コブの検知）最大値の採用:
	 * 2と3の大きい方を、そのポイントの最終的な Bulge Ratio とする。
     */
    public void extractBulgeRatiosAndCurvatures(VesselTree tree, Image3D segmentedMask) {
        System.out.println("Calculating Bulge Ratios and Curvatures (Single-Pass)...");
        ImagePlus imp = segmentedMask.getImagePlus();
        ImageStack stack = imp.getStack();
        int w = stack.getWidth(); int h = stack.getHeight(); int d = stack.getSize();

        double dx = imp.getCalibration().pixelWidth;
        double dy = imp.getCalibration().pixelHeight;
        double dz = imp.getCalibration().pixelDepth;
        if (dx <= 0) dx = 1.0; if (dy <= 0) dy = 1.0; if (dz <= 0) dz = 1.0;

        // --- 1. 曲率計算のための平滑化（ガウスブラー） ---
        // バイナリ画像のままだと微分計算が破綻するため、32-bit Floatにして平滑化する
        System.out.println("  Generating smooth scalar field for curvature derivatives...");
        ImagePlus smoothImp = imp.duplicate();
        new ImageConverter(smoothImp).convertToGray32();
        GaussianBlur3D.blur(smoothImp, 1.5, 1.5, 1.5);
        ImageStack smoothStack = smoothImp.getStack();
        
        float[][] smoothVol = new float[d][];
        for (int z = 0; z < d; z++) {
            smoothVol[z] = (float[]) smoothStack.getPixels(z + 1);
        }

        // --- 2. ベースライン半径の算出 ---
        Map<Branch, Double> baselineRadii = new HashMap<>();
        for (Branch branch : tree.getBranches()) {
            List<Double> radii = branch.getInscribedRadii();
            double rNormal = calculateMedian(radii);
            if (rNormal < 1e-6) rNormal = 1e-6;
            baselineRadii.put(branch, rNormal);

            for (int i = 0; i < radii.size(); i++) {
                branch.setBulgeRatioAt(i, radii.get(i) / rNormal);
            }
        }

        // --- 3. KDTreeの構築 ---
        List<CenterlinePointInfo> clPoints = new ArrayList<>();
        for (Branch branch : tree.getBranches()) {
            List<Point3D> path = branch.getPath();
            for (int i = 0; i < path.size(); i++) {
                clPoints.add(new CenterlinePointInfo(path.get(i), branch, i));
            }
        }
        KDTree kdTree = new KDTree(clPoints, dx, dy, dz);

        // --- 4. 表面ループによる一括マッピング ---
        byte[][] volume = new byte[d][];
        for (int z = 0; z < d; z++) {
            volume[z] = (byte[]) stack.getPixels(z + 1);
        }

        int surfaceVoxelCount = 0;
        for (int z = 0; z < d; z++) {
            byte[] pixels = volume[z];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((pixels[y * w + x] & 0xff) == 255 && isSurfaceVoxel(volume, x, y, z, w, h, d)) {
                        surfaceVoxelCount++;
                        
                        NearestResult result = kdTree.findNearest(x, y, z);
                        if (result != null && result.info != null) {
                            CenterlinePointInfo nearestCL = result.info;
                            
                            // [Bulge Ratio の更新]
                            double actualDistance = Math.sqrt(result.distanceSq);
                            double rNormal = baselineRadii.get(nearestCL.branch);
                            double bulgeRatioSurface = actualDistance / rNormal;
                            
                            double currentMaxBulge = nearestCL.branch.getBulgeRatios().get(nearestCL.index);
                            if (bulgeRatioSurface > currentMaxBulge) {
                                nearestCL.branch.setBulgeRatioAt(nearestCL.index, bulgeRatioSurface);
                            }

                            // [Shape Index & Gaussian Curvature の計算と更新]
                            double[] curvatures = computeCurvature(smoothVol, x, y, z, w, h, d, dx, dy, dz);
                            double K = curvatures[0];
                            double SI = curvatures[1];

                            // 最大のSI（よりドームに近い形状）を記録する
                            double currentMaxSI = nearestCL.branch.getShapeIndices().get(nearestCL.index);
                            if (SI > currentMaxSI) {
                                nearestCL.branch.setShapeIndexAt(nearestCL.index, SI);
                                nearestCL.branch.setGaussianCurvatureAt(nearestCL.index, K);
                            }
                        }
                    }
                }
            }
        }
        smoothImp.close();
        System.out.println("  Processed " + surfaceVoxelCount + " surface voxels.");
        System.out.println("  Bulge Ratios and Curvatures mapped successfully.");
    }

    /**
     * 暗黙関数（平滑化スカラー場）からガウス曲率(K)とShape Index(SI)を計算します。
     * (1次元配列アクセス最適化版)
     */
    private double[] computeCurvature(float[][] L, int x, int y, int z, int w, int h, int d, double dx, double dy, double dz) {
        // 境界付近は計算不可なのでデフォルト値(円柱: SI=0.5)を返す
        if (x < 1 || x >= w - 1 || y < 1 || y >= h - 1 || z < 1 || z >= d - 1) {
            return new double[]{0.0, 0.5};
        }

        // 高速アクセスのためのインデックス事前計算
        int idx = y * w + x;
        int idxUp = (y - 1) * w + x;
        int idxDown = (y + 1) * w + x;

        // 1次偏微分
        double Lx = (L[z][idx + 1] - L[z][idx - 1]) / (2.0 * dx);
        double Ly = (L[z][idxDown] - L[z][idxUp]) / (2.0 * dy);
        double Lz = (L[z + 1][idx] - L[z - 1][idx]) / (2.0 * dz);

        // 2次偏微分 (Hessian)
        double Lxx = (L[z][idx + 1] - 2.0 * L[z][idx] + L[z][idx - 1]) / (dx * dx);
        double Lyy = (L[z][idxDown] - 2.0 * L[z][idx] + L[z][idxUp]) / (dy * dy);
        double Lzz = (L[z + 1][idx] - 2.0 * L[z][idx] + L[z - 1][idx]) / (dz * dz);
        
        double Lxy = (L[z][idxDown + 1] - L[z][idxDown - 1] - L[z][idxUp + 1] + L[z][idxUp - 1]) / (4.0 * dx * dy);
        double Lxz = (L[z + 1][idx + 1] - L[z + 1][idx - 1] - L[z - 1][idx + 1] + L[z - 1][idx - 1]) / (4.0 * dx * dz);
        double Lyz = (L[z + 1][idxDown] - L[z + 1][idxUp] - L[z - 1][idxDown] + L[z - 1][idxUp]) / (4.0 * dy * dz);

        // 外向き法線を持つ暗黙関数 F = -L とする（内部値が高いため）
        double Fx = -Lx; double Fy = -Ly; double Fz = -Lz;
        double Fxx = -Lxx; double Fyy = -Lyy; double Fzz = -Lzz;
        double Fxy = -Lxy; double Fxz = -Lxz; double Fyz = -Lyz;

        double denom2 = Fx*Fx + Fy*Fy + Fz*Fz;
        if (denom2 < 1e-9) return new double[]{0.0, 0.5}; // 勾配がない（平坦）

        double denom = Math.sqrt(denom2);

        // ガウス曲率 (K)
        double K = ( Fx*Fx*(Fyy*Fzz - Fyz*Fyz) + Fy*Fy*(Fxx*Fzz - Fxz*Fxz) + Fz*Fz*(Fxx*Fyy - Fxy*Fxy)
                   + 2.0*Fx*Fy*(Fxz*Fyz - Fxy*Fzz) + 2.0*Fy*Fz*(Fxy*Fxz - Fxx*Fyz) + 2.0*Fx*Fz*(Fxy*Fyz - Fyy*Fxz) ) / (denom2 * denom2);

        // 平均曲率 (H)
        double H = ( Fx*Fx*(Fyy+Fzz) + Fy*Fy*(Fxx+Fzz) + Fz*Fz*(Fxx+Fyy)
                   - 2.0*Fx*Fy*Fxy - 2.0*Fy*Fz*Fyz - 2.0*Fx*Fz*Fxz ) / (2.0 * denom2 * denom);

        // Shape Index (SI)
        double diff = H*H - K;
        if (diff < 0) diff = 0; // 数値誤差の補正
        
        double SI = (2.0 / Math.PI) * Math.atan2(H, Math.sqrt(diff));

        return new double[]{K, SI};
    }

	/**
	 * リストの中央値（Median）を算出するヘルパーメソッド
	 */
	private double calculateMedian(List<Double> values) {
		if (values == null || values.isEmpty())
			return 1e-6;
		List<Double> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		int size = sorted.size();
		if (size % 2 == 1) {
			return sorted.get(size / 2);
		} else {
			return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
		}
	}

	/**
	 * キャッシュされた2D配列群を用いて、そのボクセルが表面かどうかを高速判定
	 */
	private boolean isSurfaceVoxel(byte[][] volume, int x, int y, int z, int w, int h, int d) {
		for (int[] off : NEIGHBORS_6) {
			int nx = x + off[0];
			int ny = y + off[1];
			int nz = z + off[2];
			if (nx < 0 || nx >= w || ny < 0 || ny >= h || nz < 0 || nz >= d)
				return true;
			if ((volume[nz][ny * w + nx] & 0xff) == 0)
				return true;
		}
		return false;
	}

	// ========================================================================
	// 高速空間検索のための軽量KDTree（Quickselect & プリミティブ & 二乗距離 最適化版）
	// ========================================================================

	private static class CenterlinePointInfo {
		Point3D pt;
		Branch branch;
		int index;

		CenterlinePointInfo(Point3D pt, Branch branch, int index) {
			this.pt = pt;
			this.branch = branch;
			this.index = index;
		}
	}

	private static class KDNode {
		CenterlinePointInfo info;
		KDNode left, right;
		int axis;

		KDNode(CenterlinePointInfo info, int axis) {
			this.info = info;
			this.axis = axis;
		}
	}

	private static class NearestResult {
		CenterlinePointInfo info;
		double distanceSq;
	}

	private static class KDTree {
		KDNode root;
		double dx, dy, dz;

		KDTree(List<CenterlinePointInfo> pointsList, double dx, double dy, double dz) {
			this.dx = dx;
			this.dy = dy;
			this.dz = dz;
			CenterlinePointInfo[] points = pointsList.toArray(new CenterlinePointInfo[0]);
			this.root = buildTree(points, 0, points.length - 1, 0);
		}

		private KDNode buildTree(CenterlinePointInfo[] points, int start, int end, int depth) {
			if (start > end)
				return null;

			int axis = depth % 3;
			int mid = start + (end - start) / 2;

			// クイックセレクトで中央値を求める (In-place, 平均O(N))
			quickSelect(points, start, end, mid, axis);

			KDNode node = new KDNode(points[mid], axis);
			node.left = buildTree(points, start, mid - 1, depth + 1);
			node.right = buildTree(points, mid + 1, end, depth + 1);

			return node;
		}

		private void quickSelect(CenterlinePointInfo[] points, int left, int right, int k, int axis) {
			while (left < right) {
				int pivotIndex = partition(points, left, right, axis);
				if (pivotIndex == k) {
					return;
				} else if (k < pivotIndex) {
					right = pivotIndex - 1;
				} else {
					left = pivotIndex + 1;
				}
			}
		}

		private int partition(CenterlinePointInfo[] points, int left, int right, int axis) {
			double pivotValue = getAxisValue(points[right].pt, axis);
			int storeIndex = left;
			for (int i = left; i < right; i++) {
				if (getAxisValue(points[i].pt, axis) < pivotValue) {
					swap(points, i, storeIndex);
					storeIndex++;
				}
			}
			swap(points, storeIndex, right);
			return storeIndex;
		}

		private void swap(CenterlinePointInfo[] points, int i, int j) {
			CenterlinePointInfo temp = points[i];
			points[i] = points[j];
			points[j] = temp;
		}

		// --- 検索処理 ---

		NearestResult findNearest(int tx, int ty, int tz) {
			NearestResult result = new NearestResult();
			result.distanceSq = Double.MAX_VALUE;
			search(root, tx, ty, tz, result);
			return result;
		}

		private void search(KDNode node, int tx, int ty, int tz, NearestResult best) {
			if (node == null)
				return;

			// 平方根を回避した二乗距離の計算
			double distSq = getPhysicalDistanceSq(node.info.pt, tx, ty, tz);
			if (distSq < best.distanceSq) {
				best.distanceSq = distSq;
				best.info = node.info;
			}

			int axis = node.axis;
			double targetAxisVal = getAxisValue(tx, ty, tz, axis);
			double nodeAxisVal = getAxisValue(node.info.pt, axis);

			KDNode first = targetAxisVal < nodeAxisVal ? node.left : node.right;
			KDNode second = targetAxisVal < nodeAxisVal ? node.right : node.left;

			search(first, tx, ty, tz, best);

			double axisDiff = targetAxisVal - nodeAxisVal;
			if ((axisDiff * axisDiff) < best.distanceSq) {
				search(second, tx, ty, tz, best);
			}
		}

		private double getAxisValue(int x, int y, int z, int axis) {
			if (axis == 0)
				return x * dx;
			if (axis == 1)
				return y * dy;
			return z * dz;
		}

		private double getAxisValue(Point3D pt, int axis) {
			if (axis == 0)
				return pt.x * dx;
			if (axis == 1)
				return pt.y * dy;
			return pt.z * dz;
		}

		private double getPhysicalDistanceSq(Point3D p1, int tx, int ty, int tz) {
			double vx = (p1.x - tx) * dx;
			double vy = (p1.y - ty) * dy;
			double vz = (p1.z - tz) * dz;
			return (vx * vx) + (vy * vy) + (vz * vz);
		}
	}
}