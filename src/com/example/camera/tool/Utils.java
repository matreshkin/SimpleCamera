package com.example.camera.tool;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.os.Build;

import java.io.*;

/**
 * User: Polikarpov V.
 * Date: 04.02.14
 */
public class Utils {

    /**
     * read EXIF and rotate if needed
     *
     * @param pathToFile
     * @param bmp
     * @return original Bitmap if no rotation
     */
    public static Bitmap rotateBitmap(String pathToFile, Bitmap bmp) {
        int angle = getRotationAngle(pathToFile);
        return rotateBitmap(angle < 0 ? 0 : angle, bmp);
    }

    public static int getRotationAngle(String pathToFile) {
        int rotationAngle = 0;
        try {
            ExifInterface exif = new ExifInterface(pathToFile);
            int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            if (rotation != ExifInterface.ORIENTATION_NORMAL) {
                if (ExifInterface.ORIENTATION_ROTATE_90 == rotation) {
                    rotationAngle = 90;
                } else if (ExifInterface.ORIENTATION_ROTATE_180 == rotation) {
                    rotationAngle = 180;
                } else if (ExifInterface.ORIENTATION_ROTATE_270 == rotation) {
                    rotationAngle = 270;
                } else {
                    rotationAngle = -1;
                }
            }
        } catch (Exception e) {
        }
        return rotationAngle;
    }

    public static Bitmap rotateBitmap(int rotationAngle, Bitmap bmp) {
        if (rotationAngle == 0) return bmp;
        if (bmp != null) {
            try {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationAngle);
                bmp = Bitmap.createBitmap(bmp, 0, 0,
                        bmp.getWidth(), bmp.getHeight(),
                        matrix, false);
            } catch (OutOfMemoryError ignored) {
            }
        } // ROTATION
        return bmp;
    }

    public static Bitmap mirrorBitmap(Bitmap bmp, boolean vertical) {
        if (bmp != null) {
            try {
                Matrix matrix = new Matrix();
                matrix.preScale(vertical ? 1f : -1f, vertical ? -1f : -1f);
                bmp = Bitmap.createBitmap(bmp, 0, 0,
                        bmp.getWidth(), bmp.getHeight(), matrix, false);
            } catch (OutOfMemoryError e) {
            }
        }
        return bmp;
    }

    public static boolean copyFile(File source, File dest) {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { is.close(); os.close(); } catch (Exception e) {}
        }
    }
}
