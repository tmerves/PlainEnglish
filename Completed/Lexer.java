package PlainEnglish;

import java.util.HashMap;
import java.util.LinkedList;

import PlainEnglish.Token.TokenTypes;

public class Lexer {

    private final TextManager tm;
    private int lineNumber;
    private int characterPosition;
    private int indentationLevel;

    // hold all the keywords for easy reference
    private HashMap<String, Token.TokenTypes> keywords;
    LinkedList<Token> listOfTokens = new LinkedList<>();
    public Lexer(String input) {
        tm = new TextManager(input);
        lineNumber = 1;
        characterPosition = 1;
        indentationLevel = 0;
        keywords = new HashMap<>();
            
        // put in all the values for the keywords
        keywords.put("\n", Token.TokenTypes.NEWLINE);
        keywords.put("To", Token.TokenTypes.TO);
        keywords.put("to", Token.TokenTypes.TO);
        keywords.put("A", Token.TokenTypes.A);
        keywords.put("a", Token.TokenTypes.A);
        keywords.put("with", Token.TokenTypes.WITH);
        keywords.put("named", Token.TokenTypes.NAMED);
        keywords.put("An", Token.TokenTypes.AN);
        keywords.put("an", Token.TokenTypes.AN);
        keywords.put("is", Token.TokenTypes.IS);
        keywords.put("if", Token.TokenTypes.IF);
        keywords.put("If", Token.TokenTypes.IF);
        keywords.put("Else", Token.TokenTypes.ELSE);
        keywords.put("else", Token.TokenTypes.ELSE);
        keywords.put("Loop", Token.TokenTypes.LOOP);
        keywords.put("loop", Token.TokenTypes.LOOP);
        keywords.put("set", Token.TokenTypes.SET);
        keywords.put("Set", Token.TokenTypes.SET);
        keywords.put("Make", Token.TokenTypes.MAKE);
        keywords.put("make", Token.TokenTypes.MAKE);
        keywords.put("of", Token.TokenTypes.OF);
        keywords.put("true", Token.TokenTypes.TRUE);
        keywords.put("false", Token.TokenTypes.FALSE);
        keywords.put("and", Token.TokenTypes.AND);
        keywords.put("or", Token.TokenTypes.OR);
        keywords.put("not", Token.TokenTypes.NOT);

        // Symbols / punctuation
        keywords.put(",", Token.TokenTypes.COMMA);
        keywords.put("+", Token.TokenTypes.PLUS);
        keywords.put("-", Token.TokenTypes.HYPHEN);
        keywords.put("*", Token.TokenTypes.ASTERISK);
        keywords.put("/", Token.TokenTypes.SLASH);
        keywords.put("%", Token.TokenTypes.PERCENT);
        keywords.put("(", Token.TokenTypes.OPENPAREN);
        keywords.put(")", Token.TokenTypes.CLOSEPAREN);
        keywords.put("==", Token.TokenTypes.DOUBLEEQUAL);
        keywords.put("!=", Token.TokenTypes.NOTEQUAL);
        keywords.put("<=", Token.TokenTypes.LESSTHANEQUAL);
        keywords.put(">=", Token.TokenTypes.GREATERTHANEQUAL);
        keywords.put(">", Token.TokenTypes.GREATERTHAN);
        keywords.put("<", Token.TokenTypes.LESSTHAN);
    }

