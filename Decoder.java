/*
 * J2ME_MPEG: MPEG-1 decoder for J2ME
 *
 * Copyright (c) 2009 David Caabeiro
 *
 */

import java.io.*;

public class Decoder {
	private Queue mQueue 		    = null;
    private InputBitStream mInput   = null;
    private VideoRenderer mRenderer = null;

    private Picture[] mPictureStore = new Picture[3];
    private int mCurrent = 0, mPrevious = -1, mFuture = -1;

    private MotionVector mForward   = new MotionVector();
    private MotionVector mBackward  = new MotionVector();

    private Idct mIdct 			    = new Idct();
    private Vlc mVlc 			    = new Vlc();

    private int mPictureCodingType;

    private int mWidth;
    private int mHeight;

    private int mMacroblockWidth;	// Width in macroblock units
    private int mMacroblockHeight;	// Height in macroblock units

    private int mMacroblockRow;
    private int mMacroblockCol;

    // Default intra quantization matrix
    private static final short[] DefaultIntraQuantizerMatrix = {
        8, 16, 19, 22, 26, 27, 29, 34,
        16, 16, 22, 24, 27, 29, 34, 37,
        19, 22, 26, 27, 29, 34, 34, 38,
        22, 22, 26, 27, 29, 34, 37, 40,
        22, 26, 27, 29, 32, 35, 40, 48,
        26, 27, 29, 32, 35, 40, 48, 58,
        26, 27, 29, 34, 38, 46, 56, 69,
        27, 29, 35, 38, 46, 56, 69, 83
    };

    // Default non-intra quantization matrix
    private static final short[] DefaultNonIntraQuantizerMatrix = {
        16, 16, 16, 16, 16, 16, 16, 16,
        16, 16, 16, 16, 16, 16, 16, 16,
        16, 16, 16, 16, 16, 16, 16, 16,
        16, 16, 16, 16, 16, 16, 16, 16,
        16, 16, 16, 16, 16, 16, 16, 16,
        16, 16, 16, 16, 16, 16, 16, 16,
        16, 16, 16, 16, 16, 16, 16, 16,
        16, 16, 16, 16, 16, 16, 16, 16
    };

    private short[] IntraQuantizerMatrix 	= new short[64];
    private short[] NonIntraQuantizerMatrix = new short[64];

    // Zig-zag scan matrix
    private static final byte[] ScanMatrix = {
        0,  1,  5,  6, 14, 15, 27, 28,
        2,  4,  7, 13, 16, 26, 29, 42,
        3,  8, 12, 17, 25, 30, 41, 43,
        9, 11, 18, 24, 31, 40, 44, 53,
       10, 19, 23, 32, 39, 45, 52, 54,
       20, 22, 33, 38, 46, 51, 55, 60,
       21, 34, 37, 47, 50, 56, 59, 61,
       35, 36, 48, 49, 57, 58, 62, 63
    };


    /*
     * Start codes are reserved bit patterns that do not otherwise
     * occur in the video stream. All start codes are byte aligned.
     */
    private static final int START_CODE 		  = 0x000001;		// 24-bit code

    private static final int PICTURE_START_CODE   = 0x00000100;
    private static final int SLICE_START_CODE     = 0x00000101;	// through 0x000001af

    private static final int USER_DATA_START_CODE = 0x000001b2;
    private static final int SEQUENCE_HEADER_CODE = 0x000001b3;
    private static final int EXTENSION_START_CODE = 0x000001b5;
    private static final int SEQUENCE_END_CODE    = 0x000001b7;
    private static final int GROUP_START_CODE     = 0x000001b8;

    /**
     * Constructs MPEG decoder
     *
     * @param queue  Playout queue
     * @param input  Video bitstream
     * @param player Canvas canvas
     */
    public Decoder(Queue queue, InputBitStream input, VideoRenderer renderer) {
    	mQueue    = queue;
    	mInput    = input;
        mRenderer = renderer;
    }

    /*
     * Remove any zero bit and zero byte stuffing and locates the next
     * start code. See ISO/IEC 11172-2 Section 2.3
     */
    private void nextStartCode() throws IOException {
        while (!mInput.isByteAligned())
            mInput.getBits(1);

        while (mInput.nextBits(24) != START_CODE)
            mInput.getBits(8);
    }

