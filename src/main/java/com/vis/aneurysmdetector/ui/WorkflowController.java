package com.vis.aneurysmdetector.ui;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Branch;
import com.vis.aneurysmdetector.core.VesselTree;
import com.vis.aneurysmdetector.cpr.UnwrappedImage2D;
import com.vis.aneurysmdetector.cpr.VesselUnwrapper;

import java.util.List;

/**
 * UIの進行（①俯瞰 → ②分岐部 → ③側壁）を管理し、
 * アルゴリズム層とプレゼンテーション層（View）を繋ぐコントローラー。
 */
public class WorkflowController {

    // --- データモデル ---
    private Image3D originalVolume;
    private Image3D vesselMask;
    private VesselTree vesselTree;

    // --- ビュー (UIコンポーネント) ---
    private View3DRenderer view3D;
    private View2DCPR viewCPR;

    // --- 状態管理 ---
    private List<AneurysmCandidate> spuriousBranches;     // ①の候補
    private List<AneurysmCandidate> bifurcationAneurysms; // ②の候補
    
    public WorkflowController(View3DRenderer view3D, View2DCPR viewCPR) {
        this.view3D = view3D;
        this.viewCPR = viewCPR;
        
        // CPRビュー上のクリックイベントを受け取る
        this.viewCPR.setCPRClickListener(this::focusOnCandidate);
    }

    /**
     * システムの初期化とデータセットアップ
     */
    public void initData(Image3D original, Image3D mask, VesselTree tree) {
        this.originalVolume = original;
        this.vesselMask = mask;
        this.vesselTree = tree;
        
        view3D.setVolumeData(originalVolume);
    }

    /**
     * 【Step 1】 ざっと脳血管を俯瞰する（短い枝のプルーニングと提示）
     */
    public void startStep1Overview() {
//        Pruner pruner = new Pruner(vesselMask);
//        this.spuriousBranches = pruner.pruneAndExtractCandidates(vesselTree);

        // 3Dビュー全体を表示し、怪しい短い枝をハイライト表示する
        view3D.resetCamera();
        view3D.setMIPMode(true); // 全体俯瞰はMIPが見やすい
        view3D.highlightCandidates(spuriousBranches);
        
        // TODO: UIパネルに spuriousBranches のリストを表示し、Clearチェックボックスを生成
    }

    /**
     * 【Step 2】 分岐部に動脈瘤が無いか確認する
     */
    public void startStep2Bifurcations() {
//        BifurcationAnalyzer analyzer = new BifurcationAnalyzer(vesselMask);
//        this.bifurcationAneurysms = analyzer.analyze(vesselTree);
//
//        // スコアが最も高い最初の分岐部候補にフォーカスを当てる
//        if (!bifurcationAneurysms.isEmpty()) {
//            focusOnCandidate(bifurcationAneurysms.get(0));
//        }
        
        // TODO: UIパネルに bifurcationAneurysms のリストを表示し、Clearチェックボックスを生成
    }

    /**
     * 【Step 3】 側壁瘤を探索する（CPR展開図の提示）
     */
    public void startStep3Sidewalls() {
        VesselUnwrapper unwrapper = new VesselUnwrapper(36, 15.0);
        
        // 主要な枝（プルーニングされなかった正常な長さの枝）に対して展開図を作成
        for (Branch branch : vesselTree.getBranches()) {
//            if (!branch.isPruned()) {
//                UnwrappedImage2D unwrapped2D = unwrapper.unwrap(vesselMask, branch);
//                List<AneurysmCandidate> sidewallAnomalies = unwrapped2D.findAnomalies();
//                
//                // CPRビューに展開図と検出された異常起伏をセット
//                viewCPR.setUnwrappedData(unwrapped2D, sidewallAnomalies);
//                
//                // 最初の枝を表示したら一旦ブレイク（UIのリスト選択で切り替える想定）
//                break; 
//            }
        }
    }

    /**
     * UIリスト上で特定の候補がクリックされたときの処理
     */
    public void focusOnCandidate(AneurysmCandidate candidate) {
        if (candidate == null) return;

        // 3Dクロップ表示の更新（Volume Renderingに切り替えて対象部位にフォーカス）
        view3D.setMIPMode(false); // DVRモードへ
//        view3D.focusAndCrop(candidate.getCenterPoint(), 20); // 20ボクセル半径でクロップ
    }
}