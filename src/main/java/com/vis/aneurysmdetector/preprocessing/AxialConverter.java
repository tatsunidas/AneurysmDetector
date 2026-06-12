package com.vis.aneurysmdetector.preprocessing;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ShortProcessor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;

public class AxialConverter {

    public static ImagePlus convertIfNeeded(ImagePlus imp) {
        CutSurface plane = PlanarSupport.planarOf(imp);
        if (plane == CutSurface.AXIAL) {
            return imp;
        }
        
        PlanarSupport.standardizeStackOrientation(imp);

        Log.logger.info("==================================================");
        Log.logger.info("=== AxialConverter: Deep Verification Logs ===");
        Log.logger.info("Detected plane: " + plane + ". Converting to AXIAL...");

        int wOrig = imp.getWidth();
        int hOrig = imp.getHeight();
        int dOrig = imp.getStackSize();

        double[] iop = GDicomTools.getImageOrientationPatient(imp, 1);
        double[] ippFirst = GDicomTools.getImagePositionPatient(imp, 1);
        double[] ippLast = GDicomTools.getImagePositionPatient(imp, dOrig);

        double spX = imp.getCalibration().pixelWidth;
        double spY = imp.getCalibration().pixelHeight;
        
        double dx = (ippLast[0] - ippFirst[0]) / Math.max(1, dOrig - 1);
        double dy = (ippLast[1] - ippFirst[1]) / Math.max(1, dOrig - 1);
        double dz = (ippLast[2] - ippFirst[2]) / Math.max(1, dOrig - 1);
        double sliceSpacing = Math.sqrt(dx * dx + dy * dy + dz * dz);

        Log.logger.info("1. Original Volume Info");
        Log.logger.info("   - Size: " + wOrig + " x " + hOrig + " x " + dOrig);
        Log.logger.info("   - Pixel Spacing: X=" + spX + ", Y=" + spY);
        Log.logger.info("   - IOP: " + Arrays.toString(iop));
        Log.logger.info("   - IPP First: " + Arrays.toString(ippFirst));
        Log.logger.info("   - IPP Last:  " + Arrays.toString(ippLast));
        Log.logger.info("   - Slice Vector (dx, dy, dz): " + dx + ", " + dy + ", " + dz);

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (int z = 0; z < dOrig; z++) {
            for (int y : new int[]{0, hOrig - 1}) {
                for (int x : new int[]{0, wOrig - 1}) {
                    double px = ippFirst[0] + x * spX * iop[0] + y * spY * iop[3] + z * dx;
                    double py = ippFirst[1] + x * spX * iop[1] + y * spY * iop[4] + z * dy;
                    double pz = ippFirst[2] + x * spX * iop[2] + y * spY * iop[5] + z * dz;
                    minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                    minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);
                }
            }
        }

        Log.logger.info("2. Computed Physical Bounding Box (mm)");
        Log.logger.info("   - X: " + minX + " to " + maxX);
        Log.logger.info("   - Y: " + minY + " to " + maxY);
        Log.logger.info("   - Z: " + minZ + " to " + maxZ);

        double minSp = Math.min(spX, Math.min(spY, sliceSpacing));
        double outSpX = minSp;
        double outSpY = minSp;
        double outSpZ = minSp;

        int newW = (int) Math.ceil((maxX - minX) / outSpX) + 1;
        int newH = (int) Math.ceil((maxY - minY) / outSpY) + 1;
        int newD = (int) Math.ceil((maxZ - minZ) / outSpZ) + 1;

        Log.logger.info("3. New AXIAL Grid Properties");
        Log.logger.info("   - Isotropic Spacing: " + outSpX + " mm");
        Log.logger.info("   - New Grid Size: " + newW + " x " + newH + " x " + newD);

        Matrix4f idxToPhys = new Matrix4f(
            (float)(spX * iop[0]), (float)(spX * iop[1]), (float)(spX * iop[2]), 0f,
            (float)(spY * iop[3]), (float)(spY * iop[4]), (float)(spY * iop[5]), 0f,
            (float)dx,             (float)dy,             (float)dz,             0f,
            (float)ippFirst[0],    (float)ippFirst[1],    (float)ippFirst[2],    1f
        );
        Matrix4f physToIdx = new Matrix4f(idxToPhys).invert();

