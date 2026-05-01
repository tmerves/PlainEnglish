package PlainEnglish;
import PlainEnglish.AST.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Stack;
public class Interpreter {
    private final Program program;

    //stack of variables to maintain scope
    Stack<HashMap<String, InterpreterDataType>> variables = new Stack<>();
    //hashmap of classes to save typeDefs
    HashMap<String, ObjectInterpreterDataType> classes = new HashMap<>();
    public Interpreter(Program prog) {
        program = prog;
    }

    public void Start() throws Exception {
        //populate class defintions
        for(int i = 0; i < program.typedef.size(); i ++) {
            processTypeDef(program.typedef.get(i));
        }
        //find inital run method defintion
        Method runMethod = null;
        for(int i = 0; i < program.method.size(); i++) {
            if(program.method.get(i).name.equals("Run")) {
                runMethod = program.method.get(i);
                break;
            }
        }
        if(runMethod == null) throw new Exception("No \"Run\" method found");
        //make a functionCall for the inital start of program
        FunctionCall run = new FunctionCall();
        run.name = "Run";
        //TODO no semantic analysis is done separetly to identify issues with function defintions, maybe check at top or make a method for it, figure out how to approach this
        //in the assignment instructions he has a separate method for the method ast
        processFunctionCall(run);
    }
    //process a typedef AST node
    void processTypeDef(TypeDef td) throws Exception {
        //check if type already defined
        if(classes.containsKey(td.name)) throw new Exception("Type already defined");
        ObjectInterpreterDataType typeDefinition = new ObjectInterpreterDataType();
        classes.put(td.name, typeDefinition);
        typeDefinition.type = td.name;
        //fill in typeDefinition fields
        for(int i = 0; i < td.field.size(); i++) {
            String fieldType = td.field.get(i).type;
            //check what dataType the field is
            if(fieldType.equals("number")) {
                typeDefinition.fields.put(td.field.get(i).name, new NumberInterpreterDataType());
            }
            else if(fieldType.equals("boolean")) {
                typeDefinition.fields.put(td.field.get(i).name, new BooleanInterpreterDataType());
            }
            else if(fieldType.equals("string")) {
                typeDefinition.fields.put(td.field.get(i).name, new StringInterpreterDataType());
            }
            else typeDefinition.fields.put(td.field.get(i).name, new ObjectInterpreterDataType());
        }
    }
    //process a functionCall AST
    void processFunctionCall(FunctionCall fc) throws Exception {
        //find function defintion 
        Method method = null;
        //check for built in print method, otherwise find the method defintion
        if(fc.name.equals("Print")) {
            method = new PrintBuiltInMethod();
            method.with = true;
            //call specific functionCall for print
            ((PrintBuiltInMethod)method).functionCall(fc, this);
            return;
        }
        else {
            for(int i = 0; i < program.method.size(); i++) {
                if(program.method.get(i).name.equals(fc.name)) {
                    method = program.method.get(i);
                }
            }
        }
        if(method == null) throw new Exception("Function is not defined");
        //add hashmap for local variables of functions
        HashMap<String, InterpreterDataType> localVars = new HashMap<String, InterpreterDataType>();
        variables.push(localVars);
        //if referencing an object add its fields as variables within scope
        if(method.className.isPresent()) {
            if(!isVarMade(fc.obj.get(), false)) throw new Exception("Referenced object in function call doesn't exist");
            localVars.putAll(((ObjectInterpreterDataType)getVar(fc.obj.get(), Optional.empty())).fields);
        }
        //if there are paramters, add them
        if(method.with) {
            //array to hold types that names are already referenced (must explicitly name a parameter after that type is used once)
            ArrayList<String> usedTypes = new ArrayList<>();
            //add each parameter to local variables
            //TODO currently dont check if functioncall and method have same length
            for(int i = 0; i < fc.parameter.size(); i++) {
                //TODO make sure the types of the parameters of the function calls match the ones of the expression
                //get the type of the parameter defined in method declaration to determine type of evaluation
                Parameter p = method.parameter.get(i);
                InterpreterDataType paramVal; //value of parameter to be evaluated
                //look at variable being set to, if its only a variable, do not need to evaluate its expression
                boolean isVariable = false;
                Optional<VariableReference> vr = fc.parameter.get(i).term.get(0).factor.get(0).variablereference;
                //holds object name for member variables
                Optional<String> ownerName = Optional.empty();
                //check if parameter is a variable and by itself
                if(vr.isPresent() && fc.parameter.get(i).term.size() ==1 && fc.parameter.get(i).term.get(0).factor.size() == 1) {
                   isVariable = true;
                   //check if referencing a member variable
                   if(vr.get().of) ownerName = vr.get().$object;
                   //check if var you're setting to is actually available/ in scope
                   else if(!isVarMade(vr.get().name, false)) throw new Exception("Variable has not been made"); 
                }
                if(p.paramType.equals("number")) {
                    paramVal = new NumberInterpreterDataType();
                    if(isVariable) paramVal = getVar(vr.get().name, ownerName); //just reference the variable
                    else ((NumberInterpreterDataType)paramVal).value = evalNumberExpression(fc.parameter.get(i));
                }
                else if(p.paramType.equals("string")) {
                    if(isVariable) paramVal = getVar(vr.get().name, ownerName); //just reference the variable
                    else {
                        paramVal = new StringInterpreterDataType();
                        ((StringInterpreterDataType)paramVal).value = evalStringExpression(fc.parameter.get(i));
                    }
                }
                else if(p.paramType.equals("boolean")) {
                    paramVal = new BooleanInterpreterDataType();
                    if(isVariable) paramVal = getVar(vr.get().name, ownerName); //just reference the variable
                    else ((BooleanInterpreterDataType)paramVal).value = evalBooleanExpression(fc.parameter.get(i));
                } 
                else { //paramtype is an object
                    //TODO might be some checking that should be performed here for the method-> functioncall
                    //since its an object, it has to be a variable reference in the given expression
                    paramVal = getVar(vr.get().name, ownerName);
                }
                //parameter is explicitly named
                if(p.nameOverride.isPresent()) {
                    //check if that name override is a variable already used
                    if(usedTypes.contains(p.nameOverride.get())) throw new Exception("Cannot use same explictly named for parameter twice");
                    if(isVarMade(p.nameOverride.get(), true)) throw new Exception("Variable name already in use");
                    localVars.put(p.nameOverride.get(), paramVal);
                }
                else { //use type as the reference name
                    if(usedTypes.contains(p.paramType)) throw new Exception ("Cannot use same implictly type name twice for parameter");
                    usedTypes.add(p.paramType);
                    localVars.put(p.paramType, paramVal);
                }
            }
        }
        //process each statement in function
        processStatementBlock(method.statementblock);
        //function ended, remove the local variables 
        variables.pop();
    }
    //process a StatementBlock AST node
    void processStatementBlock(StatementBlock sb) throws Exception{
        //process each statement in function
        for(int i = 0; i < sb.statement.size(); i++) {
            processStatement(sb.statement.get(i));
        }
    }
    //process a Statement AST node
    void processStatement(Statement s) throws Exception {
        //declares / makes a variable 
        if (s.make.isPresent()) {
            Make makeStatement = s.make.get();
            //check if var name already exists
            if(isVarMade(makeStatement.name, true)) throw new Exception("Variable already is declared");
            InterpreterDataType dataType;
            //gets the data type based of AST type variable 
            if(makeStatement.type.equals("number")) dataType = new NumberInterpreterDataType();
            else if(makeStatement.type.equals("string")) dataType = new StringInterpreterDataType();
            else if(makeStatement.type.equals("boolean")) dataType = new BooleanInterpreterDataType();
            else { //making an object
                dataType = new ObjectInterpreterDataType();
                //check if type exists
                if(!classes.containsKey(makeStatement.type)) throw new Exception("Type not defined");
                //set the interpreter's object type to the one defined in make statement
                ((ObjectInterpreterDataType) dataType).type = makeStatement.type;
                //fill in field names of the instance from class with no values
                ((ObjectInterpreterDataType) dataType).fields.putAll(classes.get(makeStatement.type).fields);
            }
            //add variable name and dataType to local variable hashmap
            variables.get(variables.size()-1).put(makeStatement.name, dataType);
        }
        else if(s.set.isPresent()) {
            Set setStatement = s.set.get();
            //variable you are setting
            String varName = setStatement.variablereference.name;
            //holds objNames for member variables
            Optional<String> ownerName = Optional.empty();
            Optional<String> variableToPointAtOwnerName= Optional.empty();
            //TODO if you set a variable to a reference it doesnt properly check types
            //check if referencing a member variable
            if(setStatement.variablereference.of) ownerName = setStatement.variablereference.$object;
            //check if var you're setting to is actually available/ in scope
            else if(!isVarMade(varName, false)) throw new Exception("Variable has not been made"); 
            //look at variable being set to, if its only a variable, do not need to evaluate its expression
            boolean isVariable = false;
            //check for variable in expression
            Optional<VariableReference> variableToPointAt = setStatement.expression.term.get(0).factor.get(0).variablereference;
            if(setStatement.expression.term.size() == 1 && setStatement.expression.term.get(0).factor.size() == 1 && variableToPointAt.isPresent()) {
                isVariable = true;
                //check if referencing a member variable
                if(variableToPointAt.get().of) variableToPointAtOwnerName = variableToPointAt.get().$object; 
            }
            if(getVar(varName, ownerName) instanceof NumberInterpreterDataType) {
                //if not a variable evaluate the expression and save the value
                if(!isVariable) ((NumberInterpreterDataType) getVar(varName, ownerName)).value = evalNumberExpression(setStatement.expression);
                //otherwise grab the value of the variableToPointAt
                else ((NumberInterpreterDataType)getVar(varName, ownerName)).value = ((NumberInterpreterDataType)getVar(variableToPointAt.get().name, variableToPointAtOwnerName)).value; 
            }
            else if(getVar(varName, ownerName) instanceof BooleanInterpreterDataType) {
                //if not a variable evaluate the expression and save the value
                if(!isVariable) ((BooleanInterpreterDataType)getVar(varName, ownerName)).value = evalBooleanExpression(setStatement.expression);
                //otherwise grab the value of the variableToPointAt
                else ((BooleanInterpreterDataType)getVar(varName, ownerName)).value = ((BooleanInterpreterDataType)getVar(variableToPointAt.get().name, variableToPointAtOwnerName)).value; 
            }
            else if(getVar(varName, ownerName) instanceof StringInterpreterDataType) {
                //if not a variable evaluate the expression and save the value
                if(!isVariable) ((StringInterpreterDataType)getVar(varName, ownerName)).value = evalStringExpression(setStatement.expression);
                //referencing another string object
                else setReference(StringInterpreterDataType.class, varName, variableToPointAt.get().name, variableToPointAtOwnerName);
            }
            else setReference(ObjectInterpreterDataType.class, varName, variableToPointAt.get().name, variableToPointAtOwnerName); //setting an object variable
        }
        else if(s.$if.isPresent()) {
            if(evalBooleanTermExpression(s.$if.get().boolexpterm)){
                //add hashmap for local variables of if statement
                HashMap<String, InterpreterDataType> localVars = new HashMap<String, InterpreterDataType>();
                variables.push(localVars);
                processStatementBlock(s.$if.get().statementblock);
                //remove local variables from scope
                variables.pop();
            }
            else if(s.$if.get().$else) {
                //add hashmap for local variables of else statement
                HashMap<String, InterpreterDataType> localVars = new HashMap<String, InterpreterDataType>();
                variables.push(localVars);
                processStatementBlock(s.$if.get().falseCase.get());
                //remove local variables from scope
                variables.pop();
            }
        }
        else if(s.loop.isPresent()) {
            //add hashmap for local variables of else statement
            HashMap<String, InterpreterDataType> localVars = new HashMap<String, InterpreterDataType>();
            variables.push(localVars);
            while(evalBooleanTermExpression(s.loop.get().boolexpterm)) {
                processStatementBlock(s.loop.get().statementblock);
            }
            //remove local variables from scope
            variables.pop();
        }
        else if(s.functioncall.isPresent()) {
            processFunctionCall(s.functioncall.get());
        }
    }
    //sets a variable to reference another variable
    void setReference(Class type,  String varName, String variableToPointAtName, Optional<String> ownerName) throws Exception {
        //points to the same object as the variable you are now referencing (share the object)
        InterpreterDataType variableToPointAtObject =  getVar(variableToPointAtName,  ownerName);
        //check if what you are trying to reference exists before setting
        if(variableToPointAtObject != null) {
            //check if it is same type
            if(variableToPointAtObject.getClass() == type) putVar(varName, variableToPointAtObject);
            else throw new Exception("Variable being referenced is not of same type");
        }
        else throw new Exception("Variable trying to be referenced does not exist");
    }
    //evalutes a boolean expression of BoolExpTerm
    //if(1*2==2 and true==true or true==false)
    boolean evalBooleanTermExpression(BoolExpTerm term) throws Exception {
        if(term.not) {
            return !evalBooleanTermExpression(term.notTerm.get());
        }
        else {
            //get first factor then check for other terms
            boolean curResult = evalBooleanFactor(term.boolexpfactor.get());
            if(term.boolexpterm.size() > 0) {
                for(int i = 0; i < term.boolexpterm.size(); i++) {
                    if(term.theandORor.get(i) == andORor.and) { //and operation
                        curResult = curResult && evalBooleanTermExpression(term.boolexpterm.get(i));
                    } //or operation
                    else curResult = curResult || evalBooleanTermExpression(term.boolexpterm.get(i));
                }
            }
            return curResult;
        }
    }
    //evalutes a BoolExpFactor 
    boolean evalBooleanFactor(BoolExpFactor factor) throws Exception {
        //check if just referencing a variable
        if(factor.variablereference.isPresent()) {
            Optional<String> ownerName = Optional.empty();
            if(factor.variablereference.get().of) ownerName = factor.variablereference.get().$object; 
            InterpreterDataType var = getVar(factor.variablereference.get().name, ownerName);
            //check type
            if(var.getClass() == BooleanInterpreterDataType.class) return ((BooleanInterpreterDataType)var).value;
            else throw new Exception("Cannot reference a non-boolean variable in boolean expression without comparators");
        }
        else {
            //determine type of expression for both sides
            Expression lhs = factor.lhs.get();
            Expression rhs = factor.rhs.get();
            Object lhsValue = getExpression(lhs);
            //check if evaluated as a variable 
            Class type = lhsValue.getClass();
            if(type == ObjectInterpreterDataType.class ) throw new Exception("Cannot directly reference an object in a boolean expression");
            else if(type == StringInterpreterDataType.class) lhsValue = ((StringInterpreterDataType)lhsValue).value;
            else if(type == BooleanInterpreterDataType.class) lhsValue = ((BooleanInterpreterDataType)lhsValue).value;
            else if(type == NumberInterpreterDataType.class) lhsValue = ((NumberInterpreterDataType)lhsValue).value;
            //check if evaluated as a variable 
            Object rhsValue = getExpression(rhs);
            type = rhsValue.getClass();
            if(type == ObjectInterpreterDataType.class) throw new Exception("Cannot directly reference an object in a boolean expression");
            else if(type == StringInterpreterDataType.class) rhsValue = ((StringInterpreterDataType)rhsValue).value;
            else if(type == BooleanInterpreterDataType.class) rhsValue = ((BooleanInterpreterDataType)rhsValue).value;
            else if(type == NumberInterpreterDataType.class) rhsValue = ((NumberInterpreterDataType)rhsValue).value;
            //check type matching
            if(lhsValue.getClass() != rhsValue.getClass()) throw new Exception("Cannot compare different types");
            switch(factor.thecompareOps.get()){ 
                case doubleequal:
                    if(lhsValue.getClass() == Float.class) {
                        //use an epsilon of .00001 to approximate equality
                        float difference = Math.abs((Float)lhsValue - (Float)rhsValue);
                        return difference < .00001f ? true : false;
                    }
                    else return lhsValue.equals(rhsValue);
                case notequal:
                    return !lhsValue.equals(rhsValue);
                //rest of cases only work for floats
                case lessthanequal:
                    if(lhsValue.getClass() == Float.class) {
                        return (Float)lhsValue <= (Float)rhsValue;
                    }
                    break;
                case greaterthanequal:
                    if(lhsValue.getClass() == Float.class) {
                        return (Float)lhsValue >= (Float)rhsValue;
                    }
                    break;
                case greaterthan:
                    if(lhsValue.getClass() == Float.class) {
                        return (Float)lhsValue > (Float)rhsValue;
                    }
                    break;
                case lessthan:
                    if(lhsValue.getClass() == Float.class) {
                        return (Float)lhsValue < (Float)rhsValue;
                    }
                    break;
            } //if none of the cases return, only scenario is: it wasn't a (==,!=) and weren't floats
            throw new Exception("Cannot compare non-float values");
        }
    }
    //gets and returns the value and type of any type of given expression
    Object getExpression(Expression expression) throws Exception {
        //if an arithmetic expression
        if(expression.term.size() > 1 || expression.term.get(0).factor.size() > 1)  {
            return evalNumberExpression(expression);
        }
        else if(expression.term.get(0).factor.get(0).number.isPresent()) return Float.parseFloat(expression.term.get(0).factor.get(0).number.get());
        else if(expression.term.get(0).factor.get(0).$false == true) return false;
        else if(expression.term.get(0).factor.get(0).$true == true) return true;
        else if(expression.term.get(0).factor.get(0).stringliteral.isPresent()) return expression.term.get(0).factor.get(0).stringliteral.get();
        else if(expression.term.get(0).factor.get(0).characterliteral.isPresent()) return expression.term.get(0).factor.get(0).characterliteral.get();
        else if(expression.term.get(0).factor.get(0).variablereference.isPresent()) {
            //check if variable is a member of an object
            Optional<String> ownerName = Optional.empty();
            if(expression.term.get(0).factor.get(0).variablereference.get().of) ownerName = expression.term.get(0).factor.get(0).variablereference.get().$object;
            return getVar(expression.term.get(0).factor.get(0).variablereference.get().name, ownerName);
        }
        //TODO double check this is right implementation for expression in factor (when testing have to use parenthesis to show its an expression inside expression smh)
        else if(expression.term.get(0).factor.get(0).expression.isPresent()) return getExpression(expression.term.get(0).factor.get(0).expression.get());
        else throw new Exception("Not a valid boolean expression"); //TODO try to see if its even possible to run this, cause don't think its possible cause of parser checks
    }
    //evalutes a boolean expression (differs from booleanTermExpression as it uses expression AST)
    boolean evalBooleanExpression(Expression expression) throws Exception {
        //check for any tokens past inital value
        if(expression.term.size() > 1 || expression.term.get(0).factor.size() > 1) throw new Exception("Boolean variable cannot be assigned to an arithmetic expression");
        Factor factor = expression.term.get(0).factor.get(0);
        if(factor.$false == true) return false;
        else if(factor.$true == true) return true;
        else throw new Exception("Cannot set non-boolean type to boolean variable"); //The one factor isn't a boolean
    }
    //evaluates and returns values of an expression AST
    float evalNumberExpression(Expression expression) throws Exception {
        //get the first term and then check for additional terms with operators
        float expressionResult = evalNumberTerm(expression.term.get(0));
        for(int i = 1; i < expression.term.size(); i++) { //start at 1, first term already handled
            float termResult = evalNumberTerm(expression.term.get(i));
            //check operator and apply the term to the total
            plusORhyphen operator = expression.theplusORhyphen.get(i-1); //operator is behind an index
            if(operator == plusORhyphen.plus) expressionResult += termResult;
            else expressionResult -= termResult; //operator = "-"
        }
        return expressionResult;
    }
    //TODO prob can rewrite this cause its clunky with double code
    //evaluates and returns values of a term AST 
    float evalNumberTerm(Term term) throws Exception {
        float termResult = 0;
        //get the first factor, and then treat the rest as right hand side 
        Factor leftFactor = term.factor.get(0);
        //check for type mismatches
        if(leftFactor.$false == true || leftFactor.$true == true || leftFactor.characterliteral.isPresent() || leftFactor.stringliteral.isPresent()) throw new Exception ("Cannot set number type to non-number type");
        //check if a variable of number, or just a number, or expression of numbers
        if(leftFactor.number.isPresent()) termResult = Float.parseFloat(term.factor.get(0).number.get());
        else if(leftFactor.variablereference.isPresent()) {
            InterpreterDataType varName;
            //holds objName for member variables
            Optional<String> obj = Optional.empty();
            //check if variableReference is a member of an object
            if(leftFactor.variablereference.get().of) obj = leftFactor.variablereference.get().$object;
            varName = getVar(leftFactor.variablereference.get().name, obj);
            termResult = ((NumberInterpreterDataType)varName).value;
        }
        else termResult = evalNumberExpression(leftFactor.expression.get()); //is an expression, evaluate that first
        //go through the rest of the factors 
        for(int i = 1; i < term.factor.size(); i++) { //start at 1, first factor already handled
            float rhs = 0;
            //get right side
            Factor rightFactor = term.factor.get(i);
            //check for type mismatches
            if(rightFactor.$false == true || rightFactor.$true == true || rightFactor.characterliteral.isPresent() || rightFactor.stringliteral.isPresent()) throw new Exception ("Cannot set number type to non-number type");
            //check if a variable of number, or just a number, or expression of numbers
            if(rightFactor.number.isPresent()) rhs = Float.parseFloat(rightFactor.number.get());
            else if(rightFactor.variablereference.isPresent()) {
                InterpreterDataType varName;
                //holds objName for member variables
                Optional<String> ownerName = Optional.empty();
                //check if variableReference is a member of an object
                if(rightFactor.variablereference.get().of) ownerName = rightFactor.variablereference.get().$object;
                varName = getVar(rightFactor.variablereference.get().name, ownerName);
                rhs = ((NumberInterpreterDataType)varName).value;
            }
            else rhs = evalNumberExpression(rightFactor.expression.get()); //is an expression, evaluate that first
            //check operator and apply rhs to the term's total
            asteriskORslashORpercent operator = term.theasteriskORslashORpercent.get(i-1); //operator is behind an index
            if(operator == asteriskORslashORpercent.asterisk) termResult *= rhs;
            else if(operator == asteriskORslashORpercent.slash) termResult /= rhs; 
            else termResult %= rhs; //operator = %
        }
        return termResult;
    }
    //evalutes a string expression
    String evalStringExpression(Expression expression) throws Exception {
        //get the first term and then check for additional terms with operators
        String expressionResult = null;
        Factor firstFactor =  expression.term.get(0).factor.get(0);
        //check for arithemtic operators
        if(expression.term.get(0).factor.size() > 1) throw new Exception("Can only use \"+\" operators on strings");
        //check for string or char
        if(firstFactor.characterliteral.isPresent())  expressionResult = firstFactor.characterliteral.get();
        else if(firstFactor.stringliteral.isPresent()) expressionResult = firstFactor.stringliteral.get();
        else throw new Exception("String type only accepts char literals and strings");
        //concatenate any strings in expression
        for(int i = 1; i < expression.term.size(); i++) { //start at 1, first term already handled 
            //check for correct operators
            plusORhyphen operator = expression.theplusORhyphen.get(i-1); //operator is behind an index
            if(expression.term.get(i).factor.size() > 1 || operator != plusORhyphen.plus) throw new Exception("Can only use \"+\" operators on strings");
            Factor factor = expression.term.get(i).factor.get(0);
            //check for string or char
            if(factor.characterliteral.isPresent())  expressionResult += factor.characterliteral.get();
            else if(factor.stringliteral.isPresent()) expressionResult += factor.stringliteral.get();
            else throw new Exception("String type only accepts char literals and strings");
        }
        return expressionResult;
    }
    //searches through stack of in scope variables and returns value of variable
    InterpreterDataType getVar(String name, Optional<String> ownerName) throws Exception {
        for(int i = variables.size()-1; i >= 0; i--) {
            HashMap<String, InterpreterDataType> cur = variables.get(i);
            //if object is present, look for member variable instead
            if(ownerName.isPresent() && cur.containsKey(ownerName.get())) {
                ObjectInterpreterDataType owner = (ObjectInterpreterDataType)cur.get(ownerName.get());
                //if name indexed is a field of the object
                if(owner.fields.containsKey(name)) return owner.fields.get(name);
                throw new Exception("Member variable of " + ownerName.get() + " not found");
            }
            else if(cur.containsKey(name)) {
                InterpreterDataType result = cur.get(name);
                if(result == null) throw new Exception("No value set for variable");
                return result;
            }
        }
        throw new Exception("Variable not in scope or not made"); 
    }
    //searching through stack of in scope variables to see if variable exists
    boolean isVarMade(String name, boolean local) {
        if(local) {
            if(variables.get(variables.size()-1).containsKey(name)) return true;
        }
        else {
            for(int i = variables.size()-1; i >=0; i--) {
                if(variables.get(i).containsKey(name)) return true;
            }
        }
        return false;
    }
    //saves or updates variable into stack of variable hashmaps
    void putVar(String name, InterpreterDataType val) {
        //find if it already exists, if so update it 
        for(int i = variables.size()-1; i >= 0; i--) {
            if(variables.get(i).containsKey(name)) {
                variables.get(i).put(name, val);
                return;
            }
        }
        //doesn't exist, put in the hashmap at the top of the stack
        variables.get(variables.size()-1).put(name, val);
    }
    public static void main(String[] args) throws Exception {
        //read in a inputted file for code
        String code = Files.readString(Path.of("")); 
        System.out.println(code);
        //parse and lex the code
        Lexer lexer = new Lexer(code);
        LinkedList<Token> tokens = lexer.lex();
        PlainEnglishParser parser = new PlainEnglishParser(tokens);
        Program pg = parser.program().get();
        Interpreter interpreter = new Interpreter(pg);
        interpreter.Start();
    }
}
