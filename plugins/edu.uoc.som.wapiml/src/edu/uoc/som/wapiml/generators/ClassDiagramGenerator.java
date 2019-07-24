package edu.uoc.som.wapiml.generators;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.uml2.uml.AggregationKind;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Constraint;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.EnumerationLiteral;
import org.eclipse.uml2.uml.Generalization;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Namespace;
import org.eclipse.uml2.uml.OpaqueExpression;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.ParameterDirectionKind;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.resource.UMLResource;
import org.eclipse.uml2.uml.util.UMLUtil;

import edu.uoc.som.openapi2.API;
import edu.uoc.som.openapi2.JSONDataType;
import edu.uoc.som.openapi2.Operation;
import edu.uoc.som.openapi2.Parameter;
import edu.uoc.som.openapi2.ParameterLocation;
import edu.uoc.som.openapi2.Path;
import edu.uoc.som.openapi2.Response;
import edu.uoc.som.openapi2.Schema;
import edu.uoc.som.wapiml.model.AssociationCandidate;
import edu.uoc.som.wapiml.utils.OpenAPIProfileUtils;
import edu.uoc.som.wapiml.utils.OpenAPIUtils;

public class ClassDiagramGenerator implements Serializable {
	/**
		 * 
		 */
	private static final long serialVersionUID = 1L;
	private UMLFactory umlFactory;
	private ResourceSet resourceSet;
	private Resource openAPIProfileResource;
	
	
	private Model umlModel;
	private String modelName;
	private API openAPIModel;
	private List<AssociationCandidate> assocationCandidates;
	private Map<edu.uoc.som.openapi2.Property, Property> propertiesMaps = new HashMap<>();
	private Map<Schema,Class> schemaMaps = new HashMap<>();

	public ClassDiagramGenerator(API OpenAPIModel, String modelName) throws IOException {
		this.openAPIModel = OpenAPIModel;
		this.modelName = modelName;
		
		umlFactory = UMLFactory.eINSTANCE;
		resourceSet = initUMLResourceSet();
		openAPIProfileResource = resourceSet
				.getResource(URI.createURI("pathmap://OPENAPI_PROFILES/openapi.profile.uml"), true);
		
		//create a temp file to hold the UML resource. the resource should exists to be able to apply the profile
		File tempUMLFile = File.createTempFile("uml"+RandomStringUtils.random(10, true, false), ".uml");
		tempUMLFile.deleteOnExit();
		URI resourceURI = URI.createFileURI(tempUMLFile.getPath());
		Resource umlModelResource = resourceSet.createResource(resourceURI);
		umlModel = UMLFactory.eINSTANCE.createModel();
		umlModelResource.getContents().add(umlModel);
		assocationCandidates =inferAssociationCandidates();

	}

	private ResourceSet initUMLResourceSet() {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getPackageRegistry().put(UMLPackage.eNS_URI, UMLPackage.eINSTANCE);
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(UMLResource.FILE_EXTENSION,
				UMLResource.Factory.INSTANCE);
		resourceSet.getURIConverter().getURIMap().put(URI.createURI("pathmap://OPENAPI_PROFILES/openapi.profile.uml"),
				URI.createPlatformPluginURI("edu.uoc.som.openapi2.profile/resources/openapi.profile.uml", true));
		resourceSet.getURIConverter().getURIMap().put(
				URI.createURI("pathmap://UML_LIBRARIES/UMLPrimitiveTypes.library.uml"), URI.createPlatformPluginURI(
						"org.eclipse.uml2.uml.resources/libraries/UMLPrimitiveTypes.library.uml", true));
		resourceSet.getURIConverter().getURIMap().put(URI.createURI(UMLResource.LIBRARIES_PATHMAP),
				URI.createPlatformPluginURI("org.eclipse.uml2.uml.resources", true).appendSegment("libraries")
						.appendSegment(""));
		resourceSet.getURIConverter().getURIMap().put(URI.createURI(UMLResource.METAMODELS_PATHMAP),
				URI.createPlatformPluginURI("org.eclipse.uml2.uml.resources", true).appendSegment("metamodels")
						.appendSegment(""));
		resourceSet.getURIConverter().getURIMap().put(URI.createURI(UMLResource.PROFILES_PATHMAP),
				URI.createPlatformPluginURI("org.eclipse.uml2.uml.resources", true).appendSegment("profiles")
						.appendSegment(""));
		return resourceSet;
	}

