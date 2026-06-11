package com.vis.aneurysmdetector.ui;

import com.vis.aneurysmdetector.anatomy.GraphPruner;
import com.vis.aneurysmdetector.anatomy.TreeGraphBuilder;
import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.VesselTree;
import com.vis.aneurysmdetector.detection.AneurysmDetector;
import com.vis.aneurysmdetector.detection.SaliencyScorer;
import com.vis.aneurysmdetector.feature.DistanceTransform3D;
import com.vis.aneurysmdetector.feature.FeatureExtractor;
import com.vis.aneurysmdetector.preprocessing.DenoiseFilter;
import com.vis.aneurysmdetector.preprocessing.JermanFilter3D;
import com.vis.aneurysmdetector.preprocessing.Skeletonizer;
/**
 * copyright visionary imaging services, inc.
 */
import com.vis.aneurysmdetector.preprocessing.VesselSegmenter;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D3.ui.VolumeData;
import com.vis.core.view.D3.ui.VolumeLoader;

import ij.ImagePlus;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * 脳動脈瘤検出システム (CADe) の本番用エントリーポイント。
 * 非同期処理 (SwingWorker) で重たいパイプラインを回し、UIを起動します。
 * 
 * @author tatsunidas
 */
public class AneurysmCADeApp {

    public static void main(String[] args) {
        // Look & Feel をOSネイティブにして綺麗にする
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } 
        catch (Exception e) { e.printStackTrace(); }

        SwingUtilities.invokeLater(() -> {
//            JFileChooser chooser = new JFileChooser();
//            chooser.setDialogTitle("Select Patient MRA Directory (DICOM / TIFF)");
//            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
//
//            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
//                File selectedFile = chooser.getSelectedFile();
//                startAnalysis(selectedFile.getAbsolutePath());
//            }
        	String path = "./test-mra";
        	startAnalysis(path);
        });
    }
    
    public AneurysmCADeApp(String imageDir) {
    	startAnalysis(imageDir);
    }
    
    public AneurysmCADeApp(Praparat pp) {
    	startAnalysis(pp.getImagePlus());
    }

    public static void startAnalysis(String imagePath) {
        ImagePlus rawImp = null;
        if(new File(imagePath).isDirectory()) {
        	rawImp = ij.plugin.FolderOpener.open(imagePath);
        }else {
        	rawImp = ij.IJ.openImage(imagePath);
        }
        
        if (rawImp == null) throw new RuntimeException("Image load failed.");
        
        startAnalysis(rawImp);
    }
    
    public static void startAnalysis(ImagePlus volume) {
        // プログレスダイアログの作成
        JDialog progressDialog = new JDialog((Frame) null, "Analyzing", true);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(null);
        progressDialog.setLayout(new BorderLayout(10, 10));

        JLabel statusLabel = new JLabel("Initializing Pipeline...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true); // ぐるぐる回るプログレスバー

        progressDialog.add(statusLabel, BorderLayout.CENTER);
        progressDialog.add(progressBar, BorderLayout.SOUTH);

        // バックグラウンド処理ワーカー
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            private List<AneurysmCandidate> candidates;
            private VolumeData segVolume;
            private VesselTree vesselTree; // ★ 追加: ツリー情報をUIに渡すために保持
            private Praparat pp;

            @Override
            protected Void doInBackground() throws Exception {
                publish("Loading Image Data...");
                ImagePlus rawImp = volume;
                if (rawImp == null) throw new RuntimeException("Image load failed.");
                
                pp = new Praparat(rawImp, null, ViewMode.SingleGrid, true);
                Image3D rawImage = new Image3D(rawImp);

                publish("Phase 1: Denoising (Fast NLM)...");
                DenoiseFilter denoiser = new DenoiseFilter(15, 1);
                Image3D denoisedImage = denoiser.apply(rawImage);

                publish("Phase 1: Jerman 3D Vessel Enhancement...");
                JermanFilter3D jermanFilter = new JermanFilter3D();
                Image3D jermanImage = jermanFilter.apply(denoisedImage, new double[]{1.0, 2.0, 3.0});

                publish("Phase 2: Vessel Segmentation...");
                VesselSegmenter segmenter = new VesselSegmenter();
                Image3D segmentedMask = segmenter.segment(jermanImage);
                // 3D表示用に VolumeData 化
                segVolume = VolumeLoader.loadDicom(segmentedMask.getImagePlus().duplicate());

                publish("Phase 2: Skeletonization...");
                Skeletonizer skeletonizer = new Skeletonizer();
                Image3D skeletonImage = skeletonizer.skeletonize(segmentedMask);

                publish("Phase 3: Graph Construction...");
                TreeGraphBuilder builder = new TreeGraphBuilder();
                vesselTree = builder.build(skeletonImage);
                GraphPruner pruner = new GraphPruner();
                pruner.prune(vesselTree, 3.0);
                pruner.mergeLinearBranches(vesselTree);

                publish("Phase 4: Distance Transform & Features...");
                DistanceTransform3D dt3D = new DistanceTransform3D();
                Image3D distanceMap = dt3D.computeDistanceMap(segmentedMask);
                FeatureExtractor extractor = new FeatureExtractor();
                extractor.extractInscribedRadii(vesselTree, distanceMap);
                extractor.extractBulgeRatiosAndCurvatures(vesselTree, segmentedMask);

                publish("Phase 4: Detecting Aneurysms...");
                AneurysmDetector detector = new AneurysmDetector(1.35, 0.65, 0.0);
                candidates = detector.detect(vesselTree);

                publish("Phase 5: Saliency Scoring...");
                SaliencyScorer scorer = new SaliencyScorer();
                scorer.scoreAndSort(candidates);

                publish("Analysis Complete! Launching UI...");
                Thread.sleep(500); // UIへの遷移を滑らかに見せるための少しのタメ
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                // publish() で送られたメッセージをラベルに反映 (UIスレッドで安全に実行される)
                String latestMessage = chunks.get(chunks.size() - 1);
                statusLabel.setText(latestMessage);
            }

            @Override
            protected void done() {
                progressDialog.dispose(); // ダイアログを閉じる
                try {
                    get(); // 例外が起きていればここでキャッチされる
                    
                    // UIの起動
                    AneurysmDetectorUI ui = new AneurysmDetectorUI(candidates, segVolume, pp, true);
                    // ★ 追加: 構築したVesselTreeをUIに渡し、カラーマップ中心線を生成！
                    ui.loadSkeletonColorMap(vesselTree);
                    ui.setVisible(true);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Error during analysis: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute(); // バックグラウンド処理開始
        progressDialog.setVisible(true); // ダイアログを表示してユーザーの操作をブロック
    }
}