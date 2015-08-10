package org.dllearner.tools.protege;

import static org.semanticweb.owlapi.model.AxiomType.DATA_PROPERTY_DOMAIN;
import static org.semanticweb.owlapi.model.AxiomType.EQUIVALENT_CLASSES;
import static org.semanticweb.owlapi.model.AxiomType.OBJECT_PROPERTY_DOMAIN;
import static org.semanticweb.owlapi.model.AxiomType.OBJECT_PROPERTY_RANGE;
import static org.semanticweb.owlapi.model.AxiomType.SUBCLASS_OF;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.algorithms.properties.AxiomAlgorithms;
import org.dllearner.core.AbstractAxiomLearningAlgorithm;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.EvaluatedAxiom;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.core.KnowledgeSource;
import org.dllearner.kb.LocalModelBasedSparqlEndpointKS;
import org.dllearner.kb.OWLAPIOntology;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.learningproblems.ClassLearningProblem;
import org.dllearner.learningproblems.EvaluatedDescriptionClass;
import org.dllearner.reasoning.ClosedWorldReasoner;
import org.dllearner.reasoning.OWLAPIReasoner;
import org.dllearner.refinementoperators.RhoDRDown;
import org.dllearner.utilities.OWLAPIUtils;
import org.dllearner.utilities.OwlApiJenaUtils;
import org.protege.editor.core.Disposable;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.event.EventType;
import org.protege.editor.owl.model.event.OWLModelManagerChangeEvent;
import org.protege.editor.owl.model.event.OWLModelManagerListener;
import org.protege.editor.owl.model.selection.OWLSelectionModelListener;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.ReasonerProgressMonitor;

import com.hp.hpl.jena.rdf.model.Model;

public class Manager implements OWLModelManagerListener, OWLSelectionModelListener, Disposable{
	
	private static final int MIN_NR_OF_INDIVIDUALS = 0;

	private static Manager instance;
	
	private OWLEditorKit editorKit;
	private ReasonerProgressMonitor progressMonitor;
	
	private boolean reinitNecessary = true;
	
	private ClassLearningProblem lp;
	private CELOE la;
	private ClosedWorldReasoner reasoner;
	private KnowledgeSource ks;
	
	private LearningType learningType;
	private int maxExecutionTimeInSeconds;
	private double noisePercentage;
	private double threshold;
	private int maxNrOfResults;
	private boolean useAllConstructor;
	private boolean useExistsConstructor;
	private boolean useHasValueConstructor;
	private boolean useNegation;
	private boolean useCardinalityRestrictions;
	private int cardinalityLimit;
	
	private volatile boolean isPreparing = false;

	private AbstractAxiomLearningAlgorithm alg;
	private OWLEntity entity;
	private AxiomType axiomType;
	
	public static synchronized Manager getInstance(OWLEditorKit editorKit){
		if(instance == null){
			instance = new Manager(editorKit);
		}
		return instance;
	}
	
	public static synchronized Manager getInstance(){
		return instance;
	}
	
	private Manager(OWLEditorKit editorKit){
		this.editorKit = editorKit;
	}
	
	public void setOWLEditorKit(OWLEditorKit editorKit){
		this.editorKit = editorKit;
	}

	public boolean isReinitNecessary(){
		return reinitNecessary;
	}
	
	public void init() throws Exception{
		initKnowledgeSource();
		if(reinitNecessary){
			initReasoner();
		}
		initLearningProblem();
		initLearningAlgorithm();
		reinitNecessary = false;
	}
	
	/**
	 * @param axiomType the axiomType to set
	 */
	public void setAxiomType(AxiomType axiomType) {
		this.axiomType = axiomType;
	}
	
	/**
	 * @param entity the entity to set
	 */
	public void setEntity(OWLEntity entity) {
		this.entity = entity;
	}
	