    public void start() throws IOException {
        nextStartCode();

        /*
         * A video sequence starts with a sequence header and is
         * followed by one or more groups of pictures and is ended
         * by a SEQUENCE_END_CODE. Immediately before each of the
         * groups of pictures there may be a sequence header.
         */

         do {
             parseSequenceHeader();

             mRenderer.setSize(mWidth, mHeight);

             mPictureStore[0] = new Picture(mMacroblockWidth, mMacroblockHeight);
             mPictureStore[1] = new Picture(mMacroblockWidth, mMacroblockHeight);
             mPictureStore[2] = new Picture(mMacroblockWidth, mMacroblockHeight);

             do {
                 parseGroupOfPictures();
             } while (mInput.nextBits(32) == GROUP_START_CODE);

         } while (mInput.nextBits(32) == SEQUENCE_HEADER_CODE);

         int sequenceEndCode = mInput.getBits(32);
    }

    /*
     * All fields in each sequence header with the exception of
     * the quantization matrices shall have the same values as
     * in the first sequence header.
     */
    private void parseSequenceHeader() throws IOException {
        int sequenceHeaderCode = mInput.getBits(32);

        mWidth = mInput.getBits(12);
        mHeight = mInput.getBits(12);

        mMacroblockWidth = (mWidth + 15) >> 4;
        mMacroblockHeight = (mHeight + 15) >> 4;

        int pelAspectRatio = mInput.getBits(4);
        int pictureRate = mInput.getBits(4);

        int bitRate = mInput.getBits(18);
        int markerBit = mInput.getBits(1);	// Should be == 0x1

        int vbvBufferSize = mInput.getBits(10);

//        int minimumBufferSize = vbvBufferSize << 14;

        int constrainedParameterFlag = mInput.getBits(1);

        boolean loadIntraQuantizerMatrix = (mInput.getBits(1) == 1);
        if (loadIntraQuantizerMatrix)
            loadIntraQuantizerMatrix();
        else
            loadDefaultIntraQuantizerMatrix();

        boolean loadNonIntraQuantizerMatrix = (mInput.getBits(1) == 1);
        if (loadNonIntraQuantizerMatrix)
            loadNonIntraQuantizerMatrix();
        else
            loadDefaultNonIntraQuantizerMatrix();

        nextStartCode();

        if (mInput.nextBits(32) == EXTENSION_START_CODE) {
            mInput.getBits(32);

            while (mInput.nextBits(24) != START_CODE) {
                int sequenceExtensionData = mInput.getBits(8);
            }

            nextStartCode();
        }

        if (mInput.nextBits(32) == USER_DATA_START_CODE) {
            mInput.getBits(32);

            while (mInput.nextBits(24) != START_CODE) {
                int userData = mInput.getBits(8);
            }

            nextStartCode();
        }
    }

    /*
     * This is a list of sixty-four 8-bit unsigned integers.
     * The value for [0][0] shall always be 8. For the 8-bit
     * unsigned integers, the value zero is forbidden.
     * The new values shall be in effect until the next occurrence
     * of a sequence header.
     */
    private void loadIntraQuantizerMatrix() throws IOException {
        for (int i = 0; i < 64; ++i) {
            int value = mInput.getBits(8);
            IntraQuantizerMatrix[i] = (short)(value & 0xff);
        }
    }

    private void loadDefaultIntraQuantizerMatrix() {
    	System.arraycopy(DefaultIntraQuantizerMatrix, 0, IntraQuantizerMatrix, 0, 64);
    }

    /*
     * This is a list of sixty-four 8-bit unsigned integers.
     * For the 8-bit unsigned integers, the value zero is forbidden.
     * The new values shall be in effect until the next occurrence
     * of a sequence header.
     */
    private void loadNonIntraQuantizerMatrix() throws IOException {
        for (int i = 0; i < 64; ++i) {
            int value = mInput.getBits(8);
            NonIntraQuantizerMatrix[i] = (short)(value & 0xff);
        }
    }

    private void loadDefaultNonIntraQuantizerMatrix() {
    	System.arraycopy(DefaultNonIntraQuantizerMatrix, 0, NonIntraQuantizerMatrix, 0, 64);
    }

