/*
 * J2ME_MPEG: MPEG-1 decoder for J2ME
 *
 * Copyright (c) 2009 David Caabeiro
 *
 */

/*
 * A Bitmap stores a video frame ready to be displayed
 */
public class Bitmap {
    private int mWidth;
    private int mHeight;

    public int[] mRgb;

    public Bitmap(int width, int height) {
        mWidth  = width;
    	mHeight = height;

    	mRgb = new int[mWidth * mHeight];
    }

	private final int C1 = 0x166E9;  // 1.402 * 2^16
	private final int C2 = 0x5819;   // 0.34414 * 2^16
	private final int C3 = 0xB6D1;   // 0.71414 * 2^16
	private final int C4 = 0x1C5A1;  // 1.772 * 2^16

	/*
	 * Perform Y'CbCr 4:2:0 to RGB conversion
	 */
    public void transform(Picture picture) {
    	// We process two lines at a time
    	int size = (mWidth * mHeight) >>> 2;

        int index1 = 0;			// First luma line
        int index2 = mWidth;	// Second luma line

        for (int i = 0; i < size; ++i) {
            int cb = picture.mCb[i] - 128;
            int cr = picture.mCr[i] - 128;

            int c1cr = C1 * cr;
            int c2cb = C2 * cb;
            int c3cr = C3 * cr;
            int c4cb = C4 * cb;

            /*
             * Apply CbCr to four neighboring luma samples
             */
            for (int j = 0; j < 2; ++j) {
            	int y  = picture.mY[index1] << 16;   // 2^16

            	int r = y + c1cr;
            	int g = y - c2cb - c3cr;
            	int b = y + c4cb;

            	// Clamp rgb values into [0-255]
            	b >>= 16;
            	b = b > 0xff? 0xff : b < 0? 0 : b & 0x000000ff;

            	g >>= 8;
            	g = g > 0xff00? 0xff00 : g < 0? 0 : g & 0x0000ff00;

            	r = r > 0xff0000? 0xff0000 : r < 0? 0 : r & 0x00ff0000;

            	mRgb[index1++] = (r | g | b);

            	y  = picture.mY[index2] << 16;   // 2^16

            	r = y + c1cr;
            	g = y - c2cb - c3cr;
            	b = y + c4cb;

            	// Clamp rgb values into [0-255]
            	b >>= 16;
            	b = b > 0xff? 0xff : b < 0? 0 : b & 0x000000ff;

            	g >>= 8;
            	g = g > 0xff00? 0xff00 : g < 0? 0 : g & 0x0000ff00;

            	r = r > 0xff0000? 0xff0000 : r < 0? 0 : r & 0x00ff0000;

            	mRgb[index2++] = (r | g | b);
            }

            // Next two lines
            if (index1 % mWidth == 0) {
            	index1 += mWidth;
            	index2 += mWidth;
            }
        }
    }
}
