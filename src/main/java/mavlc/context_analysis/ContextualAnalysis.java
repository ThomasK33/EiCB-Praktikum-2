/*******************************************************************************
 * Copyright (C) 2016 Embedded Systems and Applications Group
 * Department of Computer Science, Technische Universitaet Darmstadt,
 * Hochschulstr. 10, 64289 Darmstadt, Germany.
 * 
 * All rights reserved.
 * 
 * This software is provided free for educational use only.
 * It may not be used for commercial purposes without the
 * prior written permission of the authors.
 ******************************************************************************/

/* TODO: Please fill this out!
 * 
 * EiCB group number: 86
 * Names and student ID numbers of group members: Simon Haneke (sh58xohu), Marius Hammann (mh93fobo), Thomas Kosiewski (tk83vydi)
 */

package mavlc.context_analysis;

import java.util.Iterator;
import mavlc.ast.nodes.ASTNode;
import mavlc.ast.nodes.expression.Addition;
import mavlc.ast.nodes.expression.And;
import mavlc.ast.nodes.expression.BinaryExpression;
import mavlc.ast.nodes.expression.BoolNot;
import mavlc.ast.nodes.expression.BoolValue;
import mavlc.ast.nodes.expression.CallExpression;
import mavlc.ast.nodes.expression.Compare;
import mavlc.ast.nodes.expression.Division;
import mavlc.ast.nodes.expression.DotProduct;
import mavlc.ast.nodes.expression.ElementSelect;
import mavlc.ast.nodes.expression.Expression;
import mavlc.ast.nodes.expression.FloatValue;
import mavlc.ast.nodes.expression.IdentifierReference;
import mavlc.ast.nodes.expression.IntValue;
import mavlc.ast.nodes.expression.MatrixMultiplication;
import mavlc.ast.nodes.expression.MatrixXDimension;
import mavlc.ast.nodes.expression.MatrixYDimension;
import mavlc.ast.nodes.expression.Multiplication;
import mavlc.ast.nodes.expression.Or;
import mavlc.ast.nodes.expression.StringValue;
import mavlc.ast.nodes.expression.StructureInit;
import mavlc.ast.nodes.expression.SubMatrix;
import mavlc.ast.nodes.expression.SubVector;
import mavlc.ast.nodes.expression.Subtraction;
import mavlc.ast.nodes.expression.UnaryMinus;
import mavlc.ast.nodes.expression.VectorDimension;
import mavlc.ast.nodes.function.FormalParameter;
import mavlc.ast.nodes.function.Function;
import mavlc.ast.nodes.module.Module;
import mavlc.ast.nodes.statement.CallStatement;
import mavlc.ast.nodes.statement.CompoundStatement;
import mavlc.ast.nodes.statement.Declaration;
import mavlc.ast.nodes.statement.ForLoop;
import mavlc.ast.nodes.statement.IfStatement;
import mavlc.ast.nodes.statement.LeftHandIdentifier;
import mavlc.ast.nodes.statement.MatrixLHSIdentifier;
import mavlc.ast.nodes.statement.ReturnStatement;
import mavlc.ast.nodes.statement.Statement;
import mavlc.ast.nodes.statement.ValueDefinition;
import mavlc.ast.nodes.statement.VariableAssignment;
import mavlc.ast.nodes.statement.VectorLHSIdentifier;
import mavlc.ast.type.BoolType;
import mavlc.ast.type.FloatType;
import mavlc.ast.type.IntType;
import mavlc.ast.type.MatrixType;
import mavlc.ast.type.PrimitiveType;
import mavlc.ast.type.ScalarType;
import mavlc.ast.type.StructType;
import mavlc.ast.type.Type;
import mavlc.ast.type.VectorType;
import mavlc.ast.visitor.ASTNodeBaseVisitor;
import mavlc.error_reporting.ConstantAssignmentError;
import mavlc.error_reporting.InapplicableOperationError;
import mavlc.error_reporting.MisplacedReturnError;
import mavlc.error_reporting.MissingMainFunctionError;
import mavlc.error_reporting.NonConstantOffsetError;
import mavlc.error_reporting.StructureDimensionError;
import mavlc.error_reporting.TypeError;