	private List<AssociationCandidate> inferAssociationCandidates() {
		List<AssociationCandidate> assocationCandidates = new ArrayList<AssociationCandidate>();
		List<Schema> schemaObjectLists = new ArrayList<Schema>();
		for(Entry<String, Schema> schema: openAPIModel.getDefinitions()) {
		if(isObject(schema.getValue()))
			schemaObjectLists.add(schema.getValue());
		}
		for(Schema source: schemaObjectLists) {
			for(edu.uoc.som.openapi2.Property property: source.getProperties()) {
				for(Schema target: schemaObjectLists) {
					String propertyName = property.getName();
					String targetSchemaName = target.getName();
					//this should be transformed to a regular expression in the future
					if(propertyName != null &&  targetSchemaName != null && isPrimitive(property.getSchema()) && (propertyName.equalsIgnoreCase(targetSchemaName) || propertyName.equalsIgnoreCase(targetSchemaName+"id") || propertyName.equalsIgnoreCase(targetSchemaName+"_id") || propertyName.equalsIgnoreCase(targetSchemaName+"-id"))) {
						assocationCandidates.add(new AssociationCandidate(source, property, target,AggregationKind.SHARED_LITERAL));
					}
				}
				
			}
		}
		return assocationCandidates;
		
	}
	private List<AssociationCandidate> inferFixedAssociations(){
		List<AssociationCandidate> assocationCandidates = new ArrayList<AssociationCandidate>();
		for (Entry<String, Schema> definition : openAPIModel.getDefinitions()) {
			if (isObject(definition.getValue())) {
				for (edu.uoc.som.openapi2.Property property : definition.getValue().getProperties()) {
					if (isObject(property.getSchema()) || isArrayOfObjects(property.getSchema())) {
						assocationCandidates.add(new AssociationCandidate(definition.getValue(), property, property.getSchema(), AggregationKind.COMPOSITE_LITERAL));
						
					}

				}
			}
		}
		return assocationCandidates;
	}
	public Model generateClassDiagramFromOpenAPI(boolean applyProfile, boolean discoverAssociations)
			throws IOException {
		
		
	

		if (applyProfile) {
			
			umlModel.applyProfile((Profile) openAPIProfileResource.getContents().get(0));
			
		}
		Package package_ = umlFactory.createPackage();
		package_.setName(modelName);
		umlModel.getPackagedElements().add(package_);
		Package types = umlFactory.createPackage();
		types.setName("types");
		umlModel.getPackagedElements().add(types);


		if (applyProfile) {
			OpenAPIProfileUtils.applyAPIStereotype(umlModel, openAPIModel);
			if (openAPIModel.getInfo() != null)
				OpenAPIProfileUtils.applyAPIInfoStereotype(umlModel, openAPIModel.getInfo());
			if (openAPIModel.getExternalDocs() != null)
				OpenAPIProfileUtils.applyExternalDocsStereotype(umlModel, openAPIModel.getExternalDocs());
			if (!openAPIModel.getSecurityDefinitions().isEmpty())
				OpenAPIProfileUtils.applySecurityDefinitionsStereotype(umlModel, openAPIModel.getSecurityDefinitions());
			if (!openAPIModel.getTags().isEmpty())
				OpenAPIProfileUtils.applyTagsStereotype(umlModel, openAPIModel.getTags());
			if (!openAPIModel.getSecurity().isEmpty())
				OpenAPIProfileUtils.applySecurityStereotype(umlModel, openAPIModel.getSecurity());

		}

		// generate classes
		for (Entry<String, Schema> definition : openAPIModel.getDefinitions()) {
			if (isObject(definition.getValue())) {
				Class clazz = umlFactory.createClass();
				clazz.setName(definition.getKey());
				package_.getOwnedTypes().add(clazz);
				if (applyProfile) {
					OpenAPIProfileUtils.applySchemaStereotype(clazz, definition.getValue());
					if (definition.getValue().getExternalDocs() != null)
						OpenAPIProfileUtils.applyExternalDocsStereotype(clazz, definition.getValue().getExternalDocs());
				}
				schemaMaps.put(definition.getValue(), clazz);
				addProperties(types, definition.getValue(), definition.getKey(), clazz, applyProfile);
				if (!definition.getValue().getAllOf().isEmpty()) {
					for (Schema allOfItem : definition.getValue().getAllOf()) {
						if (allOfItem.getDeclaringContext() != null
								&& allOfItem.getDeclaringContext().equals(definition.getValue())) {
							addProperties(types, allOfItem, definition.getKey(), clazz, applyProfile);
						}
					}
				}
			}
		}
		// resolve superclasses
		for (Entry<String, Schema> definition : openAPIModel.getDefinitions()) {
			if (isObject(definition.getValue())) {
				if (!definition.getValue().getAllOf().isEmpty()) {
					Class child = schemaMaps.get(definition.getValue());
					if (child != null)
						for (Schema allOfItem : definition.getValue().getAllOf()) {
							Class parent = schemaMaps.get(allOfItem);

							if (parent != null) {
								Generalization generation = umlFactory.createGeneralization();
								generation.setGeneral(parent);
								child.getGeneralizations().add(generation);
							}
						}
				}
			}
		}
		
		// resolve additionalProperties
		for (Entry<String, Schema> definition : openAPIModel.getDefinitions()) {
			if (isObject(definition.getValue())) {
				if (definition.getValue().getAdditonalProperties()!=null) {
					Class clazz = schemaMaps.get(definition.getValue());
					Schema additionalPropertiesSchema = definition.getValue().getAdditonalProperties();
					if(isPrimitive(additionalPropertiesSchema)) {
						UMLUtil.setTaggedValue(clazz, clazz.getApplicableStereotype(OpenAPIProfileUtils.SCHEMA_QN), "additionalProperties",getUMLType(types, additionalPropertiesSchema.getType(), additionalPropertiesSchema.getFormat(), applyProfile));
					}
					if(isObject(additionalPropertiesSchema)) {
						Class referencedClass = schemaMaps.get(additionalPropertiesSchema);
						UMLUtil.setTaggedValue(clazz, clazz.getApplicableStereotype(OpenAPIProfileUtils.SCHEMA_QN), "additionalProperties",referencedClass);
					}
						
						
				}
			}
		}
		// resolve associations
		for (Entry<String, Schema> definition : openAPIModel.getDefinitions()) {
			if (isObject(definition.getValue())) {
				for (edu.uoc.som.openapi2.Property property : definition.getValue().getProperties()) {
					if (isObject(property.getSchema()) || isArrayOfObjects(property.getSchema())) {
						Association association = createAssociation(definition.getValue(), property);
						package_.getPackagedElements().add(association);
					}

				}
			}
		}
		// resolve associations for allOf
		for (Entry<String, Schema> definition : openAPIModel.getDefinitions()) {
			if (isObject(definition.getValue())) {
				if (!definition.getValue().getAllOf().isEmpty()) {
					for (edu.uoc.som.openapi2.Property property : definition.getValue().getAllOf().get(1).getProperties()) {
						if (isObject(property.getSchema()) || isArrayOfObjects(property.getSchema())) {
							Association association = createAssociation( definition.getValue(), property);
							package_.getPackagedElements().add(association);
						}

					}
				}
			}
		}
		// add operations
		for (Operation operation : openAPIModel.getAllOperations()) {
			Schema definition = OpenAPIUtils.getAppropriateLocation(openAPIModel, operation);
			Class clazz = null;
			if (definition != null) {
				if (schemaMaps.get(definition) != null) {
					clazz = schemaMaps.get(definition);
				}

			}
			if (clazz == null) {
				Path path = (Path) operation.eContainer();
				String resource = OpenAPIUtils.getLastMeaningfullSegment(path.getRelativePath());
				NamedElement namedElement = package_.getOwnedMember(StringUtils.capitalize(resource), false,
						UMLPackage.eINSTANCE.getClass_());
				if (namedElement != null) {
					clazz = (Class) namedElement;
				} else {
					clazz = umlFactory.createClass();
					clazz.setName(StringUtils.capitalize(resource));
					package_.getOwnedTypes().add(clazz);
				}
			}

			org.eclipse.uml2.uml.Operation umlOperation = umlFactory.createOperation();
			umlOperation.setName(OpenAPIUtils.getOperationName(operation));
			clazz.getOwnedOperations().add(umlOperation);
			if (applyProfile) {
				OpenAPIProfileUtils.applyAPIOperationeStereotype(umlOperation, operation);
				if (operation.getExternalDocs() != null)
					OpenAPIProfileUtils.applyExternalDocsStereotype(umlOperation, operation.getExternalDocs());
				if (!operation.getSecurity().isEmpty())
					OpenAPIProfileUtils.applySecurityStereotype(umlOperation, operation.getSecurity());
			}
			for (Parameter parameter : operation.getParameters()) {
				org.eclipse.uml2.uml.Parameter umlParameter = umlFactory.createParameter();
				umlParameter.setName(parameter.getName());
				umlParameter.setDirection(ParameterDirectionKind.IN_LITERAL);

				if (parameter.getDefault() != null)
					umlParameter.setDefault(parameter.getDefault());
				if (parameter.getMultipleOf() != null)
					addConstraint(umlOperation, parameter.getName(), "multipleOfConstraint",
							"self." + parameter.getName() + ".div(" + parameter.getMultipleOf() + ") = 0");
				if (parameter.getMaximum() != null) {
					if (parameter.getExclusiveMaximum() != null && parameter.getExclusiveMaximum().equals(Boolean.TRUE))
						addConstraint(umlOperation, umlParameter.getName(), "maximumConstraint",
								"self." + umlParameter.getName() + " < " + parameter.getMaximum());
					else
						addConstraint(umlOperation, umlParameter.getName(), "maximumConstraint",
								"self." + umlParameter.getName() + " <= " + parameter.getMaximum());
				}
				if (parameter.getMinimum() != null) {
					if (parameter.getExclusiveMinimum() != null && parameter.getExclusiveMinimum().equals(Boolean.TRUE))
						addConstraint(umlOperation, umlParameter.getName(), "minimumConstraint",
								"self." + umlParameter.getName() + " > " + parameter.getMinimum());
					else
						addConstraint(umlOperation, umlParameter.getName(), "minimumConstraint",
								"self." + umlParameter.getName() + " >= " + parameter.getMinimum());
				}
				if (parameter.getMaxLength() != null)
					addConstraint(umlOperation, parameter.getName(), "maxLengthConstraint",
							"self." + parameter.getName() + ".size() <= " + parameter.getMaxLength());
				if (parameter.getMinLength() != null)
					addConstraint(umlOperation, parameter.getName(), "minLengthConstraint",
							"self." + parameter.getName() + ".size() >= " + parameter.getMinLength());
				if (parameter.getLocation().equals(ParameterLocation.BODY)) {
					if (parameter.getSchema() != null) {
						if (parameter.getSchema().getType().equals(JSONDataType.ARRAY)) {
							umlParameter.setType(schemaMaps.get(parameter.getSchema().getItems()));
							if (parameter.getSchema().getMaxItems() != null) {
								umlParameter.setUpper(parameter.getSchema().getMaxItems());
							} else
								umlParameter.setUpper(-1);
							if (parameter.getSchema().getMinItems() != null)
								umlParameter.setLower(parameter.getSchema().getMinItems());
							else
								umlParameter.setLower(0);
						} else {
							umlParameter.setType(schemaMaps.get(parameter.getSchema()));
						}
					}
				} else {
//					if (parameter.getRequired() != null && parameter.getRequired().equals(Boolean.TRUE))
//						umlParameter.setLower(1);
//					else
						umlParameter.setLower(0);
					if (parameter.getType().equals(JSONDataType.ARRAY)) {
						if (parameter.getMaxItems() != null)
							umlParameter.setUpper(parameter.getMaxItems());
						else
							umlParameter.setUpper(-1);
						if (!parameter.getItems().getEnum().isEmpty())
							umlParameter.setType(getOrCreateEnumeration(parameter.getItems().getEnum(),
									clazz.getName() + StringUtils.capitalize(parameter.getName()), types, applyProfile));
						else
							umlParameter.setType(getUMLType(types, parameter.getItems().getType(),
									parameter.getItems().getFormat(),applyProfile));
					} else if (!parameter.getEnum().isEmpty())
						umlParameter.setType(getOrCreateEnumeration(parameter.getEnum(),
								clazz.getName() + StringUtils.capitalize(parameter.getName()), types,applyProfile));
					else
						umlParameter.setType(getUMLType(types, parameter.getType(), parameter.getFormat(),applyProfile));
					if (parameter.getDefault() != null) {
						umlParameter.setDefault(parameter.getDefault());
					}
				}

				umlOperation.getOwnedParameters().add(umlParameter);
				if (applyProfile) {
					OpenAPIProfileUtils.applyAPIParameterStereotype(umlParameter, parameter);
				}
			}
			if (!operation.getResponses().isEmpty()) {
				for (Entry<String, Response> response : operation.getResponses()) {

					org.eclipse.uml2.uml.Parameter returnedParameter = umlFactory.createParameter();
					if (response.getValue().getSchema() != null) {
						Schema returnedSchema = response.getValue().getSchema();
						boolean isObject = isObject(returnedSchema);
						boolean isArrayOfObjects = isArrayOfObjects(returnedSchema);
						Schema returnedObject = isObject ? returnedSchema
								: (isArrayOfObjects ? returnedSchema.getItems() : null);
						if (returnedObject != null) {
							Class returnedClass = schemaMaps.get(returnedObject);
							if (returnedClass != null)
								returnedParameter.setType(returnedClass);
							if (isArrayOfObjects) {
								returnedParameter.setUpper(-1);
								returnedParameter.setLower(0);
							}

						}
					}
					returnedParameter.setDirection(ParameterDirectionKind.RETURN_LITERAL);
					umlOperation.getOwnedParameters().add(returnedParameter);
					if (applyProfile) {
						OpenAPIProfileUtils.applyAPIResponseStereotype(returnedParameter, response);

					}

				}
			}

		}
		if(discoverAssociations)
			refine(package_);

		return umlModel;

	}

