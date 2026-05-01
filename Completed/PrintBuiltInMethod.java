package PlainEnglish;
import PlainEnglish.AST.*;

import java.util.HashMap;
import java.util.Optional;

public class PrintBuiltInMethod extends BuiltInMethod {
    public PrintBuiltInMethod() {
        this.name = "Print";
    }
    public void functionCall(FunctionCall fc, Interpreter interp) throws Exception {
        HashMap<String, InterpreterDataType> functionVars = new HashMap<String, InterpreterDataType>();
        // for print, determine type from the expression itself
        for(int i = 0; i < fc.parameter.size(); i++) {
            Expression param = fc.parameter.get(i);
            Factor factor = param.term.get(0).factor.get(0);
            InterpreterDataType paramVal;
            if(factor.stringliteral.isPresent() || factor.characterliteral.isPresent()) {
                paramVal = new StringInterpreterDataType();
                ((StringInterpreterDataType)paramVal).value = interp.evalStringExpression(param);
            }
            else if(factor.$true || factor.$false) {
                paramVal = new BooleanInterpreterDataType();
                ((BooleanInterpreterDataType)paramVal).value = interp.evalBooleanExpression(param);
            }
            else if(factor.number.isPresent()) {
                paramVal = new NumberInterpreterDataType();
                ((NumberInterpreterDataType)paramVal).value = interp.evalNumberExpression(param);
            }
            else if(factor.variablereference.isPresent()) {
                //check if a member variable
                Optional<String> ownerName = Optional.empty();
                if(factor.variablereference.get().of) ownerName = factor.variablereference.get().$object;
                //var by itself, just reference it
                if(fc.parameter.get(i).term.size() == 1 && fc.parameter.get(i).term.get(0).factor.size() == 1) {
                    //get variable value from stack of variables
                    paramVal = interp.getVar(factor.variablereference.get().name, ownerName);
                }
                else { //var is in an expression
                    String varName = factor.variablereference.get().name;
                    //check for type then evaluate
                    if(interp.getVar(varName, ownerName) instanceof NumberInterpreterDataType) {
                        paramVal = new NumberInterpreterDataType();
                        ((NumberInterpreterDataType)paramVal).value = interp.evalNumberExpression(param);
                    }
                    else if(interp.getVar(varName, ownerName) instanceof BooleanInterpreterDataType) {
                        paramVal = new BooleanInterpreterDataType();
                        ((BooleanInterpreterDataType)paramVal).value = interp.evalBooleanExpression(param);
                    }
                    else if(interp.getVar(varName, ownerName) instanceof StringInterpreterDataType)  {
                        paramVal = new StringInterpreterDataType();
                        ((StringInterpreterDataType)paramVal).value = interp.evalStringExpression(param);
                    }
                    else throw new Exception("Cannot perform operations on an object");
                }
            }
            else {
                paramVal = new NumberInterpreterDataType();
                ((NumberInterpreterDataType)paramVal).value = interp.evalNumberExpression(param);
            }
            functionVars.put(Integer.toString(i), paramVal);
        }
        Execute(functionVars);
    }
    public void Execute(HashMap<String, InterpreterDataType> context) {
        for (var item : context.values()) {
            System.out.println(item);
        }
    }
}