/**
 * A combined identifiation and type checking visitor.
 */
public class ContextualAnalysis extends ASTNodeBaseVisitor<Type, Boolean> {

	protected final ModuleEnvironment env;

	protected final IdentificationTable table;

	protected Function currentFunction;

	/**
	 * Constructor.
	 * 
	 * @param moduleEnvironment i.e. a simple identification table for a module's functions.
	 */
	public ContextualAnalysis(ModuleEnvironment moduleEnvironment){
		env = moduleEnvironment;
		table = new IdentificationTable();
	}

	private void checkType(ASTNode node, Type t1, Type t2){
		if(!t1.equals(t2)){
			throw new TypeError(node, t1, t2);
		}
	}

	@Override
	public Type visitModule(Module module, Boolean __) {
		boolean hasMain = false;
		for(Function function : module.getFunctions()){
			currentFunction = function;
			function.accept(this, null);
			if(isMainFunction(function)){
				hasMain = true;
			}
		}
		if(!hasMain){
			throw new MissingMainFunctionError();
		}

		return null;
	}

	private boolean isMainFunction(Function func){
		/*
		 * Signature of the main method is "function void main()"
		 */
		if(!func.getName().equals("main")){
			return false;
		}
		if(!func.getParameters().isEmpty()){
			return false;
		}
		if(!func.getReturnType().equals(Type.getVoidType())){
			return false;
		}
		return true;
	}

	@Override
	public Type visitFunction(Function functionNode, Boolean __) {
		table.openNewScope();
		for(FormalParameter param : functionNode.getParameters()){
			table.addIdentifier(param.getName(), param);
		}
		Iterator<Statement> it = functionNode.getFunctionBody().iterator();
		while(it.hasNext()){
			Statement stmt = it.next();
			if(!it.hasNext() && !functionNode.getReturnType().equals(Type.getVoidType())){
				/*
				 *  Last statement in a non-void function, the only location where 
				 *  a return statement is allowed
				 */
				stmt.accept(this, true);
			}
			else{
				stmt.accept(this, false);
			}
		}
		table.closeCurrentScope();
		return null;
	}

	@Override
	public Type visitFormalParameter(FormalParameter formalParameter, Boolean __) {
		return formalParameter.getType();
	}

	@Override
	public Type visitDeclaration(Declaration declaration, Boolean __) {
		if(declaration.getType() instanceof StructType){
			Type elemType = ((StructType) declaration.getType()).getElementType();
			if(!elemType.isScalarType()){
				throw new InapplicableOperationError(declaration, elemType, FloatType.class, IntType.class);
			}
		}
		table.addIdentifier(declaration.getName(), declaration);
		return null;
	}

	@Override
	public Type visitValueDefinition(ValueDefinition valueDefinition, Boolean __) {
		visitDeclaration(valueDefinition, null);
		Type lhs = valueDefinition.getType();
		Type rhs = valueDefinition.getValue().accept(this, null);
		checkType(valueDefinition, lhs, rhs);
		return null;
	}

	@Override
	public Type visitVariableAssignment(VariableAssignment variableAssignment, Boolean __) {
		Type lhs = variableAssignment.getIdentifier().accept(this, __);
		Type rhs = variableAssignment.getValue().accept(this, __);
		checkType(variableAssignment, lhs, rhs);

		return lhs;
	}

	@Override
	public Type visitLeftHandIdentifier(LeftHandIdentifier leftHandIdentifier, Boolean __) {
		Declaration dec = table.getDeclaration(leftHandIdentifier.getName());

		if (!dec.isVariable())
			throw new ConstantAssignmentError(leftHandIdentifier);

		String name = leftHandIdentifier.getName();
		leftHandIdentifier.setDeclaration(table.getDeclaration(name));

		return dec.getType();
	}

	@Override
	public Type visitMatrixLHSIdentifier(MatrixLHSIdentifier matrixLHSIdentifier, Boolean __) {
		Declaration d = table.getDeclaration(matrixLHSIdentifier.getName());
		
		if (!d.isVariable())
			throw new ConstantAssignmentError(matrixLHSIdentifier);
		
		Type x = matrixLHSIdentifier.getXIndex().accept(this, __);
		Type y = matrixLHSIdentifier.getYIndex().accept(this, __);
		
		checkType(matrixLHSIdentifier, x, IntType.getIntType());
		checkType(matrixLHSIdentifier, y, IntType.getIntType());
		
		matrixLHSIdentifier.setDeclaration(d);
		
		return d.getType();
	}