	public List<EvaluatedAxiom<OWLAxiom>> computeAxioms(OWLEntity entity, AxiomType axiomType) {
		System.out.print("Started learning of " + 
				axiomType.getName() + " axioms for " + 
				OWLAPIUtils.getPrintName(entity.getEntityType()) 
				+ " " + editorKit.getOWLModelManager().getRendering(entity) + "...");
		
		try {
			Class<? extends AbstractAxiomLearningAlgorithm<? extends OWLAxiom, ? extends OWLObject, ? extends OWLEntity>> algorithmClass = AxiomAlgorithms.getAlgorithmClass(axiomType);
			
			OWLOntology ontology = editorKit.getWorkspace().getOWLModelManager().getActiveOntology();
			Model model = OwlApiJenaUtils.getModel(ontology);
			LocalModelBasedSparqlEndpointKS ks = new LocalModelBasedSparqlEndpointKS(model);
			alg = algorithmClass.getConstructor(SparqlEndpointKS.class).newInstance(ks);
			alg.setEntityToDescribe(entity);
			alg.setUseSampling(false);
//			alg.setProgressMonitor(progressMonitor);
			alg.init();
			alg.start();
			System.out.println("done.");
			return alg.getCurrentlyBestEvaluatedAxioms();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		} catch (ComponentInitException e) {
			e.printStackTrace();
		} catch(Error e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public List<EvaluatedAxiom<OWLAxiom>> getCurrentlyBestEvaluatedAxioms() {
		return alg.getCurrentlyBestEvaluatedAxioms();
	}
	
	public void initLearningAlgorithm() throws Exception {
		try {
			System.out.print("Initializing learning algorithm...");
			long startTime = System.currentTimeMillis();
			la = new CELOE(lp, reasoner);
			
			RhoDRDown op = new RhoDRDown();
			op.setReasoner(reasoner);
			op.setUseNegation(useNegation);
			op.setUseHasValueConstructor(useAllConstructor);
			op.setUseCardinalityRestrictions(useCardinalityRestrictions);
			if(useCardinalityRestrictions){
				op.setCardinalityLimit(cardinalityLimit);
			}
			op.setUseExistsConstructor(useExistsConstructor);
			op.setUseHasValueConstructor(useHasValueConstructor);
			op.init();
			
			la.setOperator(op);
			
			la.setMaxExecutionTimeInSeconds(maxExecutionTimeInSeconds);
			la.setNoisePercentage(noisePercentage);
			la.setMaxNrOfResults(maxNrOfResults);

			la.init();
			System.out.println("done in " + (System.currentTimeMillis()-startTime) + "ms.");
		} catch (Error e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public void initLearningProblem() throws Exception {
		System.out.print("Initializing learning problem...");
		long startTime = System.currentTimeMillis();
		
		lp = new ClassLearningProblem(reasoner);
		lp.setClassToDescribe(editorKit.getOWLWorkspace().getOWLSelectionModel().getLastSelectedClass());
		lp.setEquivalence(axiomType.equals(EQUIVALENT_CLASSES));
		lp.setCheckConsistency(DLLearnerPreferences.getInstance().isCheckConsistencyWhileLearning());
		lp.init();
		
		System.out.println("done in " + (System.currentTimeMillis()-startTime) + "ms.");
	}
	
	public void initKnowledgeSource() throws Exception{
		ks = new OWLAPIOntology(editorKit.getOWLModelManager().getActiveOntology());
		ks.init();
	}
	
	public void initReasoner() throws Exception{
		System.out.print("Initializing DL-Learner internal reasoner...");
		long startTime = System.currentTimeMillis();
		
		// base reasoner
		OWLAPIReasoner baseReasoner = new OWLAPIReasoner(editorKit.getOWLModelManager().getReasoner());
		baseReasoner.init();
		
		// closed world reasoner
		reasoner = new ClosedWorldReasoner(Collections.singleton(ks));
		reasoner.setReasonerComponent(baseReasoner);
//		reasoner.setProgressMonitor(progressMonitor);TODO integrate progress monitor
		reasoner.init();
		reinitNecessary = false;
		System.out.println("done in " + (System.currentTimeMillis()-startTime) + "ms.");
	}
	
	public void initReasonerAsynchronously(){
		reasoner = new ClosedWorldReasoner(Collections.singleton(ks));
		OWLAPIReasoner baseReasoner = new OWLAPIReasoner(editorKit.getOWLModelManager().getReasoner());
		reasoner.setReasonerComponent(baseReasoner);
//		reasoner.setProgressMonitor(progressMonitor);TODO integrate progress monitor
		
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					reasoner.init();
				} catch (ComponentInitException e) {
					e.printStackTrace();
				}
			}
		});
		t.start();
	}
	