    /*
     * The first coded picture in a group of pictures is an I-Picture.
     * The order of the pictures in the coded stream is the order in
     * which the decoder processes them in normal play. In particular,
     * adjacent B-Pictures in the coded stream are in display order.
     * The last coded picture, in display order, of a group of pictures
     * is either an I-Picture or a P-Picture.
     */
    private void parseGroupOfPictures() throws IOException {
        int groupStartCode = mInput.getBits(32);
        int timeCode = mInput.getBits(25);
        boolean closedGop = mInput.getBits(1) == 1;
        boolean brokenLink = mInput.getBits(1) == 1;

        nextStartCode();

        if (mInput.nextBits(32) == EXTENSION_START_CODE) {
            mInput.getBits(32);

            while (mInput.nextBits(24) != START_CODE) {
                int groupExtensionData = mInput.getBits(8);
            }

            nextStartCode();
        }

        if (mInput.nextBits(32) == USER_DATA_START_CODE) {
            mInput.getBits(32);

            while (mInput.nextBits(24) != START_CODE) {
                int userData = mInput.getBits(8);
            }

            nextStartCode();
        }

        // Reset picture store indexes
        if (closedGop) {
        	mPrevious = mFuture = -1;
        }

    	do {
    		parsePicture();

    		// Send picture to player
    		mQueue.put(mPictureStore[mCurrent]);
/*
            try {
            	Thread.sleep(100);
            } catch(InterruptedException ignore) {}
*/
    		// Store current picture in Previous or Future Picture Store
    		// Refer to section 2-D.2.4
           	if (mPictureCodingType == Picture.I_TYPE || mPictureCodingType == Picture.P_TYPE) {
           		if (mPrevious == -1)
           		{
           			mPrevious = mCurrent;
           		}
           		else if (mFuture == -1)
           		{
           			mFuture = mCurrent;
           		}
           		else
           		{
           			mFuture = mCurrent;
           		}

           		mCurrent = (mCurrent + 1) % 3;
            }

    	} while (mInput.nextBits(32) == PICTURE_START_CODE);
    }

    // Only present in P and B pictures
    private int mForwardF;
    private int mForwardRSize;

    private int mBackwardF;
    private int mBackwardRSize;

    private void parsePicture() throws IOException {
        int pictureStartCode = mInput.getBits(32);
        int temporalReference = mInput.getBits(10);
        mPictureCodingType = mInput.getBits(3);
        int vbvDelay = mInput.getBits(16);

        // This data is to be used later by the player
        mPictureStore[mCurrent].mTime = temporalReference;
        mPictureStore[mCurrent].mType = mPictureCodingType;

		// "Copy" picture from Future Picture Store to Previous Picture Store
		// Refer to section 2-D.2.4
        if (mPictureCodingType == Picture.I_TYPE || mPictureCodingType == Picture.P_TYPE)
        	if (mFuture != -1)
        		mPrevious = mFuture;

        if (mPictureCodingType == Picture.P_TYPE || mPictureCodingType == Picture.B_TYPE) {
            boolean fullPelForwardVector = mInput.getBits(1) == 1;
            int forwardFCode = mInput.getBits(3);  // Can't be 0
            mForwardRSize = forwardFCode - 1;
            mForwardF = 1 << mForwardRSize;

            mForward.init(mForwardF, fullPelForwardVector);
        }

        if (mPictureCodingType == Picture.B_TYPE) {
            boolean fullPelBackwardVector = mInput.getBits(1) == 1;
            int backwardFCode = mInput.getBits(3); // Can't be 0
            mBackwardRSize = backwardFCode - 1;
            mBackwardF = 1 << mBackwardRSize;

            mBackward.init(mBackwardF, fullPelBackwardVector);
        }

        int extraBitPicture = 0;
        while (mInput.nextBits(1) == 0x1) {
            extraBitPicture = mInput.getBits(1);
            int extraInformationPicture = mInput.getBits(8);
        }
        extraBitPicture = mInput.getBits(1);

        nextStartCode();

        if (mInput.nextBits(32) == EXTENSION_START_CODE) {
            mInput.getBits(32);

            while (mInput.nextBits(24) != START_CODE) {
                int pictureExtensionData = mInput.getBits(8);
            }

            nextStartCode();
        }

        if (mInput.nextBits(32) == USER_DATA_START_CODE) {
            mInput.getBits(32);

            while (mInput.nextBits(24) != START_CODE) {
                int userData = mInput.getBits(8);
            }

            nextStartCode();
        }

        do {
            parseSlice();
        } while (mInput.nextBits(32) == SLICE_START_CODE);
    }

    // Predictors
    private int mDctDcYPast;
    private int mDctDcCbPast;
    private int mDctDcCrPast;