	@Override
	public Type visitVectorLHSIdentifier(VectorLHSIdentifier vectorLHSIdentifier, Boolean __) {
		Declaration d = table.getDeclaration(vectorLHSIdentifier.getName());
		
		if (!d.isVariable())
			throw new ConstantAssignmentError(vectorLHSIdentifier);
		
		Type i = vectorLHSIdentifier.getIndex().accept(this, __);
		
		checkType(vectorLHSIdentifier, i, IntType.getIntType());
		vectorLHSIdentifier.setDeclaration(d);
		
		return d.getType();
	}

	@Override
	public Type visitForLoop(ForLoop forLoop, Boolean __) {
		Declaration initVarDec = table.getDeclaration(forLoop.getInitVariableName());
		Expression check = forLoop.getCheck();
		Declaration incr = table.getDeclaration(forLoop.getIncrementVariableName());
		
		if (!initVarDec.isVariable() || !incr.isVariable())
			throw new ConstantAssignmentError(forLoop);
		
		check.accept(this, null);
		
		Expression incExpr = forLoop.getIncrementExpr();
		incExpr.accept(this, null);
		
		forLoop.setInitVarDeclaration(initVarDec);
		forLoop.setIncrVarDeclaration(incr);

		forLoop.getLoopBody().accept(this, null);

		return null;
	}

	@Override
	public Type visitIfStatement(IfStatement ifStatement, Boolean __) {
		Type testType = ifStatement.getTestExpression().accept(this, null);
		checkType(ifStatement, testType, Type.getBoolType());
		ifStatement.getThenStatement().accept(this, false);
		if(ifStatement.hasElseStatement()){
			ifStatement.getElseStatement().accept(this, false);
		}
		return null;
	}

	@Override
	public Type visitCallStatement(CallStatement callStatement, Boolean __) {
		return callStatement.getCall().accept(this, __);
	}

	@Override
	public Type visitReturnStatement(ReturnStatement returnStatement, Boolean returnAllowed) {
		if(!returnAllowed){
			throw new MisplacedReturnError(returnStatement);
		}
		Type retVal = returnStatement.getReturnValue().accept(this, null);
		checkType(returnStatement, retVal, currentFunction.getReturnType());
		return retVal;
	}

	@Override
	public Type visitCompoundStatement(CompoundStatement compoundStatement, Boolean __) {
		table.openNewScope();
		for (Statement s: compoundStatement.getStatements())
		{
			s.accept(this, __);
		}
		table.closeCurrentScope();
		return null;
	}

	@Override
	public Type visitMatrixMultiplication(MatrixMultiplication matrixMultiplication, Boolean __) {
		Type leftOp = matrixMultiplication.getLeftOp().accept(this, null);
		Type rightOp = matrixMultiplication.getRightOp().accept(this, null);
		
		if(!checkForArithmetic(leftOp)){
			throw new InapplicableOperationError(matrixMultiplication, leftOp, IntType.class, FloatType.class, MatrixType.class, VectorType.class);
		}
		
		if(!checkForArithmetic(rightOp)){
			throw new InapplicableOperationError(matrixMultiplication, rightOp, IntType.class, FloatType.class, MatrixType.class, VectorType.class);
		}
		
		matrixMultiplication.getLeftOp().setType(leftOp);
		matrixMultiplication.getRightOp().setType(rightOp);
		
		MatrixType lm = (MatrixType) leftOp;
		MatrixType rm = (MatrixType) rightOp;
		
		checkType(matrixMultiplication, lm.getElementType(), rm.getElementType());
		
		MatrixType res = new MatrixType(lm.getElementType(), lm.getxDimension(), rm.getyDimension());
		matrixMultiplication.setType(res);
		return res;
	}

