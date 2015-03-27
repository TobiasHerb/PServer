package de.tuberlin.pserver.core.filesystem.hdfs.parser;


/**
 * Parses a text field into a {@link Float}.
 */
public class FloatParser extends FieldParser<Float> {
	
	private float result;
	
	@Override
	public int parseField(byte[] bytes, int startPos, int limit, char delim, Float reusable) {
		
		int i = startPos;
		final byte delByte = (byte) delim;
		
		while (i < limit && bytes[i] != delByte) {
			i++;
		}
		
		String str = new String(bytes, startPos, i-startPos);
		try {
			this.result = Float.parseFloat(str);
			return (i == limit) ? limit : i+1;
		}
		catch (NumberFormatException e) {
			setErrorState(ParseErrorState.NUMERIC_VALUE_FORMAT_ERROR);
			return -1;
		}
	}
	
	@Override
	public Float createValue() {
		return Float.MIN_VALUE;
	}

	@Override
	public Float getLastResult() {
		return Float.valueOf(this.result);
	}
	
	/**
	 * Static utility to parse a field of type float from a byte sequence that represents text characters
	 * (such as when read from a file stream).
	 * 
	 * @param bytes The bytes containing the text data that should be parsed.
	 * @param startPos The offset to start the parsing.
	 * @param length The length of the byte sequence (counting from the offset).
	 * 
	 * @return The parsed value.
	 * 
	 * @throws NumberFormatException Thrown when the value cannot be parsed because the text represents not a correct number.
	 */
	public static final float parseField(byte[] bytes, int startPos, int length) {
		return parseField(bytes, startPos, length, (char) 0xffff);
	}
	
	/**
	 * Static utility to parse a field of type float from a byte sequence that represents text characters
	 * (such as when read from a file stream).
	 * 
	 * @param bytes The bytes containing the text data that should be parsed.
	 * @param startPos The offset to start the parsing.
	 * @param length The length of the byte sequence (counting from the offset).
	 * @param delimiter The delimiter that terminates the field.
	 * 
	 * @return The parsed value.
	 * 
	 * @throws NumberFormatException Thrown when the value cannot be parsed because the text represents not a correct number.
	 */
	public static final float parseField(byte[] bytes, int startPos, int length, char delimiter) {
		if (length <= 0) {
			throw new NumberFormatException("Invalid input: Empty string");
		}
		int i = 0;
		final byte delByte = (byte) delimiter;
		
		while (i < length && bytes[i] != delByte) {
			i++;
		}
		
		String str = new String(bytes, startPos, i);
		return Float.parseFloat(str);
	}
}