    private int mPastIntraAddress;
    private int mMacroblockAddress;
    private int mQuantizerScale;

    /*
     * A slice is a series of an arbitrary number of macroblocks with
     * the order of macroblocks starting from the upper-left of the
     * picture and proceeding by raster-scan order from left to right
     * and top to bottom. Every slice shall contain at least one
     * macroblock. Slices shall not overlap and there shall be no gaps
     * between slices.
     */
    private void parseSlice() throws IOException {
        int sliceStartCode = mInput.getBits(32);   // Ranging from 0x00000101 - 0x000001af
        int sliceVerticalPosition = sliceStartCode & 0xff; // Range: 0x01 - 0xaf

        mDctDcYPast = mDctDcCbPast = mDctDcCrPast = 1024; // See ISO-11172-2 page 35
        mPastIntraAddress = -2; // See ISO-11172-2 page 36

        // Reset at start of each slice
        mForward.resetPrevious();
        mBackward.resetPrevious();

        /*
         * Macroblocks have an address which is the number of the macroblock
         * in raster scan order. The top left macroblock in a picture has
         * address 0, the next one to the right has address 1 and so on.
         * If there are M macroblocks in a picture, then the bottom right
         * macroblock has an address M-1.
         */
        mMacroblockAddress = (sliceVerticalPosition - 1) * mMacroblockWidth - 1;

        mQuantizerScale = mInput.getBits(5);

        int extraBitSlice = 0;
        while (mInput.nextBits(1) == 0x1) {
            extraBitSlice = mInput.getBits(1);
            int extraInformationSlice = mInput.getBits(8);
        }
        extraBitSlice = mInput.getBits(1);

        do {
            parseMacroblock();
        } while (mInput.nextBits(23) != 0x0);

        nextStartCode();
    }

    // Used for decoding motion vectors
	private int mMotionHorizontalForwardR;
	private int mMotionVerticalForwardR;

	private int mMotionHorizontalBackwardR;
	private int mMotionVerticalBackwardR;

    private Vlc.MacroblockType mMacroblockType = mVlc.new MacroblockType();