	@Override
	public Type visitDotProduct(DotProduct dotProduct, Boolean __) {
		Type leftOp = dotProduct.getLeftOp().accept(this, null);
		Type rightOp = dotProduct.getRightOp().accept(this, null);
		PrimitiveType elementType = null;
		if(!(leftOp instanceof VectorType)){
			throw new InapplicableOperationError(dotProduct, leftOp, VectorType.class);
		}
		if(!(rightOp instanceof VectorType)){
			throw new InapplicableOperationError(dotProduct, rightOp, VectorType.class);
		}
		else{
			VectorType leftVec = (VectorType) leftOp;
			VectorType rightVec = (VectorType) rightOp;
			checkType(dotProduct, leftVec.getElementType(), rightVec.getElementType());
			if(leftVec.getDimension()!=rightVec.getDimension()){
				throw new StructureDimensionError(dotProduct, leftVec.getDimension(), rightVec.getDimension());
			}
			elementType = ((VectorType) leftOp).getElementType();
			dotProduct.setType(elementType);
			return elementType;
		}
	}
	
	private boolean checkForArithmetic (Type t)
	{
		if(t.isScalarType())
			return true;
		if (t instanceof MatrixType)
			return true;
		if (t instanceof VectorType)
			return true;
		
		return false;
	}

	private Type visitArithmeticalExpression(BinaryExpression exp, Boolean __)
	{
		Type leftOp = exp.getLeftOp().accept(this, null);
		Type rightOp = exp.getRightOp().accept(this, null);
		
		if(!checkForArithmetic(leftOp))
			throw new InapplicableOperationError(exp, leftOp, IntType.class, FloatType.class, MatrixType.class, VectorType.class);
		
		if(!checkForArithmetic(rightOp))
			throw new InapplicableOperationError(exp, rightOp, IntType.class, FloatType.class, MatrixType.class, VectorType.class);

		checkType(exp, leftOp, rightOp);

		exp.setType(leftOp);
		
		exp.getLeftOp().setType(leftOp);
		exp.getRightOp().setType(rightOp);

		return leftOp;
	}

	@Override
	public Type visitMultiplication(Multiplication multiplication, Boolean __) {
		return visitArithmeticalExpression(multiplication, __);
	}

	@Override
	public Type visitDivision(Division division, Boolean __) {
		Type leftOp = division.getLeftOp().accept(this, null);
		Type rightOp = division.getRightOp().accept(this, null);
		if(!leftOp.isScalarType()){
			throw new InapplicableOperationError(division, leftOp, FloatType.class, IntType.class);
		}
		if(!rightOp.isScalarType()){
			throw new InapplicableOperationError(division, rightOp, FloatType.class, IntType.class);
		}
		checkType(division, leftOp, rightOp);
		division.setType(leftOp);
		return leftOp;
	}

	@Override
	public Type visitAddition(Addition addition, Boolean __) {
		return visitArithmeticalExpression(addition, __);
	}

	@Override
	public Type visitSubtraction(Subtraction subtraction, Boolean __) {
		return visitArithmeticalExpression(subtraction, __);
	}

	@Override
	public Type visitCompare(Compare compare, Boolean __) {
		Type leftOp = compare.getLeftOp().accept(this, null);
		Type rightOp = compare.getRightOp().accept(this, null);
		if(!leftOp.isScalarType()){
			throw new InapplicableOperationError(compare, leftOp, FloatType.class, IntType.class);
		}
		if(!rightOp.isScalarType()){
			throw new InapplicableOperationError(compare, rightOp, FloatType.class, IntType.class);
		}
		checkType(compare, leftOp, rightOp);
		compare.setType(Type.getBoolType());
		return Type.getBoolType();
	}

	@Override
	public Type visitAnd(And and, Boolean __) {
		return visitBooleanExpression(and, null);
	}

	@Override
	public Type visitOr(Or or, Boolean __) {
		return visitBooleanExpression(or, null);
	}

	private Type visitBooleanExpression(BinaryExpression exp, Boolean __){
		Type leftOp = exp.getLeftOp().accept(this, null);
		Type rightOp = exp.getRightOp().accept(this, null);
		if(!(leftOp instanceof BoolType)){
			throw new InapplicableOperationError(exp, leftOp, BoolType.class);
		}
		if(!(rightOp instanceof BoolType)){
			throw new InapplicableOperationError(exp, rightOp, BoolType.class);
		}
		exp.setType(Type.getBoolType());
		return Type.getBoolType();
	}

