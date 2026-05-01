package PlainEnglish;
import java.util.LinkedList;
import java.util.Optional;

import PlainEnglish.Token.TokenTypes;
import PlainEnglish.AST.*;

public class PlainEnglishParser {
    public PlainEnglishParser(LinkedList<Token> input) {
        tm = new TokenManager(input);
    }
    private final TokenManager tm;
    //Program = (NEWLINE | TypeDef | Method)+
    //handles creating and populating the AST nodes
    public Optional<Program> program() throws SyntaxErrorException {
        Program pg = new Program();
        
        while(!tm.isAtEnd()) {
            optionalNewLines();
            Optional<TypeDef> typeDef = typeDef();
            if(typeDef.isPresent()) {
                pg.typedef.add(typeDef.get());
                continue;
            } 
            Optional<Method> method = method();
            if(method.isPresent()) {
                pg.method.add(method.get());
                continue;
            }
            // nothing matched and not at end — something is wrong
            throw new SyntaxErrorException("Expected a TypeDef or Method", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        
        return Optional.of(pg);
    }
    //TypeDef = {ignore}("A" | "An") {name}IDENTIFIER "is" NEWLINE+ INDENT Field+ DEDENT {ignore}NEWLINE*
    //checks and handles typedefs
    public Optional<TypeDef> typeDef() throws SyntaxErrorException {
        //check if a TypeDef
        if(tm.matchAndRemove(TokenTypes.A).isEmpty()) {
            if(tm.matchAndRemove(TokenTypes.AN).isEmpty()) {
                //not a typeDef, exit
                return Optional.empty();
            }
        }
        TypeDef td = new TypeDef();  //is a typeDef now check if it follows rules
        //check for identifier
        td.name = requireIdentifier();
        //check for "is" token
        if(tm.matchAndRemove(TokenTypes.IS).isEmpty()) {
            throw new SyntaxErrorException("Expected an IS token when parsing for a TypeDef", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        requireNewLine();
        optionalNewLines();
        //check for indent 
        if(tm.matchAndRemove(TokenTypes.INDENT).isEmpty()) {
            throw new SyntaxErrorException("Expected an INDENT token when parsing for a TypeDef", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        //check for inital required field
        td.field.add(field());
        //check for other optional fields by checking if next token is an identifier
        while(!tm.isAtEnd() && tm.peek(0).get().Type == TokenTypes.IDENTIFIER) {
            td.field.add(field());
        }
        //check for dedent
        if(tm.matchAndRemove(TokenTypes.DEDENT).isEmpty()) {
            throw new SyntaxErrorException("Expected a DEDENT token when parsing for a TypeDef", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        optionalNewLines();
        return Optional.of(td);
    }
    //Method = "To" {name}IDENTIFIER ({ignore}"a" {className}IDENTIFIER)? ("with" Parameter ("," Parameter)*)? NEWLINE+ StatementBlock 
    //checks and handles method declarations
    public Optional<Method> method() throws SyntaxErrorException {
        //check if method present
        if(tm.matchAndRemove(TokenTypes.TO).isEmpty()) {
            return Optional.empty();
        }
        Method method = new Method(); //is a method, now check for rules
        method.name = requireIdentifier(); //check for method name
        if(tm.matchAndRemove(TokenTypes.A).isPresent()) {
            //A is present, check for name of object
            method.className = Optional.of(requireIdentifier());
        }
        //check for parameters
        if(tm.matchAndRemove(TokenTypes.WITH).isPresent()) {
            method.with = true;
            method.parameter.add(parameter()); //inital parameter
            //check for other parameters
            while(tm.matchAndRemove(TokenTypes.COMMA).isPresent()) {
                 method.parameter.add(parameter());
            }
        }
        requireNewLine();
        optionalNewLines();
        method.statementblock = statementBlock();
        return Optional.of(method);
    }
    //Field = {type}IDENTIFIER {name}IDENTIFIER NEWLINE+
    //handles already identified fields
    public Field field() throws SyntaxErrorException { 
        Field f = new Field();
        f.type = requireIdentifier();
        f.name = requireIdentifier();
        requireNewLine();
        optionalNewLines();
        return f;
    } 
    //Parameter = {paramType}IDENTIFIER ("named" {nameOverride}IDENTIFIER)?
    //handles already identified paramters
    public Parameter parameter() throws SyntaxErrorException {
        Parameter param = new Parameter();
        //check for identifier
        if(tm.peek(0).get().Type == TokenTypes.IDENTIFIER) param.paramType = tm.peek(0).get().Value.get();
        else throw new SyntaxErrorException("Expected identifier when parsing for a parameter", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        tm.matchAndRemove(TokenTypes.IDENTIFIER);
        //check for a explicility named parameter
        if(tm.matchAndRemove(TokenTypes.NAMED).isPresent()) {
            param.named = true;
            Token token = tm.peek(0).get();
            if(token.Type == TokenTypes.IDENTIFIER) {
                param.nameOverride = token.Value;
                tm.matchAndRemove(TokenTypes.IDENTIFIER);
            }
            else throw new SyntaxErrorException("Expected identifier when parsing for a name override in parameter", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        return param;
    }
    //StatementBlock = INDENT Statement+ DEDENT
    //handles already identified statementBlocks
    public StatementBlock statementBlock() throws SyntaxErrorException {
        StatementBlock statementBlock = new StatementBlock();
        if(tm.matchAndRemove(TokenTypes.INDENT).isEmpty()) {
            throw new SyntaxErrorException("Expected an INDENT token when parsing for a statementBlock", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        statementBlock.statement.add(statement()); //check for inital statement
        //check for other statements
        while(tm.matchAndRemove(TokenTypes.DEDENT).isEmpty()) {
            statementBlock.statement.add(statement());
        }
        return statementBlock;
    }
    //Statement = If  | Loop | Set | Make | FunctionCall
    //checks for type of statement and calls its function
    public Statement statement() throws SyntaxErrorException {
        Statement statement = new Statement();
        if(tm.matchAndRemove(TokenTypes.IF).isPresent())        statement.$if = $if();
        else if(tm.matchAndRemove(TokenTypes.LOOP).isPresent()) statement.loop = loop();
        else if(tm.matchAndRemove(TokenTypes.SET).isPresent())  statement.set = set();
        else if(tm.matchAndRemove(TokenTypes.MAKE).isPresent()) statement.make = make();
        else if(tm.peek(0).get().Type == TokenTypes.IDENTIFIER) statement.functioncall = Optional.of(functionCall());
        else throw new SyntaxErrorException("Expected (if, loop, set, make, fuctionCall) when parsing for a statement", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        return statement;
    }
    //Set = "Set" VariableReference "to" Expression NEWLINE+
    //handles set statements
    public Optional<Set> set() throws SyntaxErrorException {
        Set set = new Set();
        Optional<VariableReference> vr = variableReference();
        if(vr.isPresent()) set.variablereference = vr.get(); else throw new SyntaxErrorException("Expected a variableReference when parsing for a set statement", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        if(tm.matchAndRemove(TokenTypes.TO).isEmpty()) {
            throw new SyntaxErrorException("Expected a TO token when parsing for a set statement", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        set.expression = expression(); 
        //if user attempted to set to a boolean expression with boolean operators
        if(tm.peek(0).get().Type == TokenTypes.AND || tm.peek(0).get().Type == TokenTypes.OR || tm.peek(0).get().Type == TokenTypes.NOT) {
            throw new SyntaxErrorException("Boolean variables can only be expressed as \"true\" or \"false\"", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        requireNewLine();
        optionalNewLines();
        return Optional.of(set);
    }
    //Make = "Make" {type}IDENTIFIER "named" {name}IDENTIFIER NEWLINE+
    //handles already identified make statements
    public Optional<Make> make() throws SyntaxErrorException {
        Make make = new Make();
        make.type = requireIdentifier();
        if(tm.matchAndRemove(TokenTypes.NAMED).isEmpty()) {
            throw new SyntaxErrorException("Expected a NAMED token when parsing for a make statement", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        make.name = requireIdentifier();
        requireNewLine();
        optionalNewLines();
        return Optional.of(make);
    }
    //If = "If" BoolExpTerm NEWLINE+ StatementBlock ("else" NEWLINE {falseCase}StatementBlock)? 
    //handles already identified if statements
    public Optional<If> $if() throws SyntaxErrorException {
        If ifStmnt = new If();
        ifStmnt.boolexpterm = boolExpTerm();
        requireNewLine();
        optionalNewLines();
        ifStmnt.statementblock = statementBlock();
        if(tm.matchAndRemove(TokenTypes.ELSE).isPresent()) {
            ifStmnt.$else = true;
            requireNewLine();
            ifStmnt.falseCase = Optional.of(statementBlock());
        }
        return Optional.of(ifStmnt);
    }
    //Loop = "Loop" BoolExpTerm NEWLINE+ StatementBlock     
    //handles already identified make statements
    public Optional<Loop> loop() throws SyntaxErrorException {
        Loop loop = new Loop();
        loop.boolexpterm = boolExpTerm();
        requireNewLine();
        optionalNewLines();
        loop.statementblock = statementBlock();
        return Optional.of(loop);
    }
    //BoolExpTerm =  BoolExpFactor (("and"|"or") BoolExpTerm)* | "not" {notTerm}BoolExpTerm
    //handles already identified boolExpTerms
    public BoolExpTerm boolExpTerm() throws SyntaxErrorException {
        BoolExpTerm boolExpTerm = new BoolExpTerm();
        //check if a "NOT" term
        if(tm.matchAndRemove(TokenTypes.NOT).isPresent()) {
            boolExpTerm.not = true;
            boolExpTerm.notTerm = Optional.of(boolExpTerm());
        }
        else { 
            boolExpTerm.boolexpfactor = Optional.of(boolExpFactor());
            //checks for additional terms
            while(true) {
                //if (AND, OR) exists there is another term
                if(tm.matchAndRemove(TokenTypes.AND).isPresent()) {
                    boolExpTerm.theandORor.add(andORor.and);
                }
                else if(tm.matchAndRemove(TokenTypes.OR).isPresent()) {
                    boolExpTerm.theandORor.add(andORor.or);
                }   
                else break; //no more terms left
                //another term identified
                BoolExpTerm nextBoolExpTerm = new BoolExpTerm();
                if(tm.matchAndRemove(TokenTypes.NOT).isPresent()) { 
                    nextBoolExpTerm.not = true;
                    nextBoolExpTerm.notTerm = Optional.of(boolExpTerm());
                } 
                else {
                    nextBoolExpTerm.boolexpfactor = Optional.of(boolExpFactor());
                }
                //add the term
                boolExpTerm.boolexpterm.add(nextBoolExpTerm);
            }
        }
        return boolExpTerm;
    }
    //BoolExpFactor = ({lhs}Expression {compareOps}( "==" | "!=" | "<=" | ">=" | ">" | "<" ) {rhs}Expression) | VariableReference
    //handles already identified boolExpFactors
    public BoolExpFactor boolExpFactor() throws SyntaxErrorException {
        BoolExpFactor boolExpFactor = new BoolExpFactor();
        
        boolExpFactor.lhs = Optional.of(expression());
        //check for the operator
        if(tm.matchAndRemove(TokenTypes.DOUBLEEQUAL).isPresent()) boolExpFactor.thecompareOps = Optional.of(compareOps.doubleequal);
        else if(tm.matchAndRemove(TokenTypes.NOTEQUAL).isPresent()) boolExpFactor.thecompareOps = Optional.of(compareOps.notequal);
        else if(tm.matchAndRemove(TokenTypes.LESSTHANEQUAL).isPresent()) boolExpFactor.thecompareOps = Optional.of(compareOps.lessthanequal);
        else if(tm.matchAndRemove(TokenTypes.GREATERTHANEQUAL).isPresent()) boolExpFactor.thecompareOps = Optional.of(compareOps.greaterthanequal);
        else if(tm.matchAndRemove(TokenTypes.GREATERTHAN).isPresent()) boolExpFactor.thecompareOps = Optional.of(compareOps.greaterthan);
        else if(tm.matchAndRemove(TokenTypes.LESSTHAN).isPresent()) boolExpFactor.thecompareOps = Optional.of(compareOps.lessthan);
        else { //no operator exists
            //only valid case is standalone variablereference, must check that is the case
            Expression lhs = boolExpFactor.lhs.get();
            //check that there isn't more than 1 term and that there is only 1 factor in that 1 term
            if(lhs.term.size() > 1 || lhs.term.get(0).factor.size() > 1) throw new SyntaxErrorException("Expected a variable reference or two compared expressions", tm.getCurrentLine(), tm.getCurrentColumnNumber());
            //the boolean expression factor is just a variable reference, go back and grab value from lhs, then undo previous assignment
            Optional<VariableReference> f = lhs.term.get(0).factor.get(0).variablereference;
            if(f.isPresent()) boolExpFactor.variablereference = f;
            else throw new SyntaxErrorException("Expected a variable reference or two compared expressions", tm.getCurrentLine(), tm.getCurrentColumnNumber());
            boolExpFactor.lhs = Optional.empty();
            return boolExpFactor;
        }
        boolExpFactor.rhs = Optional.of(expression());
        return boolExpFactor;
    }
    //FunctionCall = {name}IDENTIFIER {obj}IDENTIFIER? ({ignore}"with" {parameter}Expression ("," {parameter}Expression)* )? NEWLINE+
    //handles already identified functionCalls
    public FunctionCall functionCall() throws SyntaxErrorException {
        FunctionCall functionCall = new FunctionCall();
        functionCall.name = tm.matchAndRemove(TokenTypes.IDENTIFIER).get().Value.get();
        //check if an object is referenced
        if(tm.peek(0).get().Type == TokenTypes.IDENTIFIER) functionCall.obj = tm.matchAndRemove(TokenTypes.IDENTIFIER).get().Value;
        if(tm.matchAndRemove(TokenTypes.WITH).isPresent()) {
            //check for first parameter
            functionCall.parameter.add(expression());
            //check for additional parameters
            while(tm.matchAndRemove(TokenTypes.COMMA).isPresent()) functionCall.parameter.add(expression());
        }
        requireNewLine();
        optionalNewLines();
        return functionCall;
    }
    //Expression = Term ( ("+"|"-") Term )*
    //handles expressions that are already identified
    public Expression expression() throws SyntaxErrorException {
        Expression exp = new Expression();
        //get first required term
        exp.term.add(term());

        //check for additional terms
        Token t = tm.peek(0).get();
        while(t.Type == TokenTypes.PLUS || t.Type == TokenTypes.HYPHEN) {
            if(tm.matchAndRemove(TokenTypes.PLUS).isPresent()) exp.theplusORhyphen.add(plusORhyphen.plus);
            else if(tm.matchAndRemove(TokenTypes.HYPHEN).isPresent()) exp.theplusORhyphen.add(plusORhyphen.hyphen);
            exp.term.add(term());
            //get new token
            t = tm.peek(0).get();
        }
        return exp;
    }
    //Term = Factor ( ("*"|"/"|"%") Factor )*
    //handles terms that are already identified
    public Term term() throws SyntaxErrorException {
        Term term = new Term();
        //get first required factor
        term.factor.add(factor());
        //check for additional factors
        Token t = tm.peek(0).get(); 
        while(t.Type == TokenTypes.ASTERISK || t.Type == TokenTypes.SLASH || t.Type == TokenTypes.PERCENT) { 
            if(tm.matchAndRemove(TokenTypes.ASTERISK).isPresent()) term.theasteriskORslashORpercent.add(asteriskORslashORpercent.asterisk);
            else if(tm.matchAndRemove(TokenTypes.SLASH).isPresent()) term.theasteriskORslashORpercent.add(asteriskORslashORpercent.slash);
            else if(tm.matchAndRemove(TokenTypes.PERCENT).isPresent()) term.theasteriskORslashORpercent.add(asteriskORslashORpercent.percent);
            term.factor.add(factor());
            //get new token
            t = tm.peek(0).get(); 
        }
        return term;
    }
    //Factor = NUMBER | VariableReference | "true" | "false" | STRINGLITERAL | CHARACTERLITERAL | {ignore}"(" Expression {ignore}")" 
    //handles factors that are already identified
    public Factor factor() throws SyntaxErrorException {
        Factor fac = new Factor();
        Token tok = tm.peek(0).get();
        //check for basic token options
        if(tm.matchAndRemove(TokenTypes.NUMBER).isPresent()) fac.number = tok.Value;
        else if(tm.matchAndRemove(TokenTypes.TRUE).isPresent()) fac.$true = true;
        else if(tm.matchAndRemove(TokenTypes.FALSE).isPresent()) fac.$false = true;
        else if(tm.matchAndRemove(TokenTypes.STRINGLITERAL).isPresent()) fac.stringliteral = tok.Value;
        else if(tm.matchAndRemove(TokenTypes.CHARACTERLITERAL).isPresent()) fac.characterliteral = tok.Value;
        else {
            //check if is a variableReference
            Optional<VariableReference> vr = variableReference();
            if(vr.isPresent()) fac.variablereference = vr;
            //check for expression
            else if(tm.matchAndRemove(TokenTypes.OPENPAREN).isPresent()) {
                fac.expression = Optional.of(expression());
                if(tm.matchAndRemove(TokenTypes.CLOSEPAREN).isEmpty()) throw new SyntaxErrorException("Expected a closed parentheses at end of expression", tm.getCurrentLine(), tm.getCurrentColumnNumber());
            } 
            else throw new SyntaxErrorException("Expected a valid type when parsing for factor", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        return fac;
    }
    //VariableReference = {name}IDENTIFIER ("of" {object}IDENTIFIER)?
    //handles potential variableReferences, returns optional if not a variableReference
    public Optional<VariableReference> variableReference() throws SyntaxErrorException {
        VariableReference varRef = new VariableReference();
        if(tm.peek(0).get().Type == TokenTypes.IDENTIFIER) varRef.name = requireIdentifier(); //is a varRef, set name
        else return Optional.empty(); //not a varReference
        //check if looking at a member of a class
        if(tm.matchAndRemove(TokenTypes.OF).isPresent()) {
            varRef.of = true;
            varRef.$object = Optional.of(requireIdentifier());
        }
        return Optional.of(varRef);
    }

    //checks for an identifier and returns its value
    public String requireIdentifier() throws SyntaxErrorException {
        //check for identifier
        Optional<Token> id = tm.matchAndRemove(TokenTypes.IDENTIFIER);
        if(id.isEmpty()) {
            throw new SyntaxErrorException("Expected an identifier", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
        return id.get().Value.get();
    }
    //handles a required new line
    public void requireNewLine() throws SyntaxErrorException {
        if(tm.matchAndRemove(TokenTypes.NEWLINE).isEmpty()) {
            throw new SyntaxErrorException("Expected at least one NEWLINE token", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }
    }
    //handles optional new lines 
    public void optionalNewLines() { 
        //check for other optional newlines
        Optional<Token> tempToken = tm.matchAndRemove(TokenTypes.NEWLINE);
        while(tempToken.isPresent()) { 
            tempToken = tm.matchAndRemove(TokenTypes.NEWLINE);
        }
    }
}