    public LinkedList<Token> lex() throws SyntaxErrorException {
        //LinkedList<Token> listOfTokens = new LinkedList<>();

        while(!tm.isAtEnd()) {
            char c = tm.getCharacter();
            characterPosition++;
            if(Character.isLetter(c)) {
                String buffer = Character.toString(c);
                listOfTokens.add(readWord(buffer));
            }
            else if(Character.isDigit(c)) {
                String buffer = Character.toString(c);
                listOfTokens.add(readNumber(buffer));
            }
            else if(c == '\n') {
                //add inital newLine
                listOfTokens.add(new Token(Token.TokenTypes.NEWLINE, lineNumber, characterPosition));
                lineNumber++;
                characterPosition = 0;
                //loop through new lines 
                while(!tm.isAtEnd() && tm.peekCharacter() == '\n') {
                    tm.getCharacter();
                    listOfTokens.add(new Token(Token.TokenTypes.NEWLINE, lineNumber, characterPosition));
                    lineNumber++;
                }
                //check new indentation level
                int spaces = 0;
                int newIndentationLevel = 0;
                while(!tm.isAtEnd() && tm.peekCharacter() == ' ') {
                    tm.getCharacter();
                    spaces++; characterPosition++;
                    if(spaces == 4) {
                        newIndentationLevel++;
                        spaces = 0;
                    }
                }
                if(spaces % 4 != 0) {
                    throw new SyntaxErrorException("Invalid indentation level", lineNumber, characterPosition);
                }
                //check if next line is empty, if so do not change Indentation level
                if(!tm.isAtEnd() && tm.peekCharacter() != '\n' && tm.peekCharacter() != '\r') {
                    int indentationDiff = Math.abs(newIndentationLevel - indentationLevel);
                    //next line is more indented
                    if(newIndentationLevel > indentationLevel) {
                        for(int i = 0; i < indentationDiff; i++) {
                            listOfTokens.add(new Token(Token.TokenTypes.INDENT, lineNumber, characterPosition));
                        }
                    }
                    //next line is less indented
                    else {
                        for(int i = 0; i < indentationDiff; i++) {
                            listOfTokens.add(new Token(Token.TokenTypes.DEDENT, lineNumber, characterPosition));
                        }
                    }
                    indentationLevel = newIndentationLevel;
                }
            }
            //if a char literal
            else if (c == '\'') {
                //check if empty char literal
                if(tm.peekCharacter() == '\'') throw new SyntaxErrorException("Expected character literal between single quotes.", lineNumber, characterPosition);
                //take in the char in the middle of the ' '
                char actualChar = tm.getCharacter();
                characterPosition++;
                //char is one length, end quote should be right after
                if (tm.isAtEnd() || (tm.peekCharacter() != '\'')) throw new SyntaxErrorException("Expected end single quote.", lineNumber, characterPosition);
                tm.getCharacter(); //take in end single quote
                listOfTokens.add(new Token(Token.TokenTypes.CHARACTERLITERAL, lineNumber, characterPosition, Character.toString(actualChar)));
                characterPosition++;
            }
            //if a string literal
            else if (c == '\"') {
                char first = tm.getCharacter();
                //check if empty string
                if(first == '\"') {
                    listOfTokens.add(new Token(Token.TokenTypes.STRINGLITERAL, lineNumber, characterPosition, ""));
                }
                else {
                    //add the first character of the string to the line
                    String s = Character.toString(first);
                    
                    while(tm.peekCharacter() != '\"') {
                        s += tm.getCharacter();
                        if(tm.isAtEnd()) throw new SyntaxErrorException("Expected end double quotes.", lineNumber, characterPosition);
                    }
                    //take in the closing quote
                    tm.getCharacter();
                    //update position after, not before, so the exception above is in right spot
                    characterPosition+=s.length()+1; //+1 for the closing quote
                    listOfTokens.add(new Token(Token.TokenTypes.STRINGLITERAL, lineNumber, characterPosition, s));
                }
            }
            //if all cases fail, check for punctuation but skip spaces and carriage returns
            else if(!Character.isSpaceChar(c) && c != '\r'){
                Token newToken = readPunctuation(c);
                //if the new token isn't a comment
                if(newToken != null) {
                    listOfTokens.add(newToken);
                }
            }
        }
        listOfTokens.add(new Token(Token.TokenTypes.NEWLINE, lineNumber, characterPosition));
        //if newline not added at the end,the indentation level was not checked, need to output dedents equal to identationLevel
        for(int i = 0; i < indentationLevel; i++) {
            listOfTokens.add(new Token(Token.TokenTypes.DEDENT, lineNumber, characterPosition));
        }
        return listOfTokens;
    }
    //reads a word, must start with letters but can include numbers
    public Token readWord(String buffer) {
        //keep checking next characters until you reach end of the word or end of file
        while(!tm.isAtEnd() && (Character.isLetter(tm.peekCharacter()) || Character.isDigit(tm.peekCharacter()))) {
            buffer += tm.getCharacter();
            characterPosition++;
        }
            
        if(keywords.containsKey(buffer))
            return new Token(keywords.get(buffer), lineNumber, characterPosition);
        else
            return new Token(Token.TokenTypes.IDENTIFIER, lineNumber, characterPosition, buffer);
    } 
    //reads a number
    public Token readNumber(String buffer) throws SyntaxErrorException {
        //tracks whether decimal was used
        boolean decimal = false;
        //keep checking next characters until you reach end of the number
        while(!tm.isAtEnd() && (Character.isDigit(tm.peekCharacter()) || tm.peekCharacter() == '.')) {
            //if a decimal in number
            if(tm.peekCharacter() == '.') {
                characterPosition++;
                if(!decimal) decimal = true; else break;
                buffer+=tm.getCharacter();
            }
            else {
                buffer += tm.getCharacter();
                characterPosition++;
            }
        }
        //if number ends with a '.', append 0 (shorthand for .0)
        if(buffer.charAt(buffer.length()-1) == '.') return new Token(Token.TokenTypes.NUMBER, lineNumber, characterPosition, buffer+'0');
        return new Token(Token.TokenTypes.NUMBER, lineNumber, characterPosition, buffer);
    }
    public Token readPunctuation(char c) throws SyntaxErrorException {
        //check if a number starting with a '.'
        if(c == '.') {
            if(Character.isDigit(tm.peekCharacter())) return readNumber(Character.toString(c));
            //if not a digit after the '.'
            else throw new SyntaxErrorException("Number expected after period", lineNumber, characterPosition);
        }
        String oneLengthPunc = Character.toString(c);
        if(!tm.isAtEnd()) {
            String twoLengthPunc = oneLengthPunc+tm.peekCharacter();
            //check for two length punctuation keywords (==, >=, <=)
            if(keywords.containsKey(twoLengthPunc)) {
                //move the textManager past the second punctuation char
                tm.getCharacter();
                characterPosition++;
                return new Token(keywords.get(twoLengthPunc), lineNumber, characterPosition);
            }
            //comments
            else if (twoLengthPunc.equals("/*")) {
                tm.getCharacter();
                characterPosition++;
                //ignore anything until you find end of comment
                while(!tm.isAtEnd() && tm.peekCharacter() != '*' && tm.peekCharacter(1) != '/') {
                    tm.getCharacter();
                    characterPosition++;
                }
                //take in closing comment "*/"
                tm.getCharacter();
                tm.getCharacter();
                //take in newline if there is one after comments
                //TODO MAKE THIS WORK, currently it doesn't work as there are spaces and it doens't consume the newline
                //if(tm.peekCharacter() == '\n' || tm.peekCharacter(1) == tm.getCharacter();
                //consume all new lines and spaces after comment 
                while(tm.peekCharacter() == '\n' || tm.peekCharacter() == '\r' || tm.peekCharacter(0) == ' ') tm.getCharacter();
                //return null to signify no token for comments
                return null;
            }
        }
        //check for single length punctuation keywords ('=', ',', '+', '-', '*', '/', '%', '(', ')', '<', '>')
        if(keywords.containsKey(oneLengthPunc)) return new Token(keywords.get(oneLengthPunc), lineNumber, characterPosition);
        //not a keyword (cant be an identifier)
        else throw new SyntaxErrorException("Cannot be an identifier", lineNumber, characterPosition);
    }
}
    