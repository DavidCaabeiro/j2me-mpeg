/*
 * J2ME_MPEG: MPEG-1 decoder for J2ME
 *
 * Copyright (c) 2009 David Caabeiro
 *
 */

import java.io.*;

/*
 * Variable-length coding (VLC) is a statistical coding technique that
 * assigns codewords to values to be encoded. Values of high frequency
 * of occurrence are assigned short codewords, and those of infrequent
 * occurrence are assigned long codewords. On average, the more frequent
 * shorter codewords dominate such that the code string is shorter than
 * the original data.
 */
public class Vlc {
	public static final short NEXT1    = (short)0xdead;
	public static final short NEXT2    = (short)0xbeef;
	public static final short ESCAPE   = 0x080b;
	public static final short STUFFING = 0x0f0b;

	// Store in pair value, length
	private static final short[] macroblock_address_increment =	{
		NEXT1,   NEXT2, 0x0705, 0x0605, 0x0504, 0x0504, 0x0404, 0x0404,
		0x0303, 0x0303, 0x0303, 0x0303, 0x0203, 0x0203, 0x0203, 0x0203,
		0x0101, 0x0101, 0x0101, 0x0101, 0x0101, 0x0101, 0x0101, 0x0101,
		0x0101, 0x0101, 0x0101, 0x0101, 0x0101, 0x0101, 0x0101, 0x0101
	};

	private static final short[] macroblock_address_increment2 = {
		0x0d08, 0x0d08, 0x0d08, 0x0d08, 0x0d08, 0x0d08, 0x0d08, 0x0d08,
		0x0c08, 0x0c08, 0x0c08, 0x0c08, 0x0c08, 0x0c08, 0x0c08, 0x0c08,
		0x0b08, 0x0b08, 0x0b08, 0x0b08, 0x0b08, 0x0b08, 0x0b08, 0x0b08,
		0x0a08, 0x0a08, 0x0a08, 0x0a08, 0x0a08, 0x0a08, 0x0a08, 0x0a08,
		0x0907, 0x0907, 0x0907, 0x0907, 0x0907, 0x0907, 0x0907, 0x0907,
		0x0907, 0x0907, 0x0907, 0x0907, 0x0907, 0x0907, 0x0907, 0x0907,
		0x0807, 0x0807, 0x0807, 0x0807, 0x0807, 0x0807, 0x0807, 0x0807,
		0x0807, 0x0807, 0x0807, 0x0807, 0x0807, 0x0807, 0x0807, 0x0807
	};

	private static final short[] macroblock_address_increment1 = {
		0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
		ESCAPE, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, STUFFING,
		0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
		0x210b, 0x200b, 0x1f0b, 0x1e0b, 0x1d0b, 0x1c0b, 0x1b0b, 0x1a0b,
		0x190b, 0x180b, 0x170b, 0x160b, 0x150a, 0x150a, 0x140a, 0x140a,
		0x130a, 0x130a, 0x120a, 0x120a, 0x110a, 0x110a, 0x100a, 0x100a,
		0x0f08, 0x0f08, 0x0f08, 0x0f08, 0x0f08, 0x0f08, 0x0f08, 0x0f08,
		0x0e08, 0x0e08, 0x0e08, 0x0e08, 0x0e08, 0x0e08, 0x0e08, 0x0e08
	};

	public int getMacroblockAddressIncrement(InputBitStream input) throws IOException {
		int index = input.nextBits(11);
		int value = macroblock_address_increment[index >> 6];

		if (value == NEXT1)
			value = macroblock_address_increment1[index & 0x3f];
		else if (value == NEXT2)
			value = macroblock_address_increment2[index & 0x3f];

		input.getBits(value & 0xff);

		return value >> 8;
	}


	private static final byte[] macroblock_type_i = {
        0x00, 0x12, 0x01, 0x01
    };

	private static final short[] macroblock_type_p = {
    	0x0000, 0x1106, 0x1205, 0x1205, 0x1a05, 0x1a05, 0x0105, 0x0105,
    	0x0803, 0x0803, 0x0803, 0x0803, 0x0803, 0x0803, 0x0803, 0x0803,
    	0x0202, 0x0202, 0x0202, 0x0202, 0x0202, 0x0202, 0x0202, 0x0202,
    	0x0202, 0x0202, 0x0202, 0x0202, 0x0202, 0x0202, 0x0202, 0x0202
    };

	private static final short[] macroblock_type_b = {
    	0x0000, 0x1106, 0x1606, 0x1a06, 0x1e05, 0x1e05, 0x0105, 0x0105,
    	0x0804, 0x0804, 0x0804, 0x0804, 0x0a04, 0x0a04, 0x0a04, 0x0a04,
    	0x0403, 0x0403, 0x0403, 0x0403, 0x0403, 0x0403, 0x0403, 0x0403,
    	0x0603, 0x0603, 0x0603, 0x0603, 0x0603, 0x0603, 0x0603, 0x0603
    };

