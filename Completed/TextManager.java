package PlainEnglish;

public class TextManager
{
    //text to be read
    private final /*readonly*/ String text;

    //where you are currently reading
    private int position;

    public TextManager(String input)
    {
        position = 0;
        //replce tabs with 4 spaces
        text = input.replace("\t", "    ");
    }
    //!!!!!!!!!!!!might not work for a string that is one length long!!!!!!!!!!!!!
    //checks if you are at end string, returns true if at end, false if not
    public boolean isAtEnd() {
        return position == text.length();
    }
    //peeks at current character
    public char peekCharacter() {
        if(position >= text.length()) {
            throw new RuntimeException("tried to peek past end of file");
        }
        return text.charAt(position);
    }
    //peek a given distance to the right of the current position
    public char peekCharacter(int distance) {
        return text.charAt(position+distance);
    }

    //returns the character at the postion you are reading
    public char getCharacter() {
        return text.charAt(position++);
    }
}

