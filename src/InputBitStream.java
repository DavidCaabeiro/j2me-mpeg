/*
 * J2ME_MPEG: MPEG-1 decoder for J2ME
 *
 * Copyright (c) 2009 David Caabeiro
 *
 */

import java.io.*;

public class InputBitStream {
    private static final int BUFFER_SIZE = 16384;

    // Mask for bitstream manipulation
    private static final int[] mask = {
        0x00000000, 0x00000001, 0x00000003, 0x00000007,
        0x0000000f, 0x0000001f, 0x0000003f, 0x0000007f,
        0x000000ff, 0x000001ff, 0x000003ff, 0x000007ff,
        0x00000fff, 0x00001fff, 0x00003fff, 0x00007fff,
        0x0000ffff, 0x0001ffff, 0x0003ffff, 0x0007ffff,
        0x000fffff, 0x001fffff, 0x003fffff, 0x007fffff,
        0x00ffffff, 0x01ffffff, 0x03ffffff, 0x07ffffff,
        0x0fffffff, 0x1fffffff, 0x3fffffff, 0x7fffffff,
        0xffffffff
    };

    // Complement mask (used for sign extension)
    private static final int[] cmask = {
        0xffffffff, 0xfffffffe, 0xfffffffc, 0xfffffff8,
        0xfffffff0, 0xffffffe0, 0xffffffc0, 0xffffff80,
        0xffffff00, 0xfffffe00, 0xfffffc00, 0xfffff800,
        0xfffff000, 0xffffe000, 0xffffc000, 0xffff8000,
        0xffff0000, 0xfffe0000, 0xfffc0000, 0xfff80000,
        0xfff00000, 0xffe00000, 0xffc00000, 0xff800000,
        0xff000000, 0xfe000000, 0xfc000000, 0xf8000000,
        0xf0000000, 0xe0000000, 0xc0000000, 0x80000000,
        0x00000000
    };

    // Sign mask (used for sign extension)
    private static final int[] smask = {
        0x00000000, 0x00000001, 0x00000002, 0x00000004,
        0x00000008, 0x00000010, 0x00000020, 0x00000040,
        0x00000080, 0x00000100, 0x00000200, 0x00000400,
        0x00000800, 0x00001000, 0x00002000, 0x00004000,
        0x00008000, 0x00010000, 0x00020000, 0x00040000,
        0x00080000, 0x00100000, 0x00200000, 0x00400000,
        0x00800000, 0x01000000, 0x02000000, 0x04000000,
        0x08000000, 0x10000000, 0x20000000, 0x40000000,
        0x80000000
    };

    private InputStream mInput;
    private byte[] mBuffer;
    private int mBufferLength = BUFFER_SIZE;
    private int mIndex = BUFFER_SIZE << 3;

    public InputBitStream(String filename) throws IOException {
        mInput = new DataInputStream(getClass().getResourceAsStream(filename));
        mBuffer = new byte[mBufferLength];

        fillBuffer();
    }

    public void close() {
        try {
            mInput.close();
        }
        catch (IOException ignore) {}
    }

    private void fillBuffer() throws IOException {
        int byteOffset = mIndex >>> 3;
        int bytesLeft  = mBufferLength - byteOffset;

        // Move remaining bytes to the beginning of the buffer
        System.arraycopy(mBuffer, byteOffset, mBuffer, 0, bytesLeft);

        // Note: bytesLeft and byteOffset are interchanged due
        // to the above buffer relocation
        int length = mInput.read(mBuffer, bytesLeft, byteOffset);

        if (length < byteOffset)
            mBufferLength = bytesLeft + length;

        mIndex &= 0x7;  // Now we are at the first byte
    }

    /*
     * Peek "count" bits without removing them from the buffer
     */
    public int nextBits(int count) throws IOException {
        int value = 0;

        if (mIndex + count > mBufferLength << 3)
            fillBuffer();

        int byteOffset = mIndex >>> 3;

    	int end = (mIndex + count - 1) >>> 3;  // End byte position
        int room = 8 - (mIndex % 8);           // Room in current byte

        if (room >= count) {
            value = (mBuffer[byteOffset] >>> (room - count)) & mask[count];
            return value;
        }

        int leftover = (mIndex + count) % 8;        // Leftover bits in last byte

        value |= mBuffer[byteOffset] & mask[room];  // Fill out first byte

        for (byteOffset++; byteOffset < end; byteOffset++) {
            value <<= 8;                            // Shift and
            value |= mBuffer[byteOffset] & mask[8]; // Put next byte
        }

        if (leftover > 0) {
            value <<= leftover;                     // Make room for remaining bits
            value |= (mBuffer[byteOffset] >>> (8 - leftover)) & mask[leftover];
        }
        else {
            value <<= 8;
            value |= mBuffer[byteOffset] & mask[8];
        }

        return value;
    }

    public int nextSignedBits(int count) throws IOException {
        int value = nextBits(count);

        if ((smask[count] & value) != 0)
            return value | cmask[count];
        else
            return value;
    }

    /*
     * Remove "count" bits from the buffer
     */
    public int getBits(int count) throws IOException {
    	int value = nextBits(count);
        mIndex += count;
        return value;
    }

    public int getSignedBits(int count) throws IOException {
    	int value = nextSignedBits(count);
        mIndex += count;
        return value;
    }

    public boolean isByteAligned() {
    	return (mIndex % 8) == 0;
    }
}