    public class MacroblockType {
    	public boolean mMacroblockQuant;
    	public boolean mMacroblockMotionForward;
    	public boolean mMacroblockMotionBackward;
    	public boolean mMacroblockPattern;
    	public boolean mMacroblockIntra;
    }

	public void getMacroblockType(int pictureType, InputBitStream input, MacroblockType macroblockType) throws IOException {
		int index  = 0;
		int value  = 0;
		int length = 0;

		switch (pictureType) {
			case Picture.I_TYPE:
				index  = input.nextBits(2);
				length = macroblock_type_i[index] & 0x0f;

				macroblockType.mMacroblockQuant 		 = (macroblock_type_i[index] >> 4) != 0;
				macroblockType.mMacroblockMotionForward  = false;
				macroblockType.mMacroblockMotionBackward = false;
				macroblockType.mMacroblockPattern 		 = false;
				macroblockType.mMacroblockIntra 		 = true;

				input.getBits(length);
				break;

			case Picture.P_TYPE:
				index = input.nextBits(6);

				// Handle special case: highest bit is 1
				value  = index < 0x20? macroblock_type_p[index] >> 8 : 0x0a;
				length = index < 0x20? macroblock_type_p[index] & 0xff : 1;

				macroblockType.mMacroblockQuant 		 = (value & 0x10) != 0;
				macroblockType.mMacroblockMotionForward  = (value & 0x08) != 0;
				macroblockType.mMacroblockMotionBackward = (value & 0x04) != 0;
				macroblockType.mMacroblockPattern 		 = (value & 0x02) != 0;
				macroblockType.mMacroblockIntra 		 = (value & 0x01) != 0;

				input.getBits(length);
				break;

			case Picture.B_TYPE:
				index = input.nextBits(6);

				// Handle 2 special cases: highest bit 1
				value  = index < 0x20? macroblock_type_b[index] >> 8 :	index < 0x30? 0x0c : 0x0e;
				length = index < 0x20? macroblock_type_b[index] & 0xff : 2;

				macroblockType.mMacroblockQuant 		 = (value & 0x10) != 0;
				macroblockType.mMacroblockMotionForward  = (value & 0x08) != 0;
				macroblockType.mMacroblockMotionBackward = (value & 0x04) != 0;
				macroblockType.mMacroblockPattern 		 = (value & 0x02) != 0;
				macroblockType.mMacroblockIntra 		 = (value & 0x01) != 0;

				input.getBits(length);
				break;

			case Picture.D_TYPE:
				break;
		}
	}


	private static final short[] motion_vector = {
		0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
		0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
		0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
		0x0000100b, 0xfffff00b, 0x00000f0b, 0xfffff10b, 0x00000e0b, 0xfffff20b, 0x00000d0b, 0xfffff30b,
		0x00000c0b, 0xfffff40b, 0x00000b0b, 0xfffff50b, 0x00000a0a, 0x00000a0a, 0xfffff60a, 0xfffff60a,
		0x0000090a, 0x0000090a, 0xfffff70a, 0xfffff70a, 0x0000080a, 0x0000080a, 0xfffff80a, 0xfffff80a,
		0x00000708, 0x00000708, 0x00000708, 0x00000708, 0x00000708, 0x00000708, 0x00000708, 0x00000708,
		0xfffff908, 0xfffff908, 0xfffff908, 0xfffff908, 0xfffff908, 0xfffff908, 0xfffff908, 0xfffff908,
		0x00000608, 0x00000608, 0x00000608, 0x00000608, 0x00000608, 0x00000608, 0x00000608, 0x00000608,
		0xfffffa08, 0xfffffa08, 0xfffffa08, 0xfffffa08, 0xfffffa08, 0xfffffa08, 0xfffffa08, 0xfffffa08,
		0x00000508, 0x00000508, 0x00000508, 0x00000508, 0x00000508, 0x00000508, 0x00000508, 0x00000508,
		0xfffffb08, 0xfffffb08, 0xfffffb08, 0xfffffb08, 0xfffffb08, 0xfffffb08, 0xfffffb08, 0xfffffb08,
		0x00000407, 0x00000407, 0x00000407, 0x00000407, 0x00000407, 0x00000407, 0x00000407, 0x00000407,
		0x00000407, 0x00000407, 0x00000407, 0x00000407, 0x00000407, 0x00000407, 0x00000407, 0x00000407,
		0xfffffc07, 0xfffffc07, 0xfffffc07, 0xfffffc07, 0xfffffc07, 0xfffffc07, 0xfffffc07, 0xfffffc07,
		0xfffffc07, 0xfffffc07, 0xfffffc07, 0xfffffc07, 0xfffffc07, 0xfffffc07, 0xfffffc07, 0xfffffc07,
	};

	private static final short[] motion_vector1 = {
		0x00000000, 0x00000000, 0x00000305, 0xfffffd05, 0x00000204, 0x00000204, 0xfffffe04, 0xfffffe04,
		0x00000103, 0x00000103, 0x00000103, 0x00000103, 0xffffff03, 0xffffff03, 0xffffff03, 0xffffff03,
		0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
		0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001
	};

