/**
 * copyright visionary imaging services, inc.
 */
package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.Filters3D;
import ij.process.StackStatistics;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * MRAボリューム全体のヒストグラムから上位指定パーセントの血管領域を抽出し、 3Dクロージング、3D穴埋め、3D
 * CCAを適用して極めてクリーンな血管マスクを生成するクラス。
 * 
 * @author tatsunidas
 */
public class VesselSegmenter {

	private double topPercentile;
	private int minComponentVoxels;
	private float closingRadius; // クロージング（切れ目接続）の半径

	private static final int[][] NEIGHBORS_6 = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
			{ 0, 0, -1 } };
	private static final int[][] NEIGHBORS_26 = generate26Neighbors();

	/**
	 * カスタムパラメータで初期化するコンストラクタ。
	 * 
	 * @param topPercentile      抽出する上位ピクセルの割合（例: 0.005 = 上位0.5%）
	 * @param minComponentVoxels 削除するゴミの最大サイズ（ボクセル数、推奨: 100以上）
	 * @param closingRadius      途切れた血管を繋ぐクロージング半径（ピクセル単位、推奨: 2f）
	 */
	public VesselSegmenter(double topPercentile, int minComponentVoxels, float closingRadius) {
		this.topPercentile = topPercentile;
		this.minComponentVoxels = minComponentVoxels;
		this.closingRadius = closingRadius;
	}

	/**
	 * MRA（TOF法）向けに最適化されたデフォルトコンストラクタ。 上位0.5%抽出、微小ゴミ100ボクセル、クロージング半径2ピクセル
	 */
	public VesselSegmenter() {
		this(0.005, 100, 2f);
	}

	public Image3D segment(Image3D inputImage) {
		ImagePlus imp = inputImage.getImagePlus().duplicate();
		imp.setTitle(inputImage.getImagePlus().getTitle() + "_Segmented");

		// 1. 8-bitに変換（表示レンジを0-255に最適マッピング）
		ij.process.ImageConverter converter = new ij.process.ImageConverter(imp);
		converter.convertToGray8();

		// 2. ボリューム全体の統計情報（256階調ヒストグラム）から上位パーセンタイル閾値を算出
		StackStatistics stats = new StackStatistics(imp);
		int[] histogram = stats.histogram;
		int globalThreshold = computePercentileThreshold(histogram, topPercentile);

		System.out.println("Executing Top-Percentile Thresholding (Target: Top " + (topPercentile * 100) + "%)...");
		System.out.println("Computed Percentile Threshold: " + globalThreshold);

		// 3. 一括で完全に2値化 (255 or 0)
		ImageStack stack = imp.getStack();
		int depth = stack.getSize();

		double dx = imp.getCalibration().pixelWidth;
		double dy = imp.getCalibration().pixelHeight;
		double dz = imp.getCalibration().pixelDepth;

		// 物理半径(mm)を、各軸のピクセル数に変換
		float rx = (float) (closingRadius / dx);
		float ry = (float) (closingRadius / dy);
		float rz = (float) (closingRadius / dz);

		for (int z = 1; z <= depth; z++) {
			byte[] pixels = (byte[]) stack.getProcessor(z).getPixels();
			for (int i = 0; i < pixels.length; i++) {
				int val = pixels[i] & 0xff;
				pixels[i] = (val >= globalThreshold) ? (byte) 255 : (byte) 0;
			}
		}

		// 4. 3Dクロージング処理（膨張 MAXIMUM -> 収縮 MINIMUM）
		// 血管内の軽微な信号欠損や途切れを物理的に接続します
		if (closingRadius > 0f) {
			System.out.println("Executing 3D Closing (Dilation -> Erosion) with radius " + closingRadius + "...");
			// 各軸に独立したピクセル半径を適用
			ImageStack dilated = Filters3D.filter(imp.getStack(), Filters3D.MAX, rx, ry, rz);
			ImageStack closed = Filters3D.filter(dilated, Filters3D.MIN, rx, ry, rz);
			imp.setStack(closed); // 結果を反映
			stack = closed;

			dilated = null;
		}

		// 5. 3D穴埋め処理 (Fill Holes)
		// 血管内部に閉じ込められた中抜けの空洞（0）を255で埋め、スケルトン化での無駄な輪っか（ループ）の発生を防ぎます
		System.out.println("Executing 3D Binary Fill Holes...");
		fillHoles3D(stack);

		// 6. 3D CCA (連結成分分析) による微小ノイズ（ゴミ）の削除
		System.out.println("Executing 3D Connected Component Analysis...");
		int removedCount = removeSmallComponents(stack);
		System.out
				.println("Removed " + removedCount + " small noise components (< " + minComponentVoxels + " voxels).");

		GDicomTools.copyPivotalMeta(inputImage.getImagePlus(), imp, true);

		return new Image3D(imp);
	}

	private int computePercentileThreshold(int[] histogram, double targetPercentile) {
		long totalPixels = 0;
		for (int count : histogram)
			totalPixels += count;

		long targetPixelCount = (long) (totalPixels * targetPercentile);
		long accumulated = 0;
		int threshold = 255;

		for (int i = 255; i >= 0; i--) {
			accumulated += histogram[i];
			if (accumulated >= targetPixelCount) {
				threshold = i;
				break;
			}
		}
		return Math.max(30, threshold);
	}

	/**
	 * 3D Flood Fill（幅優先探索）を用いて、外側から到達できない「閉じ込められた空洞（穴）」を完全に埋めます。
	 */
	private void fillHoles3D(ImageStack stack) {
		int w = stack.getWidth();
		int h = stack.getHeight();
		int d = stack.getSize();

		// 外部（背景）から到達可能か記録するフラグ配列
		boolean[][][] reachable = new boolean[d][h][w];
		Queue<Point3D> queue = new LinkedList<>();

		// 外周のボクセルをスキャンし、背景(0)であればシードとしてキューに投入
		for (int z = 0; z < d; z++) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (z == 0 || z == d - 1 || y == 0 || y == h - 1 || x == 0 || x == w - 1) {
						byte[] pixels = (byte[]) stack.getPixels(z + 1);
						if ((pixels[y * w + x] & 0xff) == 0 && !reachable[z][y][x]) {
							queue.add(new Point3D(x, y, z));
							reachable[z][y][x] = true;
						}
					}
				}
			}
		}

		// BFSによる外部空間の探索（6近傍で拡張）
		while (!queue.isEmpty()) {
			Point3D p = queue.poll();

			for (int[] off : NEIGHBORS_6) {
				int nx = p.x + off[0];
				int ny = p.y + off[1];
				int nz = p.z + off[2];

				if (nx >= 0 && nx < w && ny >= 0 && ny < h && nz >= 0 && nz < d) {
					if (!reachable[nz][ny][nx]) {
						byte[] pixels = (byte[]) stack.getPixels(nz + 1);
						if ((pixels[ny * w + nx] & 0xff) == 0) {
							reachable[nz][ny][nx] = true;
							queue.add(new Point3D(nx, ny, nz));
						}
					}
				}
			}
		}

		// 探索終了後、背景(0)でありながら外部から到達できなかったボクセル（＝閉じ込められた内側の穴）を白(255)に反転
		for (int z = 0; z < d; z++) {
			byte[] pixels = (byte[]) stack.getPixels(z + 1);
			for (int y = 0; y < h; y++) {
				int offset = y * w;
				for (int x = 0; x < w; x++) {
					if ((pixels[offset + x] & 0xff) == 0 && !reachable[z][y][x]) {
						pixels[offset + x] = (byte) 255;
					}
				}
			}
		}
	}

	private int removeSmallComponents(ImageStack stack) {
		int width = stack.getWidth();
		int height = stack.getHeight();
		int depth = stack.getSize();

		boolean[][][] visited = new boolean[depth][height][width];
		int removedComponents = 0;

		for (int z = 0; z < depth; z++) {
			byte[] pixels = (byte[]) stack.getProcessor(z + 1).getPixels();
			for (int y = 0; y < height; y++) {
				int offset = y * width;
				for (int x = 0; x < width; x++) {

					if ((pixels[offset + x] & 0xff) == 255 && !visited[z][y][x]) {
						List<Point3D> componentVoxels = new ArrayList<>();
						Queue<Point3D> queue = new LinkedList<>();

						Point3D seed = new Point3D(x, y, z);
						queue.add(seed);
						visited[z][y][x] = true;
						componentVoxels.add(seed);

						while (!queue.isEmpty()) {
							Point3D p = queue.poll();
							for (int[] off : NEIGHBORS_26) {
								int nx = p.x + off[0];
								int ny = p.y + off[1];
								int nz = p.z + off[2];

								if (nx >= 0 && nx < width && ny >= 0 && ny < height && nz >= 0 && nz < depth) {
									if (!visited[nz][ny][nx]) {
										byte[] nPixels = (byte[]) stack.getProcessor(nz + 1).getPixels();
										if ((nPixels[ny * width + nx] & 0xff) == 255) {
											visited[nz][ny][nx] = true;
											Point3D nextP = new Point3D(nx, ny, nz);
											queue.add(nextP);
											componentVoxels.add(nextP);
										}
									}
								}
							}
						}

						if (componentVoxels.size() < minComponentVoxels) {
							for (Point3D p : componentVoxels) {
								byte[] pPixels = (byte[]) stack.getProcessor(p.z + 1).getPixels();
								pPixels[p.y * width + p.x] = (byte) 0;
							}
							removedComponents++;
						}
					}
				}
			}
		}
		return removedComponents;
	}

	private static int[][] generate26Neighbors() {
		int[][] neighbors = new int[26][3];
		int idx = 0;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					if (dx == 0 && dy == 0 && dz == 0)
						continue;
					neighbors[idx++] = new int[] { dx, dy, dz };
				}
			}
		}
		return neighbors;
	}
}