package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
import com.vis.dicom.image.GDicomTools;

// 提供されたパッケージからのインポート
import de.biomedical_imaging.ij.nlMeansPlugin.NLMeansDenoising_;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

/**
 * 3D画像に対するノイズ除去（平滑化）フィルタクラス。
 * 血管のエッジを保持しながら背景ノイズを除去するため、Fast Non-Local Means (NLM) フィルタを使用します。
 */
public class DenoiseFilter {
    
    // NLMアルゴリズムのパラメータ
    private int sigma;             // ノイズの標準偏差（推定値）
    private int smoothingFactor;   // 平滑化の強度

    /**
     * @param sigma ノイズの標準偏差（MRAの場合 15 程度がベース）
     * @param smoothingFactor 平滑化の強度（デフォルトは1）
     */
    public DenoiseFilter(int sigma, int smoothingFactor) {
        this.sigma = sigma;
        this.smoothingFactor = smoothingFactor;
    }

    /**
     * 画像にNLMノイズ除去フィルタを適用し、新しいImage3Dを返します。
     * @param inputImage 入力となる3D画像
     * @return 平滑化された新しい3D画像
     */
    public Image3D apply(Image3D inputImage) {
        // 元データを破壊しないよう複製
        ImagePlus imp = inputImage.getImagePlus().duplicate();
        imp.setTitle(inputImage.getImagePlus().getTitle() + "_NLM_Denoised");
        ImageStack stack = imp.getStack();
        
        System.out.println("Executing Fast Non-Local Means Denoising...");
        System.out.println("Parameters -> Sigma: " + sigma + ", Smoothing Factor: " + smoothingFactor);

        // NLMプラグインのインスタンス生成
        NLMeansDenoising_ nlmPlugin = new NLMeansDenoising_();
        
        // 実際のプラグイン内の `applyNonLocalMeans` に渡すための実効Sigmaを計算
        // プラグインの run() メソッド内と同じロジックを適用: sigma = smoothingFactor * sigma
        int effectiveSigma = this.smoothingFactor * this.sigma;

        // スタックの全スライスに対してNLMフィルタを適用
        int depth = stack.getSize();
        for (int z = 1; z <= depth; z++) {
            System.out.println("Denoising slice " + z + " / " + depth + " ...");
            ImageProcessor ip = stack.getProcessor(z);
            
            // ====================================================================
            // 提供されたプラグインの public メソッドを直接呼び出し
            // applyNonLocalMeans() は内部でマルチスレッド処理を行い ip を直接書き換えます
            // ====================================================================
            nlmPlugin.applyNonLocalMeans(ip, effectiveSigma);
        }
        
        GDicomTools.headerCopy(inputImage.getImagePlus(), imp);
        
        System.out.println("Denoising completed.");
        return new Image3D(imp);
    }

    // --- Getters & Setters ---

    public int getSigma() { return sigma; }
    public void setSigma(int sigma) { this.sigma = sigma; }

    public int getSmoothingFactor() { return smoothingFactor; }
    public void setSmoothingFactor(int smoothingFactor) { this.smoothingFactor = smoothingFactor; }
}