	public int getMotionVector(InputBitStream input) throws IOException	{
		int index  = input.nextBits(11);
		int value  = 0;
		int length = 0;

		if (((index >> 7) & 0xf) == 0x0) {
			value  = motion_vector[index] >> 0x8;
			length = motion_vector[index] & 0xff;
		}
		else {
			index >>= 6;

			value  = motion_vector1[index] >> 0x8;
			length = motion_vector1[index] & 0xff;
		}

		input.getBits(length);

		return value;
	}


	private static final short[] coded_block_pattern = {
		0x0000, 0x0000, 0x2709, 0x1b09, 0x3b09, 0x3709, 0x2f09, 0x1f09,
		0x3a08, 0x3a08, 0x3608, 0x3608, 0x2e08, 0x2e08, 0x1e08, 0x1e08,
		0x3908, 0x3908, 0x3508, 0x3508, 0x2d08, 0x2d08, 0x1d08, 0x1d08,
		0x2608, 0x2608, 0x1a08, 0x1a08, 0x2508, 0x2508, 0x1908, 0x1908,

		0x2b08, 0x2b08, 0x1708, 0x1708, 0x3308, 0x3308, 0x0f08, 0x0f08,
		0x2a08, 0x2a08, 0x1608, 0x1608, 0x3208, 0x3208, 0x0e08, 0x0e08,
		0x2908, 0x2908, 0x1508, 0x1508, 0x3108, 0x3108, 0x0d08, 0x0d08,
		0x2308, 0x2308, 0x1308, 0x1308, 0x0b08, 0x0b08, 0x0708, 0x0708,

		0x2207, 0x2207, 0x2207, 0x2207, 0x1207, 0x1207, 0x1207, 0x1207,
		0x0a07, 0x0a07, 0x0a07, 0x0a07, 0x0607, 0x0607, 0x0607, 0x0607,
		0x2107, 0x2107, 0x2107, 0x2107, 0x1107, 0x1107, 0x1107, 0x1107,
		0x0907, 0x0907, 0x0907, 0x0907, 0x0507, 0x0507, 0x0507, 0x0507,

		0x3f06, 0x3f06, 0x3f06, 0x3f06, 0x3f06, 0x3f06, 0x3f06, 0x3f06,
		0x0306, 0x0306, 0x0306, 0x0306, 0x0306, 0x0306, 0x0306, 0x0306,
		0x2406, 0x2406, 0x2406, 0x2406, 0x2406, 0x2406, 0x2406, 0x2406,
		0x1806, 0x1806, 0x1806, 0x1806, 0x1806, 0x1806, 0x1806, 0x1806,

		0x3e05, 0x3e05, 0x3e05, 0x3e05, 0x3e05, 0x3e05, 0x3e05, 0x3e05,
		0x3e05, 0x3e05, 0x3e05, 0x3e05, 0x3e05, 0x3e05, 0x3e05, 0x3e05,
		0x0205, 0x0205, 0x0205, 0x0205, 0x0205, 0x0205, 0x0205, 0x0205,
		0x0205, 0x0205, 0x0205, 0x0205, 0x0205, 0x0205, 0x0205, 0x0205,

		0x3d05, 0x3d05, 0x3d05, 0x3d05, 0x3d05, 0x3d05, 0x3d05, 0x3d05,
		0x3d05, 0x3d05, 0x3d05, 0x3d05, 0x3d05, 0x3d05, 0x3d05, 0x3d05,
		0x0105, 0x0105, 0x0105, 0x0105, 0x0105, 0x0105, 0x0105, 0x0105,
		0x0105, 0x0105, 0x0105, 0x0105, 0x0105, 0x0105, 0x0105, 0x0105,

		0x3805, 0x3805, 0x3805, 0x3805, 0x3805, 0x3805, 0x3805, 0x3805,
		0x3805,	0x3805, 0x3805, 0x3805, 0x3805, 0x3805, 0x3805, 0x3805,
		0x3405, 0x3405, 0x3405, 0x3405, 0x3405, 0x3405, 0x3405, 0x3405,
		0x3405, 0x3405, 0x3405, 0x3405, 0x3405, 0x3405, 0x3405, 0x3405,

		0x2c05, 0x2c05, 0x2c05, 0x2c05, 0x2c05, 0x2c05, 0x2c05, 0x2c05,
		0x2c05, 0x2c05, 0x2c05, 0x2c05, 0x2c05, 0x2c05, 0x2c05, 0x2c05,
		0x1c05, 0x1c05, 0x1c05, 0x1c05, 0x1c05, 0x1c05, 0x1c05, 0x1c05,
		0x1c05, 0x1c05, 0x1c05, 0x1c05, 0x1c05, 0x1c05, 0x1c05, 0x1c05,

		0x2805, 0x2805, 0x2805, 0x2805, 0x2805, 0x2805, 0x2805, 0x2805,
		0x2805, 0x2805, 0x2805, 0x2805, 0x2805, 0x2805, 0x2805, 0x2805,
		0x1405, 0x1405, 0x1405, 0x1405, 0x1405, 0x1405, 0x1405, 0x1405,
		0x1405, 0x1405, 0x1405, 0x1405, 0x1405, 0x1405, 0x1405, 0x1405,

		0x3005, 0x3005, 0x3005, 0x3005, 0x3005, 0x3005, 0x3005, 0x3005,
		0x3005, 0x3005, 0x3005, 0x3005, 0x3005, 0x3005, 0x3005, 0x3005,
		0x0c05, 0x0c05, 0x0c05, 0x0c05, 0x0c05, 0x0c05, 0x0c05, 0x0c05,
		0x0c05, 0x0c05, 0x0c05, 0x0c05, 0x0c05, 0x0c05, 0x0c05, 0x0c05,

		0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004,
		0x2004,	0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004,
		0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004,
		0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004, 0x2004,

		0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004,
		0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004,
		0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004,
		0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004, 0x1004,

		0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804,
		0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804,
		0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804,
		0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804, 0x0804,

		0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404,
		0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404,
		0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404,
		0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404, 0x0404,

		0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03,
		0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03,
		0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03,
		0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03,

		0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03,
		0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03,
		0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03,
		0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03, 0x3c03
	};

