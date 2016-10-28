package org.dllearner.tools.protege;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLEntity;

/**
 * An exception that indicates the lack of instance data needed for learning axioms.
 *
 * @author Lorenz Buehmann
 */
public class NoInstanceDataException extends Throwable {

	private final OWLEntity entity;
	private final AxiomType axiomType;

	public NoInstanceDataException(OWLEntity entity, AxiomType axiomType) {
		this.entity = entity;
		this.axiomType = axiomType;
	}
}
