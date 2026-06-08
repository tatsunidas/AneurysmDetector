package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;

/**
 * 3D画像に対するノイズ除去（平滑化）フィルタクラス。
 */
public class DenoiseFilter {
    
    private double sigma;

    /**
     * @param sigma ガウシアンフィルタの強度（標準偏差）。値が大きいほど強くぼかされます。
     */
    public DenoiseFilter(double sigma) {
        this.sigma = sigma;
    }

    /**
     * 画像にノイズ除去フィルタを適用し、新しいImage3Dを返します。
     * * @param inputImage 入力となる3D画像
     * @return 平滑化された新しい3D画像
     */
    public Image3D apply(Image3D inputImage) {
        ImagePlus imp = inputImage.getImagePlus().duplicate();
        ImageStack stack = imp.getStack();
        
        GaussianBlur blur = new GaussianBlur();
        
        // 各スライスに対して2Dガウシアンフィルタを適用（簡易的な3D平滑化）
        // ※より厳密な3D等方性平滑化が必要な場合は 3D Gaussian Blur への拡張を検討
        for (int z = 1; z <= stack.getSize(); z++) {
            ImageProcessor ip = stack.getProcessor(z);
            blur.blurGaussian(ip, sigma);
        }
        
        return new Image3D(imp);
    }

    public double getSigma() { return sigma; }
    public void setSigma(double sigma) { this.sigma = sigma; }
}