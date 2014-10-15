/*
 * J2ME_MPEG: MPEG-1 decoder for J2ME
 *
 * Copyright (c) 2009 David Caabeiro
 *
 */

/*
 * A picture consists of three rectangular matrices of eight-bit numbers;
 * a luminance matrix (Y), and two chrominance matrices (Cr and Cb)
 * The Y-matrix must have an even number of rows and columns, and the Cr
 * and Cb matrices are one half the size of the Y-matrix in both
 * horizontal and vertical dimensions.
 */

public class Picture {
    public static final int I_TYPE = 1;
    public static final int P_TYPE = 2;
    public static final int B_TYPE = 3;
    public static final int D_TYPE = 4;

    private final int mLumRowSize;
    private final int mColRowSize;

	public short[] mY;
    public short[] mCb;
    public short[] mCr;

	public int mTime;
	public int mType;

	Picture(int mbWidth, int mbHeight) {
    	final int size = (mbWidth * mbHeight) << 8;

    	mLumRowSize = mbWidth << 4;
    	mColRowSize = mbWidth << 3;

    	mY  = new short[size];
    	mCb = new short[size >>> 2];
    	mCr = new short[size >>> 2];
    }

	/*
	 * Motion compensation (MC) predicts the value of a block of
	 * neighboring pels in a picture by relocating a block of
	 * neighboring pel values from a known picture. The motion
	 * is described in terms of the two-dimensional motion vector
	 * that translates the block to the new location.
	 */
	public void compensate(Picture src, int srcRow, int srcCol, MotionVector mv) {
		Picture.compensate(src, srcRow, srcCol, this, srcRow, srcCol, mv);
	}

	/*
	 * If the decoder reconstructs a picture from the past and a
	 * picture from the future, then the intermediate pictures can
	 * be reconstructed by the technique of interpolation, or
	 * bidirectional prediction. The decoder may reconstruct pel
	 * values belonging to a given macroblock as an average of values
	 * from the past and future pictures.
	 */
    private static Picture temp1 = new Picture(1, 1);
    private static Picture temp2 = new Picture(1, 1);

	public void interpolate(Picture src1, Picture src2, int mbRow, int mbCol, MotionVector mv1, MotionVector mv2) {
		Picture.compensate(src1, mbRow, mbCol, temp1, 0, 0, mv1);
		Picture.compensate(src2, mbRow, mbCol, temp2, 0, 0, mv2);

		doInterpolation(temp1, temp2, mbRow, mbCol);
	}