	@Override
	public Type visitMatrixXDimension(MatrixXDimension xDimension, Boolean __) {
		Type opType = xDimension.getOperand().accept(this, null);
		if(!(opType instanceof MatrixType)){
			throw new InapplicableOperationError(xDimension, opType, MatrixType.class);
		}
		xDimension.setType(Type.getIntType());
		return Type.getIntType();
	}

	@Override
	public Type visitMatrixYDimension(MatrixYDimension yDimension, Boolean __) {
		Type opType = yDimension.getOperand().accept(this, null);
		if(!(opType instanceof MatrixType)){
			throw new InapplicableOperationError(yDimension, opType, MatrixType.class);
		}
		yDimension.setType(Type.getIntType());
		return Type.getIntType();
	}

	@Override
	public Type visitVectorDimension(VectorDimension vectorDimension, Boolean __) {
		Type opType = vectorDimension.getOperand().accept(this, null);
		if(!(opType instanceof VectorType)){
			throw new InapplicableOperationError(vectorDimension, opType, VectorType.class);
		}
		vectorDimension.setType(Type.getIntType());
		return Type.getIntType();
	}

	@Override
	public Type visitUnaryMinus(UnaryMinus unaryMinus, Boolean __) {
		Type opType = unaryMinus.getOperand().accept(this, null);
		if(!opType.isScalarType()){
			throw new InapplicableOperationError(unaryMinus, opType, FloatType.class, IntType.class);
		}
		unaryMinus.setType(opType);
		return opType;
	}

	@Override
	public Type visitBoolNot(BoolNot boolNot, Boolean __) {
		Type opType = boolNot.getOperand().accept(this, null);
		checkType(boolNot, opType, Type.getBoolType());
		boolNot.setType(Type.getBoolType());
		return Type.getBoolType();
	}

	@Override
	public Type visitCallExpression(CallExpression callExpression, Boolean __) {
		Function f = env.getFunctionDeclaration(callExpression.getCalleeName());
		callExpression.setCalleeDefinition(f);
		callExpression.setType(f.getReturnType());

		return f.getReturnType();
	}

	@Override
	public Type visitElementSelect(ElementSelect elementSelect, Boolean __) {
		Type baseType = elementSelect.getStruct().accept(this, null);
		if(!(baseType instanceof StructType)){
			throw new InapplicableOperationError(elementSelect, baseType, MatrixType.class, VectorType.class);
		}
		Type indexType = elementSelect.getIndex().accept(this, null);
		if(!indexType.equals(Type.getIntType())){
			throw new TypeError(elementSelect, indexType, IntType.getIntType());
		}
		if(baseType instanceof VectorType){
			Type resultType = ((VectorType) baseType).getElementType();
			elementSelect.setType(resultType);
			return resultType;
		}
		else if(baseType instanceof MatrixType){
			ScalarType elementType = ((MatrixType) baseType).getElementType();
			int size = ((MatrixType) baseType).getyDimension();
			Type resultType = new VectorType(elementType, size);
			elementSelect.setType(resultType);
			return resultType;
		}
		return null;
	}

	@Override
	public Type visitSubMatrix(SubMatrix subMatrix, Boolean __) {
		int xLB = getOffSet(subMatrix.getXStartIndex());
		int xUB = getOffSet(subMatrix.getXEndIndex());
		int yLB = getOffSet(subMatrix.getYStartIndex());
		int yUB = getOffSet(subMatrix.getYEndIndex());
		Type xIndex = subMatrix.getXBaseIndex().accept(this, null);
		checkType(subMatrix, xIndex, Type.getIntType());
		Type yIndex = subMatrix.getYBaseIndex().accept(this, null);
		checkType(subMatrix, yIndex, Type.getIntType());
		Type baseType = subMatrix.getStruct().accept(this, null);
		if(!(baseType instanceof MatrixType)){
			throw new InapplicableOperationError(subMatrix, baseType, MatrixType.class);
		}
		MatrixType matrix = (MatrixType) baseType;
		if(xUB < xLB){
			throw new StructureDimensionError(subMatrix, xUB, xLB);
		}
		int xSize = xUB - xLB + 1;
		if(matrix.getxDimension()<xSize){
			throw new StructureDimensionError(subMatrix, matrix.getxDimension(), xSize);
		}
		if(yUB < yLB){
			throw new StructureDimensionError(subMatrix, yUB, yLB);
		}
		int ySize = yUB - yLB + 1;
		if(matrix.getyDimension()<ySize){
			throw new StructureDimensionError(subMatrix, matrix.getyDimension(), ySize);
		}
		Type resultType = new MatrixType(((MatrixType) baseType).getElementType(), xSize, ySize);
		subMatrix.setType(resultType);
		return resultType;
	}

