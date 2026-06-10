package com.vis.aneurysmdetector.ui;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.CandidateType;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D3.roi.FreeFormRoi3D;
import com.vis.core.view.D3.roi.SphereRoi3D;
import com.vis.core.view.D3.ui.VolumeData;
import org.lwjgl.opengl.awt.GLData;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
public class AneurysmDetectorUI extends JFrame {

    private JPanel mainVisualPanel;
    private AneurysmGLCanvas glCanvas; // ★追加: カスタムGLCanvas
    
    private JPanel judgePanel;
    private JLabel judgeLabel;
    private JPanel listContainer;
    private JCheckBox allClearCheckBox;
    
    JToggleButton btnShowMarkers;

    private List<AneurysmCandidate> candidateList;
    private List<CandidateItemPanel> itemPanelList;
    
    private VolumeData volumeData;
    private Praparat praparat;
    
    AneurysmCandidate highlightedCandidate;
    
 // ROIカラーの設定 (ID 1=通常赤, ID 2=ハイライト黄)
    private static final int ROI_ID_NORMAL = 1;
    private static final int ROI_ID_HIGHLIGHT = 2;

    // ★追加: VolumeData をコンストラクタで受け取る
    public AneurysmDetectorUI(List<AneurysmCandidate> candidates, VolumeData volumeData, Praparat praparat) {
        this.candidateList = candidates;
        this.volumeData = volumeData;
        this.praparat = praparat;
        
        this.itemPanelList = new ArrayList<>();

        setTitle("Cerebral Aneurysm Computer-Aided Detection (CADe) Workstation");
        setSize(1280, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(850);
        splitPane.setResizeWeight(0.7);

        // ====================================================================
        // 1. 左側: MIP/VR表示パネル (GLCanvas の統合)
        // ====================================================================
        mainVisualPanel = new JPanel(new BorderLayout());
        
        // 1.1 ツールバー (表示トグルスイッチ群)
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        btnShowMarkers = new JToggleButton("🔴 Show Sphere Marker", true);
        JToggleButton btnShowBoxes = new JToggleButton("🔲 Boundingbox", false);
        
         	// ★ マーカーON/OFFトグルボタン
        btnShowMarkers = new JToggleButton("🔴 Sphere Marker (ON)", true);
        btnShowMarkers.addActionListener(e -> {
            boolean isShowing = btnShowMarkers.isSelected();
            btnShowMarkers.setText(isShowing ? "🔴 Sphere Marker (ON)" : "⚪ Sphere Marker (OFF)");
            updateMarkersToCanvas(); // トグル時にROIを再生成/更新
        });
        
        btnShowBoxes.addActionListener(e -> {
            glCanvas.setShowBoundingBoxes(btnShowBoxes.isSelected());
            glCanvas.repaint();
        });
        
        toolBar.add(btnShowMarkers);
        toolBar.addSeparator();
        toolBar.add(btnShowBoxes);
        
        mainVisualPanel.add(toolBar, BorderLayout.NORTH);

        // 1.2 GLCanvas のセットアップ
        GLData data = new GLData();
        data.majorVersion = 3;
        data.minorVersion = 3;
        data.profile = GLData.Profile.CORE;
        
        glCanvas = new AneurysmGLCanvas(data);
        if (volumeData != null) {
            glCanvas.setVolumeData(volumeData);
            glCanvas.setMIPMode(true);
            glCanvas.setShowRoi(true); // ★ ROI描画を有効化
        }
        glCanvas.setCandidates(candidateList); // 候補データをCanvasに渡す
        
        mainVisualPanel.add(glCanvas, BorderLayout.CENTER);
        splitPane.setLeftComponent(mainVisualPanel);

        // ====================================================================
        // 2. 右側: サイドバー (判定パネル & チェックリスト)
        // ====================================================================
        JPanel sidebarPanel = new JPanel(new BorderLayout());
        sidebarPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.DARK_GRAY));

        buildJudgePanel();
        sidebarPanel.add(judgePanel, BorderLayout.NORTH);

        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        
        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        sidebarPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        southPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        allClearCheckBox = new JCheckBox("全てClear (Clear All Candidates)");
        allClearCheckBox.setFont(new Font("Meiryo", Font.BOLD, 13));
        allClearCheckBox.addActionListener(e -> handleAllClear(allClearCheckBox.isSelected()));
        southPanel.add(allClearCheckBox);
        sidebarPanel.add(southPanel, BorderLayout.SOUTH);

        splitPane.setRightComponent(sidebarPanel);
        add(splitPane, BorderLayout.CENTER);

        // データの流し込みと初期化
        populateCandidates();
        updateJudgeStatus();
     // ★ 初回のマーカー(ROI)生成
        updateMarkersToCanvas();
        

