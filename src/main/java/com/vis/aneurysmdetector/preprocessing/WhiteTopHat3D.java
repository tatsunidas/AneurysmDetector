/**
 * copyright visionary imaging services, inc.
 */
package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.Filters3D;
import ij.process.ImageProcessor;

/**
 * 3D White Top-Hat 変換フィルタークラス。
 * 主にCT（CTA）画像において、血管に隣接する巨大な高輝度組織（頭蓋骨など）を除去するために使用します。
 * 処理内容: 元画像 - オープニング画像 (収縮後に膨張させた画像)
 * 
 * @author tatsunidas
 */
public class WhiteTopHat3D {

    /**
     * 入力画像に対して3D空間でのWhite Top-Hat変換を適用します。
     * @param input 入力3D-CT画像
     * @param radius 骨を除去するための構造要素（球体）の半径（ピクセル単位、推奨: 3~7）
     * 血管の最大直径よりも大きく、骨の厚みよりも小さいサイズに設定します。
     * @return 骨領域が除去され、血管構造が孤立したImage3D
     */
    public Image3D apply(Image3D input, float radius) {
        ImagePlus impOriginal = input.getImagePlus().duplicate();
        impOriginal.setTitle(input.getImagePlus().getTitle() + "_TopHat");

        // オープニング処理（収縮 -> 膨張）を行うためのスタックを複製
        ImagePlus impOpening = input.getImagePlus().duplicate();

        System.out.println("Executing 3D White Top-Hat Filter (Bone Removal)...");

        // 1. 3D収縮 (Erosion / Minimum Filter) の実行
        // 構造要素の半径（radius）より細い血管領域が消去され、骨などの巨大組織の芯だけが残ります
        System.out.println("  Running 3D Erosion...");
        ImageStack eroded = Filters3D.filter(impOpening.getStack(), Filters3D.MIN, radius, radius, radius);

        // 2. 3D膨張 (Dilation / Maximum Filter) の実行
        // 芯だけになった巨大組織を元の太さに復元します。これで「骨だけの画像（オープニング画像）」が完成します
        System.out.println("  Running 3D Dilation...");
        ImageStack stackOpen = Filters3D.filter(eroded, Filters3D.MAX, radius, radius, radius);

        // 3. 差分計算 (White Top-Hat = 元画像 - オープニング画像)
        // 元画像から骨だけの画像を引き算することで、骨が消え、細かった血管だけが鮮鮮と残ります
        System.out.println("  Calculating differential image (Original - Opening)...");
        ImageStack stackOrig = impOriginal.getStack();
        
        int w = impOriginal.getWidth();
        int h = impOriginal.getHeight();
        int d = impOriginal.getNSlices();
        int sliceSize = w * h;

        for (int z = 1; z <= d; z++) {
            ImageProcessor ipOrig = stackOrig.getProcessor(z);
            ImageProcessor ipOpen = stackOpen.getProcessor(z);

            for (int i = 0; i < sliceSize; i++) {
                float vOrig = ipOrig.getf(i);
                float vOpen = ipOpen.getf(i);
                
                // 引き算
                float diff = vOrig - vOpen;
                
                // 負の数（アンダーシュート）は黒(0)にクリッピング
                if (diff < 0f) {
                    diff = 0f;
                }
                
                // インプレースで元画像の複製スタックに書き戻す
                ipOrig.setf(i, diff);
            }
        }

        // メモリ解放の促進
        impOpening.close();
        eroded = null;
        stackOpen = null;

        System.out.println("3D White Top-Hat processing completed.");
        return new Image3D(impOriginal);
    }
}