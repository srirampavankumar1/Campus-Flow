class TextBuilder {

    private char[] characters;
    private int length;

    public TextBuilder() {
        characters = new char[256];
        length = 0;
    }

    private void makeRoomFor(int extraCount) {
        int needed = length + extraCount;
        if (needed <= characters.length) {
            return;
        }
        int newSize = characters.length;
        while (newSize < needed) {
            newSize = newSize * 2;
        }
        char[] biggerArray = new char[newSize];
        for (int index = 0; index < length; index++) {
            biggerArray[index] = characters[index];
        }
        characters = biggerArray;
    }

    public TextBuilder append(char singleCharacter) {
        makeRoomFor(1);
        characters[length] = singleCharacter;
        length++;
        return this;
    }

    public TextBuilder append(String text) {
        int textLength = text.length();
        makeRoomFor(textLength);
        for (int index = 0; index < textLength; index++) {
            characters[length] = text.charAt(index);
            length++;
        }
        return this;
    }

    public TextBuilder append(int number) {
        if (number == 0) {
            return append('0');
        }
        if (number < 0) {
            append('-');
            number = 0 - number;
        }
        char[] reversedDigits = new char[12];
        int digitCount = 0;
        while (number > 0) {
            reversedDigits[digitCount] = (char) ('0' + (number % 10));
            digitCount++;
            number = number / 10;
        }
        for (int index = digitCount - 1; index >= 0; index--) {
            append(reversedDigits[index]);
        }
        return this;
    }

    public TextBuilder appendMilliseconds(long nanoseconds) {
        long totalMicroseconds = nanoseconds / 1000;
        int wholeMilliseconds = (int) (totalMicroseconds / 1000);
        int fractionDigits = (int) (totalMicroseconds % 1000);
        append(wholeMilliseconds);
        append('.');
        if (fractionDigits < 100) {
            append('0');
        }
        if (fractionDigits < 10) {
            append('0');
        }
        append(fractionDigits);
        append(" ms");
        return this;
    }

    public TextBuilder appendEscaped(String text) {
        int textLength = text.length();
        for (int index = 0; index < textLength; index++) {
            char currentChar = text.charAt(index);
            if (currentChar == '<') {
                append("&lt;");
            } else if (currentChar == '>') {
                append("&gt;");
            } else if (currentChar == '&') {
                append("&amp;");
            } else if (currentChar == '"') {
                append("&quot;");
            } else {
                append(currentChar);
            }
        }
        return this;
    }

    public TextBuilder append(TextBuilder other) {
        makeRoomFor(other.length);
        for (int index = 0; index < other.length; index++) {
            characters[length] = other.characters[index];
            length++;
        }
        return this;
    }

    public int length() {
        return length;
    }

    public char charAt(int index) {
        return characters[index];
    }

    public byte[] toBytes() {
        byte[] bytes = new byte[length];
        for (int index = 0; index < length; index++) {
            bytes[index] = (byte) characters[index];
        }
        return bytes;
    }

    public String toText() {
        return new String(characters, 0, length);
    }
}

class TextTools {

    public static boolean isSpace(char character) {
        return character == ' ' || character == '\t' || character == '\r' || character == '\n';
    }

    public static String trim(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return cut(text, start, end);
    }

    public static String cut(String text, int start, int end) {
        TextBuilder piece = new TextBuilder();
        for (int index = start; index < end; index++) {
            piece.append(text.charAt(index));
        }
        return piece.toText();
    }

    public static int indexOfChar(String text, char target, int from) {
        for (int index = from; index < text.length(); index++) {
            if (text.charAt(index) == target) {
                return index;
            }
        }
        return -1;
    }

    public static char toLower(char character) {
        if (character >= 'A' && character <= 'Z') {
            return (char) (character + 32);
        }
        return character;
    }

    public static boolean sameTextIgnoringCase(String first, String second) {
        if (first.length() != second.length()) {
            return false;
        }
        for (int index = 0; index < first.length(); index++) {
            if (toLower(first.charAt(index)) != toLower(second.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    public static boolean startsWithIgnoringCase(String text, String prefix) {
        if (text.length() < prefix.length()) {
            return false;
        }
        for (int index = 0; index < prefix.length(); index++) {
            if (toLower(text.charAt(index)) != toLower(prefix.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    public static boolean containsIgnoringCase(String text, String word) {
        for (int start = 0; start + word.length() <= text.length(); start++) {
            boolean allMatch = true;
            for (int offset = 0; offset < word.length(); offset++) {
                if (toLower(text.charAt(start + offset)) != toLower(word.charAt(offset))) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return true;
            }
        }
        return false;
    }

    public static boolean sameText(String first, String second) {
        if (first.length() != second.length()) {
            return false;
        }
        for (int index = 0; index < first.length(); index++) {
            if (first.charAt(index) != second.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    public static int parseNonNegativeNumber(String text) {
        if (text.length() == 0 || text.length() > 9) {
            return 0;
        }
        int value = 0;
        for (int index = 0; index < text.length(); index++) {
            char digit = text.charAt(index);
            if (digit < '0' || digit > '9') {
                return 0;
            }
            value = (value * 10) + (digit - '0');
        }
        return value;
    }

    public static int hexDigitValue(char digit) {
        if (digit >= '0' && digit <= '9') {
            return digit - '0';
        }
        if (digit >= 'a' && digit <= 'f') {
            return 10 + (digit - 'a');
        }
        if (digit >= 'A' && digit <= 'F') {
            return 10 + (digit - 'A');
        }
        return -1;
    }

    public static String decodeFormText(String encoded) {
        TextBuilder decoded = new TextBuilder();
        int index = 0;
        while (index < encoded.length()) {
            char current = encoded.charAt(index);
            if (current == '+') {
                decoded.append(' ');
                index++;
            } else if (current == '%' && index + 2 < encoded.length() && hexDigitValue(encoded.charAt(index + 1)) >= 0 && hexDigitValue(encoded.charAt(index + 2)) >= 0) {
                int byteValue = (hexDigitValue(encoded.charAt(index + 1)) * 16) + hexDigitValue(encoded.charAt(index + 2));
                decoded.append((char) byteValue);
                index = index + 3;
            } else {
                decoded.append(current);
                index++;
            }
        }
        return decoded.toText();
    }

    public static String findFormValue(String formBody, String fieldName) {
        int position = 0;
        int bodyLength = formBody.length();
        while (position < bodyLength) {
            int pairEnd = indexOfChar(formBody, '&', position);
            if (pairEnd == -1) {
                pairEnd = bodyLength;
            }
            int equalsPosition = indexOfChar(formBody, '=', position);
            if (equalsPosition != -1 && equalsPosition < pairEnd) {
                String pairName = cut(formBody, position, equalsPosition);
                if (sameText(pairName, fieldName)) {
                    return decodeFormText(cut(formBody, equalsPosition + 1, pairEnd));
                }
            }
            position = pairEnd + 1;
        }
        return "";
    }

    public static String fieldName(char letter, int number, String suffix) {
        TextBuilder name = new TextBuilder();
        name.append(letter).append(number).append(suffix);
        return name.toText();
    }

    public static String labelWithNumber(char letter, int number) {
        TextBuilder label = new TextBuilder();
        label.append(letter).append(number);
        return label.toText();
    }
}