	public int getCodedBlockPattern(InputBitStream input) throws IOException {
		int index = input.nextBits(9);

		int value  = coded_block_pattern[index] >> 0x8;
		int length = coded_block_pattern[index] & 0xff;

		input.getBits(length);

		return value;
	}


	/* Decoding tables for dct_dc_size_luminance */

	private static final short[] dct_dc_size_luminance = {
		0x12, 0x12, 0x12, 0x12, 0x22, 0x22, 0x22, 0x22,
		0x03, 0x03, 0x33, 0x33, 0x43, 0x43, 0x54, 0x00
	};

	private static final short[] dct_dc_size_luminance1 = {
		0x65, 0x65, 0x65, 0x65, 0x76, 0x76, 0x87, 0x00
	};

	public int decodeDCTDCSizeLuminance(InputBitStream input) throws IOException {
		int index = input.nextBits(7);
		int value = dct_dc_size_luminance[index >> 3];

		if (value == 0)
			value = dct_dc_size_luminance1[index & 0x07];

		input.getBits(value & 0xf);

		return value >> 4;
	}

	/* Decoding tables for dct_dc_size_chrominance */

	private static final short[] dct_dc_size_chrominance = {
		0x02, 0x02, 0x02, 0x02, 0x12, 0x12, 0x12, 0x12,
		0x22, 0x22, 0x22, 0x22, 0x33, 0x33, 0x44, 0x00
	};

	private static final short[] dct_dc_size_chrominance1 = {
		0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55,
		0x66, 0x66, 0x66, 0x66, 0x77, 0x77, 0x88, 0x00
	};

	public int decodeDCTDCSizeChrominance(InputBitStream input) throws IOException {
		int index = input.nextBits(8);
		int value = dct_dc_size_chrominance[index >> 4];

		if (value == 0)
			value = dct_dc_size_chrominance1[index & 0xf];

		input.getBits(value & 0xf);

		return value >> 4;
	}


	/* Decoding tables for dct_coeff_first & dct_coeff_next
	 *
	 * This were originally 15 arrays. Now they are merged to form a single array,
	 * and using a scale to retrieve data from the correct array of coefficients.
	 */

	// Total number of entries 16 * 32 = 512
	// First 32 entries are not used in fact