    /*
     * A macroblock has 4 luminance blocks and 2 chrominance blocks.
     * The order of blocks in a macroblock is top-left, top-right,
     * bottom-left, bottom-right block for Y, followed by Cb and Cr.
     * A macroblock is the basic unit for motion compensation and
     * quantizer scale changes.
     */
	private void parseMacroblock() throws IOException {
        // Discarded by decoder
        while (mInput.nextBits(11) == 0xf) {
            int macroblockStuffing = mInput.getBits(11);
        }

		int macroblockAddressIncrement = 0;

		while (mInput.nextBits(11) == 0x8) {
            int macroblockEscape = mInput.getBits(11);
            macroblockAddressIncrement += 33;
        }

        macroblockAddressIncrement += mVlc.getMacroblockAddressIncrement(mInput);

        // Process skipped macroblocks
		if (macroblockAddressIncrement > 1) {
			mDctDcYPast = mDctDcCrPast = mDctDcCbPast = 1024;

			/*
			 * In P-pictures, the skipped macroblock is defined to be
			 * a macroblock with a reconstructed motion vector equal
			 * to zero and no DCT coefficients.
			 */
			if (mPictureCodingType == Picture.P_TYPE) {
				mForward.resetPrevious();

				for (int i = 0; i < macroblockAddressIncrement; ++i) {
					int mbRow = (mMacroblockAddress + 1 + i) / mMacroblockWidth;
					int mbCol = (mMacroblockAddress + 1 + i) % mMacroblockWidth;

					mPictureStore[mCurrent].copy(mPictureStore[mPrevious], mbRow, mbCol);
				}
			}
			/*
			 * In B-pictures, the skipped macroblock is defined to have
			 * the same macroblock_type (forward, backward, or both motion
			 * vectors) as the prior macroblock, differential motion
			 * vectors equal to zero, and no DCT coefficients.
			 */
			else if (mPictureCodingType == Picture.B_TYPE) {
				for (int i = 0; i < macroblockAddressIncrement; ++i) {
					int mbRow = (mMacroblockAddress + 1 + i) / mMacroblockWidth;
					int mbCol = (mMacroblockAddress + 1 + i) % mMacroblockWidth;

    				if (!mMacroblockType.mMacroblockMotionForward && mMacroblockType.mMacroblockMotionBackward)
				    	mPictureStore[mCurrent].compensate(mPictureStore[mFuture], mbRow, mbCol, mBackward);
    				else if (mMacroblockType.mMacroblockMotionForward && !mMacroblockType.mMacroblockMotionBackward)
				    	mPictureStore[mCurrent].compensate(mPictureStore[mPrevious], mbRow, mbCol, mForward);
    				else if (mMacroblockType.mMacroblockMotionForward && mMacroblockType.mMacroblockMotionBackward) {
    					mPictureStore[mCurrent].interpolate(mPictureStore[mPrevious], mPictureStore[mFuture], mbRow, mbCol, mForward, mBackward);
					}
				}
			}
		}

		mMacroblockAddress += macroblockAddressIncrement;

		mMacroblockRow = mMacroblockAddress / mMacroblockWidth;
		mMacroblockCol = mMacroblockAddress % mMacroblockWidth;

		/*
		 * For macroblocks in I pictures, and for intra coded macroblocks in
		 * P and B pictures, the coded block pattern is not transmitted, but
		 * is assumed to have a value of 63, i.e. all the blocks in the
		 * macroblock are coded.
		 */
		int codedBlockPattern = 0x3f;

		mVlc.getMacroblockType(mPictureCodingType, mInput, mMacroblockType);

	    if (!mMacroblockType.mMacroblockIntra) {
	    	mDctDcYPast = mDctDcCrPast = mDctDcCbPast = 1024;
	    	codedBlockPattern = 0;
	    }

		if (mMacroblockType.mMacroblockQuant)
			mQuantizerScale = mInput.getBits(5);

		if (mMacroblockType.mMacroblockMotionForward) {
			int motionHorizontalForwardCode = mVlc.getMotionVector(mInput);
			if (mForwardF != 1 && motionHorizontalForwardCode != 0) {
				mMotionHorizontalForwardR = mInput.getBits(mForwardRSize);
			}

			int motionVerticalForwardCode = mVlc.getMotionVector(mInput);
			if (mForwardF != 1 && motionVerticalForwardCode != 0) {
				mMotionVerticalForwardR = mInput.getBits(mForwardRSize);
			}

			mForward.calculate(motionHorizontalForwardCode, mMotionHorizontalForwardR, motionVerticalForwardCode, mMotionVerticalForwardR);
		}

		if (mMacroblockType.mMacroblockMotionBackward) {
			int motionHorizontalBackwardCode = mVlc.getMotionVector(mInput);
			if (mBackwardF != 1 && motionHorizontalBackwardCode != 0) {
				mMotionHorizontalBackwardR = mInput.getBits(mBackwardRSize);
			}

			int motionVerticalBackwardCode = mVlc.getMotionVector(mInput);
			if (mBackwardF != 1 && motionVerticalBackwardCode != 0) {
				mMotionVerticalBackwardR = mInput.getBits(mBackwardRSize);
			}

			mBackward.calculate(motionHorizontalBackwardCode, mMotionHorizontalBackwardR, motionVerticalBackwardCode, mMotionVerticalBackwardR);
		}

		if (mPictureCodingType == Picture.P_TYPE) {	// See 2.4.4.2
			if (mMacroblockType.mMacroblockMotionForward) {
				mPictureStore[mCurrent].compensate(mPictureStore[mPrevious], mMacroblockRow, mMacroblockCol, mForward);
			}
			else {
				mPictureStore[mCurrent].copy(mPictureStore[mPrevious], mMacroblockRow, mMacroblockCol);
			}
		}
		else if (mPictureCodingType == Picture.B_TYPE) {	// See 2.4.4.3
			if (mMacroblockType.mMacroblockMotionForward && !mMacroblockType.mMacroblockMotionBackward) {
				mPictureStore[mCurrent].compensate(mPictureStore[mPrevious], mMacroblockRow, mMacroblockCol, mForward);
			}
			else if(!mMacroblockType.mMacroblockMotionForward && mMacroblockType.mMacroblockMotionBackward) {
				mPictureStore[mCurrent].compensate(mPictureStore[mFuture], mMacroblockRow, mMacroblockCol, mBackward);
			}
			else if (mMacroblockType.mMacroblockMotionForward && mMacroblockType.mMacroblockMotionBackward) {
				mPictureStore[mCurrent].interpolate(mPictureStore[mPrevious], mPictureStore[mFuture], mMacroblockRow, mMacroblockCol, mForward, mBackward);
			}
		}

		if (mPictureCodingType == Picture.P_TYPE && !mMacroblockType.mMacroblockMotionForward)
			mForward.resetPrevious();

		if (mPictureCodingType == Picture.B_TYPE && mMacroblockType.mMacroblockIntra) {
			mForward.resetPrevious();
			mBackward.resetPrevious();
		}

		if (mMacroblockType.mMacroblockPattern)
			codedBlockPattern = mVlc.getCodedBlockPattern(mInput);

		/*
		 * The Coded Block Pattern informs the decoder which of the six blocks
		 * in the macroblock are coded, i.e. have transmitted DCT quantized
		 * coefficients, and which are not coded, i.e. have no additional
		 * correction after motion compensation
		 */
		for (int i = 0; i < 6; i++)	{
			if ((codedBlockPattern & (1 << (5 - i))) != 0) {
				parseBlock(i);

				if (mMacroblockType.mMacroblockIntra) {
				 	if (i < 4) mPictureStore[mCurrent].setLumBlock(mDctRecon, mMacroblockRow, mMacroblockCol, i);
					else	   mPictureStore[mCurrent].setColBlock(mDctRecon, mMacroblockRow, mMacroblockCol, i);
				}
				else {
					if (i < 4) mPictureStore[mCurrent].correctLumBlock(mDctRecon, mMacroblockRow, mMacroblockCol, i);
					else       mPictureStore[mCurrent].correctColBlock(mDctRecon, mMacroblockRow, mMacroblockCol, i);
				}
			}
		}

		if (mPictureCodingType == Picture.D_TYPE)
			mInput.getBits(1);
	}