	private static void compensate(Picture src, int srcRow, int srcCol, Picture dst, int dstRow, int dstCol, MotionVector mv) {
		int x = (srcCol << 4) + mv.mRightLum;
		int y = (srcRow << 4) + mv.mDownLum;

		int dst0 = dst.mLumRowSize * (dstRow << 4) + (dstCol << 4);

		if (!mv.mRightHalfLum && !mv.mDownHalfLum) {
			int src0 = src.mLumRowSize * y + x;

			for (int i = 0; i < 16; ++i) {
				System.arraycopy(src.mY, src0, dst.mY, dst0, 16);

				src0 += src.mLumRowSize;
				dst0 += dst.mLumRowSize;
			}
		}
		else if (!mv.mRightHalfLum && mv.mDownHalfLum) {
			int src0 = src.mLumRowSize * y + x;
			int src1 = src.mLumRowSize * (y + 1) + x;

			for (int i = 0; i < 16; ++i) {
				for (int j = 0; j < 16; ++j)
					dst.mY[dst0 + j] = (short)((src.mY[src0 + j] + src.mY[src1 + j]) >> 1);

				src0 += src.mLumRowSize;
				src1 += src.mLumRowSize;
				dst0 += dst.mLumRowSize;
			}
		}
		else if (mv.mRightHalfLum && !mv.mDownHalfLum) {
			int src0 = src.mLumRowSize * y + x;
			int src1 = src.mLumRowSize * y + x + 1;

			for (int i = 0; i < 16; ++i) {
				for (int j = 0; j < 16; ++j)
					dst.mY[dst0 + j] = (short)((src.mY[src0 + j] + src.mY[src1 + j]) >> 1);

				src0 += src.mLumRowSize;
				src1 += src.mLumRowSize;
				dst0 += dst.mLumRowSize;
			}
		}
		else if (mv.mRightHalfLum && mv.mDownHalfLum) {
			int src0 = src.mLumRowSize * y + x;
			int src1 = src.mLumRowSize * (y + 1) + x;
			int src2 = src.mLumRowSize * y + x + 1;
			int src3 = src.mLumRowSize * (y + 1) + x + 1;

			for (int i = 0; i < 16; ++i) {
				for (int j = 0; j < 16; ++j)
					dst.mY[dst0 + j] = (short)((src.mY[src0 + j] + src.mY[src1 + j] + src.mY[src2 + j] + src.mY[src3 + j]) >> 2);

				src0 += src.mLumRowSize;
				src1 += src.mLumRowSize;
				src2 += src.mLumRowSize;
				src3 += src.mLumRowSize;
				dst0 += dst.mLumRowSize;
			}
		}

		x = (srcCol << 3) + mv.mRightCol;
		y = (srcRow << 3) + mv.mDownCol;

		dst0 = dst.mColRowSize * (dstRow << 3) + (dstCol << 3);

		if (!mv.mRightHalfCol && !mv.mDownHalfCol) {
			int src0 = src.mColRowSize * y + x;

			for (int i = 0; i < 8; ++i)	{
				System.arraycopy(src.mCb, src0, dst.mCb, dst0, 8);
				System.arraycopy(src.mCr, src0, dst.mCr, dst0, 8);

				src0 += src.mColRowSize;
				dst0 += dst.mColRowSize;
			}
		}
		else if (!mv.mRightHalfCol && mv.mDownHalfCol) {
			int src0 = src.mColRowSize * y + x;
			int src1 = src.mColRowSize * (y + 1) + x;

			for (int i = 0; i < 8; ++i)	{
				for (int j = 0; j < 8; ++j) {
					dst.mCb[dst0 + j] = (short)((src.mCb[src0 + j] + src.mCb[src1 + j]) >> 1);
					dst.mCr[dst0 + j] = (short)((src.mCr[src0 + j] + src.mCr[src1 + j]) >> 1);
				}

				src0 += src.mColRowSize;
				src1 += src.mColRowSize;
				dst0 += dst.mColRowSize;
			}
		}
		else if (mv.mRightHalfCol && !mv.mDownHalfCol) {
			int src0 = src.mColRowSize * y + x;
			int src1 = src.mColRowSize * y + x + 1;

			for (int i = 0; i < 8; ++i) {
				for (int j = 0; j < 8; ++j)	{
					dst.mCb[dst0 + j] = (short)((src.mCb[src0 + j] + src.mCb[src1 + j]) >> 1);
					dst.mCr[dst0 + j] = (short)((src.mCr[src0 + j] + src.mCr[src1 + j]) >> 1);
				}

				src0 += src.mColRowSize;
				src1 += src.mColRowSize;
				dst0 += dst.mColRowSize;
			}
		}
		else if (mv.mRightHalfCol && mv.mDownHalfCol) {
			int src0 = src.mColRowSize * y + x;
			int src1 = src.mColRowSize * (y + 1) + x;
			int src2 = src.mColRowSize * y + x + 1;
			int src3 = src.mColRowSize * (y + 1) + x + 1;

			for (int i = 0; i < 8; ++i) {
				for (int j = 0; j < 8; ++j)	{
					dst.mCb[dst0 + j] = (short)((src.mCb[src0 + j] + src.mCb[src1 + j] + src.mCb[src2 + j] + src.mCb[src3 + j]) >> 2);
					dst.mCr[dst0 + j] = (short)((src.mCr[src0 + j] + src.mCr[src1 + j] + src.mCr[src2 + j] + src.mCr[src3 + j]) >> 2);
				}

				src0 += src.mColRowSize;
				src1 += src.mColRowSize;
				src2 += src.mColRowSize;
				src3 += src.mColRowSize;
				dst0 += dst.mColRowSize;
			}
		}
	}

