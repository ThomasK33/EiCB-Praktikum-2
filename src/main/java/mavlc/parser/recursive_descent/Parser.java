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
package mavlc.parser.recursive_descent;

import mavlc.ast.nodes.expression.*;
import mavlc.ast.nodes.function.FormalParameter;
import mavlc.ast.nodes.function.Function;
import mavlc.ast.nodes.module.Module;
import mavlc.ast.nodes.statement.*;
import mavlc.ast.type.*;
import mavlc.context_analysis.ModuleEnvironment;
import mavlc.error_reporting.SyntaxError;

import java.util.*;

import mavlc.parser.recursive_descent.Token.TokenType;
import static mavlc.ast.nodes.expression.Compare.Comparison.*;
import static mavlc.parser.recursive_descent.Token.TokenType.*;

/**
 * A recursive-descent parser for MAVL.
 */
public final class Parser {

	private final Deque<Token> tokens;
	private Token currentToken;

	/**
	 * Constructor.
	 * 
	 * @param tokens A token stream that was produced by the {@link Scanner}.
	 */
	public Parser(Deque<Token> tokens) {
		this.tokens = tokens;
		currentToken = tokens.poll();
	}

	/**
	 * Parses the MAVL grammar's start symbol, Module.
	 * 
	 * @param env A {@link ModuleEnvironment} that registers the recognized functions and will be passed to the contextual analyzer. 
	 * @return A {@link Module} node that is the root of the AST representing the tokenized input progam. 
	 * @throws SyntaxError to indicate that an unexpected token was encountered.
	 */
	public Module parse(ModuleEnvironment env) throws SyntaxError {
		Module compilationUnit = new Module(tokens.peek().line, 0);
		while (currentToken.type != EOF) {
			Function func = parseFunction();
			compilationUnit.addFunction(func);
			env.addFunction(func);
		}
		return compilationUnit;
	}

	private String accept(TokenType type) throws SyntaxError {
		Token t = currentToken;
		if (t.type != type)
			throw new SyntaxError(t, type);
		acceptIt();
		return t.spelling;
	}

	private void acceptIt() {
		currentToken = tokens.poll();
		if (currentToken.type == ERROR)
			throw new SyntaxError(currentToken);
	}

	private Function parseFunction() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		accept(FUNCTION);
		Type type = parseType();
		String name = accept(ID);

		Function function = new Function(line, column, name, type);

		accept(LPAREN);
		if (currentToken.type != RPAREN) {
			function.addParameter(parseFormalParameter());
			while (currentToken.type != RPAREN) {
				accept(COMMA);
				function.addParameter(parseFormalParameter());
			}
		}
		accept(RPAREN);

		accept(LBRACE);
		while (currentToken.type != RBRACE)
			function.addStatement(parseStatement());
		accept(RBRACE);

