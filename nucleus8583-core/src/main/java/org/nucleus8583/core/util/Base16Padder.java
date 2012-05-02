package org.nucleus8583.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.nucleus8583.core.xml.Alignment;

import rk.commons.util.IOUtils;

public class Base16Padder {

	private static final char[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	public static int hex2int(char ichar) {
        if ((ichar >= '0') && (ichar <= '9')) {
            return ichar - '0';
        }

        if ((ichar >= 'A') && (ichar <= 'F')) {
            return ichar - 'A' + 10;
        }

	    if ((ichar >= 'a') && (ichar <= 'f')) {
	        return ichar - 'a' + 10;
	    }

        return 0;
	}

	private byte padWith;

	private Alignment align;

	private int length;

	private byte[] padder;

	private byte[] emptyValue;
	
	public Base16Padder() {
		// do nothing
	}
	
	public Base16Padder(Base16Padder o) {
		padWith = o.padWith;
		align = o.align;
		
		length = o.length;
		
		padder = o.padder;
		emptyValue = o.emptyValue;
	}

	public void setPadWith(byte padWith) {
		this.padWith = padWith;
	}

	public void setAlign(Alignment align) {
		this.align = align;
	}

	public Alignment getAlign() {
		return align;
	}

	public void setLength(int length) {
		this.length = (length + 1) >> 1;
	}

	public void setEmptyValue(byte[] emptyValue) {
		this.emptyValue = emptyValue;
	}

	public void initialize() {
		padder = new byte[length];
		Arrays.fill(padder, padWith);
	}

	public void pad(OutputStream out, byte[] value, int off, int vlen)
			throws IOException {
		if (vlen == 0) {
			write(out, padder, 0, length);
		} else if (vlen == length) {
			write(out, value, off, vlen);
		} else {
			switch (align) {
			case TRIMMED_LEFT:
			case UNTRIMMED_LEFT:
				write(out, value, off, vlen);
				write(out, padder, 0, length - vlen);

				break;
			case TRIMMED_RIGHT:
			case UNTRIMMED_RIGHT:
				write(out, padder, 0, length - vlen);
				write(out, value, off, vlen);

				break;
			default: // NONE
				write(out, value, off, vlen);
				write(out, padder, 0, length - vlen);

				break;
			}
		}
	}

	public byte[] unpad(InputStream in) throws IOException {
		byte[] value = new byte[length << 1];
		read(in, value, 0, value.length);

		byte[] result;
		int resultLength;

		switch (align) {
		case TRIMMED_LEFT:
			resultLength = 0;

			for (int i = length - 1; i >= 0; --i) {
				if (value[i] != padWith) {
					resultLength = i + 1;
					break;
				}
			}

			if (resultLength == 0) {
				result = emptyValue;
			} else if (resultLength == length) {
				result = value;
			} else {
				result = new byte[resultLength];
				System.arraycopy(value, 0, result, 0, resultLength);
			}

			break;
		case TRIMMED_RIGHT:
			int padLength = length;

			for (int i = 0; i < length; ++i) {
				if (value[i] != padWith) {
					padLength = i;
					break;
				}
			}

			if (padLength == 0) {
				result = value;
			} else if (padLength == length) {
				result = emptyValue;
			} else {
				resultLength = length - padLength;

				result = new byte[resultLength];
				System.arraycopy(value, padLength, result, 0, resultLength);
			}

			break;
		default: // NONE, UNTRIMMED_LEFT, UNTRIMMED_RIGHT
			result = value;
			break;
		}

		return result;
	}

	public int unpad(InputStream in, byte[] result, int off, int length) throws IOException {
		byte[] value = new byte[length];
		read(in, value, 0, length);

		int resultLength = length;

		switch (align) {
		case TRIMMED_LEFT:
			resultLength = 0;

			for (int i = length - 1; i >= 0; --i) {
				if (value[i] != padWith) {
					resultLength = i + 1;
					break;
				}
			}

			if (resultLength == 0) {
				System.arraycopy(emptyValue, 0, result, off, length);
			} else if (resultLength == length) {
				System.arraycopy(value, 0, result, off, length);
			} else {
				System.arraycopy(value, 0, result, off, resultLength);
			}

			break;
		case TRIMMED_RIGHT:
			int padLength = length;

			for (int i = 0; i < length; ++i) {
				if (value[i] != padWith) {
					padLength = i;
					break;
				}
			}

			if (padLength == 0) {
				System.arraycopy(value, 0, result, off, length);
			} else if (padLength == length) {
				System.arraycopy(emptyValue, 0, result, off, length);
			} else {
				resultLength = length - padLength;
				System.arraycopy(value, padLength, result, off, resultLength);
			}

			break;
		default: // NONE, UNTRIMMED_LEFT, UNTRIMMED_RIGHT
			System.arraycopy(value, 0, result, off, length);
			break;
		}

		return resultLength;
	}

	/**
	 * read N*2 bytes from input stream and store it to <code>value</code>
	 * starting from offset <code>off</code>.
	 *
	 * @param in
	 * @param value
	 * @param off
	 * @param vlen
	 * @throws IOException
	 */
	public void read(InputStream in, byte[] value, int off, int vlen)
			throws IOException {
		vlen <<= 1;

		byte[] bbuf = new byte[vlen];
		IOUtils.readFully(in, bbuf, vlen);

		for (int i = 0, j = off; i < vlen; i += 2, ++j) {
			value[j] = (byte) ((hex2int((char) (bbuf[i] & 0xFF)) << 4) | hex2int((char) (bbuf[i + 1] & 0xFF)));
		}
	}

	/**
	 * write N bytes of value to output stream. As the each byte of value will
	 * be written in hexadecimal form, so this method will write N*2 bytes in
	 * the stream.
	 *
	 * @param out
	 * @param value
	 * @param off
	 * @param vlen
	 * @throws IOException
	 */
	public void write(OutputStream out, byte[] value, int off, int vlen)
			throws IOException {
		for (int i = off; i < vlen; ++i) {
			out.write(HEX[(value[i] & 0xF0) >> 4]); // hi
			out.write(HEX[value[i] & 0x0F]); // lo
		}
	}
}