        Log.logger.info("4. Transformation Matrices");
        Log.logger.info("   - IdxToPhys:\n" + idxToPhys.toString());
        Log.logger.info("   - PhysToIdx:\n" + physToIdx.toString());

        ImageStack oldStack = imp.getStack();
        short[][] srcVolume = new short[dOrig][];
        for (int z = 0; z < dOrig; z++) {
            srcVolume[z] = (short[]) oldStack.getPixels(z + 1);
        }

        ImageStack newStack = new ImageStack(newW, newH);
        short paddingValue = 0;

		// 4. 新しいグリッド（Axial）でのリサンプリング
		Vector3f pos = new Vector3f();
		for (int z = 0; z < newD; z++) {
			short[] destPixels = new short[newW * newH];

			// ==========================================================
			// ★修正1: minZ から足すのではなく、maxZ から引く（Head to Feet順）
			// ==========================================================
			double pz = maxZ - z * outSpZ;

			for (int y = 0; y < newH; y++) {
				double py = minY + y * outSpY;
				for (int x = 0; x < newW; x++) {
					double px = minX + x * outSpX;

					pos.set((float) px, (float) py, (float) pz);
                    pos.mulPosition(physToIdx);

                    int srcX = (int) Math.round(pos.x);
                    int srcY = (int) Math.round(pos.y);
                    int srcZ = (int) Math.round(pos.z);

                    if (srcX >= 0 && srcX < wOrig && srcY >= 0 && srcY < hOrig && srcZ >= 0 && srcZ < dOrig) {
                        destPixels[y * newW + x] = srcVolume[srcZ][srcY * wOrig + srcX];
                    } else {
                        destPixels[y * newW + x] = paddingValue;
                    }
                }
            }
            newStack.addSlice("Axial Z=" + String.format("%.2f", pz), new ShortProcessor(newW, newH, destPixels, null));
        }

        ImagePlus reconImp = new ImagePlus(imp.getTitle() + "_AXIAL", newStack);
        
        String patID = GDicomTools.getTag(imp, "0010,0020");
        String studyUID = GDicomTools.getTag(imp, "0020,000D");
        String seriesUID = UIDUtils.createUID();
        String sopClassUID = GDicomTools.getTag(imp, "0008,0016");

        for (int z = 0; z < newD; z++) {
            int sliceIndex = z + 1;
			// ==========================================================
			// ★修正2: こちらのメタデータ書き込み用も同様に maxZ から引く
			// ==========================================================
			double pz = maxZ - z * outSpZ;
            
            double[] newIpp = new double[]{ minX, minY, pz };
            GDicomTools.setImagePositionPatient(reconImp, sliceIndex, newIpp);
            GDicomTools.setImageOrientationPatient(reconImp, sliceIndex, new double[]{1.0, 0.0, 0.0, 0.0, 1.0, 0.0});

            GDicomTools.setTag(reconImp, sliceIndex, "0010,0020", patID);
            GDicomTools.setTag(reconImp, sliceIndex, "0020,000D", studyUID);
            GDicomTools.setTag(reconImp, sliceIndex, "0020,000E", seriesUID);
            GDicomTools.setTag(reconImp, sliceIndex, "0008,0018", UIDUtils.createUID());
            GDicomTools.setTag(reconImp, sliceIndex, "0008,0016", sopClassUID);
            GDicomTools.setTag(reconImp, sliceIndex, "0028,0030", outSpY + "\\" + outSpX);
            GDicomTools.setTag(reconImp, sliceIndex, "0018,0050", String.valueOf(outSpZ));
        }

        if (imp.getCalibration() != null) {
            Calibration cal = imp.getCalibration().copy();
            cal.pixelWidth = outSpX;
            cal.pixelHeight = outSpY;
            cal.pixelDepth = outSpZ;
            reconImp.setCalibration(cal);
        }
        reconImp.setDisplayRange(imp.getDisplayRangeMin(), imp.getDisplayRangeMax());

        Log.logger.info("=== Axial Conversion Completed Successfully ===");
        Log.logger.info("==================================================");
        return reconImp;
    }
}