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

import mavlc.ast.nodes.statement.Declaration;

/**
 * A table for identifiers used inside a function.
 */
public class IdentificationTable {
	
	private Scope scope = new Scope(null);
	
	/**
	 * Declares the given identifier in the current scope.
	 * 
	 * @param name the identifier to declare
	 * @param declaration the reference to the identifier's declaration site
	 */
	public void addIdentifier(String name, Declaration declaration){
		scope.addIdentifier(name, declaration);
	}
	
	/**
	 * Looks up the innermost declaration of the given identifier.
	 * 
	 * @param name the identifier to look up
	 * @return the identifier's innermost declaration site
	 */
	public Declaration getDeclaration(String name){
		return scope.getDeclaration(name);
	}
	
	/**
	 * Opens a new scope.
	 */
	public void openNewScope(){
		scope = new Scope(scope);
	}
	
	/**
	 * Closes the current scope.
	 */
	public void closeCurrentScope(){
		scope = scope.getParentScope();
	}

}