    private int[] mNullMatrix = new int[64];
    private int[] mDctRecon   = new int[64];
    private int[] mDctZigzag  = new int[64];

    /*
     * A block is an orthogonal 8-pel by 8-line section of a
     * luminance or chrominance component.
     */
	private void parseBlock(int index) throws IOException {
		Vlc.RunLevel runLevel = mVlc.new RunLevel();

        System.arraycopy(mNullMatrix, 0, mDctRecon, 0, 64);
        System.arraycopy(mNullMatrix, 0, mDctZigzag, 0, 64);

        int run = 0;

		if (mMacroblockType.mMacroblockIntra) {
            if (index < 4) {
                int dctDCSizeLuminance = mVlc.decodeDCTDCSizeLuminance(mInput);
                int dctDCDifferential = 0;

                if (dctDCSizeLuminance != 0) {
                    dctDCDifferential = mInput.getBits(dctDCSizeLuminance);

                    if ((dctDCDifferential & (1 << (dctDCSizeLuminance - 1))) != 0)
                        mDctZigzag[0] = dctDCDifferential;
                    else
                        mDctZigzag[0] = ((-1 << dctDCSizeLuminance) | (dctDCDifferential + 1));
                }
            }
            else {
                int dctDCSizeChrominance = mVlc.decodeDCTDCSizeChrominance(mInput);
                int dctDCDifferential = 0;

                if (dctDCSizeChrominance != 0) {
                    dctDCDifferential = mInput.getBits(dctDCSizeChrominance);

                    if ((dctDCDifferential & (1 << (dctDCSizeChrominance - 1))) != 0)
                        mDctZigzag[0] = dctDCDifferential;
                    else
                        mDctZigzag[0] = ((-1 << dctDCSizeChrominance) | (dctDCDifferential + 1));
                }
            }
        }
        else {
            // dctCoeffFirst
            mVlc.decodeDCTCoeff(mInput, true, runLevel);

		    run = runLevel.run;
	    	mDctZigzag[run] = runLevel.level;
        }

        if (mPictureCodingType != Picture.D_TYPE) {
            while (mInput.nextBits(2) != 0x2) {
                // dctCoeffNext
            	mVlc.decodeDCTCoeff(mInput, false, runLevel);

                run += runLevel.run + 1;
                mDctZigzag[run] = runLevel.level;
            }
            int endOfBlock = mInput.getBits(2); // Should be == 0x2 (EOB)

            if (mMacroblockType.mMacroblockIntra) {
                if (index == 0)
                    firstLuminanceBlock(mDctRecon);
                else if (index >= 1 && index <= 3)
                    nextLuminanceBlock(mDctRecon);
                else if (index == 4)
                    cbBlock(mDctRecon);
                else if (index == 5)
                    crBlock(mDctRecon);

                mPastIntraAddress = mMacroblockAddress;
            }
            else {
            	// See ISO/IEC 11172 2.4.4.2 / 2.4.4.3
            	for (int i = 0; i < 64; ++i) {
                    int idx = ScanMatrix[i];
                    mDctRecon[i] = ((2 * mDctZigzag[idx] + sign(mDctZigzag[idx])) * mQuantizerScale * NonIntraQuantizerMatrix[i]) >> 4;

                    if ((mDctRecon[i] & 1) == 0) {
                        mDctRecon[i] -= sign(mDctRecon[i]);
                        if (mDctRecon[i] > 2047) mDctRecon[i] = 2047;
                        if (mDctRecon[i] < -2048) mDctRecon[i] = -2048;

                        if (mDctZigzag[idx] == 0)
                            mDctRecon[i] = 0;
                    }
                }
            }

            mIdct.calculate(mDctRecon);
        }
	}