	public void refine(Package package_) {
		List<Association> associations = new ArrayList<Association>();
		List<Property> propertiesToRemore = new ArrayList<Property>();
		
		
		for(AssociationCandidate associationCandidate: assocationCandidates) {
			Property properyToRemove = propertiesMaps.get(associationCandidate.getProperty());
			associations.add(extractAssociation(schemaMaps.get(associationCandidate.getSchema()), schemaMaps.get(associationCandidate.getTargetSchema()),properyToRemove,associationCandidate.getAggregationKind()));
			propertiesToRemore.add(properyToRemove);
		}

					
		for(Association association: associations) {
			package_.getPackagedElements().add(association);
		}
		for(Property property: propertiesToRemore) {
			EcoreUtil.delete(property);
		}
	
	}
	private Association extractAssociation(Class class1, Class class2, Property property, AggregationKind aggregationKind) {
		Association association = umlFactory.createAssociation();

		association.setName(class1.getName()+"_"+class2.getName());
		Property firstEnd = umlFactory.createProperty();
		firstEnd.setName(property.getName());
		firstEnd.setType(class2);
		firstEnd.setUpper(property.getUpper());
		association.getOwnedEnds().add(firstEnd);
		Property secondEnd = umlFactory.createProperty();
		secondEnd.setName(class1.getName());
		secondEnd.setType(class1);
		secondEnd.setAggregation(aggregationKind);
		secondEnd.setIsNavigable(true);
		association.getOwnedEnds().add(secondEnd);
		
		return association;
	}
	private Association createAssociation(Schema definition,
			edu.uoc.som.openapi2.Property property) {
		Association association = umlFactory.createAssociation();
		association.setName(definition.getName() + "_" + property.getName());
		Property firstOwnedEnd = umlFactory.createProperty();
		association.getOwnedEnds().add(firstOwnedEnd);
		Property secondOwnedEnd = umlFactory.createProperty();
		association.getOwnedEnds().add(secondOwnedEnd);
		firstOwnedEnd.setName(definition.getName());
		firstOwnedEnd.setType(schemaMaps.get(definition));
		secondOwnedEnd.setName(property.getName());
		secondOwnedEnd.setAggregation(AggregationKind.COMPOSITE_LITERAL);
//		if (property.getRequired()!=null && property.getRequired())
//			secondOwnedEnd.setLower(1);
//		else
			secondOwnedEnd.setLower(0);
		if (!property.getSchema().getType().equals(JSONDataType.ARRAY)) {
			Class type = schemaMaps.get(property.getSchema());
			secondOwnedEnd.setType(type);

		} else {
			secondOwnedEnd.setUpper(-1);
			secondOwnedEnd.setType(schemaMaps.get(property.getSchema().getItems()));

		}
		association.getNavigableOwnedEnds().add(secondOwnedEnd);
		return association;
	}

