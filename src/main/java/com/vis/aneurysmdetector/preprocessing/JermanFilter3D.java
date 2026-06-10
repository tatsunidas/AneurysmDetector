package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.GaussianBlur3D;

/**
 * 3D Jerman Vesselness Filter (血管強調フィルタ)
 * 論文(Jerman et al., 2016)に忠実な Tau正則化(lambda_rho) と条件分岐を適用した厳密実装版。
 */
public class JermanFilter3D {

    private double tau;

    /**
     * @param tau 背景ノイズを抑制する正則化パラメータ（論文推奨値: 0.5 〜 1.0, 標準0.75）
     */
    public JermanFilter3D(double tau) {
        this.tau = tau;
    }

    public JermanFilter3D() {
        this(0.75); // デフォルト
    }

    public Image3D apply(Image3D input, double[] sigmas) {
        ImagePlus imp = input.getImagePlus();
        int w = imp.getWidth();
        int h = imp.getHeight();
        int d = imp.getNSlices();

        double dx = imp.getCalibration().pixelWidth;
        double dy = imp.getCalibration().pixelHeight;
        double dz = imp.getCalibration().pixelDepth;
        if (dx <= 0) dx = 1.0; if (dy <= 0) dy = 1.0; if (dz <= 0) dz = 1.0;

        double[][][] maxVesselness = new double[d][h][w];
        double globalMax = 0.0;

        System.out.println("Executing 3D Jerman Filter (Strict Version)...");

        for (double sigma : sigmas) {
            System.out.println("  Processing scale sigma = " + sigma + "...");

            ImagePlus blurredImp = imp.duplicate();
            GaussianBlur3D.blur(blurredImp, sigma, sigma, sigma);
            ImageStack blurredStack = blurredImp.getStack();

            // ==============================================================
            // 【パス1】現在のスケールにおける lambda_3 の最大値を探す
            // ==============================================================
            double maxLambda3 = 0.0;
            for (int z = 1; z <= d; z++) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        double[] eig = computeHessianAndEigenvalues(blurredStack, x, y, z, w, h, d, dx, dy, dz, sigma);
                        double l3 = -eig[2]; // Bright-on-Dark なので反転
                        if (l3 > maxLambda3) {
                            maxLambda3 = l3;
                        }
                    }
                }
            }

            // 正則化の閾値: τ * max(λ3)
            double tauThreshold = tau * maxLambda3;

            // ==============================================================
            // 【パス2】正則化された λρ を用いて、V_sigma を計算する
            // ==============================================================
            for (int z = 1; z <= d; z++) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        // メモリ節約のため再計算（OOM対策）
                        double[] eig = computeHessianAndEigenvalues(blurredStack, x, y, z, w, h, d, dx, dy, dz, sigma);
                        
                        double l2 = -eig[1];
                        double l3 = -eig[2];
                        double response = 0.0;

                        if (l2 > 0 && l3 > 0) {
                            // 1. 正則化された固有値 λρ の決定 (論文 Eq.7)
                            double lambdaRho = l3;
                            if (l3 <= tauThreshold) {
                                lambdaRho = tauThreshold;
                            }

                            // 2. 論文 Eq.14 に準拠した正しい条件分岐
                            if (l2 >= lambdaRho / 2.0) {
                                // 理想的な血管に近い場合はサチュレート（最大値1.0）
                                response = 1.0;
                            } else {
                                // 平坦な構造の場合は滑らかに減衰させる
                                double denom = Math.pow(l2 + lambdaRho, 3);
                                if (denom > 1e-12) {
                                    double numer = (l2 * l2) * (lambdaRho - l2) * 27.0;
                                    response = numer / denom;
                                }
                            }
                            
                            // コントラスト強度を反映して最終応答とする
                            response *= l3;
                        }

                        // NaNの伝播を完全に防ぐ
                        if (!Double.isNaN(response) && Double.isFinite(response)) {
                            if (response > maxVesselness[z-1][y][x]) {
                                maxVesselness[z-1][y][x] = response;
                                if (response > globalMax) {
                                    globalMax = response;
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("Normalizing output...");
        ImageStack outStack = new ImageStack(w, h);
        for (int z = 0; z < d; z++) {
            byte[] slicePixels = new byte[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double val = 0;
                    if (globalMax > 0) {
                        val = (maxVesselness[z][y][x] / globalMax) * 255.0;
                    }
                    slicePixels[y * w + x] = (byte) Math.min(255, Math.max(0, val));
                }
            }
            outStack.addSlice("", slicePixels);
        }

        ImagePlus resultImp = new ImagePlus(imp.getTitle() + "_JermanStrict", outStack);
        GDicomTools.copyPivotalMeta(imp, resultImp, true);
        resultImp.setCalibration(imp.getCalibration().copy());
        if (imp.getOriginalFileInfo() != null) {
            resultImp.setFileInfo((ij.io.FileInfo) imp.getOriginalFileInfo().clone());
        }
        if (imp.getProperty("Info") != null) {
            resultImp.setProperty("Info", imp.getProperty("Info"));
        }

        return new Image3D(resultImp);
    }

    private double[] computeHessianAndEigenvalues(ImageStack blurredStack, int x, int y, int z, int w, int h, int d, double dx, double dy, double dz, double sigma) {
        double Ixx = (getPixel(blurredStack, x+1, y, z, w, h, d) - 2.0 * getPixel(blurredStack, x, y, z, w, h, d) + getPixel(blurredStack, x-1, y, z, w, h, d)) / (dx * dx);
        double Iyy = (getPixel(blurredStack, x, y+1, z, w, h, d) - 2.0 * getPixel(blurredStack, x, y, z, w, h, d) + getPixel(blurredStack, x, y-1, z, w, h, d)) / (dy * dy);
        double Izz = (getPixel(blurredStack, x, y, z+1, w, h, d) - 2.0 * getPixel(blurredStack, x, y, z, w, h, d) + getPixel(blurredStack, x, y, z-1, w, h, d)) / (dz * dz);
        
        double Ixy = (getPixel(blurredStack, x+1, y+1, z, w, h, d) - getPixel(blurredStack, x-1, y+1, z, w, h, d) 
                    - getPixel(blurredStack, x+1, y-1, z, w, h, d) + getPixel(blurredStack, x-1, y-1, z, w, h, d)) / (4.0 * dx * dy);
        double Ixz = (getPixel(blurredStack, x+1, y, z+1, w, h, d) - getPixel(blurredStack, x-1, y, z+1, w, h, d) 
                    - getPixel(blurredStack, x+1, y, z-1, w, h, d) + getPixel(blurredStack, x-1, y, z-1, w, h, d)) / (4.0 * dx * dz);
        double Iyz = (getPixel(blurredStack, x, y+1, z+1, w, h, d) - getPixel(blurredStack, x, y-1, z+1, w, h, d) 
                    - getPixel(blurredStack, x, y+1, z-1, w, h, d) + getPixel(blurredStack, x, y-1, z-1, w, h, d)) / (4.0 * dy * dz);

        double s2 = sigma * sigma;
        Ixx *= s2; Iyy *= s2; Izz *= s2;
        Ixy *= s2; Ixz *= s2; Iyz *= s2;

        return computeEigenvalues3x3(Ixx, Iyy, Izz, Ixy, Ixz, Iyz);
    }

    private float getPixel(ImageStack stack, int x, int y, int z, int w, int h, int d) {
        x = Math.max(0, Math.min(x, w - 1));
        y = Math.max(0, Math.min(y, h - 1));
        z = Math.max(1, Math.min(z, d)); 
        return stack.getProcessor(z).getf(x, y);
    }

    private double[] computeEigenvalues3x3(double Ixx, double Iyy, double Izz, double Ixy, double Ixz, double Iyz) {
        double p1 = Ixy * Ixy + Ixz * Ixz + Iyz * Iyz;
        if (p1 == 0) return sortAbs(Ixx, Iyy, Izz);

        double q = (Ixx + Iyy + Izz) / 3.0;
        double p2 = Math.pow(Ixx - q, 2) + Math.pow(Iyy - q, 2) + Math.pow(Izz - q, 2) + 2.0 * p1;
        double p = Math.sqrt(p2 / 6.0);
        
        if (p == 0) return sortAbs(Ixx, Iyy, Izz);
        
        double Bxx = (Ixx - q) / p;
        double Byy = (Iyy - q) / p;
        double Bzz = (Izz - q) / p;
        double Bxy = Ixy / p;
        double Bxz = Ixz / p;
        double Byz = Iyz / p;

        double detB = Bxx * (Byy * Bzz - Byz * Byz) - Bxy * (Bxy * Bzz - Byz * Bxz) + Bxz * (Bxy * Byz - Byy * Bxz);
        
        double r = detB / 2.0;
        double phi;
        if (r <= -1) phi = Math.PI / 3.0;
        else if (r >= 1) phi = 0;
        else phi = Math.acos(r) / 3.0;

        double eig1 = q + 2.0 * p * Math.cos(phi);
        double eig3 = q + 2.0 * p * Math.cos(phi + (2.0 * Math.PI / 3.0));
        double eig2 = 3.0 * q - eig1 - eig3;

        return sortAbs(eig1, eig2, eig3);
    }

    private double[] sortAbs(double e1, double e2, double e3) {
        double[] arr = {e1, e2, e3};
        for(int i = 0; i < 2; i++) {
            for(int j = i + 1; j < 3; j++) {
                if(Math.abs(arr[i]) > Math.abs(arr[j])) {
                    double tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
                }
            }
        }
        return arr;
    }
}