	@Override
	public Type visitSubVector(SubVector subVector, Boolean __) {
		int lb = getOffSet(subVector.getStartIndex());
		int ub = getOffSet(subVector.getEndIndex());
		Type indexType = subVector.getBaseIndex().accept(this, null);
		checkType(subVector, indexType, Type.getIntType());
		Type baseType = subVector.getStruct().accept(this, null);
		if(!(baseType instanceof VectorType)){
			throw new InapplicableOperationError(subVector, baseType, VectorType.class);
		}
		VectorType vector = (VectorType) baseType;
		if(ub < lb){
			throw new StructureDimensionError(subVector, ub, lb);
		}
		int size = ub-lb+1;
		if(vector.getDimension()<size){
			throw new StructureDimensionError(subVector, vector.getDimension(), size);
		}
		Type resultType = new VectorType(((VectorType) baseType).getElementType(), size);
		subVector.setType(resultType);
		return resultType;
	}

	private int getOffSet(Expression offset){
		if(offset instanceof IntValue){
			offset.accept(this, null);
			return ((IntValue) offset).getValue();
		}
		else if(offset instanceof UnaryMinus){
			if(((UnaryMinus) offset).getOperand() instanceof IntValue){
				offset.accept(this, null);
				return -1* ((IntValue) ((UnaryMinus) offset).getOperand()).getValue();
			}
		}
		throw new NonConstantOffsetError(offset);
	}

	@Override
	public Type visitStructureInit(StructureInit structureInit, Boolean __) {
		// The type of the first element determines the structure
		Type firstElem = structureInit.getElements().iterator().next().accept(this, null);
		if(firstElem instanceof VectorType){
			// Matrix init
			ScalarType elemType = ((VectorType) firstElem).getElementType();
			int size = ((VectorType) firstElem).getDimension();
			int x = 0;
			for(Expression element : structureInit.getElements()){
				Type t = element.accept(this, null);
				checkType(structureInit, firstElem, t);
				++x;
			}
			MatrixType resultType = new MatrixType(elemType, x, size);
			structureInit.setType(resultType);
			return resultType;
		}
		else{
			// Vector init
			if(!firstElem.isScalarType()){
				throw new InapplicableOperationError(structureInit, firstElem, FloatType.class, IntType.class);
			}
			ScalarType elemType = (ScalarType) firstElem;
			int size=0;
			for(Expression element : structureInit.getElements()){
				Type t = element.accept(this, null);
				checkType(structureInit, elemType, t);
				++size;
			}
			VectorType resultType = new VectorType(elemType, size);
			structureInit.setType(resultType);
			return resultType;
		}
	}

	@Override
	public Type visitStringValue(StringValue stringValue, Boolean __) {
		return Type.getStringType();
	}

	@Override
	public Type visitBoolValue(BoolValue boolValue, Boolean __) {
		return Type.getBoolType();
	}

	@Override
	public Type visitIntValue(IntValue intValue, Boolean __) {
		return Type.getIntType();
	}

	@Override
	public Type visitFloatValue(FloatValue floatValue, Boolean __) {
		return Type.getFloatType();
	}

	@Override
	public Type visitIdentifierReference(IdentifierReference identifierReference, Boolean __) {
		Declaration decl = table.getDeclaration(identifierReference.getIdentifierName());
		identifierReference.setDeclaration(decl);
		identifierReference.setType(decl.getType());
		return decl.getType();
	}


}