	/*
	 * Helper function
	 */
	private int sign(int n) {
		return n > 0? 1 : (n < 0? -1 : 0);
	}

	/*
	 * Reconstruct DCT coefficients, as defined in ISO/IEC 11172 2.4.4.1
	 */
	private void firstLuminanceBlock(int[] dct_recon) throws IOException {
		for (int i = 0; i < 64; ++i) {
			int index = ScanMatrix[i];
			dct_recon[i] = (mDctZigzag[index] * mQuantizerScale * IntraQuantizerMatrix[i]) >> 3;

			if ((dct_recon[i] & 1) == 0) {
				dct_recon[i] -= sign(dct_recon[i]);
				if (dct_recon[i] > 2047) dct_recon[i] = 2047;
				if (dct_recon[i] < -2048) dct_recon[i] = -2048;
			}
		}

		dct_recon[0] = mDctZigzag[0] << 3;

		if (mMacroblockAddress - mPastIntraAddress > 1)
			dct_recon[0] += 1024;
		else
			dct_recon[0] += mDctDcYPast;

		mDctDcYPast = dct_recon[0];
	}

	private void nextLuminanceBlock(int[] dct_recon) throws IOException {
		for (int i = 0; i < 64; ++i) {
			int index = ScanMatrix[i];
			dct_recon[i] = (mDctZigzag[index] * mQuantizerScale * IntraQuantizerMatrix[i]) >> 3;

			if ((dct_recon[i] & 1) == 0) {
				dct_recon[i] -= sign(dct_recon[i]);
				if (dct_recon[i] > 2047) dct_recon[i] = 2047;
				if (dct_recon[i] < -2048) dct_recon[i] = -2048;
			}
		}

		dct_recon[0] = mDctDcYPast + (mDctZigzag[0] << 3);

		mDctDcYPast = dct_recon[0];
	}

	private void cbBlock(int[] dct_recon) throws IOException {
		for (int i = 0; i < 64; ++i) {
			int index = ScanMatrix[i];
			dct_recon[i] = (mDctZigzag[index] * mQuantizerScale * IntraQuantizerMatrix[i]) >> 3;

			if ((dct_recon[i] & 1) == 0) {
				dct_recon[i] -= sign(dct_recon[i]);
				if (dct_recon[i] > 2047) dct_recon[i] = 2047;
				if (dct_recon[i] < -2048) dct_recon[i] = -2048;
			}
		}

		dct_recon[0] = mDctZigzag[0] << 3;

		if (mMacroblockAddress - mPastIntraAddress > 1)
			dct_recon[0] += 1024;
		else
			dct_recon[0] += mDctDcCbPast;

		mDctDcCbPast = dct_recon[0];
	}

	private void crBlock(int[] dct_recon) throws IOException {
		for (int i = 0; i < 64; ++i) {
			int index = ScanMatrix[i];
			dct_recon[i] = (mDctZigzag[index] * mQuantizerScale * IntraQuantizerMatrix[i]) >> 3;

			if ((dct_recon[i] & 1) == 0) {
				dct_recon[i] -= sign(dct_recon[i]);
				if (dct_recon[i] > 2047) dct_recon[i] = 2047;
				if (dct_recon[i] < -2048) dct_recon[i] = -2048;
			}
		}

		dct_recon[0] = mDctZigzag[0] << 3;

		if (mMacroblockAddress - mPastIntraAddress > 1)
			dct_recon[0] += 1024;
		else
			dct_recon[0] += mDctDcCrPast;

		mDctDcCrPast = dct_recon[0];
	}
}
