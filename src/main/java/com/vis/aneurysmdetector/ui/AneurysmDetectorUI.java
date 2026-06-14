/**
 * copyright visionary imaging services, inc.
 */
package com.vis.aneurysmdetector.ui;

import com.vis.core.view.D2.ui.SeriesWindow;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D3.ui.VolumeData;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.CandidateType;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.anatomy.GraphPruner;
import com.vis.aneurysmdetector.feature.FeatureExtractor;
import com.vis.aneurysmdetector.detection.AneurysmDetector;
import com.vis.aneurysmdetector.detection.SaliencyScorer;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.VesselTree;
import com.vis.core.view.D3.ui.VolumeLoader;
import com.vis.core.view.D3.util.AlignMesh;

import ij.ImagePlus;

import org.lwjgl.opengl.awt.GLData;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class AneurysmDetectorUI extends JFrame {
	
	private JPanel mainVisualPanel;
	private AneurysmGLCanvas glCanvas; // ★追加: カスタムGLCanvas

	private JPanel judgePanel;
	private JLabel judgeLabel;
	private JPanel listContainer;
	private JCheckBox btnAllNormal;

	// --- フィルター用ラジオボタン ---
	private JRadioButton rbSuspectedOnly;
	private JRadioButton rbNormalOnly;
	private JRadioButton rbAll;

	private List<AneurysmCandidate> candidateList;
	private List<CandidateItemPanel> itemPanelList;
	
	JSplitPane splitPane;
	private JTabbedPane rightTabbedPane;
	private JPanel branchListContainer;
	private JSpinner spnCprWidth;
	private JSpinner spnCprAngle;
	private double maxBranchLength = 0.0;

	private ImagePlus rawImp;
	private ImagePlus nlmResultImp;
	private ImagePlus jermanImp;
	private Image3D vesselMask;
	private Image3D distanceMap;
	private VesselTree vesselTree;
	
	private VolumeData segVolume;
		
	private ij.process.LUT currentLut;//buldge colorbar
	
	AneurysmCandidate highlightedCandidate;

	public AneurysmDetectorUI(List<AneurysmCandidate> candidates, VolumeData volumeData, ImagePlus rawImp, boolean isStandalone) {
		this.candidateList = candidates;
		this.rawImp = rawImp;
		
		this.itemPanelList = new ArrayList<>();

		setTitle("Cerebral Aneurysm Computer-Aided Detection (CADe) Workstation");
		setSize(1280, 800);
		setLocationRelativeTo(null);
		
		// ★ 修正: フラグに応じて、右上の「×」ボタンを押したときの挙動を自動分岐
        if (isStandalone) {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // アプリ全体を終了
        } else {
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // この画面だけを破棄
        }
		
		setLayout(new BorderLayout());
		
		// ====================================================================
        // ★ 追加: メニューバー (JMenuBar, JMenu, JMenuItem) のセットアップ
        // ====================================================================
        JMenuBar menuBar = new JMenuBar();

        // --- 1. File メニュー ---
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("📁 Open DICOM Folder...");
        openItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select DICOM Series Folder");
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // フォルダのみ選択可能にする
            fileChooser.setAcceptAllFileFilterUsed(false);

			// ダイアログを表示
			int userSelection = fileChooser.showOpenDialog(this);
			if (userSelection == JFileChooser.APPROVE_OPTION) {
				java.io.File selectedFolder = fileChooser.getSelectedFile();
				com.vis.core.log.Log.logger.info("Selected folder: " + selectedFolder.getAbsolutePath());
				// ==========================================================
				// ★ 実装: UIを破棄して、新しいデータで解析を再スタートする
				// ==========================================================
				// 1. 現在の 3D Viewer 画面を閉じてリソースを解放する
				this.dispose();

				// 2. AneurysmCADeApp の解析パイプラインに新しいフォルダのパスを渡して呼び出す
				// (プログレスダイアログが自動で立ち上がり、終わると新しいUIが開きます)
				AneurysmCADeApp.startAnalysis(selectedFolder.getAbsolutePath());
			}
        });
        fileMenu.add(openItem);

        fileMenu.addSeparator(); // 区切り線

        JMenuItem exitItem = new JMenuItem("Exit");
		// ==========================================================
		// ★ 修正: 単体起動かどうかに応じて終了処理をスマートに分岐する
		// ==========================================================
		exitItem.addActionListener(e -> {
			if (isStandalone) {
				com.vis.core.log.Log.logger.info("Standalone mode: Exiting application.");
				System.exit(0); // アプリ全体を完全に終了
			} else {
				com.vis.core.log.Log.logger.info("Sub-window mode: Disposing this frame.");
				this.dispose(); // この画面だけを閉じ、メモリを解放して呼出元に戻る
			}
		});
        fileMenu.add(exitItem);

        // --- 2. Process メニュー ---
        JMenu processMenu = new JMenu("Process");
        
		// ==========================================================
		// ★ 追加: NLM結果表示用メニューアイテム
		// ==========================================================
		JMenuItem showNlmItem = new JMenuItem(" Show Non-Local Means Results...");
		showNlmItem.addActionListener(e -> {
			if (this.nlmResultImp != null) {
				// ※ ここは実際の SeriesViewer の仕様に合わせて調整してください
				new SeriesWindow(this.nlmResultImp, null, ViewMode.Normal);
			} else {
				JOptionPane.showMessageDialog(this, "NLM結果の画像データが保持されていません。", "Data Not Found",
						JOptionPane.WARNING_MESSAGE);
			}
		});
        processMenu.add(showNlmItem);
        
        JMenuItem showJermanItem = new JMenuItem(" Show Jerman Filter Results...");
		showJermanItem.addActionListener(e -> {
			if (this.jermanImp != null) {
				// ※ ここは実際の SeriesViewer の仕様に合わせて調整してください
				new SeriesWindow(this.jermanImp, null, ViewMode.Normal);
			} else {
				JOptionPane.showMessageDialog(this, "Jerman filter結果の画像データが保持されていません。", "Data Not Found",
						JOptionPane.WARNING_MESSAGE);
			}
		});
        processMenu.add(showJermanItem);
        
		// ==========================================================
		// ★ 修正: パラメータ調整ダイアログと再計算ロジックの実装
		// ==========================================================
		JMenuItem runDetectionItem = new JMenuItem("🚀 Run Pipeline");
		runDetectionItem.addActionListener(e -> {
			if (this.jermanImp == null) {
				JOptionPane.showMessageDialog(this, "Jermanフィルタの結果がありません。最初から解析を実行してください。", "Error",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			showParameterDialogAndRun();
		});
		processMenu.add(runDetectionItem);

        processMenu.addSeparator(); // 区切り線

        JMenuItem clearToNormalItem = new JMenuItem("🧹 Set All to NORMAL");
        // 先ほど実装した一括変更ロジックと連動
        clearToNormalItem.addActionListener(e -> handleAllNormal());
        processMenu.add(clearToNormalItem);

        // --- 3. メニューバーへ追加して JFrame にセット ---
        menuBar.add(fileMenu);
        menuBar.add(processMenu);
        setJMenuBar(menuBar); // JFrameにメニューバーを登録
        // ====================================================================

		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setDividerLocation(750);
		splitPane.setResizeWeight(0.7);

		// ====================================================================
		// 1. 左側: MIP/VR表示パネル (GLCanvas の統合)
		// ====================================================================
		mainVisualPanel = new JPanel(new BorderLayout());

		// 1.1 ツールバー (表示トグルスイッチ群)
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);
		toolBar.setBorder(new EmptyBorder(5, 5, 5, 5));

		JToggleButton btnShowBoxes = new JToggleButton("🔲 Boundingbox", false);
		btnShowBoxes.addActionListener(e -> {
			boolean isShowing = btnShowBoxes.isSelected();
			btnShowBoxes.setText(isShowing ? "🔲 3D boundingbox (ON)" : "🔲 3D boundingbox (OFF)");
			glCanvas.setShowBoundingBoxes(isShowing);
			glCanvas.repaint();
		});
		toolBar.add(btnShowBoxes);
		
		JToggleButton btnShowAllBoxes = new JToggleButton("📦 Show All Boxes (ON)", true);
        btnShowAllBoxes.addActionListener(e -> {
            boolean showAll = btnShowAllBoxes.isSelected();
            btnShowAllBoxes.setText(showAll ? "📦 Show All Boxes (ON)" : "📦 Selected Box Only");
            if (glCanvas != null) {
                glCanvas.setShowAllBoundingBoxes(showAll);
                glCanvas.repaint();
            }
        });
        toolBar.add(btnShowAllBoxes);

		// ★ スケルトン表示トグルボタンを追加
		JToggleButton btnShowSkel = new JToggleButton("🧬 Saliency Skelton (OFF)", false);
		btnShowSkel.addActionListener(e -> {
			boolean isShowing = btnShowSkel.isSelected();
			btnShowSkel.setText(isShowing ? "🧬 Saliency Skelton (ON)" : "🧬 Saliency Skelton (OFF)");
			glCanvas.setShowSkeleton(isShowing);
			glCanvas.repaint();
		});
		toolBar.addSeparator();
		toolBar.add(btnShowSkel);
		
		// ==========================================================
		// ★ 追加: メッシュとボリューム(MIP)の表示切替トグルボタン
		// ==========================================================
		JToggleButton btnShowMesh = new JToggleButton("🌈 Colored Mesh (ON)", true);
		btnShowMesh.addActionListener(e -> {
			boolean isShowing = btnShowMesh.isSelected();
			btnShowMesh.setText(isShowing ? "🌈 Colored Mesh (ON)" : "🌈 Colored Mesh (OFF)");
			if (glCanvas != null) {
				glCanvas.setMeshVisible(isShowing);
			}
		});
		toolBar.addSeparator();
		toolBar.add(btnShowMesh);

		JToggleButton btnShowVol = new JToggleButton("☁ Volume MIP (OFF)", false);
		btnShowVol.addActionListener(e -> {
			boolean isShowing = btnShowVol.isSelected();
			btnShowVol.setText(isShowing ? "☁ Volume MIP (ON)" : "☁ Volume MIP (OFF)");
			if (glCanvas != null) {
				glCanvas.setShowVolume(isShowing);
			}
		});
		toolBar.add(btnShowVol);

		// ==========================================================
        // ★ 追加: 3Dビューの回転・ズーム・注視点を初期状態に戻すリセットボタン
        // ==========================================================
        JButton btnResetView = new JButton("🔄 Reset View");
        btnResetView.addActionListener(e -> {
            if (glCanvas != null) {
                glCanvas.resetCamera();
            }
        });
        toolBar.addSeparator();
        toolBar.add(btnResetView);

		mainVisualPanel.add(toolBar, BorderLayout.NORTH);

		// 1.2 GLCanvas のセットアップ
		GLData data = new GLData();
		data.majorVersion = 3;
		data.minorVersion = 3;
		data.profile = GLData.Profile.CORE;

		// ==========================================================
		// ★ フリッカー防止のための設定追加
		// ==========================================================
		data.doubleBuffer = true; // ★ 必須: paintGL()でswapBuffer()が設定されていること。
		data.forwardCompatible = true;
//		data.samples = 4; // ★ 必須: MSAA (マルチサンプル・アンチエイリアシング) を有効化し、線を滑らかにする
//		data.swapInterval = 1; // ★ 追加: 垂直同期 (VSync) を有効にしてティアリング(描画ズレ)を防ぐ
		// ==========================================================

		glCanvas = new AneurysmGLCanvas(data);
		if (volumeData != null) {
			glCanvas.setVolumeData(volumeData);
		}
		glCanvas.setCandidates(candidateList);

		mainVisualPanel.add(glCanvas, BorderLayout.CENTER);
		splitPane.setLeftComponent(mainVisualPanel);

		// ====================================================================
		// 2. 右側: サイドバー (判定パネル & チェックリスト)
		// ====================================================================
		buildRightSidebar();

		add(splitPane, BorderLayout.CENTER);
		
		this.currentLut = com.vis.configuration.Resources.LUT_PHASE.loadLUT();

		// データの流し込みと初期化
		populateCandidates();
		updateJudgeStatus();

		// ==========================================================
		// Canvasの再描画タイマー（フリッカー防止の完全同期処理）
		// ==========================================================
		Timer timer = new Timer(30, e -> {
			if (glCanvas != null && glCanvas.isDisplayable()) {
				// 1. OpenGLの3D描画とマウスイベントを処理
				glCanvas.render();
				// 2. SwingのRepaintManagerに、安全にカラーバーを描画させる
				glCanvas.repaint();
			} else {
				((Timer) e.getSource()).stop();
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
	
	public void buildInitialMesh() {
        // デフォルトのパラメータでメッシュ生成とカラーリングを裏で1回だけ走らせる
        executeFastRecalculation(3.0, 1.35, 0.65, 0.0);
    }
	
	// コンストラクタ内の右側サイドバー構築部分を書き換え
	private void buildRightSidebar() {
	    rightTabbedPane = new JTabbedPane();

	    // --- Tab 1: Candidates (既存のリスト) ---
	    JPanel candidatesTab = new JPanel(new BorderLayout());
	    buildJudgePanel();
	    candidatesTab.add(judgePanel, BorderLayout.NORTH);
	    
	    listContainer = new JPanel();
	    listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
	    JScrollPane candScroll = new JScrollPane(listContainer);
	    candidatesTab.add(candScroll, BorderLayout.CENTER);
	    
	    // (既存のフィルター系 southPanel の追加処理もここに入れる)
	    rightTabbedPane.addTab("Candidates", candidatesTab);

	    // --- Tab 2: Branches & CPR ---
	    JPanel branchesTab = new JPanel(new BorderLayout());
	    
	    // CPRコントロールパネル (太さと角度の指定)
	    JPanel cprControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	    cprControlPanel.setBorder(BorderFactory.createTitledBorder("CPR Settings"));
	    
	    spnCprWidth = new JSpinner(new SpinnerNumberModel(20.0, 5.0, 100.0, 1.0));
	    spnCprAngle = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 360.0, 5.0));
	    
	    cprControlPanel.add(new JLabel("Width(mm):"));
	    cprControlPanel.add(spnCprWidth);
	    cprControlPanel.add(new JLabel("Angle(deg):"));
	    cprControlPanel.add(spnCprAngle);
	    
	    branchesTab.add(cprControlPanel, BorderLayout.NORTH);

	    // ブランチリスト
	    branchListContainer = new JPanel();
	    branchListContainer.setLayout(new BoxLayout(branchListContainer, BoxLayout.Y_AXIS));
	    JScrollPane branchScroll = new JScrollPane(branchListContainer);
	    branchesTab.add(branchScroll, BorderLayout.CENTER);

	    rightTabbedPane.addTab("Vessel Branches", branchesTab);

	    // JSplitPane の右側にタブをセット
	    splitPane.setRightComponent(rightTabbedPane);
	}

	private void populateCandidates() {
		listContainer.removeAll();
		itemPanelList.clear();

		for (AneurysmCandidate candidate : candidateList) {
			// ==========================================================
			// ★ 選択されたラジオボタンによるフィルタリングロジック
			// ==========================================================
			if (rbSuspectedOnly != null && rbSuspectedOnly.isSelected()) {
				// SUSPECTED only: NORMAL のものはスキップ
				if (candidate.getType() == CandidateType.NORMAL) {
					continue;
				}
			} else if (rbNormalOnly != null && rbNormalOnly.isSelected()) {
				// NORMAL only: NORMAL 以外のものはスキップ
				if (candidate.getType() != CandidateType.NORMAL) {
					continue;
				}
			}
			// All の場合はスキップせずすべて表示

			CandidateItemPanel itemPanel = new CandidateItemPanel(candidate);
			listContainer.add(itemPanel);
			itemPanelList.add(itemPanel);
		}

		listContainer.add(Box.createVerticalGlue());
		listContainer.revalidate();
		listContainer.repaint();
	}

	public void populateBranchList() {
	    if (vesselTree == null || vesselTree.getBranches().isEmpty()) return;
	    
	    branchListContainer.removeAll();
	    maxBranchLength = 0.0;

	    // 1. 全ブランチの中から最長の長さを探す
	    for (com.vis.aneurysmdetector.core.Branch b : vesselTree.getBranches()) {
	        if (b.getLength() > maxBranchLength) {
	            maxBranchLength = b.getLength();
	        }
	    }

	    // 2. リストアイテムの生成
	    for (int i = 0; i < vesselTree.getBranches().size(); i++) {
	        com.vis.aneurysmdetector.core.Branch branch = vesselTree.getBranches().get(i);
	        
	        JPanel itemPanel = new JPanel(new BorderLayout(5, 5));
	        itemPanel.setBorder(BorderFactory.createCompoundBorder(
	            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
	            new EmptyBorder(5, 5, 5, 5)
	        ));

	        // ラベル情報 (IDと絶対長)
	        JLabel lblInfo = new JLabel(String.format("Branch #%d (%.1f mm)", i, branch.getLength()));
	        
	        // 相対長を示すプログレスバー
	        JProgressBar lengthBar = new JProgressBar(0, 100);
	        int percent = (int) Math.round((branch.getLength() / maxBranchLength) * 100);
	        lengthBar.setValue(percent);
	        lengthBar.setStringPainted(true);
	        lengthBar.setString(percent + "%");
	        
	        // CPR表示ボタン
	        JButton btnShowCPR = new JButton("Show CPR");
	        btnShowCPR.addActionListener(e -> showCPRForBranch(branch));

	        itemPanel.add(lblInfo, BorderLayout.NORTH);
	        itemPanel.add(lengthBar, BorderLayout.CENTER);
	        itemPanel.add(btnShowCPR, BorderLayout.EAST);
	        
	        branchListContainer.add(itemPanel);
	    }
	    branchListContainer.revalidate();
	    branchListContainer.repaint();
	}
	
	private void showCPRForBranch(com.vis.aneurysmdetector.core.Branch branch) {
	    if (segVolume == null) return; // VolumeDataがロードされている前提

	    double cprWidthMm = (Double) spnCprWidth.getValue();
	    double cprAngle = (Double) spnCprAngle.getValue();
	    double pixelSpacingMm = 0.5; // サンプリング解像度

	    // 1. パスの補間とフレーム計算
	    List<com.vis.aneurysmdetector.cpr.VesselPathInterpolator.PathPoint> smoothPath = 
	        com.vis.aneurysmdetector.cpr.VesselPathInterpolator.createSmoothPath(branch, distanceMap/*Image3D*/, pixelSpacingMm);

	    // 2. CPR画像の生成 (ここでは通常の長さを切り出す)
	    Image3D i3d = new Image3D(rawImp);
	    ImagePlus cprImage = com.vis.aneurysmdetector.cpr.CurvedPlanarReconstructor.extractStraightenedCPR(
	            i3d, smoothPath, cprWidthMm, pixelSpacingMm, cprAngle);

	    // 3. 【最長100%スケールへのパディング】
	    // キャンバスの最大高さを計算
	    int maxPixelHeight = (int) Math.ceil(maxBranchLength / pixelSpacingMm);
	    
	    // ImageJの機能を使ってキャンバスサイズを拡張（下方向に黒で埋める）
	    ij.plugin.CanvasResizer resizer = new ij.plugin.CanvasResizer();
	    ij.process.ImageProcessor paddedProcessor = resizer.expandImage(
	            cprImage.getProcessor(), 
	            cprImage.getWidth(), 
	            maxPixelHeight, 
	            0, 0 // X, Y のオフセット (0,0 なら左上に配置され、余白は右下に追加される)
	    );
	    
	    ImagePlus finalCprImp = new ImagePlus("CPR - Branch (Length: " + branch.getLength() + "mm)", paddedProcessor);
	    finalCprImp.getCalibration().pixelWidth = pixelSpacingMm;
	    finalCprImp.getCalibration().pixelHeight = pixelSpacingMm;
	    finalCprImp.getCalibration().setUnit("mm");
	    
	    // コントラストを合わせる
	    finalCprImp.setDisplayRange(rawImp.getDisplayRangeMin(), rawImp.getDisplayRangeMax());

	    // 4. SeriesWindow で表示
	    new com.vis.core.view.D2.ui.SeriesWindow(finalCprImp, null, com.vis.core.view.D2.ui.glasses.Praparat.ViewMode.Normal);
	}
	
	/**
     * すべての検出候補のタイプを一度に NORMAL（正常血管）に一括仕分けします。
     */
    private void handleAllNormal() {
        int reply = JOptionPane.showConfirmDialog(this, 
            "すべての候補の分類を NORMAL (正常血管) に変更しますか？\n(SUSPECTED only フィルター有効時はリストから非表示になります)", 
            "Bulk Change Confirmation", 
            JOptionPane.YES_NO_OPTION, 
            JOptionPane.WARNING_MESSAGE);
            
        if (reply == JOptionPane.YES_OPTION) {
            for (AneurysmCandidate c : candidateList) {
                c.setType(CandidateType.NORMAL);
            }
            populateCandidates(); // ラジオボタンのフィルター条件に従ってリストを再描画
            updateJudgeStatus();  // 判定ステータスとボタンの有効状態を更新
        }
    }

    /**
     * NORMAL 以外の「要疑い」の数をカウントし、上部パネルの警告状態を更新します。
     */
    public void updateJudgeStatus() {
        int suspectCount = 0;
        for (AneurysmCandidate c : candidateList) {
            // NORMAL 以外のタイプ（SACCULAR, BIFURCATION等）をカウント
            if (c.getType() != CandidateType.NORMAL) {
                suspectCount++;
            }
        }

        if (suspectCount > 0) {
            judgePanel.setBackground(new Color(255, 220, 220));
            judgeLabel.setText("⚠ Suspected Cerebral Aneurysm (" + suspectCount + " suspected)");
            judgeLabel.setForeground(new Color(180, 0, 0));
            if (btnAllNormal != null) btnAllNormal.setEnabled(true);
        } else {
            judgePanel.setBackground(new Color(220, 245, 220));
            judgeLabel.setText("✔ No Findings (All Cleared to NORMAL)");
            judgeLabel.setForeground(new Color(0, 120, 0));
            if (btnAllNormal != null) btnAllNormal.setEnabled(false); // 全てNORMALならボタンを無効化
        }
        
        if (glCanvas != null) glCanvas.repaint();
    }

	/**
	 * 抽出された VesselTree の中心線を解析し、Bulge Ratio に応じて色付けした 描画用の頂点配列を生成して Canvas に渡します。
	 */
	public void loadSkeletonColorMap(com.vis.aneurysmdetector.core.VesselTree tree) {
		if (tree == null)
			return;

		// 頂点リスト: (X, Y, Z, R, G, B, A) を1頂点とする。線分なので2頂点で1セット。
		java.util.List<Float> vertices = new java.util.ArrayList<>();
		
		// 血管マスク（右手系）の横幅 w を基準に、中心線のX座標を反転マッピング
		int w = this.vesselMask.getWidth();

		for (com.vis.aneurysmdetector.core.Branch branch : tree.getBranches()) {
			java.util.List<Point3D> nodes = branch.getPath();
			List<Double> BulgeRatios = branch.getBulgeRatios();

			if (nodes.size() < 2)
				continue;

			for (int i = 0; i < nodes.size() - 1; i++) {
				Point3D n1 = nodes.get(i);
				Point3D n2 = nodes.get(i + 1);

				// 頂点1
				vertices.add((float) (w - 1 - n1.x));
				vertices.add((float) n1.y);
				vertices.add((float) n1.z);
				float[] color1 = getBulgeColor(BulgeRatios.get(i));
				vertices.add(color1[0]);
				vertices.add(color1[1]);
				vertices.add(color1[2]);
				vertices.add(color1[3]);

				// 頂点2
				vertices.add((float) (w - 1 - n2.x));
				vertices.add((float) n2.y);
				vertices.add((float) n2.z);
				float[] color2 = getBulgeColor(BulgeRatios.get(i + 1));
				vertices.add(color2[0]);
				vertices.add(color2[1]);
				vertices.add(color2[2]);
				vertices.add(color2[3]);
			}
		}

		// float[] に変換
		float[] vArray = new float[vertices.size()];
		for (int i = 0; i < vertices.size(); i++) {
			vArray[i] = vertices.get(i);
		}

		glCanvas.setSkeletonData(vArray);
	}

	/**
	 * Bulge Ratio に応じてカラーマップを生成します。
	 */
	private float[] getBulgeColor(double bulge) {
		// 正常血管 (1.0) から 異常 (1.5以上) になるように 0.0 ~ 1.0 で正規化
		double norm = (bulge - 1.0) / (1.5 - 1.0);
		norm = Math.max(0.0, Math.min(norm, 1.0)); // 0.0 ~ 1.0 にクランプ

		if (this.currentLut != null) {
			// ★ LUTがロードされている場合は、256階調のパレットからRGBをサンプリング
			int lutIndex = (int) (norm * 255.0);
			lutIndex = Math.max(0, Math.min(255, lutIndex));
			
			float r = this.currentLut.getRed(lutIndex) / 255.0f;
			float g = this.currentLut.getGreen(lutIndex) / 255.0f;
			float b = this.currentLut.getBlue(lutIndex) / 255.0f;
			return new float[] { r, g, b, 1.0f };
		} else {
			// フォールバック: HSL色空間からRGBへの簡易変換 (青=240度, 赤=0度)
			float hue = (float) ((1.0 - norm) * 240.0 / 360.0);
			int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
			Color c = new Color(rgb);
			return new float[] { c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f, 1.0f };
		}
	}
	
	public void setNLMResults(ImagePlus nlmResultImp) {
		this.nlmResultImp = nlmResultImp;
	}
	
    public void setJermanResults(ImagePlus jermanImp) {
    	this.jermanImp = jermanImp;
    }
    
    public void setVesselMaskResults(Image3D vesselMask) {
    	this.vesselMask = vesselMask;
    }
    
    public void setDistanceMapResults(Image3D distanceMap) {
    	this.distanceMap = distanceMap;
    }
    
    public void setVesselTreeResults(VesselTree vesselTree) {
    	this.vesselTree = vesselTree;
    }
    
    /**
     * パラメータ入力ダイアログを表示し、Jerman画像からパイプラインを再実行する
     */
    private void showParameterDialogAndRun() {
        JDialog dialog = new JDialog(this, "Adjust Detection Parameters", true);
        dialog.setLayout(new BorderLayout());
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
		// --- パラメータ入力フィールドの作成 (SpinnerNumberModel で数値のみ許可) ---
		JSpinner spnPrune = new JSpinner(new SpinnerNumberModel(3.0, 0.0, 10.0, 0.5));
		// ★ 追加: ツールチップを設定
		spnPrune.setToolTipText("Valid range: 0.0 to 10.0 (mm)");

		JSpinner spnBulge = new JSpinner(new SpinnerNumberModel(1.35, 1.0, 3.0, 0.05));
		// ★ 追加: ツールチップを設定
		spnBulge.setToolTipText("Valid range: 1.0 to 3.0");

		JSpinner spnShape = new JSpinner(new SpinnerNumberModel(0.65, 0.0, 1.0, 0.05));
		// ★ 追加: ツールチップを設定
		spnShape.setToolTipText("Valid range: 0.0 to 1.0");

		JSpinner spnCurv = new JSpinner(new SpinnerNumberModel(0.0, -1.0, 1.0, 0.01));
		// ★ 追加: ツールチップを設定
		spnCurv.setToolTipText("Valid range: -1.0 to 1.0");

        Font font = new Font("Meiryo", Font.PLAIN, 12);
        
        // 1行目: Graph Pruning
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel lblPrune = new JLabel("Graph Pruning Threshold (mm):"); lblPrune.setFont(font);
        formPanel.add(lblPrune, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; spnPrune.setFont(font);
        formPanel.add(spnPrune, gbc);

        // 2行目: Bulge Ratio
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel lblBulge = new JLabel("Min Bulge Ratio (>= 1.0):"); lblBulge.setFont(font);
        formPanel.add(lblBulge, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; spnBulge.setFont(font);
        formPanel.add(spnBulge, gbc);

        // 3行目: Shape Index
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        JLabel lblShape = new JLabel("Min Shape Index (Sphere=1.0):"); lblShape.setFont(font);
        formPanel.add(lblShape, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; spnShape.setFont(font);
        formPanel.add(spnShape, gbc);

        // 4行目: Curvature
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        JLabel lblCurv = new JLabel("Min Gaussian Curvature:"); lblCurv.setFont(font);
        formPanel.add(lblCurv, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; spnCurv.setFont(font);
        formPanel.add(spnCurv, gbc);

        dialog.add(formPanel, BorderLayout.CENTER);

        // --- ボタンエリア ---
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancel = new JButton("Cancel");
        JButton btnRun = new JButton("Run Re-calculation");
        btnRun.setFont(new Font("Meiryo", Font.BOLD, 12));
        btnRun.setBackground(new Color(220, 240, 255));
        
        btnCancel.addActionListener(e -> dialog.dispose());
        btnRun.addActionListener(e -> {
            dialog.dispose();
            // 入力値を取得して再計算ワーカーを起動
            double pPrune = (Double) spnPrune.getValue();
            double pBulge = (Double) spnBulge.getValue();
            double pShape = (Double) spnShape.getValue();
            double pCurv  = (Double) spnCurv.getValue();
            executeFastRecalculation(pPrune, pBulge, pShape, pCurv);
        });

        btnPanel.add(btnCancel);
        btnPanel.add(btnRun);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * Jerman画像から後半のパイプラインのみを高速に再実行するワーカー
     */
    private void executeFastRecalculation(double pruneThresh, double bulgeThresh, double siThresh, double curvThresh) {
        JDialog progressDialog = new JDialog(this, "Fast Re-calculating...", true);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progressDialog.setSize(400, 200);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setLayout(new BorderLayout(10, 10));

        JLabel statusLabel = new JLabel("Starting fast pipeline...", SwingConstants.CENTER);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressDialog.add(statusLabel, BorderLayout.CENTER);
        progressDialog.add(progressBar, BorderLayout.SOUTH);

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            private List<AneurysmCandidate> newCandidates;
            private float[] calculatedBulgeMap; 
            
            // ★ 追加: ワーカー内で生成し、カラーリングまで完了したメッシュ
            private com.vis.core.view.D3.ui.MeshData coloredMesh; 

            @Override
            protected Void doInBackground() throws Exception {

                publish("Load Vessel Segmentation...");
                segVolume = VolumeLoader.loadDicom(vesselMask.getImagePlus().duplicate());

                publish("Graph Construction & Pruning (Thresh: " + pruneThresh + ")...");
                GraphPruner pruner = new GraphPruner();
                pruner.prune(vesselTree, pruneThresh);
                pruner.mergeLinearBranches(vesselTree);

                publish("Extract Features...");
                FeatureExtractor extractor = new FeatureExtractor();
                extractor.extractInscribedRadii(vesselTree, distanceMap);
                calculatedBulgeMap = extractor.extractBulgeRatiosAndCurvatures(vesselTree, vesselMask);

                // ==========================================================
                // ★ 追加: MarchingCubesによるメッシュ生成と、BulgeMapからの頂点カラーサンプリング
                // ==========================================================
                publish("Generating Colored 3D Mesh...");
                coloredMesh = com.vis.core.view.D3.ui.MarchingCubes.generateMesh(segVolume, 127.5f);
                
                if (coloredMesh != null && coloredMesh.vertices != null) {
                    int vertexCount = coloredMesh.vertices.length / 3;
                    float[] vertexColors = new float[vertexCount * 4]; // R, G, B, A
                    
                    int w = segVolume.width;
                    int h = segVolume.height;
                    int d = segVolume.depth;
                    
					for (int i = 0; i < coloredMesh.vertices.length; i += 3) {
						// 1. 頂点座標(物理mm)から、元のボクセルインデックスを逆算
						int x = (int) Math.round(coloredMesh.vertices[i] / segVolume.pixelSpacingX);
						int y = (int) Math.round(coloredMesh.vertices[i + 1] / segVolume.pixelSpacingY);
						int z = (int) Math.round(coloredMesh.vertices[i + 2] / segVolume.sliceThickness);

						// 安全のためのクランプ処理
						// ★ メッシュから逆算した右手系インデックス x を、左手系 x_local に戻す
						int x_local = w - 1 - x;
						x_local = Math.max(0, Math.min(w - 1, x_local)); // 安全のためのクランプ
						
						y = Math.max(0, Math.min(h - 1, y));
						z = Math.max(0, Math.min(d - 1, z));

						// 2. 1次元配列(BulgeMap)から膨らみ率を取得
						int idx = z * w * h + y * w + x_local;
						float bulge = calculatedBulgeMap[idx];

						// 3. Bulge Ratioを RGBA の色に変換
						float[] rgba = getBulgeColor(bulge);

						// 4. カラー配列に格納
						int cIdx = (i / 3) * 4;
						vertexColors[cIdx] = rgba[0];
						vertexColors[cIdx + 1] = rgba[1];
						vertexColors[cIdx + 2] = rgba[2];
						vertexColors[cIdx + 3] = rgba[3];
					}
                    
                    // MeshDataにカラー配列をセット
                    coloredMesh.colors = vertexColors;
                    
                    // 最後にGLCanvasの描画空間(-0.5 ~ 0.5)にアライメント
                    AlignMesh.alignMeshToVolume(coloredMesh, segVolume);
                }

                publish("Detecting Aneurysms...");
                AneurysmDetector detector = new AneurysmDetector(bulgeThresh, siThresh, curvThresh);
                newCandidates = detector.detect(vesselTree);

                publish("Saliency Scoring...");
                SaliencyScorer scorer = new SaliencyScorer();
                scorer.scoreAndSort(newCandidates);

                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                statusLabel.setText(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    get(); 
                    
                    candidateList = newCandidates;
                    
                    if (glCanvas != null) {
                        glCanvas.setVolumeData(segVolume);
                        glCanvas.setCandidates(candidateList);
                        
						// ==========================================================
						// ★ 修正: 完成したカラー付きメッシュをCanvasに登録して表示
						// ==========================================================
						if (coloredMesh != null) {
							glCanvas.addOrUpdateMesh("VesselMask", coloredMesh);
							glCanvas.setMeshVisible(true);

							// ★ 追加: 白いボリューム表示をOFFにして、メッシュを露出させる！
							glCanvas.setShowVolume(false);
							glCanvas.setShowRoi(false);

							// フリッカーが解決できない
							//glCanvas.addLegend(1.0, 1.5, "Bulge Ratio", LegendPosition.BOTTOM_RIGHT, currentLut);
						}
                    }
                    
                    populateCandidates();
                    updateJudgeStatus();
                    populateBranchList();
                    loadSkeletonColorMap(vesselTree);
                    
                    JOptionPane.showMessageDialog(AneurysmDetectorUI.this, 
                        "Calculation Complete, \nNum of aneurysm candidates: " + candidateList.size() + " ", 
                        "Calculation Complete", JOptionPane.INFORMATION_MESSAGE);
                        
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(AneurysmDetectorUI.this, "Error occured in recalculation...: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

	// ========================================================================
	// 動脈瘤候補チェックパネル
	// ========================================================================
	private class CandidateItemPanel extends JPanel {
		private final AneurysmCandidate candidate;
		private boolean isSelected = false;
		public CandidateItemPanel(AneurysmCandidate c) {
			this.candidate = c;

			setLayout(new GridBagLayout());
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
					new EmptyBorder(8, 8, 8, 8)));
			setBackground(Color.WHITE);

			GridBagConstraints gbc = new GridBagConstraints();
			gbc.fill = GridBagConstraints.BOTH;

			// ==========================================================
			// ★修正: 1. 左側: 動脈瘤周辺の2Dスライス・サムネイルの自動生成
			// ==========================================================
			JPanel mini3DPlaceholder = new JPanel(new BorderLayout());
			mini3DPlaceholder.setBackground(Color.BLACK);
			mini3DPlaceholder.setPreferredSize(new Dimension(70, 70));

			JLabel thumbnailLabel = new JLabel("No Image", SwingConstants.CENTER);
			thumbnailLabel.setForeground(Color.DARK_GRAY);
			
			Point3D p = c.getPeakPoint();
			
			// サムネイル画像の抽出ロジック
			if (rawImp != null) {
				try {
					com.vis.core.log.Log.logger
							.info("--- Thumbnail Debug: Candidate Pos: (" + p.x + ", " + p.y + ", " + p.z + ") ---");

					if (rawImp.getNSlices() == 0) {
						com.vis.core.log.Log.logger.warning("raw image is empty!");
					} else {
						// ImageJのスタックは「1始まり」
						int targetZ = p.z + 1;

						// スタックの範囲内か安全確認
						if (targetZ >= 1 && targetZ <= rawImp.getStackSize()) {

							// ★ キャッシュに依存せず、大元データのスタックから直接ImageProcessorを複製して取り出す
							ij.process.ImageProcessor ip = rawImp.getStack().getProcessor(targetZ).duplicate();

							// コントラストを自動調整
							ip.resetMinAndMax();

							int cropSize = 80;
							int cx = Math.max(0, p.x - cropSize / 2);
							int cy = Math.max(0, p.y - cropSize / 2);

							// 幅と高さが元画像のサイズをはみ出さないようにクリップ
							int w = Math.min(cropSize, ip.getWidth() - cx);
							int h = Math.min(cropSize, ip.getHeight() - cy);

							ip.setRoi(cx, cy, w, h);
							ij.process.ImageProcessor croppedIp = ip.crop();

							// 70x70 のサイズに縮小して ImageIcon に変換
							Image img = croppedIp.getBufferedImage().getScaledInstance(70, 70, Image.SCALE_SMOOTH);
							thumbnailLabel = new JLabel(new ImageIcon(img));
						} else {
							com.vis.core.log.Log.logger.warning("Target Z (" + targetZ + ") is out of stack range.");
						}
					}
				} catch (Exception ex) {
					com.vis.core.log.Log.logger.warning("サムネイル生成例外: " + ex.getMessage());
					ex.printStackTrace();
				}
			} else {
				com.vis.core.log.Log.logger.warning("praparat is NULL!");
			}

			mini3DPlaceholder.add(thumbnailLabel, BorderLayout.CENTER);

			gbc.gridx = 0;
			gbc.gridy = 0;
			gbc.gridwidth = 1;
			gbc.gridheight = 2;
			gbc.weightx = 0.0;
			gbc.weighty = 1.0;
			gbc.insets = new Insets(0, 0, 0, 10);
			add(mini3DPlaceholder, gbc);

			// ==========================================================
			// 2. 中央: パラメータ群 (編集機能付き)
			// ==========================================================
			JLabel scoreLabel = new JLabel(String.format("Score: %.1f", c.getScore()));
			scoreLabel.setFont(new Font("Meiryo", Font.BOLD, 13));
			scoreLabel.setForeground(c.getScore() > 70 ? Color.RED : Color.DARK_GRAY);

			// ★ 修正: 単なるラベルから、列挙型(CandidateType)のプルダウンメニューに変更！
			JComboBox<CandidateType> typeCombo = new JComboBox<>(CandidateType.values());
			typeCombo.setSelectedItem(c.getType());
			typeCombo.setFont(new Font("Meiryo", Font.PLAIN, 11));
			typeCombo.setBackground(Color.WHITE);
			// 値が変更されたら、Candidateオブジェクト本体を更新する
			typeCombo.addActionListener(e -> {
				CandidateType selectedType = (CandidateType) typeCombo.getSelectedItem();
				c.setType(selectedType);
				com.vis.core.log.Log.logger.info("Changed type to: " + selectedType);
				populateCandidates();
				updateJudgeStatus();
			});

			JLabel coordLabel = new JLabel(String.format("Pos: (%d, %d, %d)", p.x, p.y, p.z));
			coordLabel.setFont(new Font("Arial", Font.PLAIN, 11));

			gbc.gridx = 1;
			gbc.gridy = 0;
			gbc.gridheight = 1;
			gbc.weightx = 0.3;
			gbc.weighty = 0.5;
			gbc.insets = new Insets(0, 0, 2, 5);
			add(scoreLabel, gbc);

			gbc.gridx = 2;
			gbc.weightx = 0.3;
			add(typeCombo, gbc); // ラベルの代わりにコンボボックスを追加

			gbc.gridx = 3;
			gbc.weightx = 0.4;
			add(coordLabel, gbc);

			JLabel bulgeLabel = new JLabel(String.format("Bulge Ratio: %.2f", c.getMaxBulgeRatio()));
			bulgeLabel.setFont(new Font("Meiryo", Font.PLAIN, 11));
			bulgeLabel.setForeground(Color.GRAY);

			JLabel siLabel = new JLabel(String.format("Shape Index: %.2f", c.getMaxShapeIndex()));
			siLabel.setFont(new Font("Meiryo", Font.PLAIN, 11));
			siLabel.setForeground(Color.GRAY);

			gbc.gridx = 1;
			gbc.gridy = 1;
			gbc.weightx = 0.3;
			gbc.weighty = 0.5;
			gbc.insets = new Insets(2, 0, 0, 5);
			add(bulgeLabel, gbc);

			gbc.gridx = 2;
			gbc.weightx = 0.4;
			add(siLabel, gbc);

			// ★ 追加: Saliency計算の詳細値を見るための「Details」ボタン
			JButton detailsBtn = new JButton("Details");
			detailsBtn.setFont(new Font("Meiryo", Font.PLAIN, 10));
			detailsBtn.setMargin(new Insets(2, 5, 2, 5));
			detailsBtn.addActionListener(e -> showDetailsDialog(c));

			gbc.gridx = 3;
			gbc.weightx = 0.3;
			add(detailsBtn, gbc);

			// 4. マウスイベント（キャンバスへのハイライト通知）
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					highlightThisPanel();
				}
			});
		}

		private void highlightThisPanel() {
            for (CandidateItemPanel panel : itemPanelList) {
                panel.setSelected(false);
            }
            setSelected(true);
            
            // ハイライト対象を更新
            highlightedCandidate = candidate;
            
            // オートフォーカス実行！
            glCanvas.focusOn(candidate.getPeakPoint());
            
            // ==========================================================
            // ★ 追加: キャンバスに「いま選択された候補」を教え、再描画を要求する
            // ==========================================================
            if (glCanvas != null) {
                glCanvas.setHighlightedCandidate(candidate);
                glCanvas.repaint();
            }
        }

		/**
         * 候補の各種計算パラメーターを一覧表示するダイアログ
         */
        private void showDetailsDialog(AneurysmCandidate c) {
            Point3D p = c.getPeakPoint();
            
            // ==========================================================
            // ★ 修正: 未実装のパラメータには必ず「数値 (double)」の 0.0 を入れる
            // ==========================================================
            double gaussianCurv = 0.0; 
            double radiusMm = 0.0;
            double volumeMm3 = 0.0;
            
            // ※もし後日 AneurysmCandidate にゲッターを追加した場合は、以下のコメントアウトを外してください。
            // gaussianCurv = c.getGaussianCurvature();
            // radiusMm = c.getRadiusMm();
            // volumeMm3 = c.getVolumeMm3();

            String details = String.format(
                "=== Aneurysm Candidate Details ===\n\n" +
                "Voxel Position : (X: %d, Y: %d, Z: %d)\n" +
                "Saliency Score : %.2f / 100\n" +
                "Current Type   : %s\n\n" +
                "--- Morphological Features ---\n" +
                "Max Bulge Ratio : %.3f (Threshold: 1.35)\n" +
                "Max Shape Index : %.3f (Sphere: 1.0, Tube: 0.5)\n" +
                "Gaussian Curv.  : %.4f\n" +
                "Vessel Radius   : %.2f mm\n" +
                "Volume Est.     : %.2f mm³\n\n" +
                "Status: %s",
                p.x, p.y, p.z,
                c.getScore(),
                c.getType(),
                c.getMaxBulgeRatio(),
                c.getMaxShapeIndex(),
                gaussianCurv, // %.4f に対応（必ず double を渡す）
                radiusMm,     // %.2f に対応（必ず double を渡す）
                volumeMm3,    // %.2f に対応（必ず double を渡す）
                // ★修正: isCleared() ではなく、Type が NORMAL かどうかで Status を判定する
                c.getType() == CandidateType.NORMAL ? "Cleared (NORMAL)" : "Active (Suspected)"
            );

            JOptionPane.showMessageDialog(
                this, 
                details, 
                "Candidate Features", 
                JOptionPane.INFORMATION_MESSAGE
            );
        }

		public void setSelected(boolean selected) {
			this.isSelected = selected;
			if (selected) {
				setBackground(new Color(230, 240, 255));
			} else {
				setBackground(Color.WHITE);
			}
			repaint();
		}
		
		@SuppressWarnings("unused")
		public boolean isSelected() {
			return isSelected;
		}
	}
}