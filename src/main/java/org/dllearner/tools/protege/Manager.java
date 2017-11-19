package org.dllearner.tools.protege;

import org.apache.jena.rdf.model.Model;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.algorithms.properties.AxiomAlgorithms;
import org.dllearner.core.*;
import org.dllearner.kb.LocalModelBasedSparqlEndpointKS;
import org.dllearner.kb.OWLAPIOntology;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.learningproblems.ClassLearningProblem;
import org.dllearner.learningproblems.EvaluatedDescriptionClass;
import org.dllearner.learningproblems.PosOnlyLP;
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
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.ReasonerProgressMonitor;
import org.semanticweb.owlapi.util.ProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static org.semanticweb.owlapi.model.AxiomType.*;

public class Manager implements OWLModelManagerListener, OWLSelectionModelListener, Disposable{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Manager.class);
	
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

	private List<ProgressMonitor> progressMonitors = new ArrayList<>();

	public boolean addProgressMonitor(ProgressMonitor progressMonitor) {
		return this.progressMonitors.add(progressMonitor);
	}

	public boolean removeProgressMonitor(ProgressMonitor progressMonitor) {
		return this.progressMonitors.remove(progressMonitor);
	}

	private void fireProgressStatusChanged(String message) {
		progressMonitors.forEach(mon -> mon.setMessage(message));
	}

	private void fireTaskStarted() {
		progressMonitors.forEach(ProgressMonitor::setStarted);
	}

	private void fireTaskFinished() {
		progressMonitors.forEach(ProgressMonitor::setFinished);
	}

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

	public boolean canLearn(OWLEntity entity, AxiomType axiomType) throws NoInstanceDataException {
		OWLOntology ontology = editorKit.getWorkspace().getOWLModelManager().getActiveOntology();
		OWLReasoner reasoner = editorKit.getOWLModelManager().getReasoner();

		long instanceDataCnt = 0;
		if(entity.isOWLClass()) {
			instanceDataCnt = reasoner.getInstances(entity.asOWLClass(), false).getFlattened().size();
		} else if(entity.isOWLObjectProperty()) {
			Set<OWLObjectPropertyExpression> properties = new HashSet<>();
			properties.add(entity.asOWLObjectProperty());

			for(Node<OWLObjectPropertyExpression> node : reasoner.getSubObjectProperties(entity.asOWLObjectProperty(), false)) {
				properties.add(node.getRepresentativeElement().asOWLObjectProperty());
			}

			instanceDataCnt += ontology.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION).parallelStream()
					.filter(axiom -> properties.contains(axiom.getProperty()))
					.count();

		} else if(entity.isOWLDataProperty()) {
			instanceDataCnt = reasoner.getSubDataProperties(entity.asOWLDataProperty(), false).getNodes().stream()
					.mapToLong(node ->
							ontology.getAxioms(node.getRepresentativeElement(), Imports.INCLUDED).stream()
									.filter(axiom -> axiom.isOfType(AxiomType.DATA_PROPERTY_ASSERTION))
									.count()
			).sum();
		}
		System.out.println("#INSTANCE DATA for entity " + entity + " : " + instanceDataCnt);

		if(instanceDataCnt <= 3) {
			throw new NoInstanceDataException(entity, axiomType);
		}

		return true;
	}

	public <A extends OWLAxiom> List<EvaluatedDescriptionClass> computeGCI(OWLEntity entity, AxiomType<A> axiomType) {
		OWLDataFactory df = editorKit.getOWLModelManager().getOWLDataFactory();

		OWLClassExpression ce;
		if (axiomType.equals(AxiomType.OBJECT_PROPERTY_DOMAIN)) {
			ce = df.getOWLObjectSomeValuesFrom(entity.asOWLObjectProperty(), df.getOWLThing());
		} else if (axiomType.equals(AxiomType.DATA_PROPERTY_DOMAIN)) {
			ce = df.getOWLDataSomeValuesFrom(entity.asOWLDataProperty(), df.getTopDatatype());
		} else if (axiomType.equals(AxiomType.OBJECT_PROPERTY_RANGE)) {
			ce = df.getOWLObjectSomeValuesFrom(entity.asOWLObjectProperty().getInverseProperty(), df.getOWLThing());
		} else {
			throw new IllegalArgumentException("Unsupported axiom type " + axiomType.getName());
		}

		try {
//			initKnowledgeSource();
//
//			initReasoner();

			PosOnlyLP lp = new PosOnlyLP(reasoner);
			lp.setPositiveExamples(reasoner.getIndividualsImpl(ce));
//			ClassExpressionLearningProblem lp = new ClassExpressionLearningProblem(reasoner);
//			lp.setClassExpressionToDescribe(ce);
			lp.init();

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

			la.start();

			return getCurrentlyLearnedDescriptions();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public <A extends OWLAxiom> List<EvaluatedAxiom<A>> computeAxioms(OWLEntity entity, AxiomType<A> axiomType) throws NoInstanceDataException {
		OWLOntology ontology = editorKit.getWorkspace().getOWLModelManager().getActiveOntology();
		OWLReasoner reasoner = editorKit.getOWLModelManager().getReasoner();


		// check if there is any instance data to learn the given axiom type for the given entity
		canLearn(entity, axiomType);


		LOGGER.info("Started learning of " +
				axiomType.getName() + " axioms for " + 
				OWLAPIUtils.getPrintName(entity.getEntityType()) 
				+ " " + editorKit.getOWLModelManager().getRendering(entity) + "...");
		fireTaskStarted();
		fireProgressStatusChanged("Learning " + axiomType.getName() + " axioms for "
				+ entity.getEntityType().getPrintName().toLowerCase() + " "
				+ editorKit.getOWLModelManager().getRendering(entity) + "...");
		try {
			Class<? extends AbstractAxiomLearningAlgorithm<? extends OWLAxiom, ? extends OWLObject, ? extends OWLEntity>> algorithmClass = AxiomAlgorithms.getAlgorithmClass(axiomType);
			Model model = OwlApiJenaUtils.getModel(ontology);
			LocalModelBasedSparqlEndpointKS ks = new LocalModelBasedSparqlEndpointKS(model);
			alg = algorithmClass.getConstructor(SparqlEndpointKS.class).newInstance(ks);
			alg.setEntityToDescribe(editorKit.getOWLWorkspace().getOWLSelectionModel().getSelectedEntity());
			alg.setUseSampling(false);
//			alg.setProgressMonitor(progressMonitor);
			alg.init();
			alg.start();
			LOGGER.info("done.");
			fireTaskFinished();
			return alg.getCurrentlyBestEvaluatedAxioms();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | ComponentInitException e) {
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
			LOGGER.info("Initializing learning algorithm...");
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
			LOGGER.info("done in " + (System.currentTimeMillis()-startTime) + "ms.");
		} catch (Error e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public void initLearningProblem() throws Exception {
		LOGGER.info("Initializing learning problem...");
		long startTime = System.currentTimeMillis();
		
		lp = new ClassLearningProblem(reasoner);
		lp.setClassToDescribe(editorKit.getOWLWorkspace().getOWLSelectionModel().getLastSelectedClass());
		lp.setEquivalence(axiomType.equals(EQUIVALENT_CLASSES));
		lp.setCheckConsistency(DLLearnerPreferences.getInstance().isCheckConsistencyWhileLearning());
		lp.init();

		LOGGER.info("done in " + (System.currentTimeMillis()-startTime) + "ms.");
	}
	
	public void initKnowledgeSource() throws Exception{
		ks = new OWLAPIOntology(editorKit.getOWLModelManager().getActiveOntology());
		ks.init();
	}
	
	public void initReasoner() throws Exception{
		LOGGER.info("Initializing DL-Learner internal reasoner...");
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
		LOGGER.info("done in " + (System.currentTimeMillis()-startTime) + "ms.");
	}
	
	public void initReasonerAsynchronously(){
		reasoner = new ClosedWorldReasoner(Collections.singleton(ks));
		OWLAPIReasoner baseReasoner = new OWLAPIReasoner(editorKit.getOWLModelManager().getReasoner());
		reasoner.setReasonerComponent(baseReasoner);
//		reasoner.setProgressMonitor(progressMonitor);TODO integrate progress monitor
		
		Thread t = new Thread(() -> {
            try {
                reasoner.init();
            } catch (ComponentInitException e) {
                e.printStackTrace();
            }
        });
		t.start();
	}
	
	public void addAxiom(OWLAxiom axiom) {
		OWLOntology ontology = editorKit.getOWLModelManager().getActiveOntology();
		editorKit.getOWLModelManager().applyChange(new AddAxiom(ontology, axiom));
	}

	/**
	 * Add the given class expression as axiom to the current ontology.
	 *
	 * @param evaluatedDescription the class expression
	 */
	public void addAxiom(EvaluatedDescription<? extends OWLClassExpression> evaluatedDescription){
		OWLDataFactory df = editorKit.getOWLModelManager().getOWLDataFactory();

		OWLAxiom axiom = null;
		OWLClassExpression ce = evaluatedDescription.getDescription();

		if(axiomType.equals(SUBCLASS_OF)) {
			axiom = df.getOWLSubClassOfAxiom(entity.asOWLClass(), ce);
		} else if(axiomType.equals(EQUIVALENT_CLASSES)) {
			axiom = df.getOWLEquivalentClassesAxiom(entity.asOWLClass(), ce);
		} else if(axiomType.equals(OBJECT_PROPERTY_DOMAIN)) {
			axiom = df.getOWLObjectPropertyDomainAxiom(entity.asOWLObjectProperty(), ce);
		} else if(axiomType.equals(OBJECT_PROPERTY_RANGE)) {
			axiom = df.getOWLObjectPropertyRangeAxiom(entity.asOWLObjectProperty(), ce);
		} else if(axiomType.equals(DATA_PROPERTY_DOMAIN)) {
			axiom = df.getOWLDataPropertyDomainAxiom(entity.asOWLDataProperty(), ce);
		}

		editorKit.getOWLModelManager().applyChange(new AddAxiom(editorKit.getOWLModelManager().getActiveOntology(), axiom));
	}
	
	public void setProgressMonitor(ReasonerProgressMonitor progressMonitor){
		this.progressMonitor = progressMonitor;
	}
	
	public void startLearning(){
		reasoner.resetStatistics();
		LOGGER.info("Started learning of " +
				axiomType.getName() + " axioms for " + 
				OWLAPIUtils.getPrintName(entity.getEntityType()) 
				+ " " + editorKit.getOWLModelManager().getRendering(entity) + "...");
		try {
			la.start();
		} catch (Error e) {
			e.printStackTrace();
		}
		LOGGER.info("done.");
	}
	
	public void stopLearning(){
		LOGGER.info("Stopped learning algorithm.");
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

		if(entity != null) {
			// get size of instance data
			int size = 0;
			if(entity.isOWLClass()) {
				size = reasoner.getIndividuals(entity.asOWLClass()).size();
			} else if(entity.isOWLObjectProperty()) {
				size = reasoner.getPropertyMembers(entity.asOWLObjectProperty()).size();
			} else if(entity.isOWLDataProperty()) {
				size = reasoner.getDatatypeMembers(entity.asOWLDataProperty()).size();
			}
			System.out.println(entity + " : " + size);
			LOGGER.info("#facts for entity {}:{}", entity, size);

			return size > MIN_NR_OF_INDIVIDUALS;
		}

		return false;
	}
	
	public String getRendering(OWLObject owlObject){
		return editorKit.getModelManager().getRendering(owlObject);
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
		entity = editorKit.getOWLWorkspace().getOWLSelectionModel().getSelectedEntity();
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