	private void addProperties(Package types, Schema schema, String definitionName, Class clazz, boolean applyProfile) {
		for (edu.uoc.som.openapi2.Property openAPIproperty : schema.getProperties()) {
			if (isPrimitive(openAPIproperty.getSchema())) {
				Property umlProperty = umlFactory.createProperty();
				propertiesMaps.put(openAPIproperty, umlProperty);
				Schema propertySchema = openAPIproperty.getSchema();
				umlProperty.setName(openAPIproperty.getName());

				if (propertySchema.getMultipleOf() != null)
					addConstraint(clazz, umlProperty.getName(), "multipleOfConstraint",
							"self." + umlProperty.getName() + ".div(" + propertySchema.getMultipleOf() + ") = 0");
				if (propertySchema.getMaximum() != null) {
					if (propertySchema.getExclusiveMaximum() != null
							&& propertySchema.getExclusiveMaximum().equals(Boolean.TRUE))
						addConstraint(clazz, umlProperty.getName(), "maximumConstraint",
								"self." + umlProperty.getName() + " < " + propertySchema.getMaximum());
					else
						addConstraint(clazz, umlProperty.getName(), "maximumConstraint",
								"self." + umlProperty.getName() + " <= " + propertySchema.getMaximum());
				}
				if (propertySchema.getMinimum() != null) {
					if (propertySchema.getExclusiveMinimum() != null
							&& propertySchema.getExclusiveMinimum().equals(Boolean.TRUE))
						addConstraint(clazz, umlProperty.getName(), "minimumConstraint",
								"self." + umlProperty.getName() + " > " + propertySchema.getMinimum());
					else
						addConstraint(clazz, umlProperty.getName(), "minimumConstraint",
								"self." + umlProperty.getName() + " >= " + propertySchema.getMinimum());
				}
				if (propertySchema.getMaxLength() != null)
					addConstraint(clazz, umlProperty.getName(), "maxLengthConstraint",
							"self." + umlProperty.getName() + ".size() <= " + propertySchema.getMaxLength());
				if (propertySchema.getMinLength() != null)
					addConstraint(clazz, umlProperty.getName(), "minLengthConstraint",
							"self." + umlProperty.getName() + ".size() >= " + propertySchema.getMinLength());

				if (propertySchema.getDefault() != null)
					umlProperty.setDefault(propertySchema.getDefault());
				if(propertySchema.getReadOnly()!= null && propertySchema.getReadOnly().equals(Boolean.TRUE))
					umlProperty.setIsReadOnly(true);

				if (!propertySchema.getType().equals(JSONDataType.ARRAY)) {
					if (!propertySchema.getEnum().isEmpty())
						umlProperty.setType(getOrCreateEnumeration(propertySchema.getEnum(),
								clazz.getName() + StringUtils.capitalize(openAPIproperty.getName()), types,applyProfile));
					else
						umlProperty.setType(getUMLType(types, propertySchema.getType(), propertySchema.getFormat(),applyProfile));
//					if (openAPIproperty.getRequired()!=null && openAPIproperty.getRequired())
//						umlProperty.setLower(1);
//					else
						umlProperty.setLower(0);
				} else {

					umlProperty.setUpper(-1);
					if (propertySchema.getMinItems() != null)
						umlProperty.setLower(propertySchema.getMinItems());
					else
						umlProperty.setLower(0);
					if (!propertySchema.getItems().getEnum().isEmpty())
						umlProperty.setType(getOrCreateEnumeration(propertySchema.getItems().getEnum(),
								clazz.getName() + StringUtils.capitalize(openAPIproperty.getName()), types,applyProfile));
					else
						umlProperty.setType(getUMLType(types, propertySchema.getItems().getType(),
								propertySchema.getItems().getFormat(),applyProfile));

				}
				clazz.getOwnedAttributes().add(umlProperty);
				if (applyProfile) {
					OpenAPIProfileUtils.applyAPIPropertyStereotype(umlProperty, openAPIproperty);
				}
			}
		}
	}