	private static final byte[][] dct_coeff = {
		// 0000 0000 0000 xxxx x
		{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
		{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
		{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
		{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
		{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
		{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
		{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
		{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},

		// 0000 0000 0001 xxxx s
		{1, 18, 17}, {1, -18, 17}, {1, 17, 17}, {1, -17, 17},
		{1, 16, 17}, {1, -16, 17}, {1, 15, 17}, {1, -15, 17},
		{6,  3, 17}, {6,  -3, 17}, {16, 2, 17}, {16, -2, 17},
		{15, 2, 17}, {15, -2, 17}, {14, 2, 17}, {14, -2, 17},
		{13, 2, 17}, {13, -2, 17}, {12, 2, 17}, {12, -2, 17},
		{11, 2, 17}, {11, -2, 17}, {31, 1, 17}, {31, -1, 17},
		{30, 1, 17}, {30, -1, 17}, {29, 1, 17}, {29, -1, 17},
		{28, 1, 17}, {28, -1, 17}, {27, 1, 17}, {27, -1, 17},

		// 0000 0000 0010 xxxs x
		{0, 40, 16}, {0, 40, 16}, {0, -40, 16}, {0, -40, 16},
		{0, 39, 16}, {0, 39, 16}, {0, -39, 16}, {0, -39, 16},
		{0, 38, 16}, {0, 38, 16}, {0, -38, 16}, {0, -38, 16},
		{0, 37, 16}, {0, 37, 16}, {0, -37, 16}, {0, -37, 16},
		{0, 36, 16}, {0, 36, 16}, {0, -36, 16}, {0, -36, 16},
		{0, 35, 16}, {0, 35, 16}, {0, -35, 16}, {0, -35, 16},
		{0, 34, 16}, {0, 34, 16}, {0, -34, 16}, {0, -34, 16},
		{0, 33, 16}, {0, 33, 16}, {0, -33, 16}, {0, -33, 16},

		// 0000 0000 0011 xxxs x
		{0, 32, 16}, {0, 32, 16}, {0, -32, 16}, {0, -32, 16},
		{1, 14, 16}, {1, 14, 16}, {1, -14, 16}, {1, -14, 16},
		{1, 13, 16}, {1, 13, 16}, {1, -13, 16}, {1, -13, 16},
		{1, 12, 16}, {1, 12, 16}, {1, -12, 16}, {1, -12, 16},
		{1, 11, 16}, {1, 11, 16}, {1, -11, 16}, {1, -11, 16},
		{1, 10, 16}, {1, 10, 16}, {1, -10, 16}, {1, -10, 16},
		{1,  9, 16}, {1,  9, 16}, {1,  -9, 16}, {1,  -9, 16},
		{1,  8, 16}, {1,  8, 16}, {1,  -8, 16}, {1,  -8, 16},

		// 0000 0000 0100 xxsx x
		{0,  31, 15}, {0,  31, 15}, {0,  31, 15}, {0,  31, 15},
		{0, -31, 15}, {0, -31, 15}, {0, -31, 15}, {0, -31, 15},
		{0,  30, 15}, {0,  30, 15}, {0,  30, 15}, {0,  30, 15},
		{0, -30, 15}, {0, -30, 15}, {0, -30, 15}, {0, -30, 15},
		{0,  29, 15}, {0,  29, 15}, {0,  29, 15}, {0,  29, 15},
		{0, -29, 15}, {0, -29, 15}, {0, -29, 15}, {0, -29, 15},
		{0,  28, 15}, {0,  28, 15}, {0,  28, 15}, {0,  28, 15},
		{0, -28, 15}, {0, -28, 15}, {0, -28, 15}, {0, -28, 15},

		// 0000 0000 0101 xxsx x
		{0,  27, 15}, {0,  27, 15}, {0,  27, 15}, {0,  27, 15},
    	{0, -27, 15}, {0, -27, 15}, {0, -27, 15}, {0, -27, 15},
    	{0,  26, 15}, {0,  26, 15}, {0,  26, 15}, {0,  26, 15},
    	{0, -26, 15}, {0, -26, 15}, {0, -26, 15}, {0, -26, 15},
    	{0,  25, 15}, {0,  25, 15}, {0,  25, 15}, {0,  25, 15},
    	{0, -25, 15}, {0, -25, 15}, {0, -25, 15}, {0, -25, 15},
    	{0,  24, 15}, {0,  24, 15}, {0,  24, 15}, {0,  24, 15},
    	{0, -24, 15}, {0, -24, 15}, {0, -24, 15}, {0, -24, 15},

    	// 0000 0000 0110 xxsx x
    	{0,  23, 15}, {0,  23, 15}, {0,  23, 15}, {0,  23, 15},
    	{0, -23, 15}, {0, -23, 15}, {0, -23, 15}, {0, -23, 15},
    	{0,  22, 15}, {0,  22, 15}, {0,  22, 15}, {0,  22, 15},
    	{0, -22, 15}, {0, -22, 15}, {0, -22, 15}, {0, -22, 15},
    	{0,  21, 15}, {0,  21, 15}, {0,  21, 15}, {0,  21, 15},
    	{0, -21, 15}, {0, -21, 15}, {0, -21, 15}, {0, -21, 15},
    	{0,  20, 15}, {0,  20, 15}, {0,  20, 15}, {0,  20, 15},
    	{0, -20, 15}, {0, -20, 15}, {0, -20, 15}, {0, -20, 15},

    	// 0000 0000 0111 xxsx x
    	{0,  19, 15}, {0,  19, 15}, {0,  19, 15}, {0,  19, 15},
    	{0, -19, 15}, {0, -19, 15}, {0, -19, 15}, {0, -19, 15},
    	{0,  18, 15}, {0,  18, 15}, {0,  18, 15}, {0,  18, 15},
    	{0, -18, 15}, {0, -18, 15}, {0, -18, 15}, {0, -18, 15},
    	{0,  17, 15}, {0,  17, 15}, {0,  17, 15}, {0,  17, 15},
    	{0, -17, 15}, {0, -17, 15}, {0, -17, 15}, {0, -17, 15},
    	{0,  16, 15}, {0,  16, 15}, {0,  16, 15}, {0,  16, 15},
    	{0, -16, 15}, {0, -16, 15}, {0, -16, 15}, {0, -16, 15},

    	// 0000 0000 1000 xsxx x
    	{10,  2, 14}, {10,  2, 14}, {10,  2, 14}, {10,  2, 14},
    	{10,  2, 14}, {10,  2, 14}, {10,  2, 14}, {10,  2, 14},
    	{10, -2, 14}, {10, -2, 14}, {10, -2, 14}, {10, -2, 14},
    	{10, -2, 14}, {10, -2, 14}, {10, -2, 14}, {10, -2, 14},
    	{ 9,  2, 14}, { 9,  2, 14}, { 9,  2, 14}, { 9,  2, 14},
    	{ 9,  2, 14}, { 9,  2, 14}, { 9,  2, 14}, { 9,  2, 14},
    	{ 9, -2, 14}, { 9, -2, 14}, { 9, -2, 14}, { 9, -2, 14},
    	{ 9, -2, 14}, { 9, -2, 14}, { 9, -2, 14}, { 9, -2, 14},

    	// 0000 0000 1001 xsxx x
    	{5,  3, 14}, {5,  3, 14}, {5,  3, 14}, {5,  3, 14},
    	{5,  3, 14}, {5,  3, 14}, {5,  3, 14}, {5,  3, 14},
    	{5, -3, 14}, {5, -3, 14}, {5, -3, 14}, {5, -3, 14},
    	{5, -3, 14}, {5, -3, 14}, {5, -3, 14}, {5, -3, 14},
    	{3,  4, 14}, {3,  4, 14}, {3,  4, 14}, {3,  4, 14},
    	{3,  4, 14}, {3,  4, 14}, {3,  4, 14}, {3,  4, 14},
    	{3, -4, 14}, {3, -4, 14}, {3, -4, 14}, {3, -4, 14},
    	{3, -4, 14}, {3, -4, 14}, {3, -4, 14}, {3, -4, 14},

    	// 0000 0000 1010 xsxx x
    	{2,  5, 14}, {2,  5, 14}, {2,  5, 14}, {2,  5, 14},
    	{2,  5, 14}, {2,  5, 14}, {2,  5, 14}, {2,  5, 14},
    	{2, -5, 14}, {2, -5, 14}, {2, -5, 14}, {2, -5, 14},
    	{2, -5, 14}, {2, -5, 14}, {2, -5, 14}, {2, -5, 14},
    	{1,  7, 14}, {1,  7, 14}, {1,  7, 14}, {1,  7, 14},
    	{1,  7, 14}, {1,  7, 14}, {1,  7, 14}, {1,  7, 14},
    	{1, -7, 14}, {1, -7, 14}, {1, -7, 14}, {1, -7, 14},
    	{1, -7, 14}, {1, -7, 14}, {1, -7, 14}, {1, -7, 14},

    	// 0000 0000 1011 xsxx x
    	{1,   6, 14}, {1,   6, 14}, {1,   6, 14}, {1,   6, 14},
    	{1,   6, 14}, {1,   6, 14}, {1,   6, 14}, {1,   6, 14},
    	{1,  -6, 14}, {1,  -6, 14}, {1,  -6, 14}, {1,  -6, 14},
    	{1,  -6, 14}, {1,  -6, 14}, {1,  -6, 14}, {1,  -6, 14},
    	{0,  15, 14}, {0,  15, 14}, {0,  15, 14}, {0,  15, 14},
    	{0,  15, 14}, {0,  15, 14}, {0,  15, 14}, {0,  15, 14},
    	{0, -15, 14}, {0, -15, 14}, {0, -15, 14}, {0, -15, 14},
    	{0, -15, 14}, {0, -15, 14}, {0, -15, 14}, {0, -15, 14},

    	// 0000 0000 1100 xsxx x
    	{0,  14, 14}, {0,  14, 14}, {0,  14, 14}, {0,  14, 14},
    	{0,  14, 14}, {0,  14, 14}, {0,  14, 14}, {0,  14, 14},
    	{0, -14, 14}, {0, -14, 14}, {0, -14, 14}, {0, -14, 14},
    	{0, -14, 14}, {0, -14, 14}, {0, -14, 14}, {0, -14, 14},
    	{0,  13, 14}, {0,  13, 14}, {0,  13, 14}, {0,  13, 14},
    	{0,  13, 14}, {0,  13, 14}, {0,  13, 14}, {0,  13, 14},
    	{0, -13, 14}, {0, -13, 14}, {0, -13, 14}, {0, -13, 14},
    	{0, -13, 14}, {0, -13, 14}, {0, -13, 14}, {0, -13, 14},

    	// 0000 0000 1101 xsxx x
    	{0,  12, 14}, {0,  12, 14}, {0,  12, 14}, {0,  12, 14},
    	{0,  12, 14}, {0,  12, 14}, {0,  12, 14}, {0,  12, 14},
    	{0, -12, 14}, {0, -12, 14}, {0, -12, 14}, {0, -12, 14},
    	{0, -12, 14}, {0, -12, 14}, {0, -12, 14}, {0, -12, 14},
    	{26,  1, 14}, {26,  1, 14}, {26,  1, 14}, {26,  1, 14},
    	{26,  1, 14}, {26,  1, 14}, {26,  1, 14}, {26,  1, 14},
    	{26, -1, 14}, {26, -1, 14}, {26, -1, 14}, {26, -1, 14},
    	{26, -1, 14}, {26, -1, 14}, {26, -1, 14}, {26, -1, 14},

    	// 0000 0000 1110 xsxx x
    	{25,  1, 14}, {25,  1, 14}, {25,  1, 14}, {25,  1, 14},
    	{25,  1, 14}, {25,  1, 14}, {25,  1, 14}, {25,  1, 14},
    	{25, -1, 14}, {25, -1, 14}, {25, -1, 14}, {25, -1, 14},
    	{25, -1, 14}, {25, -1, 14}, {25, -1, 14}, {25, -1, 14},
    	{24,  1, 14}, {24,  1, 14}, {24,  1, 14}, {24,  1, 14},
    	{24,  1, 14}, {24,  1, 14}, {24,  1, 14}, {24,  1, 14},
    	{24, -1, 14}, {24, -1, 14}, {24, -1, 14}, {24, -1, 14},
    	{24, -1, 14}, {24, -1, 14}, {24, -1, 14}, {24, -1, 14},

    	// 0000 0000 1111 xsxx x
    	{23,  1, 14}, {23,  1, 14}, {23,  1, 14}, {23,  1, 14},
    	{23,  1, 14}, {23,  1, 14}, {23,  1, 14}, {23,  1, 14},
    	{23, -1, 14}, {23, -1, 14}, {23, -1, 14}, {23, -1, 14},
    	{23, -1, 14}, {23, -1, 14}, {23, -1, 14}, {23, -1, 14},
    	{22,  1, 14}, {22,  1, 14}, {22,  1, 14}, {22,  1, 14},
    	{22,  1, 14}, {22,  1, 14}, {22,  1, 14}, {22,  1, 14},
    	{22, -1, 14}, {22, -1, 14}, {22, -1, 14}, {22, -1, 14},
    	{22, -1, 14}, {22, -1, 14}, {22, -1, 14}, {22, -1, 14}
	};

	// 0000 0001 xxxx s??? ?
	private static final byte[][] dct_coeff1 = {
		{0, 11, 13}, {0, -11, 13}, {8,  2, 13}, {8,  -2, 13},
		{4,  3, 13}, {4,  -3, 13}, {0, 10, 13}, {0, -10, 13},
		{2,  4, 13}, {2,  -4, 13}, {7,  2, 13}, {7,  -2, 13},
		{21, 1, 13}, {21, -1, 13}, {20, 1, 13}, {20, -1, 13},
		{0,  9, 13}, {0,  -9, 13}, {19, 1, 13}, {19, -1, 13},
		{18, 1, 13}, {18, -1, 13}, {1,  5, 13}, {1,  -5, 13},
		{3,  3, 13}, {3,  -3, 13}, {0,  8, 13}, {0,  -8, 13},
		{6,  2, 13}, {6,  -2, 13}, {17, 1, 13}, {17, -1, 13}
	};

	// 0000 0010 xxs? ???? ?
	private static final byte[][] dct_coeff2 = {
		{16, 1, 11}, {16, -1, 11}, {5, 2, 11}, {5, -2, 11},
		{ 0, 7, 11}, { 0, -7, 11}, {2, 3, 11}, {2, -3, 11}
	};

	// 0000 0011 xxs? ???? ?
	private static final byte[][] dct_coeff3 = {
		{1 , 4, 11}, {1 , -4, 11}, {15, 1, 11}, {15, -1, 11},
		{14, 1, 11}, {14, -1, 11}, {4 , 2, 11}, {4 , -2, 11}
	};

	// 0000 xxxs ???? ???? ?
	private static final byte[][] dct_coeff4 = {
		{0, 0, 0}, {0,  0, 0}, {0, 0, 0}, {0,  0, 0},
		{0, 0, 6}, {0,  0, 6}, {0, 0, 6}, {0,  0, 6},     // ESCAPE
		{2, 2, 8}, {2, -2, 8}, {9, 1, 8}, {9, -1, 8},
		{0, 4, 8}, {0, -4, 8}, {8, 1, 8}, {8, -1, 8}
	};

	public class RunLevel {
		int run;
		int level;
	};

	public boolean decodeDCTCoeff(InputBitStream input, boolean first, RunLevel runLevel) throws IOException {
		int value = input.nextBits(17);
		int index = (value >> 5) & 0xfff;

		int run = 0;
		int	level = 0;

		int discard = 0;

		boolean escape = false;

		if (index >= 0x1 && index <= 0xf) {
			int offset = (index << 5);    // Multiply by 32, the size of each original array
			offset += (value & 0x1f);     // Get 5 least significant bits, which determine the offset within each array

			run   = dct_coeff[offset][0];
			level = dct_coeff[offset][1];

			discard = dct_coeff[offset][2];
		}
		else if (index >= 0x10 && index <= 0x1f) {
			// Discard 4 least significant bits
			int offset = (value >> 4) & 0x1f;

			run   = dct_coeff1[offset][0];
			level = dct_coeff1[offset][1];

			discard = dct_coeff1[offset][2];
		}
		else if (index >= 0x20 && index <= 0x2f) {
			// Discard 6 least significant bits
			int offset = (value >> 6) & 0x7;

			run   = dct_coeff2[offset][0];
			level = dct_coeff2[offset][1];

			discard = dct_coeff2[offset][2];
		}
		else if (index >= 0x30 && index <= 0x3f) {
			// Discard 6 least significant bits
			int offset = (value >> 6) & 0x7;

			run   = dct_coeff3[offset][0];
			level = dct_coeff3[offset][1];

			discard = dct_coeff3[offset][2];
		}
		else if (index >= 0x40 && index <= 0xff) {
			// Discard 9 least significant bits
			int offset = (value >> 9) & 0xf;

			if (offset >= 4 && offset <= 7)
				escape = true;

			run   = dct_coeff4[offset][0];
			level = dct_coeff4[offset][1];

			discard = dct_coeff4[offset][2];
		}
		else {
			index = (value >> 13) & 0xf;

			if (index == 0x1) {
				final byte[][] dct_coeff = {
					{7, 1, 7}, {7, -1, 7}, {6, 1, 7}, {6, -1, 7},
					{1, 2, 7}, {1, -2, 7}, {5, 1, 7}, {5, -1, 7}
				};

				int offset = (value >> 10) & 0x7;

				run   = dct_coeff[offset][0];
				level = dct_coeff[offset][1];

				discard = dct_coeff[offset][2];
			}
			else if (index == 0x2) {
				final byte[][] dct_coeff = {
					{13, 1, 9}, {13, -1, 9}, {0, 6, 9}, {0, -6, 9}, {12, 1, 9}, {12, -1, 9}, {11, 1, 9}, {11, -1, 9},
					{ 3, 2, 9}, { 3, -2, 9}, {1, 3, 9}, {1, -3, 9}, { 0, 5, 9}, { 0, -5, 9}, {10, 1, 9}, {10, -1, 9},
					{ 0,  3, 6}, { 0,  3, 6}, {0,  3, 6}, {0,  3, 6}, { 0,  3, 6}, { 0,  3, 6}, { 0,  3, 6}, { 0,  3, 6},
					{ 0, -3, 6}, { 0, -3, 6}, {0, -3, 6}, {0, -3, 6}, { 0, -3, 6}, { 0, -3, 6}, { 0, -3, 6}, { 0, -3, 6},
				};

				int offset = (value >> 8) & 0x1f;

				run   = dct_coeff[offset][0];
				level = dct_coeff[offset][1];

				discard = dct_coeff[offset][2];
			}
			else if (index == 0x3) {
				final byte[][] dct_coeff = {
					{4, 1, 6}, {4, -1, 6}, {3, 1, 6}, {3, -1, 6}
				};

				int offset = (value >> 11) & 0x3;

				run   = dct_coeff[offset][0];
				level = dct_coeff[offset][1];

				discard = dct_coeff[offset][2];
			}
			else if (index == 0x4) {
				run   = 0;
				level = ((value & 0x1000) != 0? -2 : 2);

				discard = 5;
			}
			else if (index == 0x5) {
				run   = 2;
				level = ((value & 0x1000) != 0? -1 : 1);

				discard = 5;
			}
			else if (index == 0x6) {
				run   = 1;
				level = 1;

				discard = 4;
			}
			else if (index == 0x7) {
				run   = 1;
				level = -1;

				discard = 4;
			}
			// Only for CoeffFirst
			else if (first && index >= 0x8 && index <= 0xb) {
				run   = 0;
				level = 1;

				discard = 2;
			}
			else if (first && index >= 0xc && index <= 0xf)	{
				run   = 0;
				level = -1;

				discard = 2;
			}
			// Only for CoeffNext
			else if (index >= 0xc && index <= 0xd) {
				run   = 0;
				level = 1;

				discard = 3;
			}
			else if (index >= 0xe && index <= 0xf) {
				run   = 0;
				level = -1;

				discard = 3;
			}
			else {
				return false;
			}
		}

		input.getBits(discard);

		if (escape) {
			int r = input.getBits(6);
			run   = r;

			int l = input.nextBits(8);
			if (l == 0 || l == 0x80) {
				l = input.getSignedBits(16);
			}
			else {
				l = input.getSignedBits(8);
			}
			level = l;
		}

		runLevel.run   = run;
		runLevel.level = level;

		return true;
	}
}
