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
package mavlc.context_analysis;

import java.util.HashMap;
import java.util.Map;

import mavlc.ast.nodes.function.FormalParameter;
import mavlc.ast.nodes.function.Function;
import mavlc.ast.type.MatrixType;
import mavlc.ast.type.Type;

/**
 * Dummy declarations of all functions defined in the 
 * standard runtime environment.
 */
public class RuntimeFunctions {
	
	public static Map<String, Function> getRuntimeFunctions(){
		Map<String, Function> runtimeFunctions = new HashMap<>();
		MatrixType matrix512 = new MatrixType(Type.getIntType(), 512, 512);
		/*
		 * WriteImage function
		 */
		Function writeImage = new Function(0,0, "writeImage", Type.getVoidType());
		writeImage.addParameter(new FormalParameter(0, 5, "resultName", Type.getStringType()));
		writeImage.addParameter(new FormalParameter(0, 10, "image", matrix512));
		runtimeFunctions.put("writeImage", writeImage);
		/*
		 * ReadImage function
		 */
		Function readImage = new Function(1,0, "readImage", matrix512);
		readImage.addParameter(new FormalParameter(1,5, "fileName", Type.getStringType()));
		runtimeFunctions.put("readImage", readImage);
		
		/*
		 * Pow functions
		 */
		Function powF = new Function(2, 0, "powFloat", Type.getFloatType());
		powF.addParameter(new FormalParameter(2, 5, "base", Type.getFloatType()));
		powF.addParameter(new FormalParameter(2, 10, "exp", Type.getFloatType()));
		runtimeFunctions.put("powFloat", powF);
		Function powI = new Function(3, 0, "powInt", Type.getIntType());
		powI.addParameter(new FormalParameter(3, 5, "base", Type.getIntType()));
		powI.addParameter(new FormalParameter(3, 10, "exp", Type.getIntType()));
		runtimeFunctions.put("powInt", powI);
		
		/*
		 * Sqrt functions
		 */
		Function sqrtF = new Function(4, 0, "sqrtFloat", Type.getFloatType());
		sqrtF.addParameter(new FormalParameter(4, 5, "op", Type.getFloatType()));
		runtimeFunctions.put("sqrtFloat", sqrtF);
		Function sqrtI = new Function(5, 0, "sqrtInt", Type.getIntType());
		sqrtI.addParameter(new FormalParameter(5, 5, "op", Type.getIntType()));
		runtimeFunctions.put("sqrtInt", sqrtI);
		
		return runtimeFunctions;
	}

}