	private boolean isPrimitive(Schema property) {
		if (property.getType().equals(JSONDataType.BOOLEAN) || property.getType().equals(JSONDataType.INTEGER)
				|| property.getType().equals(JSONDataType.NUMBER) || property.getType().equals(JSONDataType.STRING))
			return true;
		if (property.getType().equals(JSONDataType.ARRAY) && (property.getItems().getType().equals(JSONDataType.BOOLEAN)
				|| property.getItems().getType().equals(JSONDataType.INTEGER)
				|| property.getItems().getType().equals(JSONDataType.NUMBER)
				|| property.getItems().getType().equals(JSONDataType.STRING)))
			return true;
		return false;
	}

	private boolean isObject(Schema schema) {
		if (schema.getType().equals(JSONDataType.OBJECT))
			return true;

		if (!schema.getProperties().isEmpty())
			return true;

		if (!schema.getAllOf().isEmpty())
			return true;

		return false;
	}

	private boolean isArrayOfObjects(Schema schema) {

		if (schema.getType().equals(JSONDataType.ARRAY) && isObject(schema.getItems()))
			return true;

		return false;
	}

	private PrimitiveType getUMLType(Package types, JSONDataType jsonDataType, String format, boolean applyProfile) {
		PrimitiveType type = null;
		switch (jsonDataType) {

		case INTEGER:
			if (format == null)
				type = getOrCreatePrimitiveType("Integer", jsonDataType, format, types, applyProfile);
			else if (format.equals("int32"))
				type = getOrCreatePrimitiveType("Integer",jsonDataType, format, types, applyProfile);
			else if (format.equals("int64"))
				type = getOrCreatePrimitiveType("Long", jsonDataType, format, types, applyProfile);
			else
				type = getOrCreatePrimitiveType(StringUtils.capitalize(format), jsonDataType, format, types, applyProfile);
			break;
		case NUMBER:
			if (format == null)
				type = getOrCreatePrimitiveType("Number", jsonDataType, format, types, applyProfile);
			else if (format.equals("float"))
				type = getOrCreatePrimitiveType("Float", jsonDataType, format, types, applyProfile);
			else if (format.equals("double"))
				type = getOrCreatePrimitiveType("Double", jsonDataType, format, types, applyProfile);
			else
				type = getOrCreatePrimitiveType(StringUtils.capitalize(format), jsonDataType, format, types, applyProfile);
			break;
		case STRING:
			if (format == null)
				type = getOrCreatePrimitiveType("String", jsonDataType, format, types, applyProfile);
			else if (format.equals("byte"))
				type = getOrCreatePrimitiveType("Byte", jsonDataType, format, types, applyProfile);
			else if (format.equals("binary"))
				type = getOrCreatePrimitiveType("Binary", jsonDataType, format, types, applyProfile);
			else if (format.equals("date"))
				type = getOrCreatePrimitiveType("Date", jsonDataType, format, types, applyProfile);
			else if (format.equals("date-time"))
				type = getOrCreatePrimitiveType("DateTime", jsonDataType, format, types, applyProfile);
			else if (format.equals("password"))
				type = getOrCreatePrimitiveType("Password", jsonDataType, format, types, applyProfile);
			else
				type = getOrCreatePrimitiveType(StringUtils.capitalize(format), jsonDataType, format, types, applyProfile);

			break;
		case BOOLEAN:
			type = getOrCreatePrimitiveType("Boolean", jsonDataType, format, types, applyProfile);
			break;
		case FILE:
			type = getOrCreatePrimitiveType("File", jsonDataType, format, types, applyProfile);
		default:
			break;
		}
		return type;

	}

