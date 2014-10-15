/*
 * J2ME_MPEG: MPEG-1 decoder for J2ME
 *
 * Copyright (c) 2009 David Caabeiro
 *
 */

public class MotionVector {
	/*
	 * Reconstructed motion vector for the previous predictive-coded
	 * macroblock.
	 */
	private int mRightPrevious;
	private int mDownPrevious;

	/*
	 * Reconstructed horizontal and vertical components of the
	 * motion vector for the current macroblock.
	 */
	public int mRightLum;
	public int mDownLum;
	public boolean mRightHalfLum;
	public boolean mDownHalfLum;

	public int mRightCol;
	public int mDownCol;
	public boolean mRightHalfCol;
	public boolean mDownHalfCol;

	private int mVector;
	private boolean mFullPelVector;

	public void init(int v, boolean f) {
		mVector = v;
		mFullPelVector = f;
	}

	public void resetPrevious() {
		mRightPrevious = mDownPrevious = 0;
	}

	/*
	 * Reconstruct the motion vector horizontal and vertical components
	 */
	public void calculate(int motionHorizontalCode, int motionHorizontalR, int motionVerticalCode, int motionVerticalR)	{
		int complementHorizontalR;
	    if (mVector == 1 || motionHorizontalCode == 0)
	        complementHorizontalR = 0;
	    else
	        complementHorizontalR = mVector - 1 - motionHorizontalR;

	    int complementVerticalR;
	    if (mVector == 1 || motionVerticalCode == 0)
	        complementVerticalR = 0;
	    else
	        complementVerticalR = mVector - 1 - motionVerticalR;

	    // rightLittle should always be != vector * 16
	    int rightLittle = motionHorizontalCode * mVector;
	    int rightBig = 0;

	    if (rightLittle == 0) {
	        rightBig = 0;
	    }
	    else {
	        if (rightLittle > 0) {
	            rightLittle = rightLittle - complementHorizontalR;
	            rightBig = rightLittle - (mVector << 5);
	        }
	        else {
	            rightLittle = rightLittle + complementHorizontalR;
	            rightBig = rightLittle + (mVector << 5);
	        }
	    }

	    // downLittle should always be != vector * 16
	    int downLittle = motionVerticalCode * mVector;
	    int downBig = 0;

	    if (downLittle == 0) {
	        downBig = 0;
	    }
	    else {
	        if (downLittle > 0) {
	            downLittle = downLittle - complementVerticalR;
	            downBig = downLittle - (mVector << 5);
	        }
	        else {
	            downLittle = downLittle + complementVerticalR;
	            downBig = downLittle + (mVector << 5);
	        }
	    }

	    int max =  (mVector << 4) - 1;
	    int min = -(mVector << 4);

	    int reconRight = 0;

	    int newVector = mRightPrevious + rightLittle;
	    if (newVector <= max && newVector >= min)
	        reconRight = mRightPrevious + rightLittle;
	    else
	        reconRight = mRightPrevious + rightBig;
	    mRightPrevious = reconRight;

	    if (mFullPelVector)
	        reconRight <<= 1;

	    int reconDown = 0;

	    newVector = mDownPrevious + downLittle;
	    if (newVector <= max && newVector >= min)
	        reconDown = mDownPrevious + downLittle;
	    else
	        reconDown = mDownPrevious + downBig;
	    mDownPrevious = reconDown;

	    if (mFullPelVector)
	        reconDown <<= 1;

	    // LUMINANCE
	    mRightLum       = reconRight >> 1;
	    mDownLum        = reconDown >> 1;
	    mRightHalfLum  = (reconRight - (mRightLum << 1)) != 0;
	    mDownHalfLum   = (reconDown - (mDownLum << 1)) != 0;

	    reconRight >>= 1;
	    reconDown  >>= 1;

	    // CHROMINANCE
	    mRightCol       = reconRight >> 1;
	    mDownCol        = reconDown >> 1;
	    mRightHalfCol  = (reconRight - (mRightCol << 1)) != 0;
	    mDownHalfCol   = (reconDown - (mDownCol << 1)) != 0;
	}
}