	public void addAxiom(OWLAxiom axiom) {
		OWLOntology ontology = editorKit.getOWLModelManager().getActiveOntology();
		editorKit.getOWLModelManager().applyChange(new AddAxiom(ontology, axiom));
	}
	
	public void addAxiom(EvaluatedDescription<? extends OWLClassExpression> evaluatedDescription){
		OWLClass selectedClass = editorKit.getOWLWorkspace().getOWLSelectionModel().getLastSelectedClass();
		
		OWLDataFactory df = editorKit.getOWLModelManager().getOWLDataFactory();
		OWLAxiom axiom;
		if(axiomType.equals(SUBCLASS_OF)) {
			axiom = df.getOWLSubClassOfAxiom(selectedClass, evaluatedDescription.getDescription());
		} else if(axiomType.equals(EQUIVALENT_CLASSES)) {
			axiom = df.getOWLEquivalentClassesAxiom(selectedClass, evaluatedDescription.getDescription());
		} else if(axiomType.equals(OBJECT_PROPERTY_DOMAIN)) {
			axiom = df.getOWLObjectPropertyDomainAxiom(entity.asOWLObjectProperty(), selectedClass);
		} else if(axiomType.equals(OBJECT_PROPERTY_RANGE)) {
			axiom = df.getOWLObjectPropertyRangeAxiom(entity.asOWLObjectProperty(), selectedClass);
		} else if(axiomType.equals(DATA_PROPERTY_DOMAIN)) {
			axiom = df.getOWLDataPropertyDomainAxiom(entity.asOWLDataProperty(), selectedClass);
		}
	}
	
	public void setProgressMonitor(ReasonerProgressMonitor progressMonitor){
		this.progressMonitor = progressMonitor;
	}
	
	public void startLearning(){
		reasoner.resetStatistics();
		System.out.print("Started learning of " + 
				axiomType.getName() + " axioms for " + 
				OWLAPIUtils.getPrintName(entity.getEntityType()) 
				+ " " + editorKit.getOWLModelManager().getRendering(entity) + "...");
		try {
			la.start();
		} catch (Error e) {
			e.printStackTrace();
		}
		System.out.println("done.");
	}
	
	public void stopLearning(){
		System.out.println("Stopped learning algorithm.");
		la.stop();
	}
	
	public boolean isLearning(){
		return la != null && la.isRunning();
	}
	
	public LearningType getLearningType() {
		return learningType;
	}

	public void setLearningType(LearningType learningType) {
		this.learningType = learningType;
	}

	public int getMaxExecutionTimeInSeconds() {
		return maxExecutionTimeInSeconds;
	}

	public void setMaxExecutionTimeInSeconds(int maxExecutionTimeInSeconds) {
		this.maxExecutionTimeInSeconds = maxExecutionTimeInSeconds;
	}

	public void setNoisePercentage(double noisePercentage) {
		this.noisePercentage = noisePercentage;
	}

	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	public int getMaxNrOfResults() {
		return maxNrOfResults;
	}

	public void setMaxNrOfResults(int maxNrOfResults) {
		this.maxNrOfResults = maxNrOfResults;
	}

	public void setUseAllConstructor(boolean useAllConstructor) {
		this.useAllConstructor = useAllConstructor;
	}