	public UMLFactory getUmlFactory() {
		return umlFactory;
	}

	public void setUmlFactory(UMLFactory umlFactory) {
		this.umlFactory = umlFactory;
	}

	public void saveClassDiagram(File target) throws IOException {

		
		umlModel.eResource().setURI(URI.createFileURI(target.getPath()));
		umlModel.eResource().save(Collections.EMPTY_MAP);
		
	}

	private PrimitiveType getOrCreatePrimitiveType(String commonName,  JSONDataType jsonDataType, String format, Package types,  boolean applyProfile) {

		Type type = types.getOwnedType(commonName, false, UMLPackage.eINSTANCE.getPrimitiveType(), false);
		if (type != null)
			return (PrimitiveType) type;
		else {
			PrimitiveType primitiveType = umlFactory.createPrimitiveType();
			primitiveType.setName(commonName);
			types.getOwnedTypes().add(primitiveType);
			if(applyProfile) {
				OpenAPIProfileUtils.applyAPIDataTypeStereotype((PrimitiveType) primitiveType, jsonDataType, format);
			}
			return primitiveType;
		}
	}

	private Enumeration getOrCreateEnumeration(List<String> literals, String name, Package types, boolean applyProfile) {

		Type type = types.getOwnedType(name, false, UMLPackage.eINSTANCE.getEnumeration(), false);
		if (type != null)
			return (Enumeration) type;
		else {
			Enumeration enumeration = umlFactory.createEnumeration();
			enumeration.setName(name);
			types.getOwnedTypes().add(enumeration);
			for (String l : literals) {
				EnumerationLiteral literal = umlFactory.createEnumerationLiteral();
				literal.setName(l);
				enumeration.getOwnedLiterals().add(literal);
			}
			if(applyProfile) {
				OpenAPIProfileUtils.applyAPIDataTypeStereotype((Enumeration) enumeration, JSONDataType.STRING, null);
			}
			return enumeration;
		}
	}

	/**
	 * Adds a OCL constraint to a concept
	 * 
	 * @param concept        The concept which holds the constraint
	 * @param constraintName The name of the constraint (will be eventually formed
	 *                       as conceptName-constraintName-constraintType
	 * @param constraintType The type of the constraint being applied (e.g.,
	 *                       macLengthConstraint)
	 * @param constraintExp  The OCL expression
	 */
	private void addConstraint(Namespace namespace, String constraintName, String constraintType,
			String constraintExp) {
		Constraint constraint = UMLFactory.eINSTANCE.createConstraint();
		String constraintId = namespace.getName() + "-" + constraintName + "-" + constraintType;
		constraint.setName(constraintId);
		OpaqueExpression expression = UMLFactory.eINSTANCE.createOpaqueExpression();
		expression.getLanguages().add("OCL");
		expression.getBodies().add(constraintExp);
		constraint.setSpecification(expression);
		namespace.getOwnedRules().add(constraint);
	}

	public List<AssociationCandidate> getAssocationCandidates() {
		return assocationCandidates;
	}

	public void setAssocationCandidates(List<AssociationCandidate> assocationCandidates) {
		this.assocationCandidates = assocationCandidates;
	}




}