	/*
	 * Perform a block copy on the specified macroblock
	 * This is equivalent to a compensation with motion
	 * vector components equal zero.
	 */
	public void copy(Picture src, int mbRow, int mbCol) {
		int dst = mLumRowSize * (mbRow << 4) + (mbCol << 4);

		for (int i = 0; i < 16; ++i) {
			System.arraycopy(src.mY, dst, mY, dst, 16);

			dst += mLumRowSize;
		}

		dst = mColRowSize * (mbRow << 3) + (mbCol << 3);

		for (int i = 0; i < 8; ++i) {
			System.arraycopy(src.mCb, dst, mCb, dst, 8);
			System.arraycopy(src.mCr, dst, mCr, dst, 8);

			dst += mColRowSize;
		}
	}

	private void doInterpolation(Picture src1, Picture src2, int mbRow, int mbCol)
	{
		int src = 0;
		int dst = mLumRowSize * (mbRow << 4) + (mbCol << 4);

		for (int i = 0; i < 16; ++i) {
			for (int j = 0; j < 16; ++j) {
				mY[dst + j] = (short)((src1.mY[src + j] + src2.mY[src + j]) >> 1);
			}

			src += src1.mLumRowSize;
			dst += mLumRowSize;
		}

		src = 0;
		dst = mColRowSize * (mbRow << 3) + (mbCol << 3);

		for (int i = 0; i < 8; ++i) {
			for (int j = 0; j < 8; ++j) {
				mCb[dst + j] = (short)((src1.mCb[src + j] + src2.mCb[src + j]) >> 1);
			}

			for (int j = 0; j < 8; ++j) {
				mCr[dst + j] = (short)((src1.mCr[src + j] + src2.mCr[src + j]) >> 1);
			}

			src += src1.mColRowSize;
			dst += mColRowSize;
		}
	}

	/*
	 * blockNumber can be:
	 *
	 * 0	00	 |	 1	01
	 * 2	10	 |   3	11
	 *
	 * Bit 0 determines the column position within the macroblock
	 * Bit 1 determines the row position within the macroblock
	 *
	 */
	void setLumBlock(int[] dct, int mbRow, int mbCol, int blockNumber) {
		int dst = mLumRowSize * ((mbRow << 4) + ((blockNumber & 0x2) << 2)) +
		  (mbCol << 4) + ((blockNumber & 0x1) << 3);

		for (int i = 0; i < 8; ++i)	{
			for (int j = 0; j < 8; ++j)
				mY[dst + j] = (short)dct[i * 8 + j];

			dst += mLumRowSize;
		}
	}

	void setColBlock(int[] dct, int mbRow, int mbCol, int blockNumber) {
		int dst = mColRowSize * (mbRow << 3) + (mbCol << 3);

		for (int i = 0; i < 8; ++i)	{
			for (int j = 0; j < 8; ++j)
				if (blockNumber == 4)
					mCb[dst + j] = (short)dct[i * 8 + j];
				else
					mCr[dst + j] = (short)dct[i * 8 + j];

			dst += mColRowSize;
		}
	}

	void correctLumBlock(int[] dct, int mbRow, int mbCol, int blockNumber) {
		int dst = mLumRowSize * ((mbRow << 4) + ((blockNumber & 0x2) << 2)) +
		  (mbCol << 4) + ((blockNumber & 0x1) << 3);

		for (int i = 0; i < 8; ++i)	{
			for (int j = 0; j < 8; ++j)
				mY[dst + j] += dct[i * 8 + j];

			dst += mLumRowSize;
		}
	}

	void correctColBlock(int[] dct, int mbRow, int mbCol, int blockNumber) {
		int dst = mColRowSize * (mbRow << 3) + (mbCol << 3);

		for (int i = 0; i < 8; ++i)	{
			for (int j = 0; j < 8; ++j)
				if (blockNumber == 4)
					mCb[dst + j] += dct[i * 8 + j];
				else
					mCr[dst + j] += dct[i * 8 + j];

			dst += mColRowSize;
		}
	}
}