	public void setUseExistsConstructor(boolean useExistsConstructor) {
		this.useExistsConstructor = useExistsConstructor;
	}

	public void setUseHasValueConstructor(boolean useHasValueConstructor) {
		this.useHasValueConstructor = useHasValueConstructor;
	}

	public void setUseNegation(boolean useNegation) {
		this.useNegation = useNegation;
	}

	public void setUseCardinalityRestrictions(boolean useCardinalityRestrictions) {
		this.useCardinalityRestrictions = useCardinalityRestrictions;
	}

	public void setCardinalityLimit(int cardinalityLimit) {
		this.cardinalityLimit = cardinalityLimit;
	}
	
	@SuppressWarnings("unchecked")
	public synchronized List<EvaluatedDescriptionClass> getCurrentlyLearnedDescriptions() {
		List<EvaluatedDescriptionClass> result;
		if (la != null) {
			result = Collections.unmodifiableList((List<EvaluatedDescriptionClass>) la
					.getCurrentlyBestEvaluatedDescriptions(maxNrOfResults, threshold, true));
		} else {
			result = Collections.emptyList();
		}
		return result;
	}
	
	public int getMinimumHorizontalExpansion(){
		return ((CELOE)la).getMinimumHorizontalExpansion();
	}
	
	public int getMaximumHorizontalExpansion(){
		return ((CELOE)la).getMaximumHorizontalExpansion();
	}
	
	public boolean isConsistent(){
		return reasoner.isSatisfiable();
	}
	
	public SortedSet<OWLIndividual> getIndividuals(){
		OWLClass selectedClass = editorKit.getOWLWorkspace().getOWLSelectionModel().getLastSelectedClass();
		return reasoner.getIndividuals(selectedClass);
	}
	
	public boolean canLearn(){
		// get the selected entity
		OWLEntity entity = editorKit.getOWLWorkspace().getOWLSelectionModel().getSelectedEntity();
		
		// check if number of examples is above min number
		if(entity.isOWLClass()) {
			return reasoner.getIndividuals(entity.asOWLClass()).size() > MIN_NR_OF_INDIVIDUALS;
		} else if(entity.isOWLObjectProperty()) {
			return reasoner.getPropertyMembers(entity.asOWLObjectProperty()).size() > MIN_NR_OF_INDIVIDUALS;
		} else if(entity.isOWLDataProperty()) {
			return reasoner.getDatatypeMembers(entity.asOWLDataProperty()).size() > MIN_NR_OF_INDIVIDUALS;
		}
		return false;
	}
	
	public String getRendering(OWLObject owlObject){
		String rendering = editorKit.getModelManager().getRendering(owlObject);
		return rendering;
	}
	
	public ClosedWorldReasoner getReasoner(){
		return reasoner;
	}
	
	public OWLOntology getActiveOntology(){
		return editorKit.getOWLModelManager().getActiveOntology();
	}
	
	public OWLClass getCurrentlySelectedClass(){
		return editorKit.getOWLWorkspace().getOWLSelectionModel().getLastSelectedClass();
	}
	
	public String getCurrentlySelectedClassRendered(){
		return getRendering(getCurrentlySelectedClass());
	}
	
	public synchronized void setIsPreparing(boolean isPreparing){
		this.isPreparing = isPreparing;
	}
	
	public synchronized boolean isPreparing(){
		return isPreparing;
	}
	
	@Override
	public void handleChange(OWLModelManagerChangeEvent event) {
		if(event.isType(EventType.REASONER_CHANGED) || event.isType(EventType.ACTIVE_ONTOLOGY_CHANGED)){
			reinitNecessary = true;
		}
	}

	@Override
	public void selectionChanged() throws Exception {
	}

	@Override
	public void dispose() throws Exception {
		alg = null;
		entity = null;
		axiomType = null;
//		reasoner.releaseKB();
		editorKit.getOWLModelManager().removeListener(this);
		editorKit.getOWLWorkspace().getOWLSelectionModel().removeListener(this);
		
	}

}