		return function;
	}

	private FormalParameter parseFormalParameter() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Type type = parseType();
		String name = accept(ID);
		
		return new FormalParameter(line, column, name, type);
	}

	private Type parseType() throws SyntaxError {
		boolean vector = false;
		switch (currentToken.type) {
		case INT:    acceptIt(); return Type.getIntType();
		case FLOAT:  acceptIt(); return Type.getFloatType();
		case BOOL:   acceptIt(); return Type.getBoolType();
		case VOID:   acceptIt(); return Type.getVoidType();
		case STRING: acceptIt(); return Type.getStringType();
		case VECTOR: accept(VECTOR); vector = true; break;
		case MATRIX: accept(MATRIX); break;
		default:
			throw new SyntaxError(currentToken, INT, FLOAT, BOOL, VOID, STRING, VECTOR, MATRIX);
		}

		accept(LANGLE);
		ScalarType subtype = null;
		switch (currentToken.type) {
		case INT:   subtype = Type.getIntType(); break;
		case FLOAT: subtype = Type.getFloatType(); break;
		default:
			throw new SyntaxError(currentToken, INT, FLOAT);
		}
		acceptIt();
		accept(RANGLE);
		accept(LBRACKET);
		int x = parseIntLit();
		accept(RBRACKET);

		if (vector)
			return new VectorType(subtype, x);

		accept(LBRACKET);
		int y = parseIntLit();
		accept(RBRACKET);

		return new MatrixType(subtype, x, y);
	}

	private Statement parseStatement() throws SyntaxError {
		Statement s = null;
		switch (currentToken.type) {
		case VAL:    s = parseValueDef();     break;
		case VAR:    s = parseVarDecl();      break;
		case RETURN: s = parseReturn();       break;
		case ID:     s = parseAssignOrCall(); break;
		case FOR:    s = parseFor();          break;
		case IF:     s = parseIf();           break;
		case LBRACE: s = parseCompound();     break;
		default:
			throw new SyntaxError(currentToken, VAL, VAR, FOR, IF, RETURN, LBRACE, ID);
		}

		return s;
	}

	private ValueDefinition parseValueDef() throws SyntaxError {
		/*
		 * Delete for P1
		 */
		int line = currentToken.line;
		int column = currentToken.column;

		accept(VAL);
		Type t = parseType();
		String s = accept(ID);
		accept(ASSIGN);
		Expression e = parseExpr();
		accept(SEMICOLON);
		
		return new ValueDefinition(line, column, t, s, e);
	}

	private VariableDeclaration parseVarDecl() throws SyntaxError {
		/*
		 * Delete for P1
		 */
		int line = currentToken.line;
		int column = currentToken.column;

		accept(VAR);
		Type t = parseType();
		String s = accept(ID);
		accept(SEMICOLON);
		
		return new VariableDeclaration(line, column, t, s);
	}

	private ReturnStatement parseReturn() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;
		accept(RETURN);
		Expression e = parseExpr();
		accept(SEMICOLON);
		
		return new ReturnStatement(line, column, e);
	}

	private Statement parseAssignOrCall() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		String name = accept(ID);

		Statement s;
		if (currentToken.type != LPAREN)
			s = parseAssign(name, line, column);
		else
			s = new CallStatement(line, column, parseCall(name, line, column));
		accept(SEMICOLON);
		
		return s;
	}

	private VariableAssignment parseAssign(String name, int line, int column) throws SyntaxError {
		/*
		 * Delete for P1
		 */
		LeftHandIdentifier lhi = new LeftHandIdentifier(line, column, name);

		if (currentToken.type == LBRACKET) {
			acceptIt();
			Expression xIndex = parseExpr();
			lhi = new VectorLHSIdentifier(line, column, name, xIndex);
			accept(RBRACKET);

			if (currentToken.type == LBRACKET) {
				acceptIt();
				Expression yIndex = parseExpr();
				lhi = new MatrixLHSIdentifier(line, column, name, xIndex, yIndex);
				accept(RBRACKET);
			}
		}

		accept(ASSIGN);
		Expression value = parseExpr();
		
		return new VariableAssignment(line, column, lhi, value);
	}
	
	private CallExpression parseCall(String name, int line, int column) {
		CallExpression callExpression = new CallExpression(line, column, name);
		accept(LPAREN);
		if (currentToken.type != RPAREN) {
			callExpression.addActualParameter(parseExpr());
			while (currentToken.type != RPAREN) {
				accept(COMMA);
				callExpression.addActualParameter(parseExpr());
			}
		}
		accept(RPAREN);
		
		return callExpression;
	}

	private ForLoop parseFor() throws SyntaxError {
		/*
		 * Delete for P1
		 */
		int line = currentToken.line;
		int column = currentToken.column;

		accept(FOR);
		accept(LPAREN);
		String name = accept(ID);
		accept(ASSIGN);
		Expression a = parseExpr();
		accept(SEMICOLON);
		Expression b = parseExpr();
		accept(SEMICOLON);
		String inc = accept(ID);
		accept(ASSIGN);
		Expression c = parseExpr();
		accept(RPAREN);
		return new ForLoop(line, column, name, a, b, inc, c, parseStatement());
	}

	private IfStatement parseIf() throws SyntaxError {
		/*
		 * Delete for P1
		 */
		int line = currentToken.line;
		int column = currentToken.column;
		accept(IF);
		accept(LPAREN);
		Expression test = parseExpr();
		accept(RPAREN);
		Statement then = parseStatement();
		if (currentToken.type == ELSE) {
			acceptIt();
			return new IfStatement(line, column, test, then, parseStatement());
		}
		return new IfStatement(line, column, test, then);
	}

	private CompoundStatement parseCompound() throws SyntaxError {
		/*
		 * Delete for P1
		 */
		CompoundStatement c = new CompoundStatement(currentToken.line, currentToken.column);
		accept(LBRACE);
		while (currentToken.type != RBRACE)
			c.addStatement(parseStatement());
		accept(RBRACE);
		return c;
	}

	private Expression parseExpr() throws SyntaxError {
		return parseOr();
	}
	
	private Expression parseOr() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseAnd();
		while (currentToken.type == OR) {
			acceptIt();
			x = new Or(line, column, x, parseAnd());
		}
		return x;
	}

	private Expression parseAnd() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseNot();
		while (currentToken.type == AND) {
			acceptIt();
			x = new And(line, column, x, parseNot());
		}
		return x;
	}

	private Expression parseNot() throws SyntaxError {
		/*
		 * Delete calls to xparseCompare for P1
		 */
		int line = currentToken.line;
		int column = currentToken.column;
		
		if (currentToken.type == NOT) {
			acceptIt();
			return new BoolNot(line, column, parseCompare());
		}
		return parseCompare();
	}

	private Expression parseCompare() throws SyntaxError {
		/*
		 * Delete for P1
		 */
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseAddSub();

		List<TokenType> list = Arrays.asList(RANGLE, LANGLE, CMPLE, CMPGE, CMPEQ, CMPNE);

		while (list.contains(currentToken.type)) {
			TokenType type = currentToken.type;
			acceptIt();
			switch (type) {
			case RANGLE: x = new Compare(line, column, x, parseAddSub(), GREATER);       break;
			case LANGLE: x = new Compare(line, column, x, parseAddSub(), LESS);          break;
			case CMPLE:  x = new Compare(line, column, x, parseAddSub(), LESS_EQUAL);    break;
			case CMPGE:  x = new Compare(line, column, x, parseAddSub(), GREATER_EQUAL); break;
			case CMPEQ:  x = new Compare(line, column, x, parseAddSub(), EQUAL);         break;
			case CMPNE:  x = new Compare(line, column, x, parseAddSub(), NOT_EQUAL);     break;
			default: /* unreachable */
			}
		}
		return x;
	}

	private Expression parseAddSub() throws SyntaxError {
		/*
		 * Delete for P1
		 */
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseMulDiv();
		while (currentToken.type == ADD || currentToken.type == SUB) {
			TokenType type = currentToken.type;
			acceptIt();
			if (type == ADD) {
				x = new Addition(line, column, x, parseMulDiv());
			} else {
				x = new Subtraction(line, column, x, parseMulDiv());
			}
		}
		return x;

	}

	private Expression parseMulDiv() throws SyntaxError {
		/*
		 * Delete for P1
		 */
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseUnaryMinus();
		while (currentToken.type == MULT || currentToken.type == DIV) {
			TokenType type = currentToken.type;
			acceptIt();
			if (type == MULT)
				x = new Multiplication(line, column, x, parseUnaryMinus());
			else
				x = new Division(line, column, x, parseUnaryMinus());
		}
		return x;
	}

	private Expression parseUnaryMinus() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		if (currentToken.type == SUB) {
			acceptIt();
			return new UnaryMinus(line, column, parseDim());
		} else {
			return parseDim();
		}
	}

	private Expression parseDim() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseElementSelect();
		switch (currentToken.type) {
		case XDIM: acceptIt(); return new MatrixXDimension(line, column, x);
		case YDIM: acceptIt(); return new MatrixYDimension(line, column, x);
		case DIM:  acceptIt(); return new VectorDimension(line, column, x);
		default:
			return x;
		}
	}

	private Expression parseElementSelect() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseDotProd();

		while (currentToken.type == LBRACKET) {
			acceptIt();
			Expression idx = parseExpr();
			accept(RBRACKET);
			x = new ElementSelect(line, column, x, idx);
		}

		return x;
	}

	private Expression parseDotProd() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseMatrixMul();
		while (currentToken.type == DOTPROD) {
			acceptIt();
			x = new DotProduct(line, column, x, parseMatrixMul());
		}
		
		return x;
	}

	private Expression parseMatrixMul() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseSubrange();
		while (currentToken.type == MATMULT) {
			acceptIt();
			x = new MatrixMultiplication(line, column, x, parseSubrange());
		}
		return x;

	}

	private Expression parseSubrange() throws SyntaxError {
		/*
		 * Delete for P1
		 */
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseAtom();

		if (currentToken.type == LBRACE) {
			acceptIt();
			Expression xStartIndex = parseExpr();
			accept(COLON);
			Expression xBaseIndex = parseExpr();
			accept(COLON);
			Expression xEndIndex = parseExpr();
			accept(RBRACE);
			if (currentToken.type != LBRACE)
				return new SubVector(line, column, x, xBaseIndex, xStartIndex, xEndIndex);

			accept(LBRACE);
			Expression yStartIndex = parseExpr();
			accept(COLON);
			Expression yBaseIndex = parseExpr();
			accept(COLON);
			Expression yEndIndex = parseExpr();
			accept(RBRACE);
			return new SubMatrix(line, column, x, xBaseIndex, xStartIndex, xEndIndex, yBaseIndex, yStartIndex, yEndIndex);
		}

		return x;
	}

	private Expression parseAtom() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		switch (currentToken.type) {
		case INTLIT:    return new IntValue(line, column, parseIntLit());
		case FLOATLIT:  return new FloatValue(line, column, parseFloatLit());
		case BOOLLIT:   return new BoolValue(line, column, parseBoolLit());
		case STRINGLIT: return new StringValue(line, column, accept(STRINGLIT));
		default: /* check other cases below */
		}

		if (currentToken.type == ID) {
			String name = accept(ID);
			if (currentToken.type != LPAREN)
				return new IdentifierReference(line, column, name);
			else
				return parseCall(name, line, column);
		}

		if (currentToken.type == LPAREN) {
			acceptIt();
			Expression x = parseExpr();
			accept(RPAREN);
			return x;
		}

		if (currentToken.type == LBRACKET) {
			acceptIt();
			StructureInit s = new StructureInit(line, column);
			s.addElement(parseExpr());
			while (currentToken.type == COMMA) {
				accept(COMMA);
				s.addElement(parseExpr());
			}
			accept(RBRACKET);
			return s;
		}

		throw new SyntaxError(currentToken, INTLIT, FLOATLIT, BOOLLIT, STRINGLIT, ID, LPAREN, LBRACKET);
	}

	private int parseIntLit() throws SyntaxError {
		String s = accept(INTLIT);
		return Integer.parseInt(s);
	}

	private float parseFloatLit() throws SyntaxError {
		return Float.parseFloat(accept(FLOATLIT));
	}

	private boolean parseBoolLit() throws SyntaxError {
		return Boolean.parseBoolean(accept(BOOLLIT));
	}
}