        // Canvasの再描画タイマー（60fps駆動）
        Timer timer = new Timer(16, e -> {
            if (glCanvas != null) {
                glCanvas.render();
                glCanvas.repaint();
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    private void buildJudgePanel() {
        judgePanel = new JPanel(new BorderLayout());
        judgePanel.setPreferredSize(new Dimension(350, 60));
        judgePanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        
        judgeLabel = new JLabel("", SwingConstants.CENTER);
        judgeLabel.setFont(new Font("Meiryo", Font.BOLD, 18));
        judgePanel.add(judgeLabel, BorderLayout.CENTER);
    }

    private void populateCandidates() {
        listContainer.removeAll();
        itemPanelList.clear();

        for (AneurysmCandidate candidate : candidateList) {
            CandidateItemPanel itemPanel = new CandidateItemPanel(candidate);
            listContainer.add(itemPanel);
            itemPanelList.add(itemPanel);
        }
        
        listContainer.add(Box.createVerticalGlue());
        listContainer.revalidate();
        listContainer.repaint();
    }
    
    /**
     * 現在の候補リストとUIの状態（ON/OFF、ハイライト）に基づいて、
     * FreeFormRoi3D のリストを生成し、GLCanvas に転送します。
     */
    private void updateMarkersToCanvas() {
        if (volumeData == null || praparat == null || glCanvas == null) return;

        List<FreeFormRoi3D> roiList = new ArrayList<>();

        // トグルボタンがOFFの場合は、空のリストを渡して既存のROIを消去する
        if (!btnShowMarkers.isSelected()) {
            double[] dummyIpp = {0,0,0};
            double[] dummyIop = {1,0,0, 0,1,0};
            double[] dummyStepZ = {0,0,1};
            glCanvas.setRoiData(roiList, dummyIpp, dummyIop, dummyStepZ);
            return;
        }

        // 空間のメタデータを取得（GLCanvas.setRoiData に必要）
        // 最初のSlideGlassから現在のC(チャンネル)とT(タイムフレーム)も取得しておく
        SlideGlass firstSg = praparat.getAllSlides().get(0);
        com.vis.dicom.DicomObject header = firstSg.getHeader();
        int frameIdx = praparat.isMultiFrame() ? header.getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1 : 0;
        
        double[] startIpp = praparat.getSafeIPP(header, frameIdx);
        double[] iop = praparat.getSafeIOP(header, frameIdx);
        double spZ = header.getDouble(com.vis.dicom.Tag.SpacingBetweenSlices, header.getDouble(com.vis.dicom.Tag.SliceThickness, 1.0));
        
        // nベクトル (Z方向へのステップベクトル) を計算
        double[] n = new double[3];
        n[0] = iop[1]*iop[5] - iop[2]*iop[4];
        n[1] = iop[2]*iop[3] - iop[0]*iop[5];
        n[2] = iop[0]*iop[4] - iop[1]*iop[3];
        double[] stepZ = { n[0]*spZ, n[1]*spZ, n[2]*spZ };

        // 現在表示しているボリュームの C と T を取得（スライス特定用）
        int[] zctArray = praparat.getZCTArray(firstSg);
        int currentC = zctArray[1];
        int currentT = zctArray[2];

        // 候補を走査してボクセル化（FreeFormRoi3D の生成）
        for (AneurysmCandidate c : candidateList) {
            if (c.isCleared()) continue;

            Point3D p = c.getPeakPoint(); // ボクセル座標 (x, y, z)
            
            // 半径(mm)
            double radiusMm = Math.min(5.0, c.getMaxBulgeRatio() * 1.5); 

            // ========================================================
            // ★ 最も近い SlideGlass (Zスライス) の取得
            // ========================================================
            // ボクセル座標の Z (p.z) はそのままスライスインデックスとして利用可能
            int targetZctIdx = praparat.calcZctIndex(new int[]{p.z, currentC, currentT});
            SlideGlass targetSg = praparat.getSlideGlassAt(targetZctIdx);
            
            // 万が一該当スライスが取得できない場合のフォールバック
            if (targetSg == null) {
                targetSg = firstSg;
            }

            // ========================================================
            // ★ ROIの2Dバウンディングボックス(x, y, width, height)の算出
            // ========================================================
            // 半径(mm) を ピクセルサイズで割って画像上のピクセル幅に換算
            int radiusPxX = (int) Math.round(radiusMm / volumeData.pixelSpacingX);
            int radiusPxY = (int) Math.round(radiusMm / volumeData.pixelSpacingY);
            
            int boxX = p.x - radiusPxX;
            int boxY = p.y - radiusPxY;
            int boxWidth = radiusPxX * 2;
            int boxHeight = radiusPxY * 2;

            // 中心が存在する SlideGlass と 2Dバウンディングボックスを渡してインスタンス化
            SphereRoi3D sphere = new SphereRoi3D(boxX, boxY, boxWidth, boxHeight, targetSg);
            
            // ※補足：もし SphereRoi3D クラスに setRadiusMm(...) や setCenter(...) のような
            // 物理単位を厳密に上書きするメソッドがあれば、ここで呼び出しておくとより正確です。

            // グループ名で「通常」か「ハイライト」かを分ける
            String groupName = (c == highlightedCandidate) ? "HighlightMarker" : "NormalMarker";
            
            // SphereRoi3D をボクセル化して FreeFormRoi3D のマスクを作成
            FreeFormRoi3D maskRoi = FreeFormRoi3D.createFromSphere(praparat, sphere, groupName);
            
            // 色の設定
            if (c == highlightedCandidate) {
                maskRoi.setStrokeColor(Color.YELLOW); // ハイライトは黄色
            } else {
                maskRoi.setStrokeColor(Color.RED);    // 通常は赤
            }

            roiList.add(maskRoi);
        }

        // GLCanvasにROIデータを流し込む（非同期でマスクがGPUへ転送される）
        glCanvas.setRoiData(roiList, startIpp, iop, stepZ);
    }

    public void updateJudgeStatus() {
        int unclearedCount = 0;
        for (AneurysmCandidate c : candidateList) {
            if (!c.isCleared()) {
                unclearedCount++;
            }
        }

        if (unclearedCount > 0) {
            judgePanel.setBackground(new Color(255, 220, 220));
            judgeLabel.setText("⚠ Suspected Cerebral Aneurysm (" + unclearedCount + " uncleared)");
            judgeLabel.setForeground(new Color(180, 0, 0));
        } else {
            judgePanel.setBackground(new Color(220, 245, 220));
            judgeLabel.setText("✔ No Findings (All Cleared)");
            judgeLabel.setForeground(new Color(0, 120, 0));
        }
        
        allClearCheckBox.setSelected(unclearedCount == 0);
        glCanvas.repaint(); // Clear状態がCanvas(マーカー描画)にも反映されるように再描画
    }

    private void handleAllClear(boolean selectAll) {
        for (CandidateItemPanel itemPanel : itemPanelList) {
            itemPanel.setClearStatus(selectAll);
        }
        updateJudgeStatus();
    }

    // ========================================================================
    // 動脈瘤候補チェックパネル
    // ========================================================================
    @SuppressWarnings("serial")
	private class CandidateItemPanel extends JPanel {
        private final AneurysmCandidate candidate;
        private final JCheckBox clearCheckBox;
        private boolean isSelected = false;

        public CandidateItemPanel(AneurysmCandidate c) {
            this.candidate = c;
            
            setLayout(new GridBagLayout());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                    new EmptyBorder(8, 8, 8, 8)
            ));
            setBackground(Color.WHITE);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.BOTH;

            // 1. 左側: Mini 3D Box (プレースホルダー)
            JPanel mini3DPlaceholder = new JPanel(new BorderLayout());
            mini3DPlaceholder.setBackground(Color.BLACK);
            mini3DPlaceholder.setPreferredSize(new Dimension(70, 70));
            JLabel miniLabel = new JLabel("Mini3D", SwingConstants.CENTER);
            miniLabel.setFont(new Font("Arial", Font.PLAIN, 10));
            miniLabel.setForeground(Color.DARK_GRAY);
            mini3DPlaceholder.add(miniLabel, BorderLayout.CENTER);

            gbc.gridx = 0; gbc.gridy = 0;
            gbc.gridwidth = 1; gbc.gridheight = 2;
            gbc.weightx = 0.0; gbc.weighty = 1.0;
            gbc.insets = new Insets(0, 0, 0, 10);
            add(mini3DPlaceholder, gbc);

            // 2. 中央: パラメータ群
            JLabel scoreLabel = new JLabel(String.format("Score: %.1f", c.getScore()));
            scoreLabel.setFont(new Font("Meiryo", Font.BOLD, 13));
            scoreLabel.setForeground(c.getScore() > 70 ? Color.RED : Color.DARK_GRAY);

            JLabel typeLabel = new JLabel("[" + c.getType() + "]");
            typeLabel.setFont(new Font("Meiryo", Font.ITALIC, 12));
            
            Point3D p = c.getPeakPoint();
            JLabel coordLabel = new JLabel(String.format("Pos: (%d, %d, %d)", p.x, p.y, p.z));
            coordLabel.setFont(new Font("Arial", Font.PLAIN, 11));

            gbc.gridx = 1; gbc.gridy = 0;
            gbc.gridheight = 1;
            gbc.weightx = 0.3; gbc.weighty = 0.5;
            gbc.insets = new Insets(0, 0, 2, 5);
            add(scoreLabel, gbc);

            gbc.gridx = 2; gbc.weightx = 0.3;
            add(typeLabel, gbc);

            gbc.gridx = 3; gbc.weightx = 0.4;
            add(coordLabel, gbc);

            JLabel bulgeLabel = new JLabel(String.format("Bulge Ratio: %.2f", c.getMaxBulgeRatio()));
            bulgeLabel.setFont(new Font("Meiryo", Font.PLAIN, 11));
            bulgeLabel.setForeground(Color.GRAY);

            JLabel siLabel = new JLabel(String.format("Shape Index: %.2f", c.getMaxShapeIndex()));
            siLabel.setFont(new Font("Meiryo", Font.PLAIN, 11));
            siLabel.setForeground(Color.GRAY);

            gbc.gridx = 1; gbc.gridy = 1;
            gbc.weightx = 0.3; gbc.weighty = 0.5;
            gbc.insets = new Insets(2, 0, 0, 5);
            add(bulgeLabel, gbc);

            gbc.gridx = 2; gbc.gridwidth = 2; gbc.weightx = 0.7;
            add(siLabel, gbc);

            // 3. 右側: Clear チェックボックス
            clearCheckBox = new JCheckBox("Clear");
            clearCheckBox.setFont(new Font("Meiryo", Font.PLAIN, 11));
            clearCheckBox.setBackground(Color.WHITE);
            clearCheckBox.setSelected(c.isCleared());
            clearCheckBox.addActionListener(e -> {
                candidate.setCleared(clearCheckBox.isSelected());
                updateJudgeStatus();
            });

            gbc.gridx = 4; gbc.gridy = 0;
            gbc.gridwidth = 1; gbc.gridheight = 2;
            gbc.weightx = 0.0; gbc.weighty = 1.0;
            gbc.insets = new Insets(0, 5, 0, 0);
            add(clearCheckBox, gbc);

            // 4. マウスイベント（キャンバスへのハイライト通知）
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { highlightThisPanel(); }
            });
            clearCheckBox.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { highlightThisPanel(); }
            });
        }

        public void setClearStatus(boolean cleared) {
            clearCheckBox.setSelected(cleared);
            candidate.setCleared(cleared);
        }

        private void highlightThisPanel() {
            for (CandidateItemPanel panel : itemPanelList) {
                panel.setSelected(false);
            }
            setSelected(true);
            
            // ハイライト対象を更新
            highlightedCandidate = candidate;
            
            // 対象が変わったのでマーカーの色を更新するために再計算
            updateMarkersToCanvas();
            
            // TODO: 余力があれば、ここで glCanvas.camera.LookAt(...) などを呼び出して
            // カメラを対象の座標にズームさせると完璧です。
        }

        public void setSelected(boolean selected) {
            this.isSelected = selected;
            if (selected) {
                setBackground(new Color(230, 240, 255));
                clearCheckBox.setBackground(new Color(230, 240, 255));
            } else {
                setBackground(Color.WHITE);
                clearCheckBox.setBackground(Color.WHITE);
            }
            repaint();
        }
    }

    // ========================================================================
    // UI テスト起動 (VolumeData に null を渡してUI骨組みだけを起動可能)
    // ========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // ダミーの検出候補リストを作成
            List<AneurysmCandidate> dummyCandidates = new ArrayList<>();
            
            // 1. テストデータ1: 非常に危険な側壁瘤（高スコア）
            AneurysmCandidate c1 = new AneurysmCandidate(null);
            c1.addPoint(new Point3D(250, 133, 39), 5.2, 0.98, 0.15);
            c1.setScore(98.5); 
            c1.setType(CandidateType.SACCULAR);
            dummyCandidates.add(c1);

            // 2. テストデータ2: リスク中程度の分岐部動脈瘤（中スコア）
            AneurysmCandidate c2 = new AneurysmCandidate(null);
            c2.addPoint(new Point3D(148, 253, 39), 2.1, 0.76, 0.05);
            c2.setScore(65.4); 
            c2.setType(CandidateType.BIFURCATION);
            dummyCandidates.add(c2);

            // 3. テストデータ3: 正常血管の急カーブによるノイズ（低スコア）
            AneurysmCandidate c3 = new AneurysmCandidate(null);
            c3.addPoint(new Point3D(330, 220, 64), 1.38, 0.66, 0.01);
            c3.setScore(18.2); 
            c3.setType(CandidateType.UNKNOWN);
            dummyCandidates.add(c3);

            // ★ 修正ポイント: 引数を最新の (candidates, volumeData, praparat) の3つに合わせる
            // 画像データがない単体起動時は、後ろの2つに null を渡すことで安全にUIのガワだけをテストできます
            AneurysmDetectorUI ui = new AneurysmDetectorUI(dummyCandidates, null, null);
            ui.setVisible(true);
        });
    }
}