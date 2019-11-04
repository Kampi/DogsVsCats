package com.kampis_elektroecke.dogsvscats.ObjectDetection;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.core.graphics.BitmapCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ImagePreprocessing
{
    /***
     * Convert a bitmap image into a byte buffer
     * @param Image: Input bitmap
     * @param Channels: Color channel count
     * @return Byte buffer with floating point image data
     */
    public static ByteBuffer Bitmap2ByteBuffer(@NonNull Bitmap Image, int Channels)
    {
        ByteBuffer Input;
        Input = ByteBuffer.allocateDirect(BitmapCompat.getAllocationByteCount(Image) * Channels);
        Input.order(ByteOrder.nativeOrder());

        Input.rewind();
        int[] Pixel = new int[Image.getWidth() * Image.getHeight()];
        Image.getPixels(Pixel, 0, Image.getWidth(), 0, 0, Image.getWidth(), Image.getHeight());

        int j = 0;
        for(int i = 0; i < Pixel.length * Channels; i = i + 3)
        {
            for(int k = (Channels - 1); k >= 0; k--)
            {
                Input.putFloat((((Pixel[j] >> 8 * k) & 0xFF)) / 1.0f);
            }

            j++;
        }

        return Input;
    }
}
